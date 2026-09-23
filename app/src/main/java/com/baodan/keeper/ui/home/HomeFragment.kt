package com.baodan.keeper.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.baodan.keeper.R
import com.baodan.keeper.data.Store
import com.baodan.keeper.databinding.FragmentHomeBinding
import com.baodan.keeper.databinding.ItemPolicyBinding
import com.baodan.keeper.model.Policy
import com.baodan.keeper.model.PolicyStatus
import com.baodan.keeper.ui.policy.EditPolicyActivity
import com.baodan.keeper.ui.policy.PolicyItemBinder
import com.baodan.keeper.util.Money
import java.util.Calendar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val store get() = Store.get(requireContext())

    private val editor =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { render() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.fabAdd.setOnClickListener { openEditor(null) }
        binding.btnEmptyAdd.setOnClickListener { openEditor(null) }

        // 待收返款提醒条：就地展开清单，处理完下滑即回首页。
        // 关闭时统一刷新一次 —— 面板里可能已经把钱标记成已收了
        binding.boxPendingRebate.setOnClickListener {
            PendingRebateSheet.show(requireContext()) { render() }
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun openEditor(policyId: String?) {
        editor.launch(EditPolicyActivity.intent(requireContext(), policyId))
    }

    private fun render() {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        // 大数字用净保费（原价 − 返款），下面附注原价与已返金额
        binding.tvYearTotal.text = Money.format(store.yearTotal(year))
        binding.tvYearLabel.text = getString(R.string.home_year_net_label, year)

        val yearOriginal = store.yearTotal(year, net = false)
        val yearReceived = store.yearRebateReceived(year)
        val yearPending = store.yearRebatePending(year)
        binding.tvYearSub.visibility = if (yearReceived + yearPending > 0.0) View.VISIBLE else View.GONE
        if (yearReceived + yearPending > 0.0) {
            binding.tvYearSub.text = if (yearPending > 0.0) {
                getString(
                    R.string.home_year_breakdown_pending,
                    "¥" + Money.format(yearOriginal),
                    "¥" + Money.format(yearReceived),
                    "¥" + Money.format(yearPending)
                )
            } else {
                getString(
                    R.string.home_year_breakdown,
                    "¥" + Money.format(yearOriginal),
                    "¥" + Money.format(yearReceived)
                )
            }
        }

        // 待收返款提醒（跨年份统计，只要没到账就一直提示）
        val pending = store.totalPendingRebate()
        if (pending > 0.0) {
            binding.boxPendingRebate.visibility = View.VISIBLE
            binding.tvPendingRebate.text = getString(
                R.string.home_pending_rebate,
                "¥" + Money.format(pending),
                store.pendingRebateCount()
            )
        } else {
            binding.boxPendingRebate.visibility = View.GONE
        }

        val empty = store.policies.isEmpty()
        binding.boxEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.sectionAttention.visibility = if (empty) View.GONE else View.VISIBLE
        binding.sectionRecent.visibility = if (empty) View.GONE else View.VISIBLE
        binding.fabAdd.visibility = if (empty) View.GONE else View.VISIBLE

        if (empty) return

        // 待续保：车辆与人员分开两块，各自的紧急程度单独提示
        val (vehicleAttention, personAttention) = store.needAttentionByType()
        binding.tvActiveCount.text = store.activePolicies().size.toString()
        binding.tvInsuredCount.text = store.insureds.size.toString()
        binding.tvExpiringCount.text =
            (vehicleAttention.size + personAttention.size).toString()

        bindAttentionGroup(
            binding.groupAttentionVehicle,
            binding.boxAttentionVehicle,
            binding.tvVehicleHint,
            vehicleAttention
        )
        bindAttentionGroup(
            binding.groupAttentionPerson,
            binding.boxAttentionPerson,
            binding.tvPersonHint,
            personAttention
        )
        binding.tvAttentionEmpty.visibility =
            if (vehicleAttention.isEmpty() && personAttention.isEmpty()) View.VISIBLE else View.GONE

        // 最近起保
        binding.boxRecent.removeAllViews()
        val recent = store.policies.sortedByDescending { it.startDate }.take(5)
        for (p in recent) {
            binding.boxRecent.addView(makeCard(p, binding.boxRecent))
        }
    }

    /** 渲染一组待续保：空组连标题一起隐藏；右侧提示该组里最紧急的那一条 */
    private fun bindAttentionGroup(
        section: View,
        box: LinearLayout,
        hint: TextView,
        list: List<Policy>
    ) {
        section.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        box.removeAllViews()
        if (list.isEmpty()) return
        for (p in list) box.addView(makeCard(p, box))
        // 提示文案同样走状态机，保证与卡片标签口径一致
        val first = list.first()
        hint.text = when (store.statusOf(first)) {
            PolicyStatus.EXPIRED -> getString(R.string.attention_overdue)
            PolicyStatus.DUE_TODAY -> getString(R.string.attention_due_today)
            else -> getString(R.string.attention_fastest, first.daysLeft())
        }
    }

    private fun makeCard(p: Policy, parent: ViewGroup): View {
        val item = ItemPolicyBinding.inflate(layoutInflater, parent, false)
        PolicyItemBinder.bind(item, p, store)
        item.root.setOnClickListener { openEditor(p.id) }
        return item.root
    }
}
