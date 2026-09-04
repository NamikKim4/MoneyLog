package com.moneylog.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 로그인 없이 앱을 켜면 바로 뜨는 스플래시 화면.
 * 배경 없이 핑크 돼지 저금통(사진)만 통통 튀어 오르듯 팝업되고,
 * 은은한 글로우가 번진 뒤, 진짜 동전 모양이 저금통 구멍으로 톡톡 떨어지는
 * 애니메이션이 이어지고, 앱 이름이 나타나면 지출 목록 화면으로 넘어간다.
 * 화면을 탭하면 바로 건너뛸 수 있다.
 */
class SplashActivity : AppCompatActivity() {

    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val splashRoot: View = findViewById(R.id.splashRoot)
        val ivGlow: View = findViewById(R.id.ivGlow)
        val ivPigLogo: ImageView = findViewById(R.id.ivPigLogo)
        val tvAppName: TextView = findViewById(R.id.tvAppName)
        val tvTagline: TextView = findViewById(R.id.tvTagline)
        val coin1: View = findViewById(R.id.coin1)
        val coin2: View = findViewById(R.id.coin2)
        val coin3: View = findViewById(R.id.coin3)
        val coins = listOf(coin1, coin2, coin3)

        // 시작 전 초기 상태(전부 숨김/축소)
        ivGlow.alpha = 0f
        ivGlow.scaleX = 0.4f
        ivGlow.scaleY = 0.4f
        ivPigLogo.alpha = 0f
        ivPigLogo.scaleX = 0.3f
        ivPigLogo.scaleY = 0.3f
        tvAppName.alpha = 0f
        tvAppName.translationY = 40f
        tvTagline.alpha = 0f
        coins.forEach {
            it.alpha = 0f
            it.translationY = -50f
        }

        val glowIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(ivGlow, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(ivGlow, View.SCALE_X, 0.4f, 1f),
                ObjectAnimator.ofFloat(ivGlow, View.SCALE_Y, 0.4f, 1f)
            )
            duration = 500
            interpolator = AccelerateDecelerateInterpolator()
        }

        val pigPopIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(ivPigLogo, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(ivPigLogo, View.SCALE_X, 0.3f, 1.15f, 1f),
                ObjectAnimator.ofFloat(ivPigLogo, View.SCALE_Y, 0.3f, 1.15f, 1f)
            )
            duration = 650
            startDelay = 150
            interpolator = OvershootInterpolator(2.2f)
        }

        val pigWiggle = AnimatorSet().apply {
            playSequentially(
                ObjectAnimator.ofFloat(ivPigLogo, View.ROTATION, 0f, -6f).setDuration(120),
                ObjectAnimator.ofFloat(ivPigLogo, View.ROTATION, -6f, 6f).setDuration(180),
                ObjectAnimator.ofFloat(ivPigLogo, View.ROTATION, 6f, 0f).setDuration(120)
            )
            startDelay = 820
        }

        val coinDrop = AnimatorSet().apply {
            val perCoin = coins.mapIndexed { index, coin ->
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(coin, View.TRANSLATION_Y, -50f, 8f, -4f),
                        ObjectAnimator.ofFloat(coin, View.ALPHA, 0f, 1f, 1f, 0f)
                    )
                    duration = 480
                    startDelay = 950L + index * 90L
                    interpolator = AccelerateDecelerateInterpolator()
                }
            }
            playTogether(perCoin)
        }

        val textIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(tvAppName, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(tvAppName, View.TRANSLATION_Y, 40f, 0f),
                ObjectAnimator.ofFloat(tvTagline, View.ALPHA, 0f, 1f)
            )
            duration = 450
            startDelay = 550
            interpolator = AccelerateDecelerateInterpolator()
        }

        // 마지막 동전 애니메이션이 끝나고도 잠깐 여운을 두기 위한 홀드 구간
        val hold = ObjectAnimator.ofFloat(tvTagline, View.ALPHA, 1f, 1f).apply {
            duration = 350
            startDelay = 1650
        }

        val fullAnimation = AnimatorSet().apply {
            playTogether(glowIn, pigPopIn, pigWiggle, coinDrop, textIn, hold)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    goToExpenseList()
                }
            })
        }

        fullAnimation.start()

        // 애니메이션이 다 끝나기 전에 화면을 탭하면 바로 건너뛴다.
        splashRoot.setOnClickListener {
            fullAnimation.cancel()
            goToExpenseList()
        }
    }

    private fun goToExpenseList() {
        if (hasNavigated) return
        hasNavigated = true
        startActivity(Intent(this, ExpenseListActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
