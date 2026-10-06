package com.whsanha55.cineseek.collect.service

import java.time.LocalDate
import java.time.Month
import java.time.Year
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 한국 발굴(KR_DISCOVER) 수집 구간 — TMDB discover의 날짜 범위(primary_release_date)를
 * [from, to] 폐구간으로 표현한다. criterion은 검색 기준("origin"=with_origin_country,
 * "lang"=with_original_language)이며, payloadKey 인코딩으로 큐에 식별된다.
 * 순수 값 객체라 타임존·시계를 모른다 — 오늘 날짜는 호출 쪽에서 설정 zone으로 구해 넘긴다
 */
data class KrWindow(val from: LocalDate, val to: LocalDate, val criterion: String) {

    /** 큐 식별자(payload_key) — 예: kr:origin:2023-01-01..2023-12-31 */
    fun payloadKey(): String = "kr:$criterion:$from..$to"

    /**
     * 구간을 월 기준 절반으로 분할한다 — 페이지 상한(pageCap)에 도달해 구간을 쪼개 재수집할 때 쓴다.
     * 전체 월 수가 [minWindowMonths] 이하면 더 나눌 수 없어 null
     */
    fun split(minWindowMonths: Int): Pair<KrWindow, KrWindow>? {
        val fromYm = YearMonth.from(from)
        val months = ChronoUnit.MONTHS.between(fromYm, YearMonth.from(to)) + 1
        if (months <= minWindowMonths) {
            return null
        }
        val mid = fromYm.plusMonths(months / 2).atDay(1)
        return KrWindow(from, mid.minusDays(1), criterion) to KrWindow(mid, to, criterion)
    }

    companion object {
        private val PAYLOAD_KEY_PATTERN =
            Regex("kr:(origin|lang):(\\d{4}-\\d{2}-\\d{2})\\.\\.(\\d{4}-\\d{2}-\\d{2})")

        /**
         * 연도 구간 생성 — [today] 기준 [yearsBack]년 전 연도부터 현재 연도까지 1년 단위(오래된 해가 먼저).
         * 예: today=2026-06-15, yearsBack=30 → 1996년~2026년의 31개 구간
         */
        fun yearly(today: LocalDate, yearsBack: Int, criterion: String): List<KrWindow> {
            val lastYear = today.year
            return (lastYear - yearsBack..lastYear).map { year ->
                KrWindow(
                    from = Year.of(year).atDay(1),
                    to = Year.of(year).atMonth(Month.DECEMBER).atEndOfMonth(),
                    criterion = criterion,
                )
            }
        }

        /** payloadKey 역파싱 — 형식이 아니거나 from이 to보다 늦으면 null */
        fun fromPayloadKey(payloadKey: String): KrWindow? {
            val match = PAYLOAD_KEY_PATTERN.matchEntire(payloadKey) ?: return null
            val window = KrWindow(
                from = LocalDate.parse(match.groupValues[2]),
                to = LocalDate.parse(match.groupValues[3]),
                criterion = match.groupValues[1],
            )
            return window.takeIf { it.from <= it.to }
        }
    }
}
