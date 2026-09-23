package com.baodan.keeper.ui.policy

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import com.baodan.keeper.R
import com.baodan.keeper.data.Store
import com.baodan.keeper.databinding.ActivityEditPolicyBinding
import com.baodan.keeper.databinding.ItemAttachmentBinding
import com.baodan.keeper.model.Attachment
import com.baodan.keeper.model.Categories
import com.baodan.keeper.model.InsuredType
import com.baodan.keeper.model.Policy
import com.baodan.keeper.ui.insured.InsuredDialogs
import com.baodan.keeper.util.Dates
import com.baodan.keeper.util.Money
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class EditPolicyActivity : AppCompatActivity() {

    private lateinit var b: ActivityEditPolicyBinding
    private val store get() = Store.get(this)

    private var isNew = true
    private var policyId: String = UUID.randomUUID().toString()
    private var selectedInsuredId: String? = null

    private val insuredIds = mutableListOf<String>()
    private val insuredLabels = mutableListOf<String>()

    private val currentAttachments = mutableListOf<Attachment>()
    private val initialAttachmentNames = mutableSetOf<String>()

    private var saved = false
    private var pendingPhoto: File? = null

    private val pickFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val a = store.importAttachment(uri, queryDisplayName(uri))
            if (a == null) {
                toast("无法读取该文件")
            } else {
                currentAttachments.add(a)
                renderAttachments()
            }
        }

    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok: Boolean ->
            val f = pendingPhoto
            pendingPhoto = null
            if (ok && f != null && f.exists()) {
                currentAttachments.add(
                    Attachment(
                        storedName = f.name,
                        displayName = "保单照片.jpg",
                        mime = "image/jpeg",
                        size = f.length(),
                        addedAt = System.currentTimeMillis()
                    )
                )
                renderAttachments()
            } else {
                f?.delete()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityEditPolicyBinding.inflate(layoutInflater)
        setContentView(b.root)

        val id = intent.getStringExtra(EXTRA_ID)
        if (id != null) {
            val p = store.policyById(id)
            if (p == null) {
                finish()
                return
            }
            isNew = false
            policyId = p.id
            selectedInsuredId = p.insuredId
            b.etInsurer.setText(p.insurer)
            b.etPolicyNo.setText(p.policyNo)
            b.etStart.setText(p.startDate)
            b.etEnd.setText(p.endDate)
            b.etPremium.setText(if (p.premium == 0.0) "" else trimNumber(p.premium))
            b.etRebate.setText(if (p.rebate == 0.0) "" else trimNumber(p.rebate))
            b.toggleRebateState.check(if (p.rebateReceived) R.id.btnReceived else R.id.btnPending)
            b.etNote.setText(p.note)
            b.etCategory.setText(p.category, false)
            b.etCycle.setText(p.cycle, false)
            currentAttachments.addAll(p.attachments)
            initialAttachmentNames.addAll(p.attachments.map { it.storedName })
        } else {
            b.etStart.setText(Dates.todayText())
            val end = Dates.today().apply {
                add(Calendar.YEAR, 1)
                add(Calendar.DAY_OF_MONTH, -1)
            }
            b.etEnd.setText(Dates.format(end))
            b.etCycle.setText("年缴", false)
            // 新保单默认返款「待收」，等钱到手再改
            b.toggleRebateState.check(R.id.btnPending)
        }

        b.tvTitle.setText(if (isNew) R.string.form_title_new else R.string.form_title_edit)
        b.btnDelete.visibility = if (isNew) View.GONE else View.VISIBLE

        b.etCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, Categories.all)
        )
        b.etCategory.threshold = 1
        b.etCycle.setAdapter(
            ArrayAdapter(
                this, android.R.layout.simple_list_item_1,
                listOf("年缴", "月缴", "趸缴", "其他")
            )
        )
        b.etCycle.threshold = 0

        // 原价 / 返款 任一变化都实时刷新净保费
        b.etPremium.doAfterTextChanged { refreshNetPreview() }
        b.etRebate.doAfterTextChanged { refreshNetPreview() }

        b.etInsured.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                updateInsuredText()
                InsuredDialogs.show(this, null) { newId ->
                    selectedInsuredId = newId
                    refreshInsuredDropdown()
                    val type = store.insuredById(newId)?.type ?: InsuredType.VEHICLE
                    b.etCategory.setAdapter(
                        ArrayAdapter(
                            this, android.R.layout.simple_list_item_1,
                            Categories.suggest(type)
                        )
                    )
                }
            } else {
                selectedInsuredId = insuredIds.getOrNull(position - 1)
                updateInsuredText()
            }
        }

        b.etStart.setOnClickListener { pickDate(b.etStart.text?.toString()) { b.etStart.setText(it) } }
        b.etEnd.setOnClickListener { pickDate(b.etEnd.text?.toString()) { b.etEnd.setText(it) } }

        b.btnAddAttachment.setOnClickListener { showAttachMenu() }
        b.btnBack.setOnClickListener { finish() }
        b.btnSave.setOnClickListener { save() }
        b.btnDelete.setOnClickListener { confirmDelete() }

        refreshInsuredDropdown()
        renderAttachments()
        refreshNetPreview()

        if (!isNew) {
            b.etCategory.setAdapter(
                ArrayAdapter(this, android.R.layout.simple_list_item_1, Categories.all)
            )
        }

        // 首次使用：还没有任何投保对象时直接引导添加
        if (isNew && store.insureds.isEmpty()) {
            b.root.post {
                InsuredDialogs.show(this, null) { newId ->
                    selectedInsuredId = newId
                    refreshInsuredDropdown()
                    val type = store.insuredById(newId)?.type ?: InsuredType.VEHICLE
                    b.etCategory.setAdapter(
                        ArrayAdapter(
                            this, android.R.layout.simple_list_item_1,
                            Categories.suggest(type)
                        )
                    )
                }
            }
        }
    }

    // ===================== 投保对象下拉 =====================

    private fun refreshInsuredDropdown() {
        insuredIds.clear()
        insuredLabels.clear()
        val items = mutableListOf("＋ 新增投保对象…")
        for (v in store.insureds) {
            val tag = if (v.type == InsuredType.VEHICLE) "🚗 " else "👤 "
            val label = tag + v.name + if (v.detail.isNotBlank()) "（${v.detail}）" else ""
            insuredIds.add(v.id)
            insuredLabels.add(label)
            items.add(label)
        }
        b.etInsured.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        )
        b.etInsured.threshold = 0
        updateInsuredText()
    }

    private fun updateInsuredText() {
        val idx = insuredIds.indexOf(selectedInsuredId)
        b.etInsured.setText(if (idx >= 0) insuredLabels[idx] else "", false)
    }

    // ===================== 净保费实时计算 =====================

    /** 原价 − 返款 = 实际净保费，输入时实时刷新 */
    private fun refreshNetPreview() {
        val original = Money.parse(b.etPremium.text?.toString()) ?: 0.0
        val rebate = Money.parse(b.etRebate.text?.toString()) ?: 0.0
        val net = (original - rebate).coerceAtLeast(0.0)
        val over = rebate > original

        b.tvNetPremium.text = "¥" + Money.format(net)
        b.tvNetPremium.setTextColor(
            ContextCompat.getColor(this, if (over) R.color.status_expired else R.color.brand_primary)
        )
        b.tvNetHint.text = if (over) {
            getString(R.string.form_net_negative)
        } else {
            getString(R.string.form_net_hint_value, Money.format(original), Money.format(rebate))
        }
        b.tvNetHint.setTextColor(
            ContextCompat.getColor(
                this, if (over) R.color.status_expired else R.color.text_secondary
            )
        )
        b.boxNetPremium.setBackgroundResource(
            if (over) R.drawable.bg_net_premium_warn else R.drawable.bg_net_premium
        )
        // 只有填了返款才需要选「待收 / 已收」
        b.boxRebateState.visibility = if (rebate > 0.0) View.VISIBLE else View.GONE
    }

    // ===================== 日期 =====================

    private fun pickDate(initial: String?, onPicked: (String) -> Unit) {
        val c = Dates.parse(initial) ?: Dates.today()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, y)
                    set(Calendar.MONTH, m)
                    set(Calendar.DAY_OF_MONTH, d)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onPicked(Dates.format(cal))
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ===================== 附件 =====================

    private fun showAttachMenu() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.form_add_attachment)
            .setItems(arrayOf("拍照", "从相册或文件中选择")) { _, which ->
                if (which == 0) startCamera() else pickFile.launch(arrayOf("*/*"))
            }
            .show()
    }

    private fun startCamera() {
        try {
            val f = File(store.attachDir, UUID.randomUUID().toString() + ".jpg")
            pendingPhoto = f
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            takePhoto.launch(uri)
        } catch (e: Exception) {
            toast("无法启动相机")
        }
    }

    private fun renderAttachments() {
        b.boxAttachments.removeAllViews()
        for (a in currentAttachments) {
            val item = ItemAttachmentBinding.inflate(layoutInflater, b.boxAttachments, false)
            item.tvName.text = a.displayName
            item.tvMeta.text = buildMeta(a)
            if (a.mime.startsWith("image/")) {
                val bmp = decodeThumb(store.fileOf(a), 160)
                if (bmp != null) {
                    item.ivThumb.setImageBitmap(bmp)
                    item.ivThumb.imageTintList = null
                }
            }
            item.btnRemove.setOnClickListener {
                currentAttachments.remove(a)
                renderAttachments()
            }
            item.root.setOnClickListener { openAttachment(a) }
            b.boxAttachments.addView(item.root)
        }
    }

    private fun buildMeta(a: Attachment): String {
        val type = when {
            a.mime.startsWith("image/") -> a.mime.substringAfter('/').uppercase(Locale.US)
            a.mime.contains("pdf") -> "PDF"
            a.mime.isBlank() -> "文件"
            else -> a.mime.substringAfter('/').uppercase(Locale.US)
        }
        return "$type · ${formatSize(a.size)}"
    }

    private fun formatSize(size: Long): String = when {
        size >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", size / 1048576.0)
        size >= 1024 -> String.format(Locale.US, "%.0f KB", size / 1024.0)
        else -> "$size B"
    }

    private fun decodeThumb(file: File, target: Int): Bitmap? {
        if (!file.exists()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var scale = 1
            while (opts.outWidth / scale > target || opts.outHeight / scale > target) scale *= 2
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                inSampleSize = scale
            })
        } catch (e: Exception) {
            null
        }
    }

    private fun openAttachment(a: Attachment) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", store.fileOf(a))
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, a.mime.ifBlank { "*/*" })
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            toast("没有可以打开该文件的应用")
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) c.getString(i) else null
        }
    } catch (e: Exception) {
        null
    }

    // ===================== 保存 / 删除 =====================

    private fun save() {
        val insuredId = selectedInsuredId
        if (insuredId.isNullOrBlank()) {
            b.etInsured.error = getString(R.string.form_insured_hint)
            return
        }
        val category = b.etCategory.text?.toString()?.trim().orEmpty()
        if (category.isEmpty()) {
            b.etCategory.error = getString(R.string.form_required)
            return
        }
        val startText = b.etStart.text?.toString()?.trim().orEmpty()
        val endText = b.etEnd.text?.toString()?.trim().orEmpty()
        val start = Dates.parse(startText)
        val end = Dates.parse(endText)
        if (start == null) {
            b.etStart.error = getString(R.string.form_date_invalid)
            return
        }
        if (end == null) {
            b.etEnd.error = getString(R.string.form_date_invalid)
            return
        }
        if (end.timeInMillis < start.timeInMillis) {
            b.etEnd.error = getString(R.string.form_end_before_start)
            return
        }
        val premiumText = b.etPremium.text?.toString()?.trim().orEmpty()
        val premium = if (premiumText.isEmpty()) 0.0 else Money.parse(premiumText)
        if (premium == null || premium < 0) {
            b.etPremium.error = getString(R.string.form_premium_invalid)
            return
        }
        val rebateText = b.etRebate.text?.toString()?.trim().orEmpty()
        val rebate = if (rebateText.isEmpty()) 0.0 else Money.parse(rebateText)
        if (rebate == null || rebate < 0) {
            b.etRebate.error = getString(R.string.form_rebate_invalid)
            return
        }
        if (rebate > premium) {
            b.etRebate.error = getString(R.string.form_net_negative)
            return
        }

        val p = Policy(
            id = policyId,
            insuredId = insuredId,
            category = category,
            insurer = b.etInsurer.text?.toString()?.trim().orEmpty(),
            policyNo = b.etPolicyNo.text?.toString()?.trim().orEmpty(),
            startDate = Dates.format(start),
            endDate = Dates.format(end),
            premium = premium,
            rebate = rebate,
            rebateReceived = rebate > 0.0 &&
                b.toggleRebateState.checkedButtonId == R.id.btnReceived,
            cycle = b.etCycle.text?.toString()?.trim().orEmpty().ifBlank { "年缴" },
            note = b.etNote.text?.toString()?.trim().orEmpty(),
            attachments = currentAttachments.toMutableList()
        )
        store.upsertPolicy(p)

        // 清理被移除的附件文件
        for (name in initialAttachmentNames) {
            if (currentAttachments.none { it.storedName == name }) {
                File(store.attachDir, name).takeIf { it.exists() }?.delete()
            }
        }

        saved = true
        toast(getString(R.string.form_saved))
        finish()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.form_delete_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.deletePolicy(policyId)
                saved = true
                toast(getString(R.string.form_deleted))
                finish()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 放弃编辑时清理本次新导入但未保存的附件
        if (!saved) {
            for (a in currentAttachments) {
                if (!initialAttachmentNames.contains(a.storedName)) {
                    store.deleteAttachmentFile(a)
                }
            }
        }
    }

    private fun trimNumber(v: Double): String =
        if (Math.abs(v % 1.0) < 0.005) v.toLong().toString() else v.toString()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val EXTRA_ID = "policy_id"

        fun intent(ctx: Context, policyId: String?): Intent =
            Intent(ctx, EditPolicyActivity::class.java).apply { putExtra(EXTRA_ID, policyId) }
    }
}
