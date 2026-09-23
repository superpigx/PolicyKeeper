package com.baodan.keeper.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.baodan.keeper.model.Attachment
import com.baodan.keeper.model.Insured
import com.baodan.keeper.model.InsuredType
import com.baodan.keeper.model.Policy
import com.baodan.keeper.model.PolicyStatus
import com.baodan.keeper.util.Dates
import com.baodan.keeper.util.Money
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 全部数据的唯一入口与本地持久化层。
 * 数据以 JSON 存放在私有目录，附件以文件形式放在 files/attachments/。
 * 不使用数据库，保证零依赖、可直接导出备份。
 */
class Store private constructor(private val app: Context) {

    val insureds = mutableListOf<Insured>()
    val policies = mutableListOf<Policy>()

    private val dataFile: File get() = File(app.filesDir, "data.json")

    val attachDir: File
        get() = File(app.filesDir, "attachments").also { if (!it.exists()) it.mkdirs() }

    init {
        load()
    }

    // ===================== 持久化 =====================

    fun load() {
        insureds.clear()
        policies.clear()
        if (!dataFile.exists()) return
        try {
            val root = JSONObject(dataFile.readText(Charsets.UTF_8))
            val iArr = root.optJSONArray("insureds") ?: JSONArray()
            for (i in 0 until iArr.length()) {
                val o = iArr.optJSONObject(i) ?: continue
                insureds.add(
                    Insured(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        type = InsuredType.from(o.optString("type")),
                        name = o.optString("name"),
                        detail = o.optString("detail"),
                        note = o.optString("note")
                    )
                )
            }
            val pArr = root.optJSONArray("policies") ?: JSONArray()
            for (i in 0 until pArr.length()) {
                val o = pArr.optJSONObject(i) ?: continue
                val att = mutableListOf<Attachment>()
                val aArr = o.optJSONArray("attachments") ?: JSONArray()
                for (j in 0 until aArr.length()) {
                    val a = aArr.optJSONObject(j) ?: continue
                    att.add(
                        Attachment(
                            storedName = a.optString("storedName"),
                            displayName = a.optString("displayName"),
                            mime = a.optString("mime"),
                            size = a.optLong("size"),
                            addedAt = a.optLong("addedAt")
                        )
                    )
                }
                policies.add(
                    Policy(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        insuredId = o.optString("insuredId"),
                        category = o.optString("category"),
                        insurer = o.optString("insurer"),
                        policyNo = o.optString("policyNo"),
                        startDate = o.optString("startDate"),
                        endDate = o.optString("endDate"),
                        premium = o.optDouble("premium", 0.0),
                        rebate = o.optDouble("rebate", 0.0),
                        rebateReceived = o.optBoolean("rebateReceived", false),
                        cycle = o.optString("cycle", "年缴"),
                        note = o.optString("note"),
                        attachments = att
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun save() {
        try {
            val tmp = File(app.filesDir, "data.json.tmp")
            tmp.writeText(toJson().toString(), Charsets.UTF_8)
            if (dataFile.exists()) dataFile.delete()
            tmp.renameTo(dataFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("app", "PolicyKeeper")
        root.put("version", 3)
        root.put("exportedAt", System.currentTimeMillis())

        val iArr = JSONArray()
        for (v in insureds) {
            iArr.put(
                JSONObject()
                    .put("id", v.id)
                    .put("type", v.type.code)
                    .put("name", v.name)
                    .put("detail", v.detail)
                    .put("note", v.note)
            )
        }
        root.put("insureds", iArr)

        val pArr = JSONArray()
        for (p in policies) {
            val aArr = JSONArray()
            for (a in p.attachments) {
                aArr.put(
                    JSONObject()
                        .put("storedName", a.storedName)
                        .put("displayName", a.displayName)
                        .put("mime", a.mime)
                        .put("size", a.size)
                        .put("addedAt", a.addedAt)
                )
            }
            pArr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("insuredId", p.insuredId)
                    .put("category", p.category)
                    .put("insurer", p.insurer)
                    .put("policyNo", p.policyNo)
                    .put("startDate", p.startDate)
                    .put("endDate", p.endDate)
                    .put("premium", p.premium)
                    .put("rebate", p.rebate)
                    .put("rebateReceived", p.rebateReceived)
                    .put("cycle", p.cycle)
                    .put("note", p.note)
                    .put("attachments", aArr)
            )
        }
        root.put("policies", pArr)
        return root
    }

    // ===================== 投保对象 =====================

    fun insuredById(id: String?): Insured? = insureds.firstOrNull { it.id == id }

    fun insuredLabel(id: String?): String = insuredById(id)?.name ?: "未指定"

    fun upsertInsured(item: Insured) {
        val idx = insureds.indexOfFirst { it.id == item.id }
        if (idx >= 0) insureds[idx] = item else insureds.add(item)
        save()
    }

    fun deleteInsured(id: String) {
        val doomed = policies.filter { it.insuredId == id }
        for (p in doomed) removeAttachmentFiles(p)
        policies.removeAll { it.insuredId == id }
        insureds.removeAll { it.id == id }
        save()
    }

    fun policyCountOf(insuredId: String): Int = policies.count { it.insuredId == insuredId }

    // ===================== 保单 =====================

    fun policyById(id: String?): Policy? = policies.firstOrNull { it.id == id }

    fun upsertPolicy(item: Policy) {
        val idx = policies.indexOfFirst { it.id == item.id }
        if (idx >= 0) policies[idx] = item else policies.add(item)
        save()
    }

    fun deletePolicy(id: String) {
        policyById(id)?.let { removeAttachmentFiles(it) }
        policies.removeAll { it.id == id }
        save()
    }

    private fun removeAttachmentFiles(p: Policy) {
        for (a in p.attachments) {
            try {
                File(attachDir, a.storedName).takeIf { it.exists() }?.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 按对象筛选 + 关键词搜索。
     *
     * 排序分两段：待处理的按紧急程度排在前面；已续保的属于历史归档，
     * 沉到列表最底（否则它们的 daysLeft 是负数，会一直霸占最显眼的位置）。
     */
    fun query(keyword: String = "", insuredId: String? = null): List<Policy> {
        val kw = keyword.trim().lowercase(Locale.getDefault())
        val matched = policies
            .filter { insuredId == null || it.insuredId == insuredId }
            .filter {
                if (kw.isEmpty()) true
                else it.category.lowercase(Locale.getDefault()).contains(kw) ||
                    it.insurer.lowercase(Locale.getDefault()).contains(kw) ||
                    it.policyNo.lowercase(Locale.getDefault()).contains(kw) ||
                    insuredLabel(it.insuredId).lowercase(Locale.getDefault()).contains(kw)
            }
        val (archived, pending) = matched.partition { statusOf(it).isArchived }
        return pending.sortedBy { it.daysLeft() } + archived.sortedByDescending { it.endDate }
    }

    /**
     * 当前真正处在保障期内的保单。
     *
     * 排除「未生效」（起保日在未来）——它还没开始保你。
     * 已续保的旧保单只要仍在保障期内就照算，因为接续的新单可能还没起保。
     */
    fun activePolicies(): List<Policy> =
        policies.filter { !it.isExpired() && statusOf(it) != PolicyStatus.PENDING }

    fun expiredPolicies(): List<Policy> = policies.filter { it.isExpired() }

    /**
     * 该保单的续保提醒窗口（天）：
     * 车险到期前 3 个月就能续保，所以提前 90 天提醒；
     * 人身险是到期日续保，因此窗口为 0（到期当天才进提醒）。
     */
    fun renewWindowDays(p: Policy): Int =
        if (insuredById(p.insuredId)?.type == InsuredType.PERSON) 0 else VEHICLE_RENEW_DAYS

    /**
     * 接续这份保单的那份新保单：同一投保对象下保障期更晚的记录中，到期最晚的一份。
     *
     * 车辆：一辆车一年一条，只要后续有更晚的记录就算续上了（不区分险种名，
     *       因为车险常是交强险 + 车船税 + 商业险一起买，险种写法容易变）。
     * 人员：按险种分别判断，否则长期重疾险会把一年期医疗险的到期提醒吃掉。
     */
    fun renewedTo(p: Policy): Policy? {
        if (p.endDate.isBlank()) return null
        val splitByCategory = insuredById(p.insuredId)?.type == InsuredType.PERSON
        return policies
            .filter { o ->
                o.id != p.id &&
                    o.insuredId == p.insuredId &&
                    o.endDate > p.endDate &&
                    (!splitByCategory || o.category.trim() == p.category.trim())
            }
            .maxByOrNull { it.endDate }
    }

    /** 是否已被续保（存在接续它的新保单） */
    fun isRenewed(p: Policy): Boolean = renewedTo(p) != null

    /**
     * 保单状态，全 App 唯一判定入口。
     *
     * 判定顺序是关键：「已续保」必须优先于一切，且**不能附加「已过期」这个前提**。
     * 车险到期前 90 天就能续保，此时旧保单的 daysLeft 仍为正数 —— 若把已续保
     * 绑定在已过期上，用户续保完标签会一直停在「可续保」，而首页待续保列表
     * 早已把它剔除，两边自相矛盾。
     */
    fun statusOf(p: Policy): PolicyStatus {
        if (isRenewed(p)) return PolicyStatus.RENEWED

        val days = p.daysLeft()
        if (days < 0) return PolicyStatus.EXPIRED

        // 起保日在未来 = 还没生效。续保时新保单常是「接续生效」，需要与在保的旧单区分开
        val toStart = Dates.daysFromToday(p.startDate)
        if (toStart != null && toStart > 0) return PolicyStatus.PENDING

        if (days == 0L) return PolicyStatus.DUE_TODAY
        if (days <= renewWindowDays(p)) return PolicyStatus.RENEWABLE
        return PolicyStatus.ACTIVE
    }

    /** 需要处理的保单：可续保 / 今天到期 / 已过期未续保，按紧急程度排序 */
    fun needAttention(): List<Policy> =
        policies.filter { statusOf(it).needsAction }.sortedBy { it.daysLeft() }

    /** 待续保按「车辆 / 人员」分成两块 */
    fun needAttentionByType(): Pair<List<Policy>, List<Policy>> {
        val all = needAttention()
        val person = all.filter { insuredById(it.insuredId)?.type == InsuredType.PERSON }
        val vehicle = all.filterNot { insuredById(it.insuredId)?.type == InsuredType.PERSON }
        return vehicle to person
    }

    // ===================== 统计 =====================

    /**
     * 按「起保日期」归属年份，统计该年支出。
     * [net] = true 返回实际净保费合计（原价 − 返款），false 返回原价合计。
     */
    fun yearTotal(year: Int, net: Boolean = true): Double =
        policies.filter { Dates.yearOf(it.startDate) == year }.sumOf { it.amount(net) }

    /** 该年返款合计（含待收） */
    fun yearRebate(year: Int): Double =
        policies.filter { Dates.yearOf(it.startDate) == year }.sumOf { it.rebate }

    /** 该年已到账的返款合计 */
    fun yearRebateReceived(year: Int): Double =
        policies.filter { Dates.yearOf(it.startDate) == year }.sumOf { it.receivedRebate }

    /** 该年还没到账的返款合计 */
    fun yearRebatePending(year: Int): Double =
        policies.filter { Dates.yearOf(it.startDate) == year }.sumOf { it.pendingRebate }

    /** 全部待收返款金额 */
    fun totalPendingRebate(): Double = policies.sumOf { it.pendingRebate }

    /** 存在待收返款的保单数 */
    fun pendingRebateCount(): Int = policies.count { it.pendingRebate > 0.0 }

    fun policiesOfYear(year: Int): List<Policy> =
        policies.filter { Dates.yearOf(it.startDate) == year }

    /** 有数据的年份，降序；没有任何数据时返回当前年份 */
    fun yearsWithData(): List<Int> {
        val ys = policies.mapNotNull { Dates.yearOf(it.startDate) }.distinct().sortedDescending()
        return if (ys.isEmpty()) listOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) else ys
    }

    /** 保费构成：名称 -> 金额，按金额降序。[net] 控制用净保费还是原价口径 */
    fun breakdown(year: Int?, byInsured: Boolean, net: Boolean = true): List<Pair<String, Double>> {
        val list = if (year == null) policies else policiesOfYear(year)
        val map = LinkedHashMap<String, Double>()
        for (p in list) {
            val key = if (byInsured) insuredLabel(p.insuredId) else p.category.ifBlank { "未分类" }
            map[key] = (map[key] ?: 0.0) + p.amount(net)
        }
        return map.entries.map { it.key to it.value }.sortedByDescending { it.second }
    }

    fun attachmentCount(): Int = policies.sumOf { it.attachments.size }

    // ===================== 按投保对象的逐年对比 =====================

    /** 有保单记录的投保对象 */
    fun insuredsWithData(): List<Insured> = insureds.filter { policyCountOf(it.id) > 0 }

    /** 某投保对象有数据的年份，升序 */
    fun yearsOf(insuredId: String): List<Int> =
        policies.filter { it.insuredId == insuredId }
            .mapNotNull { Dates.yearOf(it.startDate) }
            .distinct()
            .sorted()

    /** 某投保对象某一年的合计；[net] = true 取净保费 */
    fun yearTotalOf(insuredId: String, year: Int, net: Boolean = true): Double =
        policies.filter { it.insuredId == insuredId && Dates.yearOf(it.startDate) == year }
            .sumOf { it.amount(net) }

    /** 某投保对象某一年的返款合计 */
    fun yearRebateOf(insuredId: String, year: Int): Double =
        policies.filter { it.insuredId == insuredId && Dates.yearOf(it.startDate) == year }
            .sumOf { it.rebate }

    // ===================== 附件 =====================

    fun fileOf(a: Attachment): File = File(attachDir, a.storedName)

    fun importAttachment(uri: Uri, displayName: String?): Attachment? {
        return try {
            val resolver = app.contentResolver
            var mime = resolver.getType(uri) ?: ""
            val nameFromUri = displayName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "附件"
            if (mime.isEmpty()) {
                mime = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(nameFromUri.substringAfterLast('.', "").lowercase(Locale.US))
                    ?: ""
            }
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?: nameFromUri.substringAfterLast('.', "").ifBlank { "bin" }
            val stored = UUID.randomUUID().toString() + "." + ext
            attachDir.mkdirs()
            val dest = File(attachDir, stored)
            resolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            Attachment(
                storedName = stored,
                displayName = nameFromUri,
                mime = mime,
                size = dest.length(),
                addedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteAttachmentFile(a: Attachment) {
        try {
            fileOf(a).takeIf { it.exists() }?.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ===================== 导入导出 =====================

    /** 导出 zip 备份：data.json + attachments/ */
    fun exportZip(out: OutputStream) {
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(toJson().toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            for (p in policies) {
                for (a in p.attachments) {
                    val f = fileOf(a)
                    if (!f.exists()) continue
                    zos.putNextEntry(ZipEntry("attachments/" + a.storedName))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    fun exportJson(out: OutputStream) {
        out.write(toJson().toString(2).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /** 导入备份，支持 .pkbak(zip) 与纯 .json。成功返回保单数量，失败返回 -1 */
    fun importFrom(input: InputStream): Int {
        return try {
            val bytes = input.readBytes()
            // 判断是否为 zip（PK 头）
            if (bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                importZip(bytes)
                policies.size
            } else {
                val text = String(bytes, Charsets.UTF_8)
                applyJson(JSONObject(text))
                save()
                policies.size
            }
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    private fun importZip(bytes: ByteArray) {
        val staging = File(app.filesDir, "staging").also {
            if (it.exists()) it.deleteRecursively()
            it.mkdirs()
        }
        var jsonText: String? = null
        ZipInputStream(bytes.inputStream()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "data.json") {
                    jsonText = String(zis.readBytes(), Charsets.UTF_8)
                } else if (name.startsWith("attachments/") && !name.contains("..")) {
                    val target = File(staging, name.removePrefix("attachments/"))
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        if (jsonText == null) throw IllegalArgumentException("备份中缺少 data.json")

        // 用新数据替换
        attachDir.deleteRecursively()
        attachDir.mkdirs()
        for (f in staging.listFiles().orEmpty()) {
            f.copyTo(File(attachDir, f.name), overwrite = true)
        }
        staging.deleteRecursively()

        applyJson(JSONObject(jsonText!!))
        save()
    }

    /** 用 JSON 内容整体替换内存数据（不落盘前的附件清理由调用方处理） */
    private fun applyJson(root: JSONObject) {
        val newInsureds = mutableListOf<Insured>()
        val newPolicies = mutableListOf<Policy>()

        val iArr = root.optJSONArray("insureds") ?: JSONArray()
        for (i in 0 until iArr.length()) {
            val o = iArr.optJSONObject(i) ?: continue
            newInsureds.add(
                Insured(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    type = InsuredType.from(o.optString("type")),
                    name = o.optString("name"),
                    detail = o.optString("detail"),
                    note = o.optString("note")
                )
            )
        }
        val pArr = root.optJSONArray("policies") ?: JSONArray()
        for (i in 0 until pArr.length()) {
            val o = pArr.optJSONObject(i) ?: continue
            val att = mutableListOf<Attachment>()
            val aArr = o.optJSONArray("attachments") ?: JSONArray()
            for (j in 0 until aArr.length()) {
                val a = aArr.optJSONObject(j) ?: continue
                att.add(
                    Attachment(
                        storedName = a.optString("storedName"),
                        displayName = a.optString("displayName"),
                        mime = a.optString("mime"),
                        size = a.optLong("size"),
                        addedAt = a.optLong("addedAt")
                    )
                )
            }
            newPolicies.add(
                Policy(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    insuredId = o.optString("insuredId"),
                    category = o.optString("category"),
                    insurer = o.optString("insurer"),
                    policyNo = o.optString("policyNo"),
                    startDate = o.optString("startDate"),
                    endDate = o.optString("endDate"),
                    premium = o.optDouble("premium", 0.0),
                    rebate = o.optDouble("rebate", 0.0),
                    rebateReceived = o.optBoolean("rebateReceived", false),
                    cycle = o.optString("cycle", "年缴"),
                    note = o.optString("note"),
                    attachments = att
                )
            )
        }
        insureds.clear(); insureds.addAll(newInsureds)
        policies.clear(); policies.addAll(newPolicies)
    }

    /** 导出 CSV（带 BOM，Excel/WPS 直接打开不乱码） */
    fun exportCsv(out: OutputStream, year: Int?) {
        val sb = StringBuilder()
        sb.append("投保对象,对象类型,险种,保险公司,保单号,生效日期,到期日期,原价(元),返款(元),返款状态,净保费(元),缴费方式,附件数,备注\r\n")
        val list = if (year == null) policies else policiesOfYear(year)
        for (p in list.sortedBy { it.insuredId }) {
            val v = insuredById(p.insuredId)
            sb.append(csv(v?.name ?: "")).append(',')
                .append(csv(v?.type?.label ?: "")).append(',')
                .append(csv(p.category)).append(',')
                .append(csv(p.insurer)).append(',')
                .append(csv(p.policyNo)).append(',')
                .append(csv(p.startDate)).append(',')
                .append(csv(p.endDate)).append(',')
                .append(csv(Money.format(p.premium))).append(',')
                .append(csv(Money.format(p.rebate))).append(',')
                .append(csv(if (p.hasRebate()) (if (p.rebateReceived) "已收" else "待收") else "")).append(',')
                .append(csv(Money.format(p.netPremium))).append(',')
                .append(csv(p.cycle)).append(',')
                .append(p.attachments.size).append(',')
                .append(csv(p.note)).append("\r\n")
        }
        out.write("\uFEFF".toByteArray(Charsets.UTF_8))
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun csv(text: String): String {
        val t = text.replace("\"", "\"\"")
        return if (t.contains(',') || t.contains('"') || t.contains('\n')) "\"$t\"" else t
    }

    fun clearAll() {
        for (p in policies) removeAttachmentFiles(p)
        policies.clear()
        insureds.clear()
        attachDir.deleteRecursively()
        attachDir.mkdirs()
        save()
    }

    fun backupFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "保单备份_$stamp.pkbak"
    }

    companion object {
        /** 车险可提前续保的天数：到期前 3 个月 */
        const val VEHICLE_RENEW_DAYS = 90

        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store =
            instance ?: synchronized(this) {
                instance ?: Store(context.applicationContext).also { instance = it }
            }
    }
}
