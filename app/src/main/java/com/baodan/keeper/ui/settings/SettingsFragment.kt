package com.baodan.keeper.ui.settings

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.baodan.keeper.R
import com.baodan.keeper.data.Store
import com.baodan.keeper.databinding.FragmentSettingsBinding
import com.baodan.keeper.ui.insured.InsuredListActivity
import com.baodan.keeper.util.Updater
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val store get() = Store.get(requireContext())

    /** 防止重复点击「检查更新」 */
    private var checkingUpdate = false

    private val exportBackup =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                requireContext().contentResolver.openOutputStream(uri)?.use { store.exportZip(it) }
                toast("备份已导出")
            } catch (e: Exception) {
                toast(getString(R.string.export_fail))
            }
        }

    private val exportCsv =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                requireContext().contentResolver.openOutputStream(uri)?.use {
                    store.exportCsv(it, null)
                }
                toast("CSV 已导出")
            } catch (e: Exception) {
                toast(getString(R.string.export_fail))
            }
        }

    private val importBackup =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_import)
                .setMessage(R.string.settings_import_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ -> doImport(uri) }
                .show()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rowInsured.ivIcon.setImageResource(R.drawable.ic_person)
        binding.rowInsured.tvTitle.setText(R.string.settings_insured_manage)
        binding.rowInsured.tvDesc.setText(R.string.settings_insured_manage_desc)
        binding.rowInsured.root.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), InsuredListActivity::class.java))
        }

        binding.rowExport.ivIcon.setImageResource(R.drawable.ic_download)
        binding.rowExport.tvTitle.setText(R.string.settings_export)
        binding.rowExport.tvDesc.setText(R.string.settings_export_desc)
        binding.rowExport.root.setOnClickListener {
            exportBackup.launch(store.backupFileName())
        }

        binding.rowExportCsv.ivIcon.setImageResource(R.drawable.ic_tab_policy)
        binding.rowExportCsv.tvTitle.setText(R.string.settings_export_csv)
        binding.rowExportCsv.tvDesc.setText(R.string.settings_export_csv_desc)
        binding.rowExportCsv.root.setOnClickListener {
            exportCsv.launch("保单明细_${com.baodan.keeper.util.Dates.todayText()}.csv")
        }

        binding.rowImport.ivIcon.setImageResource(R.drawable.ic_upload)
        binding.rowImport.tvTitle.setText(R.string.settings_import)
        binding.rowImport.tvDesc.setText(R.string.settings_import_desc)
        binding.rowImport.root.setOnClickListener {
            importBackup.launch(arrayOf("*/*"))
        }

        binding.rowClear.ivIcon.setImageResource(R.drawable.ic_delete)
        binding.rowClear.tvTitle.setText(R.string.settings_clear)
        binding.rowClear.tvDesc.setText(R.string.settings_clear_desc)
        binding.rowClear.root.setOnClickListener { confirmClear() }

        binding.rowCheckUpdate.ivIcon.setImageResource(R.drawable.ic_update)
        binding.rowCheckUpdate.tvTitle.setText(R.string.settings_check_update)
        binding.rowCheckUpdate.tvDesc.setText(getString(R.string.settings_check_update_desc, appVersion()))
        binding.rowCheckUpdate.root.setOnClickListener { checkUpdate() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render() {
        binding.tvDataCount.text = getString(
            R.string.settings_data_count,
            store.insureds.size,
            store.policies.size
        )
        binding.tvVersion.text = "保单管家 · " + getString(R.string.settings_version, appVersion())
        if (!checkingUpdate) {
            binding.rowCheckUpdate.tvDesc.text =
                getString(R.string.settings_check_update_desc, appVersion())
        }
    }

    // ==================== 检查更新 ====================

    /** 把状态行还原成「当前版本 x.y」 */
    private fun resetUpdateRow() {
        _binding?.rowCheckUpdate?.tvDesc?.text =
            getString(R.string.settings_check_update_desc, appVersion())
    }

    private fun checkUpdate() {
        if (checkingUpdate) return
        if (!Updater.isConfigured()) {
            toast(getString(R.string.update_not_configured))
            return
        }

        checkingUpdate = true
        binding.rowCheckUpdate.tvDesc.setText(R.string.update_checking)

        Updater.check { result ->
            checkingUpdate = false
            if (_binding == null) return@check
            resetUpdateRow()

            when (result) {
                is Updater.Result.UpToDate -> MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.update_latest_title)
                    .setMessage(getString(R.string.update_latest_msg, appVersion()))
                    .setPositiveButton(R.string.confirm, null)
                    .show()

                is Updater.Result.Available -> showUpdateDialog(result.release)

                is Updater.Result.Failed -> MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.update_fail_title)
                    .setMessage(result.message)
                    .setPositiveButton(R.string.confirm, null)
                    .show()
            }
        }
    }

    private fun showUpdateDialog(release: Updater.Release) {
        val sizeLine = Updater.formatSize(release.sizeBytes)
            .takeIf { it.isNotEmpty() }
            ?.let { "\n" + getString(R.string.update_size, it) }
            .orEmpty()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.update_found_title, release.version))
            .setMessage(getString(R.string.update_found_msg, appVersion(), release.version) +
                    sizeLine + cleanNotes(release.notes))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.update_now) { _, _ -> startDownload(release) }
            .show()
    }

    private fun startDownload(release: Updater.Release) {
        binding.rowCheckUpdate.tvDesc.setText(R.string.update_preparing)

        Updater.download(
            context = requireContext(),
            release = release,
            onProgress = { percent ->
                _binding?.rowCheckUpdate?.tvDesc?.text =
                    getString(R.string.update_downloading, percent)
            },
            onDone = { file ->
                if (_binding == null) return@download
                resetUpdateRow()

                if (file == null) {
                    toast(getString(R.string.update_download_fail))
                    return@download
                }
                if (!Updater.canInstall(requireContext())) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.update_need_permission_title)
                        .setMessage(R.string.update_need_permission_msg)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.update_go_settings) { _, _ ->
                            Updater.openInstallPermissionSettings(requireContext())
                        }
                        .show()
                    return@download
                }
                toast(getString(R.string.update_installing))
                if (!Updater.install(requireContext(), file)) {
                    toast(getString(R.string.update_install_fail))
                }
            },
            onError = { message ->
                if (_binding == null) return@download
                resetUpdateRow()
                toast(message)
            }
        )
    }

    /** Release 说明一般是 Markdown，这里只做轻度清理，保留可读的纯文本 */
    private fun cleanNotes(raw: String): String {
        if (raw.isBlank()) return ""
        val text = raw
            .replace(Regex("(?m)^#{1,6}\\s*"), "")
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("[*`>]"), "")
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .trim()
        return if (text.isBlank()) "" else "\n\n" + text.take(600)
    }

    private fun doImport(uri: Uri) {
        val count = try {
            requireContext().contentResolver.openInputStream(uri)?.use { store.importFrom(it) } ?: -1
        } catch (e: Exception) {
            -1
        }
        if (count >= 0) {
            toast(getString(R.string.settings_import_ok, count))
            render()
        } else {
            toast(getString(R.string.settings_import_fail))
        }
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_clear)
            .setMessage(R.string.settings_clear_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.clearAll()
                toast(getString(R.string.settings_cleared))
                render()
            }
            .show()
    }

    private fun appVersion(): String = try {
        val ctx = requireContext()
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}
