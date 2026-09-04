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

/** 지출 목록 화면에서 날짜 헤더 + 그 날짜의 지출들을 한 목록으로 섞어 보여주기 위한 항목 타입. */
sealed class ExpenseListItem {
    data class Header(val label: String, val dayTotal: Long) : ExpenseListItem()
    data class Row(val expense: Expense) : ExpenseListItem()
}

/**
 * 지출 목록 화면 전용 어댑터. 날짜마다 반복해서 날짜를 찍는 대신,
 * 날짜 그룹 헤더 한 줄 아래로 그 날의 지출들을 모아 보여준다.
 * 헤더는 스와이프해도 삭제되지 않도록 isSwipeable로 구분한다.
 * 항목을 탭하면 onItemClick으로 알려줘서, 그 지출의 상세를 보거나 수정할 수 있게 한다.
 * 새로 나타나는 항목마다 살짝 떠오르는 애니메이션을 줘서 화면이 덜 밋밋해 보이게 한다
 * (검색어를 입력해 다시 그릴 때는 이미 봤던 위치라 다시 애니메이션하지 않는다).
 */
class GroupedExpenseAdapter(
    private var items: List<ExpenseListItem> = emptyList(),
    private val onItemClick: (Expense) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1
    }

    private var maxAnimatedPosition = -1

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLabel: TextView = view.findViewById(R.id.tvHeaderDate)
        val tvTotal: TextView = view.findViewById(R.id.tvHeaderTotal)
    }

    inner class RowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView = view.findViewById(R.id.tvItemIcon)
        val tvCategory: TextView = view.findViewById(R.id.tvItemCategory)
        val tvMemo: TextView = view.findViewById(R.id.tvItemMemo)
        val tvAmount: TextView = view.findViewById(R.id.tvItemAmount)
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ExpenseListItem.Header -> TYPE_HEADER
        is ExpenseListItem.Row -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_expense_header, parent, false))
        } else {
            RowViewHolder(inflater.inflate(R.layout.item_expense, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ExpenseListItem.Header -> {
                holder as HeaderViewHolder
                holder.tvLabel.text = item.label
                holder.tvTotal.text = holder.itemView.context.getString(
                    R.string.amount_won_format,
                    item.dayTotal
                )
            }
            is ExpenseListItem.Row -> {
                holder as RowViewHolder
                val expense = item.expense
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
            }
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

    fun submitList(newItems: List<ExpenseListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    /** 달을 바꿀 때처럼 목록이 통째로 새로 나타나는 느낌을 주고 싶을 때 호출한다. */
    fun resetAnimation() {
        maxAnimatedPosition = -1
    }

    fun expenseAt(position: Int): Expense? = (items.getOrNull(position) as? ExpenseListItem.Row)?.expense

    fun isSwipeable(position: Int): Boolean = items.getOrNull(position) is ExpenseListItem.Row
}
