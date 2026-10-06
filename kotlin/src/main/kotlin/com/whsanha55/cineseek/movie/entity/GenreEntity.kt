package com.whsanha55.cineseek.movie.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/** TMDB 장르 마스터 — genre_id는 TMDB id를 그대로 쓴다 (자동 생성 아님). 한 번 넣으면 바꾸지 않는다 */
@Entity
@Table(name = "genre")
class GenreEntity(
    @Id
    val genreId: Long,

    // ponytail-audit #1: nameKo 제거 — 채우는 경로가 없었다. TMDB ko-KR 응답이라 name이 이미 한글. name_ko 컬럼은 DB에 남겨둠
    @Column(nullable = false)
    val name: String,
)
