package com.baodan.keeper.ui.insured

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.baodan.keeper.R
import com.baodan.keeper.data.Store
import com.baodan.keeper.databinding.DialogEditInsuredBinding
import com.baodan.keeper.databinding.ItemFilterChipBinding
import com.baodan.keeper.model.Insured
import com.baodan.keeper.model.InsuredType
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 新增 / 编辑投保对象的底部表单，保单表单与对象管理页共用。
 *
 * 用底部面板而不是居中对话框：表单是全宽输入的场景，底部弹出时输入框不被窄边挤压，
 * 键盘弹出也能与内容自然贴合。
 */
object InsuredDialogs {

    private val relations = listOf("本人", "配偶", "子女", "父母")

    fun show(context: Context, existing: Insured?, onSaved: (String) -> Unit) {
        val store = Store.get(context)
        val b = DialogEditInsuredBinding.inflate(LayoutInflater.from(context))
        val editing = existing != null

        b.tvTitle.setText(
            if (editing) R.string.insured_edit_title else R.string.insured_add_title
        )

        if (existing != null) {
            b.etName.setText(existing.name)
            b.etDetail.setText(existing.detail)
            b.etNote.setText(existing.note)
        }

        // 常用关系快捷标签（仅人员可见）
        val chips = mutableListOf<Pair<String, Chip>>()
        for (r in relations) {
            val chip = ItemFilterChipBinding
                .inflate(LayoutInflater.from(context), b.relationChips, false).root
            chip.id = View.generateViewId()
            chip.text = r
            b.relationChips.addView(chip)
            chips.add(r to chip)
        }
        b.relationChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val idx = chips.indexOfFirst { it.second.id == checkedIds.firstOrNull() }
            if (idx >= 0) b.etDetail.setText(chips[idx].first)
        }

        var type = existing?.type ?: InsuredType.VEHICLE

        /**
         * 切换对象类型时，字段标签与示例同步改写。
         * hint 承担浮动标签（填写后仍可见），placeholder 只在空值时给例子，两者不重复。
         */
        fun applyType(t: InsuredType) {
            type = t
            val vehicle = t == InsuredType.VEHICLE
            b.tilName.hint = context.getString(
                if (vehicle) R.string.insured_name_vehicle else R.string.insured_name_person
            )
            b.tilName.placeholderText = context.getString(
                if (vehicle) R.string.insured_ph_name_vehicle else R.string.insured_ph_name_person
            )
            b.tilDetail.hint = context.getString(
                if (vehicle) R.string.insured_detail_vehicle else R.string.insured_detail_person
            )
            b.tilDetail.placeholderText = context.getString(
                if (vehicle) R.string.insured_ph_detail_vehicle else R.string.insured_ph_detail_person
            )
            b.boxRelation.visibility = if (vehicle) View.GONE else View.VISIBLE
        }

        val dialog = BottomSheetDialog(context, R.style.Theme_PolicyKeeper_BottomSheet)
        dialog.setContentView(b.root)
        // 键盘弹出时压缩内容区并允许滚动，而不是把整个面板顶出屏幕
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        /*
         * 面板内容的高度是变化的：切到「人员」会多出「常用关系」一行，切回「车辆」又收起来。
         * BottomSheet 默认会跟着重新测量，并把折叠状态一起重算 —— 结果就是面板突然向上跳、
         * 甚至被顶到屏幕外，用户看到的就是「弹飞」。
         *
         * 这里直接在显示时把折叠态排除掉并锁定为展开：高度变化只发生在面板内部，
         * 不再牵动整个窗口的定位。内容矮时面板依然自然贴底（isFitToContents）。
         */
        dialog.setOnShowListener {
            dialog.behavior.apply {
                skipCollapsed = true
                isFitToContents = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        // 先定初始状态再挂监听，避免初始化时被回调打断
        b.typeToggle.check(
            if (type == InsuredType.PERSON) R.id.btnPerson else R.id.btnVehicle
        )
        applyType(type)
        b.typeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            applyType(
                if (checkedId == R.id.btnPerson) InsuredType.PERSON else InsuredType.VEHICLE
            )
        }

        // 编辑时把已填的关系同步到快捷选择
        if (existing != null && existing.type == InsuredType.PERSON) {
            chips.firstOrNull { it.first == existing.detail }?.second?.isChecked = true
        }

        b.btnClose.setOnClickListener { dialog.dismiss() }

        // 编辑态才给删除入口，新增态保存按钮独占整宽
        b.btnDelete.visibility = if (editing) View.VISIBLE else View.GONE
        if (existing != null) {
            b.btnDelete.setOnClickListener {
                confirmDelete(context, existing, store) {
                    dialog.dismiss()
                    onSaved(existing.id)
                }
            }
        }

        b.btnSave.setOnClickListener {
            val name = b.etName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                // 校验不通过时不关闭面板，避免辛苦填的内容丢失
                b.tilName.error = context.getString(R.string.form_required)
                b.etName.requestFocus()
                return@setOnClickListener
            }
            b.tilName.error = null

            val item = existing ?: Insured()
            item.type = type
            item.name = name
            item.detail = b.etDetail.text?.toString()?.trim().orEmpty()
            item.note = b.etNote.text?.toString()?.trim().orEmpty()
            store.upsertInsured(item)
            dialog.dismiss()
            onSaved(item.id)
        }

        dialog.show()
    }

    /** 删除确认：有保单时明确告知会连带删除，避免误操作 */
    private fun confirmDelete(
        context: Context,
        item: Insured,
        store: Store,
        after: () -> Unit
    ) {
        val count = store.policyCountOf(item.id)
        val message = if (count > 0) {
            context.getString(R.string.insured_delete_confirm, count)
        } else {
            context.getString(R.string.insured_delete_confirm_empty, item.name)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.delete)
            .setMessage(message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.deleteInsured(item.id)
                after()
            }
            .show()
    }
}
