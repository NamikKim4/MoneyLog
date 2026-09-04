package com.moneylog.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.moneylog.app.R
import com.moneylog.app.common.TouchPopEffect
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

    private var maxAnimatedPosition = -1

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
        holder.itemView.setOnClickListener {
            TouchPopEffect.pop(it)
            onItemClick(memo)
        }
        holder.tvDelete.setOnClickListener {
            TouchPopEffect.pop(it)
            onDeleteClick(memo)
        }

        if (position > maxAnimatedPosition) {
            maxAnimatedPosition = position
            holder.itemView.alpha = 0f
            holder.itemView.translationY = 24f
            holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(minOf(position, 8) * 30L)
                .setDuration(220L)
                .start()
        } else {
            holder.itemView.animate().cancel()
            holder.itemView.alpha = 1f
            holder.itemView.translationY = 0f
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<DailyMemo>) {
        items = newItems
        notifyDataSetChanged()
    }

    /** 달을 바꿀 때처럼 목록이 통째로 새로 나타나는 느낌을 주고 싶을 때 호출한다. */
    fun resetAnimation() {
        maxAnimatedPosition = -1
    }
}
