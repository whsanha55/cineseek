package com.whsanha55.cineseek.movie.repository

import com.whsanha55.cineseek.movie.entity.MovieEntity
import jakarta.persistence.Tuple
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface MovieRepository :
    JpaRepository<MovieEntity, Long>,
    JpaSpecificationExecutor<MovieEntity> {

    fun findByTmdbId(tmdbId: Long): MovieEntity?

    fun findAllByTmdbIdIn(tmdbIds: Collection<Long>): List<MovieEntity>

    /** 색인 시 genres 즉시 로딩 (트랜잭션 밖 payload 조립용) */
    @EntityGraph(attributePaths = ["genres"])
    override fun findAll(): List<MovieEntity>

    /** 카드 조립용 — id 목록으로 genres까지 한 번에 */
    @EntityGraph(attributePaths = ["genres"])
    override fun findAllById(ids: Iterable<Long>): List<MovieEntity>

    /** bootstrap 백필용 — 엔티티 전체 대신 tmdbId만 경량 프로젝션으로 */
    @Query(
        """
        SELECT m.tmdbId
        FROM MovieEntity m
        """,
    )
    fun findAllTmdbIds(): List<Long>

    /**
     * 분위기 태그가 필요한 영화 — overview 있고 현재 태그 버전과 다르며, 현재 버전에서 영구 실패하지 않은 것
     * (IS NULL과 비교 혼합이라 파생 쿼리 불가)
     */
    @Query(
        """
        SELECT m
        FROM MovieEntity m
        WHERE m.overview IS NOT NULL
            AND (
                m.moodModel IS NULL
                OR m.moodModel <> :version
            )
            AND (
                m.moodFailedModel IS NULL
                OR m.moodFailedModel <> :version
            )
        """,
    )
    fun findRequiringMoodTag(version: String): List<MovieEntity>

    /** 일일 증분용 — 줄거리가 비어 있는 영화의 tmdbId를 오래된 순으로 [pageable] 크기만큼 */
    @Query(
        """
        SELECT m.tmdbId
        FROM MovieEntity m
        WHERE m.overview IS NULL
        ORDER BY m.movieId ASC
        """,
    )
    fun findTmdbIdsByOverviewIsNull(pageable: Pageable): List<Long>

    fun countByOverviewIsNull(): Long

    /** 일일 증분용 — 개봉일이 [from] 이후(개봉 예정 포함)인 영화의 tmdbId. 개봉일 미상은 제외 */
    @Query(
        """
        SELECT m.tmdbId
        FROM MovieEntity m
        WHERE m.releaseDate >= :from
        ORDER BY m.movieId ASC
        """,
    )
    fun findTmdbIdsReleasedSince(from: LocalDate): List<Long>

    /** 한국 영화(제작국가 KR)의 연도별 수 — 수집 통계용. 연도 내림차순 (yr=연도, total=수) */
    @Query(
        """
        SELECT m.releaseYear AS yr, COUNT(m) AS total
        FROM MovieEntity m
        WHERE m.originCountry = 'KR'
            AND m.releaseYear IS NOT NULL
        GROUP BY m.releaseYear
        ORDER BY m.releaseYear DESC
        """,
    )
    fun countKrMoviesByYear(): List<Tuple>
}
