package com.baodan.keeper.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import com.baodan.keeper.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * 基于 GitHub Releases 的更新检查。
 *
 * 流程：读取 latest release 的 tag 与 APK 资源 → 与当前 versionName 比较 →
 * 下载到 cacheDir → 通过 FileProvider 交给系统安装器。
 *
 * 不引入 OkHttp / 协程等新依赖，保持项目「零额外依赖」的构建约定：
 * 网络走 HttpURLConnection，JSON 走 Android 内置的 org.json，
 * 线程用单线程池 + 主线程 Handler 回传。
 */
object Updater {

    private const val TAG_APK_SUFFIX = ".apk"

    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long
    )

    sealed class Result {
        /** 有新版本可用 */
        data class Available(val release: Release) : Result()
        /** 已是最新 */
        object UpToDate : Result()
        /** 检查失败，message 已可直接展示给用户 */
        data class Failed(val message: String) : Result()
    }

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** 仓库地址未配置时，更新功能不可用 */
    fun isConfigured(): Boolean =
        BuildConfig.GITHUB_OWNER.isNotBlank() && BuildConfig.GITHUB_OWNER != "YOUR_GITHUB_USERNAME"

    // ==================== 检查更新 ====================

    fun check(onResult: (Result) -> Unit) {
        io.execute {
            val result = try {
                fetchLatest()
            } catch (e: Exception) {
                Result.Failed(e.message ?: "网络连接失败")
            }
            main.post { onResult(result) }
        }
    }

    private fun fetchLatest(): Result {
        val url = "https://api.github.com/repos/" +
                "${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub API 强制要求 User-Agent
            setRequestProperty("User-Agent", "PolicyKeeper-Android")
        }
        return try {
            when (val code = conn.responseCode) {
                200 -> parseRelease(conn.inputStream.bufferedReader().use { it.readText() })
                404 -> Result.Failed("还没有可用的发布版本")
                403 -> Result.Failed("请求过于频繁，请稍后再试")
                else -> Result.Failed("服务器返回异常（$code）")
            }
        } catch (e: Exception) {
            Result.Failed(e.message ?: "网络连接失败")
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRelease(body: String): Result {
        val json = JSONObject(body)
        val version = json.optString("tag_name").trim().trimStart('v', 'V')
        val notes = json.optString("body").trim()

        var apkUrl = ""
        var size = 0L
        json.optJSONArray("assets")?.let { assets ->
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(TAG_APK_SUFFIX, ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    size = asset.optLong("size")
                    break
                }
            }
        }

        if (version.isEmpty() || apkUrl.isEmpty()) {
            return Result.Failed("该版本没有附带安装包")
        }
        return if (compareVersion(version, BuildConfig.VERSION_NAME) > 0) {
            Result.Available(Release(version, notes, apkUrl, size))
        } else {
            Result.UpToDate
        }
    }

    /** 逐段比数字，a > b 返回正数。容忍 "1.6"、"v1.6.2"、"1.6-beta" 这类写法。 */
    fun compareVersion(a: String, b: String): Int {
        val left = segments(a)
        val right = segments(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun segments(v: String): List<Int> =
        v.split(Regex("[^0-9]+"))
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }

    // ==================== 下载 ====================

    fun download(
        context: Context,
        release: Release,
        onProgress: (Int) -> Unit,
        onDone: (File?) -> Unit,
        onError: (String) -> Unit
    ) {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        val target = File(dir, "policykeeper-${release.version}.apk")

        io.execute {
            try {
                // 清掉历史安装包，避免用户点到过期版本
                dir.listFiles()?.forEach { if (it.name != target.name) it.delete() }

                val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "PolicyKeeper-Android")
                }
                try {
                    val code = conn.responseCode
                    if (code != 200) {
                        main.post { onError("下载失败（$code）") }
                        return@execute
                    }

                    val total = conn.contentLengthLong
                    var downloaded = 0L
                    var lastPercent = -1

                    conn.inputStream.use { input ->
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (total > 0) {
                                    val percent = (downloaded * 100 / total).toInt()
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        main.post { onProgress(percent) }
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                } finally {
                    conn.disconnect()
                }

                if (target.length() <= 0) {
                    main.post { onError("下载的安装包为空") }
                } else {
                    main.post { onDone(target) }
                }
            } catch (e: Exception) {
                main.post { onError(e.message ?: "下载失败") }
            }
        }
    }

    // ==================== 安装 ====================

    /** Android 8.0 起安装 APK 需要用户显式授权「安装未知应用」 */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // 个别 ROM 没有该页面，退回应用详情页
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }

    fun install(context: Context, apk: File): Boolean = try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        false
    }

    /** 把字节数格式化成「12.4 MB」这类文案 */
    fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
    }
}
