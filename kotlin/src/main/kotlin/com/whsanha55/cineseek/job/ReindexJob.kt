package com.whsanha55.cineseek.job

import com.whsanha55.cineseek.config.TmdbProperties
import com.whsanha55.cineseek.index.IndexedMovie
import com.whsanha55.cineseek.index.MovieIndexer
import com.whsanha55.cineseek.index.MoviePayload
import com.whsanha55.cineseek.movie.repository.MovieCastRepository
import com.whsanha55.cineseek.movie.repository.MovieDirectorRepository
import com.whsanha55.cineseek.movie.repository.MovieRepository
import com.whsanha55.cineseek.movie.service.MovieUpsertService
import com.whsanha55.cineseek.tmdb.TmdbClient
import com.whsanha55.cineseek.tmdb.TmdbMovie
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.system.exitProcess
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 재색인 배치 — 수집(TMDB) → PG(SoT) → 임베딩 → Qdrant (pipeline.main 이식).
 * 실행: docker compose run --rm api --cineseek.job=reindex
 */
@Component
@ConditionalOnProperty(prefix = "cineseek", name = ["job"], havingValue = "reindex")
class ReindexJob(
	private val tmdbClient: TmdbClient,
	private val tmdbProperties: TmdbProperties,
	private val upsertService: MovieUpsertService,
	private val movieRepository: MovieRepository,
	private val movieDirectorRepository: MovieDirectorRepository,
	private val movieCastRepository: MovieCastRepository,
	private val indexer: MovieIndexer,
) : ApplicationRunner {

	override fun run(args: ApplicationArguments) {
		val ids = tmdbClient.fetchTmdbIds(tmdbProperties.pages)
		println("TMDB 후보: ${ids.size}개 id (discover ${tmdbProperties.pages}페이지)")

		print("상세 수집(가상 스레드)...")
		val movies = fetchDetailsParallel(ids)
		println(" 완료: ${movies.size}건 (overview 있는 영화)")

		print("PG 적재...")
		val stored = movies.mapNotNull { upsertSafe(it) }
		println(" 완료: ${stored.size}건")

		print("임베딩 + Qdrant 색인...")
		val indexed = buildIndexedMovies()
		indexer.reindex(indexed)
		println(" 완료: ${indexed.size}건")

		println("재색인 완료: PG movie=${stored.size}, 색인 대상=${indexed.size}")
		exitProcess(0) // 배치 성격 — 출력 후 종료
	}

	/** 가상 스레드로 detail + credits 병렬 수집 — 단건 실패는 건너뛴다 (python fetch_detail_safe) */
	private fun fetchDetailsParallel(ids: List<Long>): List<TmdbMovie> =
		Executors.newVirtualThreadPerTaskExecutor().use { executor ->
			executor.invokeAll(ids.map { id -> Callable { runCatching { tmdbClient.fetchDetail(id) } } })
		}.mapNotNull { future ->
			future.get()
				.onFailure { println("  수집 스킵: ${it.message}") }
				.getOrNull()
		}

	/** 한 건이 실패해도 나머지는 계속 (영화 한 건당 트랜잭션) */
	private fun upsertSafe(m: TmdbMovie): Long? =
		runCatching { upsertService.upsert(m) }
			.onFailure { println("  upsert 스킵 tmdbId=${m.tmdbId}: ${it.message}") }
			.getOrNull()

	/** PG에서 payload를 만들어 색인 입력 조립 — SoT 기준 */
	private fun buildIndexedMovies(): List<IndexedMovie> =
		movieRepository.findAll().mapNotNull { movie ->
			val overview = movie.overview ?: return@mapNotNull null // 임베딩 불가 → 스킵
			val directors = movieDirectorRepository.findAllByMovieId(movie.movieId)
			val cast = movieCastRepository.findAllByMovieId(movie.movieId)
			IndexedMovie(
				movieId = movie.movieId,
				overview = overview,
				payload = MoviePayload(
					title = movie.title,
					releaseYear = movie.releaseYear,
					rating = movie.voteAverage?.toDouble(),
					genres = movie.genres.map { it.name },
					directors = directors.map { it.name }.take(3),
					cast = cast.sortedBy { it.castOrder }.map { it.name }.take(5),
				),
			)
		}
}
