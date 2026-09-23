package com.baodan.keeper.ui.policy

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.baodan.keeper.data.Store
import com.baodan.keeper.databinding.ItemPolicyBinding
import com.baodan.keeper.model.Policy

class PolicyAdapter(
    private val store: Store,
    private val onClick: (Policy) -> Unit
) : RecyclerView.Adapter<PolicyAdapter.VH>() {

    private val items = mutableListOf<Policy>()

    fun submit(list: List<Policy>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemPolicyBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemPolicyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        PolicyItemBinder.bind(holder.binding, p, store)
        holder.binding.root.setOnClickListener { onClick(p) }
    }
}
