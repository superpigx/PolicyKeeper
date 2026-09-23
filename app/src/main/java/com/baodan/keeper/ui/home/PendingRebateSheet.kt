package com.baodan.keeper.ui.home

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.baodan.keeper.R
import com.baodan.keeper.data.Store
import com.baodan.keeper.databinding.ItemPendingRebateBinding
import com.baodan.keeper.databinding.SheetPendingRebateBinding
import com.baodan.keeper.model.Policy
import com.baodan.keeper.ui.policy.EditPolicyActivity
import com.baodan.keeper.util.Money
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 首页「待收返款」提醒条展开后的清单。
 *
 * 为什么做成就地展开的面板，而不是跳到保单页筛选：
 * 提醒条本身是个聚合数字（「¥150 · 2 笔」），用户点它的意图是「看看是哪几张单、
 * 顺手把钱标掉」，不是「去保单列表逛一圈」。就地展开少一层跳转，处理完下滑即回首页。
 *
 * 点行 = 进入该保单的编辑页；行尾的按钮 = 直接把返款标为已收。
 * 标记后行不会消失，而是就地变成「已收」并保留撤销入口 —— 这类涉及钱的操作
 * 一旦手滑就没有回头路，代价太大。
 */
object PendingRebateSheet {

    fun show(context: Context, onChanged: () -> Unit) {
        val store = Store.get(context)
        val b = SheetPendingRebateBinding.inflate(LayoutInflater.from(context))

        // 本次面板会话内被标记为已收的保单：已不在「待收」集合里，
        // 但仍要留在列表中显示为已收（并保留撤销），所以单独记一份
        val justReceived = linkedSetOf<String>()
        val rows = LinkedHashMap<String, ItemPendingRebateBinding>()
        var changed = false

        val dialog = BottomSheetDialog(context, R.style.Theme_PolicyKeeper_BottomSheet)
        dialog.setContentView(b.root)

        /*
         * 和投保对象表单同一个坑：面板高度会随内容变化（标记完后清单会变短、
         * 空态比列表矮），BottomSheet 默认会跟着重算折叠状态，表现为面板跳动。
         * 固定成展开态，让高度变化只在面板内部发生。
         */
        dialog.setOnShowListener {
            dialog.behavior.apply {
                skipCollapsed = true
                isFitToContents = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.setOnDismissListener { if (changed) onChanged() }

        // ==================== 局部函数 ====================

        fun renderSummary() {
            val pending = store.pendingRebatePolicies()
            b.tvSummary.text = if (pending.isEmpty()) {
                context.getString(R.string.rebate_sheet_all_clear, justReceived.size)
            } else {
                context.getString(
                    R.string.rebate_sheet_summary,
                    "¥" + Money.format(pending.sumOf { it.pendingRebate }),
                    pending.size
                )
            }
            // 一笔待收都没有时，批量动作失去意义，连说明文案一起收起来
            b.btnMarkAll.visibility = if (pending.isEmpty()) View.GONE else View.VISIBLE
            b.tvHint.visibility = if (pending.isEmpty()) View.GONE else View.VISIBLE
        }

        /** 已收 / 待收两种表现：颜色、删除线、按钮文案三处一起换，避免只改一半 */
        fun applyRowState(row: ItemPendingRebateBinding, p: Policy) {
            val received = p.rebateReceived

            row.tvAmount.setTextColor(
                ContextCompat.getColor(
                    context, if (received) R.color.text_tertiary else R.color.status_warn
                )
            )
            row.tvAmount.paintFlags = if (received) {
                row.tvAmount.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                row.tvAmount.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            val tint = ContextCompat.getColor(
                context, if (received) R.color.text_tertiary else R.color.brand_secondary
            )
            row.btnMark.text =
                context.getString(if (received) R.string.rebate_undo else R.string.rebate_mark_done)
            row.btnMark.setTextColor(tint)
            row.btnMark.strokeColor = ColorStateList.valueOf(tint)
        }

        fun toggle(p: Policy) {
            val target = !p.rebateReceived
            if (!store.setRebateReceived(p.id, target)) return
            if (target) justReceived.add(p.id) else justReceived.remove(p.id)
            changed = true
            rows[p.id]?.let { applyRowState(it, p) }
            renderSummary()
        }

        fun buildRow(p: Policy): View {
            val row = ItemPendingRebateBinding.inflate(LayoutInflater.from(context), b.boxList, false)

            row.tvInsured.text = store.insuredLabel(p.insuredId)

            // 副标题要能区分「同一对象的哪一年哪一张」，所以带上起保日期
            val category = p.category.ifBlank { "未分类" }
            val start = p.startDate.replace("-", ".")
            row.tvMeta.text = if (start.isBlank()) {
                context.getString(R.string.rebate_row_meta_plain, category)
            } else {
                context.getString(R.string.rebate_row_meta, category, start)
            }

            row.tvAmount.text = "¥" + Money.format(p.rebate)
            applyRowState(row, p)

            row.btnMark.setOnClickListener { toggle(p) }
            row.root.setOnClickListener {
                // 先收起面板再跳转，返回时不会看到一层盖在首页上的旧清单
                dialog.dismiss()
                context.startActivity(EditPolicyActivity.intent(context, p.id))
            }

            rows[p.id] = row
            return row.root
        }

        fun render() {
            b.boxList.removeAllViews()
            rows.clear()
            justReceived.clear()

            val list = store.pendingRebatePolicies()
            if (list.isEmpty()) {
                b.boxList.addView(makeAllDone(context))
            } else {
                list.forEachIndexed { index, p ->
                    b.boxList.addView(buildRow(p))
                    if (index != list.lastIndex) b.boxList.addView(makeDivider(context))
                }
            }
            renderSummary()
        }

        // ==================== 交互 ====================

        b.btnMarkAll.setOnClickListener {
            val count = store.markAllRebateReceived()
            if (count == 0) return@setOnClickListener
            changed = true
            // 就地切到已收态而不是重建列表：行不跳动，也能逐个撤销
            for ((id, row) in rows) {
                store.policyById(id)?.let {
                    applyRowState(row, it)
                    justReceived.add(id)
                }
            }
            renderSummary()
            Toast.makeText(
                context,
                context.getString(R.string.rebate_marked_all, count),
                Toast.LENGTH_SHORT
            ).show()
        }

        render()
        dialog.show()
    }

    // ==================== 局部视图构造 ====================

    /** 全部收完后的空态：不再显示列表，给一个明确的「没事可做」信号 */
    private fun makeAllDone(context: Context): View {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(context, 26f), 0, dp(context, 28f))
        }

        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_inbox)
            imageTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.status_ok))
        }
        box.addView(icon, LinearLayout.LayoutParams(dp(context, 32f), dp(context, 32f)))

        val title = TextView(context).apply {
            text = context.getString(R.string.rebate_all_done)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }
        box.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 12f) }
        )

        val hint = TextView(context).apply {
            text = context.getString(R.string.rebate_all_done_hint)
            setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
            textSize = 12f
            gravity = Gravity.CENTER
        }
        box.addView(
            hint,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 5f) }
        )

        return box
    }

    /** 行之间的细分隔线，用色阶而不是留白来区分记录 */
    private fun makeDivider(context: Context): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(context, 1f).coerceAtLeast(1)
        )
        setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
    }

    private fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
