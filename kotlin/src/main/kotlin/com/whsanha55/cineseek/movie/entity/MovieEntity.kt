package com.whsanha55.cineseek.movie.entity

import com.whsanha55.cineseek.global.base.BaseEntity
import com.whsanha55.cineseek.movie.vo.TmdbMovie
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * 영화 메타 + 줄거리 — 진실의 원천(SoT). overview가 임베딩 입력이다 (NULL이면 색인 스킵)
 */
@Entity
@Table(name = "movie")
class MovieEntity(
    @Column(nullable = false, unique = true)
    val tmdbId: Long,
    title: String,
    val originalTitle: String? = null,
    overview: String? = null,
    releaseDate: LocalDate? = null,
    releaseYear: Int? = null,
    runtime: Int? = null,
    voteAverage: BigDecimal? = null,
    voteCount: Int? = null,
    posterPath: String? = null,
    val backdropPath: String? = null,
    val originalLanguage: String? = null,
    overviewUpdatedAt: Instant? = null,
    moodTags: String? = null,
    moodDesc: String? = null,
    moodModel: String? = null,
    moodTaggedAt: Instant? = null,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var movieId: Long? = null
        protected set

    @Column(nullable = false)
    var title: String = title
        protected set

    var overview: String? = overview
        protected set

    var releaseDate: LocalDate? = releaseDate
        protected set

    var releaseYear: Int? = releaseYear
        protected set

    var runtime: Int? = runtime
        protected set

    var voteAverage: BigDecimal? = voteAverage
        protected set

    var voteCount: Int? = voteCount
        protected set

    var posterPath: String? = posterPath
        protected set

    var overviewUpdatedAt: Instant? = overviewUpdatedAt
        protected set

    var moodTags: String? = moodTags
        protected set

    var moodDesc: String? = moodDesc
        protected set

    var moodModel: String? = moodModel
        protected set

    var moodTaggedAt: Instant? = moodTaggedAt
        protected set

    @ManyToMany
    @JoinTable(
        name = "movie_genre",
        joinColumns = [JoinColumn(name = "movie_id")],
        inverseJoinColumns = [JoinColumn(name = "genre_id")],
    )
    val genres: MutableSet<GenreEntity> = mutableSetOf()

    /** TMDB 재수집 값으로 갱신 — python DO UPDATE SET 절과 같은 필드만 바꾼다 */
    fun updateFrom(tmdb: TmdbMovie, now: Instant) {
        title = tmdb.title
        overview = tmdb.overview
        releaseDate = tmdb.releaseLocalDate()
        releaseYear = tmdb.releaseYear()
        runtime = tmdb.runtime
        voteAverage = tmdb.voteAverage
        voteCount = tmdb.voteCount
        posterPath = tmdb.posterPath
        overviewUpdatedAt = now
    }

    /** LLM 분위기 태그 저장 — updateFrom(TMDB 재수집)이 건드리지 않는 필드다 */
    fun applyMood(tags: String, desc: String, model: String, now: Instant) {
        moodTags = tags
        moodDesc = desc
        moodModel = model
        moodTaggedAt = now
    }

    fun replaceGenres(newGenres: Collection<GenreEntity>) {
        genres.clear()
        genres += newGenres
    }
}
