package com.moneylog.app.common

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.OvershootInterpolator

/**
 * 돼지 저금통 테마에 어울리는 자잘한 재미 애니메이션 모음.
 * - bounce: 돼지가 통통 튀는 듯한 확대/축소 (저금통에 돈이 들어가거나, 화면에 등장할 때)
 * - jingle: 동전이 짤랑거리듯 좌우로 흔들리는 회전 (금액이 바뀌거나 늘어날 때)
 * 둘 다 이미 진행 중인 애니메이션이 있으면 취소하고 새로 시작해서 여러 번 빠르게
 * 눌러도 view가 이상한 상태로 멈추지 않게 한다.
 */
object FunAnimations {

    fun bounce(view: View) {
        view.animate().cancel()
        view.scaleX = 1f
        view.scaleY = 1f
        view.animate()
            .scaleX(1.28f)
            .scaleY(1.28f)
            .setDuration(140L)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(260L)
                    .setInterpolator(OvershootInterpolator(3f))
                    .start()
            }
            .start()
    }

    fun jingle(view: View) {
        view.animate().cancel()
        view.rotation = 0f
        ObjectAnimator.ofFloat(
            view, View.ROTATION,
            0f, -12f, 10f, -8f, 6f, -3f, 0f
        ).apply {
            duration = 480L
            start()
        }
    }
}
