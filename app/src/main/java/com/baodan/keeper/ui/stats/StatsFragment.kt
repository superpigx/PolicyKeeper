package com.baodan.keeper.ui.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.baodan.keeper.R
import com.baodan.keeper.data.Store
import com.baodan.keeper.databinding.FragmentStatsBinding
import com.baodan.keeper.databinding.ItemBreakdownBinding
import com.baodan.keeper.databinding.ItemCompareRowBinding
import com.baodan.keeper.databinding.ItemFilterChipBinding
import com.baodan.keeper.model.InsuredType
import com.baodan.keeper.util.Money
import com.google.android.material.chip.Chip
import java.util.Calendar
import java.util.Locale

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private val store get() = Store.get(requireContext())

    private var selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR)
    private var yearChipIds = mutableListOf<Int>()

    /** 统计口径：true = 实际净保费（原价 − 返款），false = 原价 */
    private var useNet = true

    /** 逐年对比当前选中的投保对象 */
    private var compareInsuredId: String? = null
    private val compareChipIds = mutableListOf<Int>()
    private val compareChipInsuredIds = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        binding.modeChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val net = checkedIds.contains(R.id.chipNet)
            if (net != useNet) {
                useNet = net
                render()
            }
        }
        binding.compareChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val idx = compareChipIds.indexOf(checkedIds.firstOrNull() ?: -1)
            if (idx >= 0) {
                val id = compareChipInsuredIds[idx]
                if (id != compareInsuredId) {
                    compareInsuredId = id
                    renderCompareData()
                }
            }
        }
        return binding.root
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
        val empty = store.policies.isEmpty()
        binding.empty.emptyText.text = getString(R.string.stats_empty)
        binding.empty.root.visibility = if (empty) View.VISIBLE else View.GONE
        binding.content.visibility = if (empty) View.GONE else View.VISIBLE
        if (empty) return

        val years = store.yearsWithData()
        if (selectedYear !in years) selectedYear = years.first()

        buildYearChips(years)

        // 口径切换：只有存在返款时才有意义
        val hasRebate = store.policies.any { it.hasRebate() }
        binding.rowMode.visibility = if (hasRebate) View.VISIBLE else View.GONE
        if (!hasRebate) useNet = true

        val total = store.yearTotal(selectedYear, useNet)
        binding.tvTotal.text = Money.format(total)
        binding.tvTotalLabel.text = getString(
            if (useNet) R.string.stats_year_total else R.string.stats_year_total_original,
            selectedYear
        )

        val yearOriginal = store.yearTotal(selectedYear, net = false)
        val yearReceived = store.yearRebateReceived(selectedYear)
        val yearPending = store.yearRebatePending(selectedYear)
        val hasRebateOfYear = yearReceived + yearPending > 0.0
        binding.tvYearSub.visibility = if (hasRebateOfYear) View.VISIBLE else View.GONE
        if (hasRebateOfYear) {
            binding.tvYearSub.text = if (yearPending > 0.0) {
                getString(
                    R.string.stats_year_breakdown_pending,
                    "¥" + Money.format(yearOriginal),
                    "¥" + Money.format(yearReceived),
                    "¥" + Money.format(yearPending)
                )
            } else {
                getString(
                    R.string.stats_year_breakdown,
                    "¥" + Money.format(yearOriginal),
                    "¥" + Money.format(yearReceived)
                )
            }
        }

        val prev = store.yearTotal(selectedYear - 1, useNet)
        val yoyText: String
        val colorRes: Int
        when {
            prev <= 0.0 -> {
                yoyText = getString(R.string.stats_no_prev)
                colorRes = R.color.white
            }
            total > prev -> {
                yoyText = getString(
                    R.string.stats_up,
                    String.format(Locale.US, "%.1f", (total - prev) / prev * 100)
                )
                colorRes = R.color.white
            }
            total < prev -> {
                yoyText = getString(
                    R.string.stats_down,
                    String.format(Locale.US, "%.1f", (prev - total) / prev * 100)
                )
                colorRes = R.color.white
            }
            else -> {
                yoyText = getString(R.string.stats_flat)
                colorRes = R.color.white
            }
        }
        binding.tvYoy.text = yoyText
        binding.tvYoy.setTextColor(ContextCompat.getColor(requireContext(), colorRes))

        // 趋势柱状图（按年份升序）
        val asc = years.sorted()
        val labels = asc.map { it.toString() }
        val values = asc.map { store.yearTotal(it, useNet) }
        binding.chart.setData(labels, values, asc.indexOf(selectedYear))

        buildBreakdown(binding.boxCategory, store.breakdown(selectedYear, false, useNet))
        buildBreakdown(binding.boxInsured, store.breakdown(selectedYear, true, useNet))

        buildCompare()
    }

    // ===================== 同一对象逐年对比 =====================

    private fun buildCompare() {
        val list = store.insuredsWithData()
        binding.cardCompare.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        if (list.isEmpty()) return

        if (compareInsuredId == null || list.none { it.id == compareInsuredId }) {
            compareInsuredId = list.maxByOrNull { store.policyCountOf(it.id) }?.id
        }

        binding.compareChips.removeAllViews()
        compareChipIds.clear()
        compareChipInsuredIds.clear()
        for (v in list) {
            val chip: Chip = ItemFilterChipBinding
                .inflate(layoutInflater, binding.compareChips, false).root
            chip.id = View.generateViewId()
            chip.text = (if (v.type == InsuredType.VEHICLE) "🚗 " else "👤 ") + v.name
            chip.isChecked = v.id == compareInsuredId
            compareChipIds.add(chip.id)
            compareChipInsuredIds.add(v.id)
            binding.compareChips.addView(chip)
        }

        renderCompareData()
    }

    private fun renderCompareData() {
        val id = compareInsuredId
        val years = if (id == null) emptyList() else store.yearsOf(id)
        if (id == null || years.isEmpty()) {
            binding.boxCompareRows.removeAllViews()
            binding.tvCompareDelta.text = ""
            binding.compareChart.visibility = View.GONE
            return
        }
        binding.compareChart.visibility = View.VISIBLE

        val values = years.map { store.yearTotalOf(id, it, useNet) }
        binding.compareChart.setData(years.map { it.toString() }, values, years.lastIndex)

        // 最新一年 vs 上一年
        val latestYear = years.last()
        val latestValue = values.last()
        val latestText = "¥" + Money.format(latestValue)
        val deltaText: String
        val deltaColor: Int
        if (years.size < 2 || values[values.size - 2] <= 0.005) {
            deltaText = getString(R.string.stats_compare_only, latestYear, latestText)
            deltaColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        } else {
            val prevYear = years[years.size - 2]
            val prevValue = values[values.size - 2]
            val diff = latestValue - prevValue
            val pct = String.format(Locale.US, "%.1f", Math.abs(diff) / prevValue * 100)
            val diffText = "¥" + Money.format(Math.abs(diff))
            when {
                diff > 0.005 -> {
                    deltaText = getString(
                        R.string.stats_compare_up, latestYear, latestText, prevYear, diffText, pct
                    )
                    deltaColor = ContextCompat.getColor(requireContext(), R.color.status_expired)
                }
                diff < -0.005 -> {
                    deltaText = getString(
                        R.string.stats_compare_down, latestYear, latestText, prevYear, diffText, pct
                    )
                    deltaColor = ContextCompat.getColor(requireContext(), R.color.status_ok)
                }
                else -> {
                    deltaText = getString(
                        R.string.stats_compare_flat, latestYear, latestText, prevYear
                    )
                    deltaColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                }
            }
        }
        binding.tvCompareDelta.text = deltaText
        binding.tvCompareDelta.setTextColor(deltaColor)

        // 逐年明细
        binding.boxCompareRows.removeAllViews()
        var prev: Double? = null
        for (i in years.indices) {
            val y = years[i]
            val value = values[i]
            val row = ItemCompareRowBinding
                .inflate(layoutInflater, binding.boxCompareRows, false)
            row.tvYear.text = y.toString()

            val rebate = store.yearRebateOf(id, y)
            row.tvMeta.text = if (rebate > 0.0) {
                getString(
                    R.string.stats_compare_row_meta,
                    "¥" + Money.format(store.yearTotalOf(id, y, net = false)),
                    "¥" + Money.format(rebate)
                )
            } else {
                getString(R.string.stats_compare_row_meta_none)
            }
            row.tvAmount.text = "¥" + Money.format(value)

            val before = prev
            if (before == null) {
                row.tvDelta.text = ""
            } else if (before <= 0.005) {
                row.tvDelta.text = "—"
                row.tvDelta.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.text_tertiary)
                )
            } else {
                val d = (value - before) / before * 100
                row.tvDelta.text = when {
                    Math.abs(d) < 0.05 -> "0.0%"
                    d > 0 -> "+" + String.format(Locale.US, "%.1f", d) + "%"
                    else -> "−" + String.format(Locale.US, "%.1f", Math.abs(d)) + "%"
                }
                row.tvDelta.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        when {
                            d > 0.05 -> R.color.status_expired
                            d < -0.05 -> R.color.status_ok
                            else -> R.color.text_tertiary
                        }
                    )
                )
            }
            binding.boxCompareRows.addView(row.root)
            prev = value
        }
    }

    private fun buildYearChips(years: List<Int>) {
        binding.yearChips.removeAllViews()
        yearChipIds.clear()
        binding.yearScroll.visibility = if (years.size <= 1) View.GONE else View.VISIBLE
        for (y in years.sortedDescending()) {
            val chip: Chip =
                ItemFilterChipBinding.inflate(layoutInflater, binding.yearChips, false).root
            chip.id = View.generateViewId()
            chip.text = "${y}年"
            chip.isChecked = y == selectedYear
            yearChipIds.add(chip.id)
            binding.yearChips.addView(chip)
        }
        binding.yearChips.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val idx = yearChipIds.indexOf(checkedIds[0])
            if (idx >= 0) {
                val list = years.sortedDescending()
                if (idx < list.size && list[idx] != selectedYear) {
                    selectedYear = list[idx]
                    render()
                }
            }
        }
    }

    private fun buildBreakdown(container: LinearLayout, data: List<Pair<String, Double>>) {
        container.removeAllViews()
        if (data.isEmpty()) {
            val tv = android.widget.TextView(requireContext()).apply {
                text = "暂无数据"
                textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_tertiary))
                setPadding(0, 16, 0, 8)
            }
            container.addView(tv)
            return
        }
        val total = data.sumOf { it.second }
        for ((name, amount) in data) {
            val item = ItemBreakdownBinding.inflate(layoutInflater, container, false)
            val pct = if (total <= 0) 0 else (amount / total * 100).toInt()
            item.tvName.text = name
            item.tvAmount.text = "¥${Money.format(amount)}   $pct%"
            item.pb.progress = pct
            container.addView(item.root)
        }
    }
}
