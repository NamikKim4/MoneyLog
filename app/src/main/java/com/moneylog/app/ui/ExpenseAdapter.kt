package com.moneylog.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.moneylog.app.R
import com.moneylog.app.model.Expense

/**
 * 지출 목록을 보여주는 RecyclerView 어댑터.
 * 각 항목 우측의 ✕를 누르면 onDeleteClick으로 알려준다 (실제 삭제/확인 다이얼로그는 호출부에서 처리).
 */
class ExpenseAdapter(
    private var items: List<Expense>,
    private val onDeleteClick: (Expense) -> Unit = {}
) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    inner class ExpenseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvItemDate)
        val tvCategory: TextView = view.findViewById(R.id.tvItemCategory)
        val tvAmount: TextView = view.findViewById(R.id.tvItemAmount)
        val tvMemo: TextView = view.findViewById(R.id.tvItemMemo)
        val tvDelete: TextView = view.findViewById(R.id.tvItemDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val expense = items[position]

        holder.tvDate.text = expense.date
        holder.tvCategory.text = expense.category
        holder.tvAmount.text = holder.itemView.context.getString(
            R.string.amount_won_format,
            expense.amount
        )
        holder.tvMemo.text = expense.memo
        holder.tvMemo.visibility = if (expense.memo.isBlank()) View.GONE else View.VISIBLE

        holder.tvDelete.setOnClickListener { onDeleteClick(expense) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<Expense>) {
        items = newItems
        notifyDataSetChanged()
    }
}
