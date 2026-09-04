package com.moneylog.app.common

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.random.Random

/**
 * 키패드나 버튼처럼 톡톡 누르는 곳마다 작은 돼지/동전 이모지가 팡 하고
 * 떠올랐다 사라지는 효과. 어떤 화면의 어떤 버튼에서 호출해도 그 버튼이
 * 화면에서 차지하는 위치에서 이모지가 하나 튀어나온다.
 *
 * 액티비티마다 레이아웃을 따로 손대지 않아도 되도록, 모든 액티비티가 기본으로
 * 가지고 있는 android.R.id.content(윈도우가 만들어주는 최상위 FrameLayout) 위에
 * 이모지를 잠깐 얹었다가 애니메이션이 끝나면 스스로 제거한다.
 */
object TouchPopEffect {

    private val emojis = listOf("🐷", "🪙", "🐷", "🪙", "✨")

    fun pop(anchorView: View) {
        val activity = anchorView.context as? Activity ?: return
        val overlayRoot = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        val overlayLocation = IntArray(2)
        overlayRoot.getLocationOnScreen(overlayLocation)
        val anchorLocation = IntArray(2)
        anchorView.getLocationOnScreen(anchorLocation)

        val centerX = anchorLocation[0] - overlayLocation[0] + anchorView.width / 2f
        val centerY = anchorLocation[1] - overlayLocation[1] + anchorView.height / 2f

        val emoji = TextView(activity).apply {
            text = emojis.random()
            textSize = 18f
            alpha = 0f
            scaleX = 0.4f
            scaleY = 0.4f
        }

        overlayRoot.addView(
            emoji,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        emoji.post {
            emoji.x = centerX - emoji.width / 2f
            emoji.y = centerY - emoji.height / 2f

            val jitterX = Random.nextInt(-14, 15).toFloat()

            emoji.animate()
                .alpha(1f)
                .scaleX(1.2f)
                .scaleY(1.2f)
                .translationYBy(-18f)
                .translationXBy(jitterX)
                .setDuration(180L)
                .withEndAction {
                    emoji.animate()
                        .alpha(0f)
                        .scaleX(0.85f)
                        .scaleY(0.85f)
                        .translationYBy(-26f)
                        .setDuration(340L)
                        .withEndAction {
                            overlayRoot.removeView(emoji)
                        }
                        .start()
                }
                .start()
        }
    }
}
