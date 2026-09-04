package com.moneylog.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.moneylog.app.common.BudgetPrefs
import com.moneylog.app.common.FunAnimations
import com.moneylog.app.common.SavingsPrefs
import com.moneylog.app.common.TouchPopEffect
import com.moneylog.app.data.ExpenseRepository
import com.moneylog.app.ui.PiggyFillView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

/**
 * 🐷 돼지 저금통 화면.
 * - 화면에 들어올 때, 앱을 처음 켤 때와 똑같은 등장 연출(은은한 글로우 + 통통 튀는 팝업 +
 *   동전이 톡톡 떨어지는 효과)을 저금통 모양 위에 그대로 재현한다.
 * - 지난 달들 중 예산보다 덜 쓴 달의 차액이 자동으로 여기 쌓인다(적립 판단은 PiggyBankManager가
 *   앱을 켤 때 미리 해둔다 — 이 화면은 그 결과를 보여주기만 한다).
 * - 목표 금액 대비 얼마나 모았는지 돼지 저금통이 아래에서부터 차오르는 애니메이션으로 보여준다.
 * - 방금 막 새로 적립된 금액이 있으면(=지난번 화면을 본 뒤로 달이 넘어갔으면) 등장 연출이 끝난 뒤
 *   축하 메시지와 함께 동전이 팡팡 튀는 연출을 한 번 더 보여준다.
 */
class PiggyBankActivity : AppCompatActivity() {

    private lateinit var ivPiggyGlow: View
    private lateinit var piggyFillView: PiggyFillView
    private lateinit var piggyCoin1: View
    private lateinit var piggyCoin2: View
    private lateinit var piggyCoin3: View
    private lateinit var tvPiggyTotal: TextView
    private lateinit var pbPiggyGoal: ProgressBar
    private lateinit var tvPiggyGoalProgress: TextView
    private lateinit var goalSection: LinearLayout
    private lateinit var tvGoalValue: TextView
    private lateinit var tvThisMonthPreview: TextView
    private lateinit var tvTestAddSaving: TextView
    private lateinit var tvResetPiggy: TextView

