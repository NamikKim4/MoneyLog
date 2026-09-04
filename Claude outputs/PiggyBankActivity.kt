package com.moneylog.app

import android.animation.ObjectAnimator
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
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

/**
 * 🐷 돼지 저금통 화면.
 * - 지난 달들 중 예산보다 덜 쓴 달의 차액이 자동으로 여기 쌓인다(적립 판단은 PiggyBankManager가
 *   앱을 켤 때 미리 해둔다 — 이 화면은 그 결과를 보여주기만 한다).
 * - 목표 금액 대비 얼마나 모았는지 돼지 저금통이 아래에서부터 차오르는 애니메이션으로 보여준다.
 * - 방금 막 새로 적립된 금액이 있으면(=지난번 화면을 본 뒤로 달이 넘어갔으면) 축하 메시지와
 *   함께 통통 튀는 애니메이션을 한 번 보여준다.
 */
class PiggyBankActivity : AppCompatActivity() {

    private lateinit var piggyFillView: PiggyFillView
    private lateinit var tvPiggyTotal: TextView
    private lateinit var pbPiggyGoal: ProgressBar
    private lateinit var tvPiggyGoalProgress: TextView
    private lateinit var goalSection: LinearLayout
    private lateinit var tvGoalValue: TextView
    private lateinit var tvThisMonthPreview: TextView
    private lateinit var tvResetPiggy: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_piggy_bank)

        piggyFillView = findViewById(R.id.piggyFillView)
        tvPiggyTotal = findViewById(R.id.tvPiggyTotal)
        pbPiggyGoal = findViewById(R.id.pbPiggyGoal)
        tvPiggyGoalProgress = findViewById(R.id.tvPiggyGoalProgress)
        goalSection = findViewById(R.id.goalSection)
        tvGoalValue = findViewById(R.id.tvGoalValue)
        tvThisMonthPreview = findViewById(R.id.tvThisMonthPreview)
        tvResetPiggy = findViewById(R.id.tvResetPiggy)

        goalSection.setOnClickListener {
            TouchPopEffect.pop(it)
            showGoalEditDialog()
        }

        tvResetPiggy.setOnClickListener {
            TouchPopEffect.pop(it)
            confirmResetPiggy()
        }

        loadPiggyState(celebrateIfNew = true)
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

        if (celebrateIfNew) {
            val credited = SavingsPrefs.consumeLastCredited(this)
            if (credited > 0) {
                celebrateNewSaving(credited)
            }
        }
    }

    /** 새로 저금통에 쌓인 금액이 있을 때, 돼지가 통통 튀고 동전이 팡팡 튀는 축하 연출을 보여준다. */
    private fun celebrateNewSaving(amount: Long) {
        piggyFillView.postDelayed({
            FunAnimations.bounce(piggyFillView)
            TouchPopEffect.pop(piggyFillView)
            piggyFillView.postDelayed({ TouchPopEffect.pop(piggyFillView) }, 160L)
            piggyFillView.postDelayed({ TouchPopEffect.pop(piggyFillView) }, 320L)

            Snackbar.make(
                findViewById(R.id.piggyRoot),
                getString(R.string.piggy_new_saving_message, amount),
                Snackbar.LENGTH_LONG
            ).show()
        }, 400L)
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
