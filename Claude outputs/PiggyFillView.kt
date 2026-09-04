package com.moneylog.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.moneylog.app.R

/**
 * 저금통에 모인 금액을 "돼지 저금통 안이 아래에서부터 채워지는" 느낌으로 보여주는 커스텀 뷰.
 * 목표 금액(goal) 대비 모은 금액(saved)의 비율만큼 하단부터 분홍색이 차오르고,
 * setFraction()이 호출될 때마다 지금 채워진 높이에서부터 새 목표 높이까지 부드럽게 이어서
 * 애니메이션된다(0부터 다시 시작하지 않아서, 조금씩 더 쌓일 때도 자연스럽다).
 * 가운데엔 🐷가 자리를 지키고 있어서, 물이 차오르듯 돼지 발밑까지 분홍색이 차오르는 걸 볼 수 있다.
 */
class PiggyFillView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var animatedFraction = 0f
    private var targetFraction = 0f
    private var animator: ValueAnimator? = null

    private val jarStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = ContextCompat.getColor(context, R.color.border)
    }
    private val jarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.input_bg)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
    }
    private val fillSurfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary_dark)
        style = Paint.Style.STROKE
    }
    private val coinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chip_primary_bg)
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    /** 0f~1f 사이 비율로 채워진 높이를 정한다(1f를 넘는 값은 가득 찬 것으로 잘라낸다). */
    fun setFraction(fraction: Float) {
        targetFraction = fraction.coerceIn(0f, 1f)
        animator?.cancel()
        val start = animatedFraction
        animator = ValueAnimator.ofFloat(start, targetFraction).apply {
            duration = 1000L
            interpolator = OvershootInterpolator(0.6f)
            addUpdateListener {
                animatedFraction = (it.animatedValue as Float).coerceIn(0f, 1f)
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val dp = resources.displayMetrics.density
        val radius = 26f * dp
        val rect = RectF(3f * dp, 3f * dp, width - 3f * dp, height - 3f * dp)
        val clipPath = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }

        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRoundRect(rect, radius, radius, jarBgPaint)

        // 저금통 바닥에 살짝 보이는 동전 두 개 — 채워질수록 분홍 물결 아래로 자연스럽게 묻힌다.
        canvas.drawCircle(rect.centerX() - 20f * dp, rect.bottom - 14f * dp, 7f * dp, coinPaint)
        canvas.drawCircle(rect.centerX() + 22f * dp, rect.bottom - 10f * dp, 6f * dp, coinPaint)

        val fillTop = rect.bottom - (rect.height() * animatedFraction)
        if (animatedFraction > 0f) {
            canvas.drawRect(rect.left, fillTop, rect.right, rect.bottom, fillPaint)
            fillSurfacePaint.strokeWidth = 2.4f * dp
            canvas.drawLine(rect.left, fillTop, rect.right, fillTop, fillSurfacePaint)
        }

        canvas.restore()
        canvas.drawRoundRect(rect, radius, radius, jarStrokePaint)

        emojiPaint.textSize = 40f * dp
        canvas.drawText("🐷", rect.centerX(), rect.centerY() + 14f * dp, emojiPaint)
    }
}
