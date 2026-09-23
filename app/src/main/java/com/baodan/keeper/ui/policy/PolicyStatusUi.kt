package com.baodan.keeper.ui.policy

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.baodan.keeper.R
import com.baodan.keeper.model.PolicyStatus

/**
 * 保单状态的视觉编码：一个状态对应一组「文案 + 前景色 + 浅底」。
 *
 * 配色按语义分组，而不是按好看分组：
 * - 需要行动（可续保 / 今天到期 / 已过期）走暖色，越紧急越红
 * - 无需行动（生效中）走绿色，未生效走品牌蓝
 * - 已完成（已续保）走中性灰，让它在视觉上退出待办队列
 *
 * 前景色与浅底成对定义，保证任意组合下文字对比度都达到 WCAG AA。
 */
object PolicyStatusUi {

    @StringRes
    fun labelRes(status: PolicyStatus): Int = when (status) {
        PolicyStatus.RENEWED -> R.string.status_renewed
        PolicyStatus.EXPIRED -> R.string.status_expired
        PolicyStatus.PENDING -> R.string.status_pending
        PolicyStatus.DUE_TODAY -> R.string.status_due_today
        PolicyStatus.RENEWABLE -> R.string.status_renewable
        PolicyStatus.ACTIVE -> R.string.status_active
    }

    @ColorRes
    fun fgRes(status: PolicyStatus): Int = when (status) {
        PolicyStatus.RENEWED -> R.color.text_tertiary
        PolicyStatus.EXPIRED -> R.color.status_expired
        PolicyStatus.PENDING -> R.color.brand_primary
        PolicyStatus.DUE_TODAY -> R.color.status_warn
        PolicyStatus.RENEWABLE -> R.color.status_warn
        PolicyStatus.ACTIVE -> R.color.status_ok
    }

    @ColorRes
    fun bgRes(status: PolicyStatus): Int = when (status) {
        PolicyStatus.RENEWED -> R.color.divider
        PolicyStatus.EXPIRED -> R.color.status_expired_bg
        PolicyStatus.PENDING -> R.color.brand_primary_container
        PolicyStatus.DUE_TODAY -> R.color.status_warn_bg
        PolicyStatus.RENEWABLE -> R.color.status_warn_bg
        PolicyStatus.ACTIVE -> R.color.status_ok_bg
    }
}
