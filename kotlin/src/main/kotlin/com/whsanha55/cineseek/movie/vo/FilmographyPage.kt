package com.whsanha55.cineseek.movie.vo

import com.whsanha55.cineseek.movie.enums.PersonFilmoStateEnum
import java.time.Instant
import java.time.LocalDate

/**
 * 필모그래피 사람 요약 — person 마스터가 없으면 movie_cast의 이름으로 대체한 값
 * (profilePath·slug·filmoState·상세는 기본값). totalWorks는 제외 조건과 무관한 전체 출연 작품 수다.
 * 출생지·소개는 언어별로 따로 내려 화면이 언어를 고를 수 있게 한다
 */
data class FilmographyPerson(
    val personId: Long,
    val name: String,
    val profilePath: String? = null,
    val slug: String? = null,
    val filmoState: PersonFilmoStateEnum = PersonFilmoStateEnum.NONE,
    val filmoCheckedAt: Instant? = null,
    val totalWorks: Long,
    val birthday: LocalDate? = null,
    val deathday: LocalDate? = null,
    val placeOfBirthKo: String? = null,
    val placeOfBirthEn: String? = null,
    val biographyKo: String? = null,
    val biographyKoTranslated: Boolean = false,
    val biographyEn: String? = null,
    val imdbId: String? = null,
    val instagramId: String? = null,
    val homepage: String? = null,
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
