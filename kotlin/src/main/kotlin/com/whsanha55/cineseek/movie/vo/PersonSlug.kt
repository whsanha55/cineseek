package com.whsanha55.cineseek.movie.vo

import java.text.Normalizer
import java.util.Locale

/**
 * 인물 페이지 URL slug — 영어 이름을 소문자·하이픈으로 바꾼다 (예: "Song Kang-ho" → "song-kang-ho").
 * 인물은 URL 앞의 id로 찾고 slug는 읽기 좋게 붙이는 장식이라 겹쳐도 된다. 라틴 문자가 남지 않으면 null
 */
object PersonSlug {
    private const val MAX_LENGTH = 60

    private val DIACRITICS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    fun from(nameEn: String?): String? {
        if (nameEn == null) return null
        val ascii = DIACRITICS.replace(Normalizer.normalize(nameEn, Normalizer.Form.NFD), "")
        return NON_ALNUM.replace(ascii.lowercase(Locale.ROOT), "-")
            .trim('-')
            .take(MAX_LENGTH)
            .trimEnd('-')
            .ifEmpty { null }
    }
}
