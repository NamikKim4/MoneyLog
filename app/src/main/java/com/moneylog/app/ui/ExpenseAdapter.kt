package com.moneylog.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.moneylog.app.R
import com.moneylog.app.common.CategoryStyle
import com.moneylog.app.common.TouchPopEffect
import com.moneylog.app.model.Expense

/**
 * 헤더 없이 지출 항목만 죽 보여줄 때 쓰는 어댑터(캘린더 화면의 "그날 지출" 목록 등).
 * 각 항목은 카테고리 이모지가 들어간 원형 아이콘 + 카테고리/메모 + 금액으로 표시되고,
 * 삭제는 화면에서 왼쪽으로 스와이프하는 제스처로 처리한다(어댑터는 데이터만 들고 있는다).
 * 처음 나타나는 항목마다 살짝 떠오르는 애니메이션을 줘서 다시 그릴 때마다
 * 밋밋하게 뚝 나타나지 않도록 한다.
 * 항목을 탭하면 onItemClick으로 알려줘서, 그 지출의 상세를 보거나 수정할 수 있게 한다.
 */
class ExpenseAdapter(
    private var items: List<Expense>,
    private val onItemClick: (Expense) -> Unit = {}
) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {

    private var maxAnimatedPosition = -1

    inner class ExpenseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView = view.findViewById(R.id.tvItemIcon)
        val tvCategory: TextView = view.findViewById(R.id.tvItemCategory)
        val tvMemo: TextView = view.findViewById(R.id.tvItemMemo)
        val tvAmount: TextView = view.findViewById(R.id.tvItemAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val expense = items[position]
        val context = holder.itemView.context

        holder.tvIcon.text = CategoryStyle.icon(expense.category)
        holder.tvIcon.backgroundTintList = ContextCompat.getColorStateList(
            context,
            CategoryStyle.backgroundColorRes(expense.category)
        )
        holder.tvCategory.text = CategoryStyle.label(expense.category)
        holder.tvMemo.text = expense.memo
        holder.tvMemo.visibility = if (expense.memo.isBlank()) View.GONE else View.VISIBLE
        holder.tvAmount.text = context.getString(R.string.amount_won_format, expense.amount)

        // 탭하면 이 지출의 상세(수정/삭제) 화면이 열린다.
        holder.itemView.setOnClickListener {
            TouchPopEffect.pop(it)
            onItemClick(expense)
        }

        animateEntranceIfNeeded(holder.itemView, position)
    }

    private fun animateEntranceIfNeeded(itemView: View, position: Int) {
        if (position > maxAnimatedPosition) {
            maxAnimatedPosition = position
            itemView.alpha = 0f
            itemView.translationY = 24f
            itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(minOf(position, 8) * 30L)
                .setDuration(220L)
                .start()
        } else {
            itemView.animate().cancel()
            itemView.alpha = 1f
            itemView.translationY = 0f
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<Expense>) {
        items = newItems
        notifyDataSetChanged()
    }

    /** 날짜를 새로 고를 때처럼 처음부터 다시 등장하는 느낌을 주고 싶을 때 호출한다. */
    fun resetAnimation() {
        maxAnimatedPosition = -1
    }

    fun getItemAt(position: Int): Expense? = items.getOrNull(position)
}
