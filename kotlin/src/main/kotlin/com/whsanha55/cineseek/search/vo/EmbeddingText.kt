package com.whsanha55.cineseek.search.vo

/**
 * 임베딩 입력 조립 — 형식이 곧 인덱스 호환성(input_hash)이다.
 * 형식을 바꾸면 전체 재임베딩(서버 CPU 기준 수 시간~수십 시간)이 유도되므로 신중하게 변경한다.
 */
object EmbeddingText {

    /**
     * `제목 (원제) · 장르: A, B · 분위기: 태그1, 태그2 · 분위기 설명: … · 줄거리: … · 분위기: 태그1, 태그2` 형식으로 조립한다.
     * 원제는 null·blank이거나 제목과 같으면 생략하고, 값이 비는 섹션은 구분자(` · `)까지 통째로 생략한다.
     * 분위기 태그는 줄거리 뒤에 한 번 더 반복한다 — 긴 줄거리에 태그 토큰이 묻혀 분위기 쿼리 매칭이 실패하는 것을 비중으로 보정한다(#34)
     */
    fun assemble(
        title: String,
        originalTitle: String?,
        genreNames: List<String>,
        moodTags: List<String>,
        moodDesc: String?,
        overview: String,
    ): String {
        val heading = if (originalTitle.isNullOrBlank() || originalTitle == title) title else "$title ($originalTitle)"
        return buildList {
            add(heading)
            if (genreNames.isNotEmpty()) add("장르: ${genreNames.joinToString(", ")}")
            if (moodTags.isNotEmpty()) add("분위기: ${moodTags.joinToString(", ")}")
            if (!moodDesc.isNullOrBlank()) add("분위기 설명: $moodDesc")
            if (overview.isNotBlank()) add("줄거리: $overview")
            if (moodTags.isNotEmpty()) add("분위기: ${moodTags.joinToString(", ")}")
        }.joinToString(SEPARATOR)
    }

    private const val SEPARATOR = " · "
}
