package com.baodan.keeper.ui.policy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.baodan.keeper.R
import com.baodan.keeper.data.Store
import com.baodan.keeper.databinding.FragmentPoliciesBinding
import com.baodan.keeper.databinding.ItemFilterChipBinding
import com.google.android.material.chip.Chip

class PolicyListFragment : Fragment() {

    private var _binding: FragmentPoliciesBinding? = null
    private val binding get() = _binding!!
    private val store get() = Store.get(requireContext())

    private lateinit var adapter: PolicyAdapter
    private var selectedInsuredId: String? = null
    private var allChipId: Int = View.NO_ID

    private val editor =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPoliciesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = PolicyAdapter(store) { p ->
            editor.launch(EditPolicyActivity.intent(requireContext(), p.id))
        }
        binding.rvPolicies.adapter = adapter
        binding.empty.emptyText.text = getString(R.string.policy_empty)

        binding.etSearch.doAfterTextChanged { applyFilter() }
        binding.fabAdd.setOnClickListener {
            editor.launch(EditPolicyActivity.intent(requireContext(), null))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refresh() {
        buildChips()
        applyFilter()
    }

    private fun buildChips() {
        binding.chipGroup.removeAllViews()

        val all = ItemFilterChipBinding.inflate(layoutInflater, binding.chipGroup, false).root
        all.id = View.generateViewId()
        allChipId = all.id
        all.text = "全部（${store.policies.size}）"
        all.isChecked = true
        binding.chipGroup.addView(all)

        for (v in store.insureds) {
            val chip: Chip =
                ItemFilterChipBinding.inflate(layoutInflater, binding.chipGroup, false).root
            chip.id = View.generateViewId()
            chip.tag = v.id
            chip.text = "${v.name}（${store.policyCountOf(v.id)}）"
            binding.chipGroup.addView(chip)
        }

        // 若当前选中的对象已被删除，回到「全部」
        if (selectedInsuredId != null && store.insuredById(selectedInsuredId) == null) {
            selectedInsuredId = null
        }
        if (selectedInsuredId == null) {
            all.isChecked = true
        } else {
            for (i in 0 until binding.chipGroup.childCount) {
                val c = binding.chipGroup.getChildAt(i) as? Chip ?: continue
                if (c.tag == selectedInsuredId) c.isChecked = true
            }
        }

        binding.chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                selectedInsuredId = null
            } else {
                val id = checkedIds[0]
                selectedInsuredId = if (id == allChipId) null
                else group.findViewById<Chip>(id)?.tag as? String
            }
            applyFilter()
        }
    }

    private fun applyFilter() {
        val keyword = binding.etSearch.text?.toString().orEmpty()
        val list = store.query(keyword, selectedInsuredId)
        adapter.submit(list)
        binding.tvCount.text = "共 ${list.size} 份保单"
        val empty = list.isEmpty()
        binding.empty.root.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvPolicies.visibility = if (empty) View.GONE else View.VISIBLE
    }
}
