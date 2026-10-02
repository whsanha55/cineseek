package com.whsanha55.cineseek.search.vo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** EmbeddingText — 조립 형식을 고정한다 (형식 변경 = 전체 재임베딩 유도) */
class EmbeddingTextTest {

    @Test
    fun `전체 필드가 있으면 정해진 형식 문자열로 조립한다`() {
        val text = EmbeddingText.assemble(
            title = "인셉션",
            originalTitle = "Inception",
            genreNames = listOf("액션", "SF"),
            moodTags = listOf("긴장감 있는", "몽환적인"),
            moodDesc = "꿈속을 누비는 두뇌 게임",
            overview = "생각을 훔치는 특수요원의 마지막 작전",
        )

        assertThat(text).isEqualTo(
            "인셉션 (Inception) · 장르: 액션, SF · 분위기: 긴장감 있는, 몽환적인 · " +
                "분위기 설명: 꿈속을 누비는 두뇌 게임 · 줄거리: 생각을 훔치는 특수요원의 마지막 작전",
        )
    }

    @Test
    fun `원제가 없거나 blank이거나 제목과 같으면 원제를 생략한다`() {
        val expected = "인셉션 · 줄거리: 줄거리"

        assertThat(EmbeddingText.assemble("인셉션", null, emptyList(), emptyList(), null, "줄거리")).isEqualTo(expected)
        assertThat(EmbeddingText.assemble("인셉션", " ", emptyList(), emptyList(), null, "줄거리")).isEqualTo(expected)
        assertThat(EmbeddingText.assemble("인셉션", "인셉션", emptyList(), emptyList(), null, "줄거리")).isEqualTo(expected)
    }

    @Test
    fun `분위기 태그와 설명이 없으면 분위기 섹션을 통째로 생략한다`() {
        val text = EmbeddingText.assemble(
            title = "인셉션",
            originalTitle = "Inception",
            genreNames = listOf("액션"),
            moodTags = emptyList(),
            moodDesc = null,
            overview = "줄거리",
        )

        assertThat(text).isEqualTo("인셉션 (Inception) · 장르: 액션 · 줄거리: 줄거리")
    }

    @Test
    fun `장르가 없으면 장르 섹션을 생략한다`() {
        val text = EmbeddingText.assemble(
            title = "인셉션",
            originalTitle = "Inception",
            genreNames = emptyList(),
            moodTags = listOf("긴장감 있는"),
            moodDesc = "설명",
            overview = "줄거리",
        )

        assertThat(text).isEqualTo("인셉션 (Inception) · 분위기: 긴장감 있는 · 분위기 설명: 설명 · 줄거리: 줄거리")
    }

    @Test
    fun `제목과 줄거리만 있으면 최소 형식으로 조립한다`() {
        val text = EmbeddingText.assemble("인셉션", null, emptyList(), emptyList(), null, "줄거리")

        assertThat(text).isEqualTo("인셉션 · 줄거리: 줄거리")
    }

    @Test
    fun `분위기 태그만 있고 설명이 없으면 태그 섹션만 남긴다`() {
        val text = EmbeddingText.assemble("인셉션", null, emptyList(), listOf("힐링"), " ", "줄거리")

        assertThat(text).isEqualTo("인셉션 · 분위기: 힐링 · 줄거리: 줄거리")
    }
}
