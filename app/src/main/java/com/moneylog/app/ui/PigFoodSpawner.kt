package com.moneylog.app.ui

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 화면 위 아무 곳에나 동전/먹이 이모지를 하나 띄워두고, 사용자가 손가락으로 끌어다
 * 목표 뷰(돼지 마스코트) 위에 놓으면 [onFed]를 불러주는 작은 헬퍼.
 * 목표 위에 놓지 않으면 원래 뜬 자리로 톡 하고 되돌아간다.
 */
object PigFoodSpawner {

    private val FOOD_EMOJIS = listOf("🪙", "🍎", "🥕", "🌽", "🍞", "🍓")

    /**
     * [container] 안 임의의 위치에 먹이를 하나 띄운다. 이미 떠 있는 먹이가 있으면 아무 일도 하지 않는다
     * (한 번에 하나만 떠 있게 해서 화면이 지저분해지지 않게 한다).
     * [onDragStart]/[onDragEnd]는 드래그가 시작되고 끝날 때 호출된다 — 화면 전체에 걸린 다른 제스처
     * (예: 좌우 스와이프로 달 넘기기)가 드래그 도중에 끼어들지 않도록, 호출하는 쪽에서 그 제스처를
     * 잠깐 꺼두는 용도로 쓴다.
     */
    fun spawn(
        context: Context,
        container: FrameLayout,
        target: View,
        onDragStart: () -> Unit = {},
        onDragEnd: () -> Unit = {},
        onFed: () -> Unit
    ) {
        if (container.childCount > 0) return

        val containerWidth = container.width
        val containerHeight = container.height
        if (containerWidth <= 0 || containerHeight <= 0) return // 아직 레이아웃 전이면 다음 시도 때 다시 뜬다.

        val density = context.resources.displayMetrics.density
        val foodSizePx = (32 * density).toInt()
        val marginPx = (24 * density).toInt()
        val spawnTopPx = (90 * density).toInt()

        val maxLeft = (containerWidth - foodSizePx - marginPx).coerceAtLeast(marginPx)
        val maxTop = (containerHeight * 0.45f).toInt().coerceAtLeast(spawnTopPx)

        val food = TextView(context).apply {
            text = FOOD_EMOJIS.random()
            textSize = 22f
            gravity = Gravity.CENTER
            includeFontPadding = false
        }

        val params = FrameLayout.LayoutParams(foodSizePx, foodSizePx).apply {
            leftMargin = (marginPx..maxLeft).random()
            topMargin = (spawnTopPx..maxTop).random()
        }
        container.addView(food, params)

        // 등장할 때 살짝 통통 튀듯 나타나게 한다.
        food.scaleX = 0f
        food.scaleY = 0f
        food.animate().scaleX(1f).scaleY(1f).setDuration(220L).start()

        var lastRawX = 0f
        var lastRawY = 0f
        food.setOnTouchListener { view, event ->
            // 이 이벤트를 먹이가 가져간다고 부모(스크롤뷰 등)에게 알려서, 드래그 도중에
            // 화면이 대신 스크롤되거나 다른 제스처가 끼어들지 않게 한다.
            view.parent?.requestDisallowInterceptTouchEvent(true)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    onDragStart()
                    view.animate().scaleX(1.25f).scaleY(1.25f).setDuration(120L).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    view.translationX += event.rawX - lastRawX
                    view.translationY += event.rawY - lastRawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    onDragEnd()
                    if (overlaps(view, target)) {
                        onFed()
                        view.animate()
                            .scaleX(0.15f)
                            .scaleY(0.15f)
                            .alpha(0f)
                            .setDuration(220L)
                            .withEndAction { container.removeView(view) }
                            .start()
                    } else {
                        view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationX(0f)
                            .translationY(0f)
                            .setDuration(260L)
                            .start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** 먹이가 목표 뷰 위에 놓였는지, 화면 좌표 기준으로 (여유를 좀 두고) 겹치는지 확인한다. */
    private fun overlaps(food: View, target: View): Boolean {
        val foodRect = Rect()
        food.getGlobalVisibleRect(foodRect)
        val targetRect = Rect()
        target.getGlobalVisibleRect(targetRect)

        val expand = (16 * target.resources.displayMetrics.density).toInt()
        targetRect.inset(-expand, -expand)

        return Rect.intersects(foodRect, targetRect)
    }
}
