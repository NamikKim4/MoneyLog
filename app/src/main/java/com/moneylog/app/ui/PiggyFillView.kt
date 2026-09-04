package com.moneylog.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.moneylog.app.R
import kotlin.random.Random

/**
 * 저금통에 모인 금액을 "돼지 저금통 안이 아래에서부터 채워지는" 느낌으로 보여주는 커스텀 뷰.
 * - 스플래시 화면과 같은 핑크 돼지 사진을 그대로 써서, 앱 어디서든 "같은 돼지"라는 느낌을 준다.
 * - 목표 금액(goal) 대비 모은 금액(saved)의 비율만큼 하단부터 분홍색이 차오르고, 그 비율이 높을수록
 *   돼지도 조금씩 더 통통해지고 색도 진해져서, 눈으로도 "많이 모았다"는 게 느껴지게 한다.
 * - 화면을 손가락으로 톡 건드리면 돼지가 잠깐 기분 좋게 씰룩거린다(실제 저축 데이터와는 무관한,
 *   순전히 재미를 위한 반응).
 * setFraction()이 호출될 때마다 지금 채워진 높이에서부터 새 목표 높이까지 부드럽게 이어서
 * 애니메이션된다(0부터 다시 시작하지 않아서, 조금씩 더 쌓일 때도 자연스럽다).
 */
