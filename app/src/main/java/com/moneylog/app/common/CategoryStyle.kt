package com.moneylog.app.common

import com.moneylog.app.R

/**
 * 지출 카테고리 문자열(예: "🍚 식비")을 리스트 아이콘/색으로 바꿔주는 헬퍼.
 * 프리셋 카테고리는 앞의 이모지를 그대로 원형 아이콘에 쓰고, 이모지가 없는
 * 직접 입력 카테고리는 기본 아이콘(💰)을 쓴다. 배경색은 카테고리 이름을 기준으로
 * 늘 같은 파스텔 색이 나오게 해서, 새로 만든 카테고리도 자동으로 색이 생긴다.
 */
object CategoryStyle {

    private val palette = listOf(
        R.color.cat_bg_1,
        R.color.cat_bg_2,
        R.color.cat_bg_3,
        R.color.cat_bg_4,
        R.color.cat_bg_5,
        R.color.cat_bg_6,
        R.color.cat_bg_7,
        R.color.cat_bg_8
    )

    fun icon(category: String): String {
        val firstToken = category.trim().substringBefore(' ')
        return if (looksLikeEmoji(firstToken)) firstToken else "💰"
    }

    /** 아이콘에 이미 이모지가 들어가므로, 글자에는 이모지를 뺀 이름만 보여준다. */
    fun label(category: String): String {
        val trimmed = category.trim()
        val firstToken = trimmed.substringBefore(' ')
        return if (looksLikeEmoji(firstToken) && trimmed.contains(' ')) {
            trimmed.substringAfter(' ').trim()
        } else {
            trimmed
        }
    }

    fun backgroundColorRes(category: String): Int {
        val index = (category.hashCode() and Int.MAX_VALUE) % palette.size
        return palette[index]
    }

    /** 글자/숫자가 하나도 없으면(한글·영문 단어가 아니면) 이모지/기호로 본다. */
    private fun looksLikeEmoji(token: String): Boolean {
        if (token.isEmpty()) return false
        var i = 0
        while (i < token.length) {
            val codePoint = token.codePointAt(i)
            if (Character.isLetterOrDigit(codePoint)) return false
            i += Character.charCount(codePoint)
        }
        return true
    }
}
