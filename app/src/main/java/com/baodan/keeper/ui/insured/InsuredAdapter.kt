package com.baodan.keeper.ui.insured

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.baodan.keeper.R
import com.baodan.keeper.data.Store
import com.baodan.keeper.databinding.ItemInsuredBinding
import com.baodan.keeper.model.Insured
import com.baodan.keeper.model.InsuredType

class InsuredAdapter(
    private val store: Store,
    private val onEdit: (Insured) -> Unit,
    private val onDelete: (Insured) -> Unit
) : RecyclerView.Adapter<InsuredAdapter.VH>() {

    private val items = mutableListOf<Insured>()

    fun submit(list: List<Insured>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val binding: ItemInsuredBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemInsuredBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val v = items[position]
        val b = holder.binding
        b.ivIcon.setImageResource(
            if (v.type == InsuredType.VEHICLE) R.drawable.ic_car else R.drawable.ic_person
        )
        b.tvName.text = v.name
        val count = store.policyCountOf(v.id)
        val parts = mutableListOf<String>()
        if (v.detail.isNotBlank()) parts.add(v.detail)
        parts.add("$count 份保单")
        b.tvDetail.text = parts.joinToString(" · ")

        b.root.setOnClickListener { onEdit(v) }
        b.btnEdit.setOnClickListener { onEdit(v) }
        b.btnDelete.setOnClickListener { onDelete(v) }
    }
}