class PiggyFillView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var currentFillFraction = 0f
    private var targetFraction = 0f
    private var fillAnimator: ValueAnimator? = null
    private var touchWiggleAnimator: AnimatorSet? = null

    /** 돼지를 톡 건드릴 때마다 위에서 톡 떨어지는 동전 한두 개를 표현하기 위한 상태. */
    private data class FallingCoin(
        val startX: Float,
        val startY: Float,
        val endY: Float,
        val drift: Float,
        val radius: Float,
        var progress: Float = 0f
    )
    private val fallingCoins = mutableListOf<FallingCoin>()

    private val pigBitmap: Bitmap? = runCatching {
        ContextCompat.getDrawable(context, R.drawable.splash_pig_photo)?.toBitmap()
    }.getOrNull()

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
        color = Color.parseColor("#FFD873")
    }
    private val coinStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#E8A93D")
    }
    private val pigPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val primaryDarkColor by lazy { ContextCompat.getColor(context, R.color.primary_dark) }

    init {
        isClickable = true
    }

    /** 0f~1f 사이 비율로 채워진 높이를 정한다(1f를 넘는 값은 가득 찬 것으로 잘라낸다). */
    fun setFraction(fraction: Float) {
        targetFraction = fraction.coerceIn(0f, 1f)
        fillAnimator?.cancel()
        val start = currentFillFraction
        fillAnimator = ValueAnimator.ofFloat(start, targetFraction).apply {
            duration = 1000L
            interpolator = OvershootInterpolator(0.9f)
            addUpdateListener {
                // 살짝 넘치듯 튀어 올랐다가 자리 잡는 느낌을 살리려고 일부러 0~1 범위로 딱 자르지 않는다
                // (그려질 땐 clipPath로 저금통 밖으로 새어나가지 않게 막아준다).
                currentFillFraction = (it.animatedValue as Float).coerceAtLeast(0f)
                invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
            playTouchWiggle()
            spawnFallingCoins()
        }
        return true
    }

    /** 돼지를 건드릴 때마다 동전 2~3개가 위에서 톡톡 떨어졌다가 사라지는 연출을 새로 시작한다. */
    private fun spawnFallingCoins() {
        if (width <= 0 || height <= 0) return
        val dp = resources.displayMetrics.density
        val startY = height * 0.18f
        val endY = height * 0.8f
        val coinCount = 2 + Random.nextInt(2) // 2~3개

        repeat(coinCount) { index ->
            val coin = FallingCoin(
                startX = width / 2f + (Random.nextFloat() - 0.5f) * 46f * dp,
                startY = startY,
                endY = endY,
                drift = (Random.nextFloat() - 0.5f) * 34f * dp,
                radius = (5.5f + Random.nextFloat() * 2f) * dp
            )
            fallingCoins.add(coin)

            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 520L
                startDelay = index * 80L
                interpolator = AccelerateInterpolator(1.15f)
                addUpdateListener {
                    coin.progress = it.animatedValue as Float
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        fallingCoins.remove(coin)
                        invalidate()
                    }
                })
                start()
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** 손가락으로 톡 건드리면 돼지가 살짝 눌렸다가 기분 좋게 씰룩씰룩 흔들린다. */
    private fun playTouchWiggle() {
        touchWiggleAnimator?.cancel()
        scaleX = 1f
        scaleY = 1f
        rotation = 0f

        val squish = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(this@PiggyFillView, View.SCALE_X, 1f, 1.1f, 1f),
                ObjectAnimator.ofFloat(this@PiggyFillView, View.SCALE_Y, 1f, 0.9f, 1f)
            )
            duration = 260
            interpolator = OvershootInterpolator(3f)
        }
        val wiggle = ObjectAnimator.ofFloat(this@PiggyFillView, View.ROTATION, 0f, -8f, 8f, -5f, 3f, 0f).apply {
            duration = 420
        }

        touchWiggleAnimator = AnimatorSet().apply {
            playTogether(squish, wiggle)
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
        val fraction = currentFillFraction.coerceIn(0f, 1f)

        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawRoundRect(rect, radius, radius, jarBgPaint)

        val fillTop = rect.bottom - (rect.height() * currentFillFraction)
        if (currentFillFraction > 0f) {
            canvas.drawRect(rect.left, fillTop, rect.right, rect.bottom, fillPaint)
            fillSurfacePaint.strokeWidth = 2.4f * dp
            canvas.drawLine(rect.left, fillTop, rect.right, fillTop, fillSurfacePaint)
        }

        canvas.restore()
        canvas.drawRoundRect(rect, radius, radius, jarStrokePaint)

        drawPig(canvas, rect, dp, fraction)
        if (fallingCoins.isNotEmpty()) {
            drawFallingCoins(canvas)
        }
    }

    /** 톡 건드릴 때마다 위에서부터 톡톡 떨어지며 흩날리다 사라지는 동전들을 그린다. */
    private fun drawFallingCoins(canvas: Canvas) {
        fallingCoins.forEach { coin ->
            val y = coin.startY + (coin.endY - coin.startY) * coin.progress
            val x = coin.startX + coin.drift * coin.progress
            val alpha = if (coin.progress > 0.7f) {
                (255 * (1f - (coin.progress - 0.7f) / 0.3f)).toInt().coerceIn(0, 255)
            } else {
                255
            }
            coinPaint.alpha = alpha
            coinStrokePaint.alpha = alpha
            drawCoin(canvas, x, y, coin.radius)
        }
        coinPaint.alpha = 255
        coinStrokePaint.alpha = 255
    }

    /** 실제 사진 돼지를 그린다. 많이 모을수록 살짝 더 통통해지고, 색도 은은하게 진해진다. */
    private fun drawPig(canvas: Canvas, rect: RectF, dp: Float, fraction: Float) {
        val bitmap = pigBitmap ?: return

        // 0%일 땐 조금 작고 마른 느낌, 다 채우면 더 크고 통통한 느낌.
        val pigScale = 0.82f + 0.34f * currentFillFraction
        val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val baseHeight = 96f * dp * pigScale
        val baseWidth = baseHeight * aspect

        val left = rect.centerX() - baseWidth / 2f
        val top = rect.centerY() - baseHeight / 2f - 6f * dp
        val destRect = RectF(left, top, left + baseWidth, top + baseHeight)

        val tintAlpha = (fraction * 90).toInt().coerceIn(0, 90)
        pigPaint.colorFilter = if (tintAlpha > 0) {
            PorterDuffColorFilter(
                Color.argb(tintAlpha, Color.red(primaryDarkColor), Color.green(primaryDarkColor), Color.blue(primaryDarkColor)),
                PorterDuff.Mode.SRC_ATOP
            )
        } else {
            null
        }

        canvas.drawBitmap(bitmap, null, destRect, pigPaint)
    }

    private fun drawCoin(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawCircle(cx, cy, r, coinPaint)
        canvas.drawCircle(cx, cy, r, coinStrokePaint)
    }
}