    /** 목표 달성 축하 연출이 재생 중일 때, 저금통 값이 바뀌어도 축하를 중복으로 띄우지 않기 위한 플래그. */
    private var isCelebratingGoal = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_piggy_bank)

        ivPiggyGlow = findViewById(R.id.ivPiggyGlow)
        piggyFillView = findViewById(R.id.piggyFillView)
        piggyCoin1 = findViewById(R.id.piggyCoin1)
        piggyCoin2 = findViewById(R.id.piggyCoin2)
        piggyCoin3 = findViewById(R.id.piggyCoin3)
        tvPiggyTotal = findViewById(R.id.tvPiggyTotal)
        pbPiggyGoal = findViewById(R.id.pbPiggyGoal)
        tvPiggyGoalProgress = findViewById(R.id.tvPiggyGoalProgress)
        goalSection = findViewById(R.id.goalSection)
        tvGoalValue = findViewById(R.id.tvGoalValue)
        tvThisMonthPreview = findViewById(R.id.tvThisMonthPreview)
        tvTestAddSaving = findViewById(R.id.tvTestAddSaving)
        tvResetPiggy = findViewById(R.id.tvResetPiggy)

        goalSection.setOnClickListener {
            TouchPopEffect.pop(it)
            showGoalEditDialog()
        }

        // 돼지가 통통해지는 모습을 바로 확인해볼 수 있는 테스트용 버튼. 실제 절약 계산과는 무관하게
        // 그냥 저금통에 10만원을 더해준다.
        tvTestAddSaving.setOnClickListener {
            TouchPopEffect.pop(it)
            SavingsPrefs.addSaved(this, 100_000L)
            SavingsPrefs.consumeLastCredited(this)
            loadPiggyState(celebrateIfNew = false)
            Snackbar.make(findViewById(R.id.piggyRoot), getString(R.string.piggy_test_add_message), Snackbar.LENGTH_SHORT).show()
        }

        tvResetPiggy.setOnClickListener {
            TouchPopEffect.pop(it)
            confirmResetPiggy()
        }

        playEntranceAnimation()
        loadPiggyState(celebrateIfNew = true)
    }

    /** 앱 스플래시 화면과 같은 등장 연출: 글로우가 번지고, 저금통이 통통 튀며 나타나고, 동전이 톡톡 떨어진다. */
    private fun playEntranceAnimation() {
        val coins = listOf(piggyCoin1, piggyCoin2, piggyCoin3)

        ivPiggyGlow.alpha = 0f
        ivPiggyGlow.scaleX = 0.4f
        ivPiggyGlow.scaleY = 0.4f
        piggyFillView.alpha = 0f
        piggyFillView.scaleX = 0.3f
        piggyFillView.scaleY = 0.3f
        coins.forEach {
            it.alpha = 0f
            it.translationY = -50f
        }

        val glowIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(ivPiggyGlow, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(ivPiggyGlow, View.SCALE_X, 0.4f, 1f),
                ObjectAnimator.ofFloat(ivPiggyGlow, View.SCALE_Y, 0.4f, 1f)
            )
            duration = 500
            interpolator = AccelerateDecelerateInterpolator()
        }

        val piggyPopIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(piggyFillView, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(piggyFillView, View.SCALE_X, 0.3f, 1.15f, 1f),
                ObjectAnimator.ofFloat(piggyFillView, View.SCALE_Y, 0.3f, 1.15f, 1f)
            )
            duration = 650
            startDelay = 150
            interpolator = OvershootInterpolator(2.2f)
        }

        val piggyWiggle = AnimatorSet().apply {
            playSequentially(
                ObjectAnimator.ofFloat(piggyFillView, View.ROTATION, 0f, -6f).setDuration(120),
                ObjectAnimator.ofFloat(piggyFillView, View.ROTATION, -6f, 6f).setDuration(180),
                ObjectAnimator.ofFloat(piggyFillView, View.ROTATION, 6f, 0f).setDuration(120)
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

        AnimatorSet().apply {
            playTogether(glowIn, piggyPopIn, piggyWiggle, coinDrop)
            start()
        }
    }

    private fun loadPiggyState(celebrateIfNew: Boolean) {
        val totalSaved = SavingsPrefs.getTotalSaved(this)
        val goal = SavingsPrefs.getGoal(this)
        val fraction = if (goal > 0) totalSaved.toFloat() / goal.toFloat() else 0f
        val percent = ((totalSaved * 100) / goal).toInt().coerceIn(0, 999)

        piggyFillView.setFraction(fraction)
        tvPiggyTotal.text = getString(R.string.amount_won_format, totalSaved)
        tvGoalValue.text = getString(R.string.amount_won_format, goal)
        tvPiggyGoalProgress.text = getString(R.string.piggy_goal_progress_format, totalSaved, goal, percent)

        ObjectAnimator.ofInt(pbPiggyGoal, "progress", pbPiggyGoal.progress, percent.coerceIn(0, 100)).apply {
            duration = 700
            start()
        }

        lifecycleScope.launch {
            val currentMonth = LocalDate.now().toString().substring(0, 7)
            val spent = withContext(Dispatchers.IO) { ExpenseRepository.totalAmountForMonth(currentMonth) }
            val budget = BudgetPrefs.getBudget(this@PiggyBankActivity)
            val diff = budget - spent
            if (diff > 0) {
                tvThisMonthPreview.text = getString(R.string.piggy_preview_positive_format, diff)
                tvThisMonthPreview.setTextColor(ContextCompat.getColor(this@PiggyBankActivity, R.color.safe))
            } else {
                tvThisMonthPreview.text = getString(R.string.piggy_preview_over_message)
                tvThisMonthPreview.setTextColor(ContextCompat.getColor(this@PiggyBankActivity, R.color.text_sub))
            }
        }

        if (goal > 0 && totalSaved >= goal && !isCelebratingGoal) {
            // 목표를 다 채웠을 때는 평소의 "새로 쌓였어요" 축하 대신, 훨씬 화려한
            // "부자 되세요~" 연출을 보여주고 저금통을 다음 사이클을 위해 비운다.
            SavingsPrefs.consumeLastCredited(this)
            playGoalReachedCelebration()
        } else if (celebrateIfNew) {
            val credited = SavingsPrefs.consumeLastCredited(this)
            if (credited > 0) {
                celebrateNewSaving(credited)
            }
        }
    }

    /**
     * 목표 금액을 가득 채웠을 때, 화면 전체에 어두운 오버레이 + "부자 되세요~" 큰 문구 +
     * 색종이처럼 흩날리는 이모지들로 아주 화려하게 축하한 뒤, 저금통을 비우고 새로 시작한다.
     */
    private fun playGoalReachedCelebration() {
        isCelebratingGoal = true

        val root = window.decorView as ViewGroup
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CC1A1424"))
            alpha = 0f
            isClickable = true
        }
        root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val messageContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val titleView = TextView(this).apply {
            text = getString(R.string.piggy_goal_reached_title)
            setTextColor(Color.WHITE)
            textSize = 30f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            alpha = 0f
        }
        val subtitleView = TextView(this).apply {
            text = getString(R.string.piggy_goal_reached_subtitle)
            setTextColor(Color.parseColor("#FFF3D8"))
            textSize = 15f
            gravity = Gravity.CENTER
            alpha = 0f
        }
        messageContainer.addView(
            titleView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        messageContainer.addView(
            subtitleView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * resources.displayMetrics.density).toInt()
            }
        )
        overlay.addView(
            messageContainer,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )

        // 색종이처럼 흩날리는 이모지들을 시간차를 두고 계속 터뜨린다.
        val confettiEmojis = listOf("🎉", "✨", "💰", "🪙", "💵")
        repeat(18) { index ->
            overlay.postDelayed({
                if (overlay.isAttachedToWindow) {
                    spawnConfettiParticle(overlay, confettiEmojis.random())
                }
            }, index * 85L)
        }

        val overlayFadeIn = ObjectAnimator.ofFloat(overlay, View.ALPHA, 0f, 1f).apply { duration = 320 }
        val titlePop = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(titleView, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(titleView, View.SCALE_X, 0.3f, 1.25f, 1f),
                ObjectAnimator.ofFloat(titleView, View.SCALE_Y, 0.3f, 1.25f, 1f)
            )
            duration = 650
            startDelay = 200
            interpolator = OvershootInterpolator(2.6f)
        }
        val subtitleFadeIn = ObjectAnimator.ofFloat(subtitleView, View.ALPHA, 0f, 1f).apply {
            duration = 400
            startDelay = 700
        }
        val titleWiggle = AnimatorSet().apply {
            playSequentially(
                ObjectAnimator.ofFloat(titleView, View.ROTATION, 0f, -4f).setDuration(200),
                ObjectAnimator.ofFloat(titleView, View.ROTATION, -4f, 4f).setDuration(280),
                ObjectAnimator.ofFloat(titleView, View.ROTATION, 4f, 0f).setDuration(200)
            )
            startDelay = 900
        }

        AnimatorSet().apply {
            playTogether(overlayFadeIn, titlePop, subtitleFadeIn, titleWiggle)
            start()
        }

        // 충분히 감상할 시간을 준 뒤, 오버레이를 걷어내고 저금통을 다음 사이클을 위해 비운다.
        overlay.postDelayed({
            ObjectAnimator.ofFloat(overlay, View.ALPHA, 1f, 0f).apply {
                duration = 350
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        root.removeView(overlay)
                        isCelebratingGoal = false
                        SavingsPrefs.resetSavedOnly(this@PiggyBankActivity)
                        loadPiggyState(celebrateIfNew = false)
                    }
                })
                start()
            }
        }, 2500L)
    }

    /** 축하 오버레이 위로 이모지 하나가 회전하며 떨어졌다가 사라지는 색종이 조각 하나를 만든다. */
    private fun spawnConfettiParticle(overlay: FrameLayout, emoji: String) {
        val dp = resources.displayMetrics.density
        val screenWidth = if (overlay.width > 0) overlay.width else resources.displayMetrics.widthPixels
        val screenHeight = if (overlay.height > 0) overlay.height else resources.displayMetrics.heightPixels

        val particle = TextView(overlay.context).apply {
            text = emoji
            textSize = (18 + Random.nextInt(14)).toFloat()
            alpha = 1f
        }
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = Random.nextInt(screenWidth.coerceAtLeast(1))
        lp.topMargin = -(40 * dp).toInt()
        overlay.addView(particle, lp)

        val fallDistance = screenHeight.toFloat() + 140f
        val duration = 1600L + Random.nextInt(700)
        val rotateBy = if (Random.nextBoolean()) 280f else -280f
        val drift = (Random.nextFloat() - 0.5f) * 90f * dp

        particle.animate()
            .translationY(fallDistance)
            .translationX(drift)
            .rotation(rotateBy)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                particle.animate().alpha(0f).setDuration(200).withEndAction {
                    overlay.removeView(particle)
                }.start()
            }
            .start()
    }

    /** 새로 저금통에 쌓인 금액이 있을 때, 등장 연출이 끝난 뒤 동전이 팡팡 튀는 축하 연출을 한 번 더 보여준다. */
    private fun celebrateNewSaving(amount: Long) {
        piggyFillView.postDelayed({
            TouchPopEffect.pop(piggyFillView)
            piggyFillView.postDelayed({ TouchPopEffect.pop(piggyFillView) }, 160L)
            piggyFillView.postDelayed({ TouchPopEffect.pop(piggyFillView) }, 320L)

            Snackbar.make(
                findViewById(R.id.piggyRoot),
                getString(R.string.piggy_new_saving_message, amount),
                Snackbar.LENGTH_LONG
            ).show()
        }, 1700L)
    }

    private fun showGoalEditDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            val current = SavingsPrefs.getGoal(this@PiggyBankActivity).toString()
            setText(current)
            setSelection(current.length)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.piggy_goal_edit_title)
            .setView(input)
            .setPositiveButton(R.string.budget_save_button) { _, _ ->
                val amount = input.text.toString().toLongOrNull()
                if (amount != null && amount > 0) {
                    SavingsPrefs.setGoal(this, amount)
                    FunAnimations.jingle(tvGoalValue)
                    loadPiggyState(celebrateIfNew = false)
                }
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    private fun confirmResetPiggy() {
        AlertDialog.Builder(this)
            .setTitle(R.string.piggy_reset_confirm_title)
            .setMessage(R.string.piggy_reset_confirm_message)
            .setPositiveButton(R.string.delete_button) { _, _ ->
                SavingsPrefs.reset(this)
                loadPiggyState(celebrateIfNew = false)
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }
}
