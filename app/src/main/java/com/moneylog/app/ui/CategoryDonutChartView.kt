package com.moneylog.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.moneylog.app.R

/**
 * 카테고리별 지출 비중을 꽉 막힌 원 그래프 대신, 조각 사이사이 살짝 틈을 준
 * "도넛" 모양으로 보여주는 커스텀 뷰. 가운데에는 그 달 총 지출 금액을 적어서
 * 그래프만 봐도 "전체 대비 이만큼"이 바로 느껴지게 한다.
 */
class CategoryDonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Segment(val amount: Long, val colorRes: Int)

    private var segments: List<Segment> = emptyList()
    private var totalAmount: Long = 0L
    private var progress = 0f
    private var animator: ValueAnimator? = null

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val emptyRingColor = ContextCompat.getColor(context, R.color.divider)
    private val centerAmountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_main)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val centerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_sub)
        textAlign = Paint.Align.CENTER
    }
    private val centerLabelText = context.getString(R.string.category_donut_center_label)

    fun setData(newSegments: List<Segment>, total: Long) {
        segments = newSegments
        totalAmount = total
        animator?.cancel()
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 620L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val dp = resources.displayMetrics.density
        val strokeWidth = 16f * dp
        arcPaint.strokeWidth = strokeWidth
        centerAmountPaint.textSize = 15f * dp
        centerLabelPaint.textSize = 10f * dp

        val size = minOf(width, height).toFloat()
        val inset = strokeWidth / 2f + 3f * dp
        val left = (width - size) / 2f + inset
        val top = (height - size) / 2f + inset
        val rect = RectF(left, top, left + size - inset * 2f, top + size - inset * 2f)

        if (segments.isEmpty() || totalAmount <= 0) {
            arcPaint.color = emptyRingColor
            canvas.drawArc(rect, 0f, 359.9f, false, arcPaint)
        } else {
            val gapDegrees = 3f
            val usableDegrees = (360f - gapDegrees * segments.size).coerceAtLeast(0f)
            var startAngle = -90f
            segments.forEach { segment ->
                val fullSweep = (segment.amount.toFloat() / totalAmount) * usableDegrees
                val sweep = fullSweep * progress
                arcPaint.color = ContextCompat.getColor(context, segment.colorRes)
                if (sweep > 0f) canvas.drawArc(rect, startAngle, sweep, false, arcPaint)
                startAngle += fullSweep + gapDegrees
            }
        }

        val cx = width / 2f
        val cy = height / 2f
        canvas.drawText(centerLabelText, cx, cy - centerAmountPaint.textSize / 1.4f, centerLabelPaint)
        canvas.drawText(
            context.getString(R.string.amount_won_format, totalAmount),
            cx,
            cy + centerAmountPaint.textSize / 2.5f,
            centerAmountPaint
        )
    }
}
