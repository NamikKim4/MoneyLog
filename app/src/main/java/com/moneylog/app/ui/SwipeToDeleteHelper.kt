package com.moneylog.app.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.moneylog.app.R

/**
 * 지출 목록에서 왼쪽으로 스와이프하면 붉은 "삭제" 배경이 드러나고,
 * 끝까지 밀면 onSwiped가 호출되는 공용 스와이프 삭제 헬퍼.
 * canSwipe로 날짜 헤더처럼 삭제할 수 없는 항목은 스와이프 자체를 막는다.
 * onSwiped는 위치뿐 아니라 스와이프된 항목의 View도 함께 넘겨줘서,
 * 그 자리에서 팡 이펙트 같은 걸 띄우기 편하게 한다.
 */
class SwipeToDeleteHelper(
    private val density: Float,
    private val canSwipe: (position: Int) -> Boolean,
    private val onSwiped: (position: Int, itemView: View) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val position = viewHolder.adapterPosition
        if (position == RecyclerView.NO_POSITION || !canSwipe(position)) return 0
        return super.getSwipeDirs(recyclerView, viewHolder)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition
        if (position == RecyclerView.NO_POSITION) return
        onSwiped.invoke(position, viewHolder.itemView)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
            val itemView = viewHolder.itemView
            val context = itemView.context
            val cornerRadius = 14 * density

            val backgroundPaint = Paint().apply {
                isAntiAlias = true
                color = ContextCompat.getColor(context, R.color.danger)
            }
            val background = RectF(
                itemView.right + dX,
                itemView.top.toFloat(),
                itemView.right.toFloat(),
                itemView.bottom.toFloat()
            )
            c.drawRoundRect(background, cornerRadius, cornerRadius, backgroundPaint)

            val textPaint = Paint().apply {
                isAntiAlias = true
                isFakeBoldText = true
                color = ContextCompat.getColor(context, R.color.white)
                textSize = 13 * density
                textAlign = Paint.Align.RIGHT
            }
            val label = context.getString(R.string.delete_button)
            val textY = itemView.top + itemView.height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            c.drawText(label, itemView.right - 20 * density, textY, textPaint)
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    fun attachTo(recyclerView: RecyclerView) {
        ItemTouchHelper(this).attachToRecyclerView(recyclerView)
    }
}
