package com.baodan.keeper.ui.policy

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import androidx.core.content.ContextCompat
import com.baodan.keeper.R
import com.baodan.keeper.data.Store
import com.baodan.keeper.databinding.ItemPolicyBinding
import com.baodan.keeper.model.Policy
import com.baodan.keeper.util.Money

/** 保单卡片的统一渲染逻辑，首页与列表页共用 */
object PolicyItemBinder {

    fun bind(b: ItemPolicyBinding, p: Policy, store: Store) {
        val ctx = b.root.context

        // 状态由 Store 的状态机统一裁决，卡片只负责表达，不参与判定
        val status = store.statusOf(p)
        val archived = status.isArchived

        b.tvCategory.text = p.category.ifBlank { "未分类" }

        val insurer = p.insurer.ifBlank { "未填写公司" }
        b.tvSubtitle.text = "$insurer · ${store.insuredLabel(p.insuredId)}"

        b.tvDates.text = "${dotted(p.startDate)} → ${dotted(p.endDate)}"
        // 主金额显示实际净保费（原价 − 返款）
        b.tvPremium.text = "¥" + Money.format(p.netPremium)

        // 状态标签：文案与配色都取自 PolicyStatusUi，全局只有这一份定义
        b.tvStatus.text = ctx.getString(PolicyStatusUi.labelRes(status))
        b.tvStatus.setTextColor(ContextCompat.getColor(ctx, PolicyStatusUi.fgRes(status)))
        b.tvStatus.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(ctx, PolicyStatusUi.bgRes(status)))

        // 归档降权：已续保的保单已经完成使命，不该再和待办事项抢注意力，
        // 于是把标题与金额从主色降为次要色，让它在列表中自然沉下去
        b.tvCategory.setTextColor(
            ContextCompat.getColor(
                ctx, if (archived) R.color.text_secondary else R.color.text_primary
            )
        )
        b.tvPremium.setTextColor(
            ContextCompat.getColor(
                ctx, if (archived) R.color.text_tertiary else R.color.brand_primary
            )
        )

        // 续至信息：回答「这份旧保单被接续到了什么时候」，避免「已续保 = 失效」的误读
        val renewedTo = if (archived) store.renewedTo(p) else null
        if (renewedTo != null) {
            b.tvRenewedTo.visibility = View.VISIBLE
            b.tvRenewedTo.text =
                ctx.getString(R.string.card_renewed_to, dotted(renewedTo.endDate))
        } else {
            b.tvRenewedTo.visibility = View.GONE
        }

        val hasAttach = p.attachments.isNotEmpty()
        val hasRebate = p.hasRebate()
        b.rowBottom.visibility = if (hasAttach || hasRebate) View.VISIBLE else View.GONE

        if (hasAttach) {
            b.tvAttach.visibility = View.VISIBLE
            b.tvAttach.text = "📎 ${p.attachments.size} 个附件"
        } else {
            b.tvAttach.visibility = View.GONE
        }

        if (hasRebate) {
            b.tvRebateNote.visibility = View.VISIBLE
            val note = ctx.getString(
                R.string.card_rebate_note,
                Money.format(p.premium),
                Money.format(p.rebate)
            )
            val state = ctx.getString(
                if (p.rebateReceived) R.string.rebate_received else R.string.rebate_pending
            )
            b.tvRebateNote.text = "$note · $state"
            // 待收用橙色标出来，钱没到手最需要被看见
            b.tvRebateNote.setTextColor(
                ContextCompat.getColor(
                    ctx, if (p.rebateReceived) R.color.text_tertiary else R.color.status_warn
                )
            )
            b.tvRebateNote.setTypeface(null, if (p.rebateReceived) Typeface.NORMAL else Typeface.BOLD)
        } else {
            b.tvRebateNote.visibility = View.GONE
        }
    }

    /** 日期压成 2025.03.01：比横杠紧凑，不与金额争抢横向空间 */
    private fun dotted(date: String): String = date.replace("-", ".")
}
