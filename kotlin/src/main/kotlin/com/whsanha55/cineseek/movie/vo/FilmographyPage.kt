package com.whsanha55.cineseek.movie.vo

import com.whsanha55.cineseek.movie.enums.PersonFilmoStateEnum
import java.time.Instant

/**
 * 필모그래피 사람 요약 — person 마스터가 없으면 movie_cast의 이름으로 대체한 값
 * (profilePath·filmoState는 기본값). totalWorks는 제외 조건과 무관한 전체 출연 작품 수다
 */
data class FilmographyPerson(
    val personId: Long,
    val name: String,
    val profilePath: String? = null,
    val filmoState: PersonFilmoStateEnum = PersonFilmoStateEnum.NONE,
    val filmoCheckedAt: Instant? = null,
    val totalWorks: Long,
)

/** 필모그래피 아이템 — 카드 + 해당 배우의 배역 */
data class FilmographyItem(val card: MovieCard, val character: String?)

/** 필모그래피 페이지 — 저장된 출연작을 즉시 반환하고 갱신은 백그라운드로 요청한다 (collecting 표시) */
data class FilmographyPage(
    val person: FilmographyPerson,
    val items: List<FilmographyItem>,
    val limit: Int,
    val offset: Int,
    val hasNext: Boolean,
    val collecting: Boolean,
)
