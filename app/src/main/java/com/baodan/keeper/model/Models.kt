package com.baodan.keeper.model

import java.util.UUID

/** 投保对象类型 */
enum class InsuredType(val code: String, val label: String) {
    VEHICLE("vehicle", "车辆"),
    PERSON("person", "人员");

    companion object {
        fun from(code: String?): InsuredType =
            if (code == PERSON.code) PERSON else VEHICLE
    }
}

/** 投保对象：一辆车，或一位家庭成员 */
data class Insured(
    var id: String = UUID.randomUUID().toString(),
    var type: InsuredType = InsuredType.VEHICLE,
    /** 车牌号 或 姓名 */
    var name: String = "",
    /** 车型 或 与本人关系（本人 / 配偶 / 子女 / 父母） */
    var detail: String = "",
    var note: String = ""
)

/** 保单附件 */
data class Attachment(
    var storedName: String = "",
    var displayName: String = "",
    var mime: String = "",
    var size: Long = 0L,
    var addedAt: Long = System.currentTimeMillis()
)

/** 一份保单记录 */
data class Policy(
    var id: String = UUID.randomUUID().toString(),
    /** 关联的投保对象 id */
    var insuredId: String = "",
    /** 险种类别，如 交强险 / 车损险 / 重疾险 */
    var category: String = "",
    var insurer: String = "",
    var policyNo: String = "",
    /** yyyy-MM-dd */
    var startDate: String = "",
    /** yyyy-MM-dd */
    var endDate: String = "",
    /** 原价：实际支付的金额（元） */
    var premium: Double = 0.0,
    /** 返款：优惠返点金额（元），可为 0 */
    var rebate: Double = 0.0,
    /** 返款到账状态：false = 待收，true = 已收 */
    var rebateReceived: Boolean = false,
    /** 缴费方式：年缴 / 月缴 / 趸缴 */
    var cycle: String = "年缴",
    var note: String = "",
    var attachments: MutableList<Attachment> = mutableListOf()
) {
    /** 实际净保费 = 原价 − 返款（元） */
    val netPremium: Double
        get() = if (rebate <= 0.0) premium else (premium - rebate).coerceAtLeast(0.0)

    /** 记账口径金额：net = true 取净保费，false 取原价 */
    fun amount(net: Boolean): Double = if (net) netPremium else premium

    /** 是否拿过返款 */
    fun hasRebate(): Boolean = rebate > 0.0

    /** 已到账的返款金额 */
    val receivedRebate: Double
        get() = if (hasRebate() && rebateReceived) rebate else 0.0

    /** 还没到账的返款金额 */
    val pendingRebate: Double
        get() = if (hasRebate() && !rebateReceived) rebate else 0.0

    /** 剩余天数，负数表示已过期 */
    fun daysLeft(): Long = com.baodan.keeper.util.Dates.daysFromToday(endDate) ?: Long.MAX_VALUE

    fun isExpired(): Boolean = daysLeft() < 0

    fun isExpiringSoon(days: Int = 30): Boolean {
        val d = daysLeft()
        return d in 0..days.toLong()
    }
}

/**
 * 保单状态：全 App 唯一的判定结果，任何地方要表达「这张保单现在怎么样」都必须用它。
 *
 * 之所以把判定收敛成一个枚举，是因为状态原本散落在卡片渲染与首页提示两处各写一套，
 * 一旦规则调整（例如续保窗口变化）就会出现「列表说已续保、卡片还喊可续保」的自相矛盾。
 */
enum class PolicyStatus {
    /** 已被保障期更晚的保单接续，属于历史归档 */
    RENEWED,

    /** 已过期且没有接续，存在脱保风险 */
    EXPIRED,

    /** 起保日在未来，保险还没开始 */
    PENDING,

    /** 今天到期 */
    DUE_TODAY,

    /** 已进入该险种的续保窗口，可以续保了 */
    RENEWABLE,

    /** 保障期内且无需动作 */
    ACTIVE;

    /** 归档态：已续保的保单不再需要关注，列表中降权并沉底 */
    val isArchived: Boolean
        get() = this == RENEWED

    /** 需要用户采取行动：该续保、该处理 */
    val needsAction: Boolean
        get() = this == EXPIRED || this == DUE_TODAY || this == RENEWABLE
}

/** 预置险种，供选择与自动补全 */
object Categories {

    val vehicle = listOf(
        "交强险", "车船税", "车损险", "第三者责任险", "车上人员责任险",
        "全车盗抢险", "玻璃单独破碎险", "车身划痕险", "不计免赔", "商业综合险"
    )

    val person = listOf(
        "百万医疗险", "医疗险", "重疾险", "意外险", "定期寿险",
        "门急诊险", "住院津贴", "教育金/年金", "惠民保", "高端医疗"
    )

    val other = listOf(
        "家财险", "雇主责任险", "宠物险", "旅行意外险", "责任险"
    )

    val all: List<String> = vehicle + person + other

    fun suggest(type: InsuredType): List<String> = when (type) {
        InsuredType.VEHICLE -> vehicle + other
        InsuredType.PERSON -> person + other
    }
}
