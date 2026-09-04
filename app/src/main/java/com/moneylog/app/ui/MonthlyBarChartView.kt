package com.moneylog.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.moneylog.app.R
import kotlin.math.abs

/**
 * 최근 몇 달치 지출 합계를, 딱딱한 막대그래프 대신 끝이 둥근 "젤리" 막대로 보여주는
 * 커스텀 뷰. 가장 많이 쓴 달 막대 위에는 작은 돼지 이모지를 얹어서 한눈에 띄게 한다.
 * 값이 실제로 채워지는 느낌을 주기 위해 매번 setData()를 부를 때마다 0에서부터
 * 자라나는 애니메이션으로 그려진다.
 * 막대를 탭하면 onBarClick(그 막대의 인덱스)으로 알려줘서, 그 달의 지출 상세로 이동할 수 있게 한다.
 */
class MonthlyBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** label: "9월"처럼 막대 아래 보여줄 짧은 라벨. isCurrent면 진한 색으로 강조한다. */
    data class MonthBar(val label: String, val amount: Long, val isCurrent: Boolean)

    /** 막대를 탭했을 때 그 막대의 인덱스(setData에 넘긴 리스트 기준)를 알려준다. */
    var onBarClick: ((Int) -> Unit)? = null

    private var bars: List<MonthBar> = emptyList()
    private var progress = 0f
    private var animator: ValueAnimator? = null

    // 통계 화면은 화면을 좌우로 스와이프해서 달을 넘기는 제스처도 함께 쓴다. 스와이프의 마지막
    // 손가락 뗀 지점(ACTION_UP)이 이 그래프 위에서 끝나면, 손가락을 거의 움직이지 않은 "탭"과
    // 구분해야 한다 — 그렇지 않으면 다음 달로 넘기려고 스와이프할 때마다 그 지점의 막대를 탭한
    // 것으로 잘못 인식되어, 그 달 지출 목록 화면으로 튕겨나가 버린다(= "처음 화면으로 넘어가는 버그").
    private val tapTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f

    init {
        isClickable = true
    }

    private val barPaintPast = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.border)
    }
    private val barPaintCurrent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_sub)
        textAlign = Paint.Align.CENTER
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    fun setData(newBars: List<MonthBar>) {
        bars = newBars
        animator?.cancel()
        progress = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val movedX = abs(event.x - downX)
                val movedY = abs(event.y - downY)
                val wasTap = movedX < tapTouchSlop && movedY < tapTouchSlop
                if (wasTap && bars.isNotEmpty() && width > 0) {
                    val slot = width.toFloat() / bars.size
                    val index = (event.x / slot).toInt().coerceIn(0, bars.size - 1)
                    performClick()
                    onBarClick?.invoke(index)
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty() || width <= 0 || height <= 0) return

        val dp = resources.displayMetrics.density
        labelPaint.textSize = 11f * dp
        emojiPaint.textSize = 16f * dp

        val labelAreaHeight = 16f * dp
        val emojiAreaHeight = 20f * dp
        val chartBottom = height - labelAreaHeight
        val chartTop = emojiAreaHeight
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)

        val maxAmount = (bars.maxOfOrNull { it.amount } ?: 0L).coerceAtLeast(1L)
        val maxIndex = bars.indices.maxByOrNull { bars[it].amount } ?: -1
        val slot = width.toFloat() / bars.size
        val barWidth = (slot * 0.4f).coerceAtMost(28f * dp)
        val radius = barWidth / 2f

        bars.forEachIndexed { index, bar ->
            val centerX = slot * index + slot / 2f
            val heightFraction = bar.amount.toFloat() / maxAmount
            val minVisibleHeight = if (bar.amount > 0) 4f * dp else 0f
            val barHeight = (chartHeight * heightFraction * progress).coerceAtLeast(minVisibleHeight * progress)
            val top = chartBottom - barHeight
            val rect = RectF(centerX - barWidth / 2f, top, centerX + barWidth / 2f, chartBottom)
            canvas.drawRoundRect(rect, radius, radius, if (bar.isCurrent) barPaintCurrent else barPaintPast)

            canvas.drawText(bar.label, centerX, height.toFloat() - 3f * dp, labelPaint)

            if (index == maxIndex && bar.amount > 0 && progress > 0.6f) {
                canvas.drawText("🐷", centerX, top - 6f * dp, emojiPaint)
            }
        }
    }
}
