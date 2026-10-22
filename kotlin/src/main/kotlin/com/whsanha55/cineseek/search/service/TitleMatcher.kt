package com.whsanha55.cineseek.search.service

import com.whsanha55.cineseek.movie.repository.MovieRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Locale

private val log = KotlinLogging.logger {}

/**
 * 쿼리 → 제목이 일치하는 영화 (#40, #95). 검색이 RRF로 내용 매칭에 맞춰져 있어도 제목 검색은 따로 지키기 위한 신호다.
 *
 * 쿼리의 모든 단어가 제목의 서로 다른 단어의 앞부분과 일치하고, 쿼리 단어 수가 제목 단어 수의 절반을 넘으면 일치로 본다.
 * 제목은 한국어 제목·원제와 각각의 부제(`:`, ` - ` 뒤)를 뗀 본제목을 함께 본다.
 * "레버넌트"→『레버넌트: 죽음에서 돌아온 자』, "남자가 사랑할"→『남자가 사랑할 때』는 일치하고,
 * "좀비"→『오만과 편견 그리고 좀비』처럼 제목의 일부 단어만 겹치는 경우는 일치하지 않는다.
 * 일치가 [MAX_MATCHES]편을 넘으면 일반어 검색으로 보고 신호를 주지 않는다.
 *
 * 제목은 PG에서 읽어 메모리에 두고 [RELOAD_INTERVAL]마다 다시 읽는다. 읽기에 실패하면 이전 목록을 쓴다
 */
@Component
class TitleMatcher(private val movieRepository: MovieRepository, private val clock: Clock) {

    private class Titles(val entries: List<Pair<Long, List<List<String>>>>, val loadedAt: Instant)

    @Volatile
    private var titles: Titles? = null

    /** 제목이 일치하는 영화 id. 없거나 [MAX_MATCHES]편을 넘으면 빈 목록 */
    fun match(query: String): List<Long> {
        val words = words(query)
        if (words.isEmpty()) return emptyList()
        val matched = titles()
            .filter { (_, variants) -> variants.any { covers(words, it) } }
            .map { it.first }
        return if (matched.size > MAX_MATCHES) emptyList() else matched
    }

    private fun titles(): List<Pair<Long, List<List<String>>>> =
        titles?.takeIf { isFresh(it) }?.entries ?: synchronized(this) {
            val current = titles
            if (current != null && isFresh(current)) current.entries else load(current)
        }

    private fun isFresh(titles: Titles) = Duration.between(titles.loadedAt, Instant.now(clock)) < RELOAD_INTERVAL

    private fun load(previous: Titles?): List<Pair<Long, List<List<String>>>> = runCatching {
        movieRepository.findAllTitles().map { movie ->
            val variants = listOfNotNull(movie.title, movie.originalTitle)
                .flatMap { listOf(it, mainTitle(it)) }
                .map { words(it) }
                .filter { it.isNotEmpty() }
                .distinct()
            movie.movieId to variants
        }
    }.onSuccess { titles = Titles(it, Instant.now(clock)) }
        .onFailure { log.warn(it) { "제목 목록을 읽지 못했다 — 이전 목록으로 매칭한다" } }
        .getOrElse { previous?.entries.orEmpty() }

    /** 쿼리 단어마다 서로 다른 제목 단어의 앞부분이어야 하고, 쿼리 단어가 제목 단어의 절반을 넘어야 한다 */
    private fun covers(query: List<String>, title: List<String>): Boolean {
        if (query.size * 2 <= title.size) return false
        val used = BooleanArray(title.size)
        return query.all { word ->
            val index = title.indices.firstOrNull { !used[it] && title[it].startsWith(word) } ?: return@all false
            used[index] = true
            true
        }
    }

    companion object {
        private const val MAX_MATCHES = 20
        private val RELOAD_INTERVAL: Duration = Duration.ofMinutes(10)
        private val WORD = Regex("[\\p{L}\\p{N}\\p{M}_]+")
        private val SUBTITLE_SEPARATORS = listOf(":", " - ")

        private fun words(text: String): List<String> =
            WORD.findAll(text.lowercase(Locale.ROOT)).map { it.value }.toList()

        private fun mainTitle(title: String): String =
            SUBTITLE_SEPARATORS.fold(title) { acc, separator -> acc.substringBefore(separator) }
    }
}
