package com.moneylog.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.moneylog.app.R
import com.moneylog.app.model.DailyMemo

/**
 * 일별 메모 목록을 보여주는 RecyclerView 어댑터.
 * 항목을 탭하면 onItemClick(수정), 우측 ✕를 누르면 onDeleteClick으로 알려준다.
 */
class MemoAdapter(
    private var items: List<DailyMemo>,
    private val onItemClick: (DailyMemo) -> Unit = {},
    private val onDeleteClick: (DailyMemo) -> Unit = {}
) : RecyclerView.Adapter<MemoAdapter.MemoViewHolder>() {

    inner class MemoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvMemoItemDate)
        val tvContent: TextView = view.findViewById(R.id.tvMemoItemContent)
        val tvDelete: TextView = view.findViewById(R.id.tvMemoItemDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_memo, parent, false)
        return MemoViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemoViewHolder, position: Int) {
        val memo = items[position]
        holder.tvDate.text = memo.date
        holder.tvContent.text = memo.memo
        holder.itemView.setOnClickListener { onItemClick(memo) }
        holder.tvDelete.setOnClickListener { onDeleteClick(memo) }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<DailyMemo>) {
        items = newItems
        notifyDataSetChanged()
    }
}
