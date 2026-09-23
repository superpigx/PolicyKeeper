package com.baodan.keeper.ui.insured

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.baodan.keeper.R
import com.baodan.keeper.data.Store
import com.baodan.keeper.databinding.ActivityInsuredListBinding
import com.baodan.keeper.model.Insured
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class InsuredListActivity : AppCompatActivity() {

    private lateinit var b: ActivityInsuredListBinding
    private val store get() = Store.get(this)
    private lateinit var adapter: InsuredAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityInsuredListBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.empty.emptyText.setText(R.string.insured_empty)

        adapter = InsuredAdapter(
            store,
            onEdit = { InsuredDialogs.show(this, it) { render() } },
            onDelete = { confirmDelete(it) }
        )
        b.rvInsured.adapter = adapter

        b.btnBack.setOnClickListener { finish() }
        b.btnAdd.setOnClickListener { InsuredDialogs.show(this, null) { render() } }

        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val list = store.insureds.sortedWith(compareBy({ it.type.ordinal }, { it.name }))
        adapter.submit(list)
        val empty = list.isEmpty()
        b.empty.root.visibility = if (empty) View.VISIBLE else View.GONE
        b.rvInsured.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun confirmDelete(item: Insured) {
        val count = store.policyCountOf(item.id)
        val msg = if (count > 0) {
            getString(R.string.insured_delete_confirm, count)
        } else {
            getString(R.string.insured_delete_confirm_empty, item.name)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(msg)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.deleteInsured(item.id)
                render()
            }
            .show()
    }
}
