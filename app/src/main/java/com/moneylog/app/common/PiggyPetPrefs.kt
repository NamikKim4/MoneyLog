package com.moneylog.app.common

import android.content.Context
import java.time.LocalDate

/**
 * 메인 화면 옆의 작은 돼지 마스코트를 몇 번이나 쓰다듬어줬는지(=탭한 횟수), 오늘 먹이를 몇 번
 * 줬는지를 기억해두는 저장소. 실제 저축 금액(SavingsPrefs)과는 완전히 별개이고, 그냥 돼지를
 * 키우는 듯한 재미를 주기 위한 카운터일 뿐이다.
 */
object PiggyPetPrefs {

    private const val PREFS_NAME = "moneylog_prefs"
    private const val KEY_INTERACTIONS = "pig_mascot_interactions"
    private const val KEY_FEED_DATE = "pig_mascot_feed_date"
    private const val KEY_FEED_COUNT_TODAY = "pig_mascot_feed_count_today"

    /** 아기 -> 무럭무럭 자라는 중 -> 다 컸어요, 세 단계로 나눠서 자라는(영구적인) 느낌을 준다. */
    const val STAGE_BABY = 0
    const val STAGE_GROWING = 1
    const val STAGE_GROWN = 2

    private const val GROWING_THRESHOLD = 5
    private const val GROWN_THRESHOLD = 15

    /** 오늘 먹이를 준 횟수 * 이 값(dp)만큼, 원래 크기 위에 하루 동안만 살짝 더 커 보이게 한다. */
    private const val PER_FEED_GROWTH_DP = 1
    private const val MAX_DAILY_FEEDS = 8

    fun getInteractionCount(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_INTERACTIONS, 0)
    }

    /** 돼지 마스코트를 탭할(=쓰다듬을) 때마다 호출한다. 반환값은 이번 상호작용 이후의 누적 횟수. */
    fun recordInteraction(context: Context): Int {
        val next = getInteractionCount(context) + 1
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_INTERACTIONS, next)
            .apply()
        return next
    }

    fun stageFor(interactionCount: Int): Int = when {
        interactionCount >= GROWN_THRESHOLD -> STAGE_GROWN
        interactionCount >= GROWING_THRESHOLD -> STAGE_GROWING
        else -> STAGE_BABY
    }

    /** 오늘 먹이를 준 횟수. 날짜가 바뀌면(=자정이 지나면) 자동으로 0으로 돌아간다. */
    private fun getTodayFeedCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(KEY_FEED_DATE, null)
        return if (savedDate == LocalDate.now().toString()) prefs.getInt(KEY_FEED_COUNT_TODAY, 0) else 0
    }

    /** 먹이를 줄 때마다 호출한다. 날짜가 바뀐 뒤 첫 먹이라면 1부터 새로 센다. */
    fun recordFeeding(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val next = (getTodayFeedCount(context) + 1).coerceAtMost(MAX_DAILY_FEEDS)
        prefs.edit()
            .putString(KEY_FEED_DATE, LocalDate.now().toString())
            .putInt(KEY_FEED_COUNT_TODAY, next)
            .apply()
    }

    /** 오늘 먹이를 준 만큼 더해줄 임시 크기(dp). 다음 날이 되면 자연스럽게 0으로 돌아간다. */
    fun todaysFeedGrowthDp(context: Context): Int = getTodayFeedCount(context) * PER_FEED_GROWTH_DP
}
