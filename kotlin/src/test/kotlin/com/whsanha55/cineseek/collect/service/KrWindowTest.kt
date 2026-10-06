package com.whsanha55.cineseek.collect.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** KrWindow — 연도 구간 생성(경계), 반분할, payloadKey 인코딩/파싱 왕복 검증 (순수 단위) */
class KrWindowTest {

    @Test
    fun `연도 구간 생성 — yearsBack년 전 연도부터 현재 연도까지 1년 단위`() {
        // given: 오늘 2026-06-15, 30년 전부터
        val windows = KrWindow.yearly(LocalDate.of(2026, 6, 15), yearsBack = 30, criterion = "origin")

        // then: 1996~2026 — 31개 구간, 각 연도 1월 1일~12월 31일
        assertThat(windows).hasSize(31)
        assertThat(windows.first().from).isEqualTo(LocalDate.of(1996, 1, 1))
        assertThat(windows.first().to).isEqualTo(LocalDate.of(1996, 12, 31))
        assertThat(windows.last().from).isEqualTo(LocalDate.of(2026, 1, 1))
        assertThat(windows.last().to).isEqualTo(LocalDate.of(2026, 12, 31))
    }

    @Test
    fun `연도 구간 경계 — 윤년 2월 29일을 포함하고 상하반기로 나뉜다`() {
        val leap = KrWindow.yearly(LocalDate.of(2024, 3, 1), yearsBack = 0, criterion = "lang").single()

        assertThat(leap.from).isEqualTo(LocalDate.of(2024, 1, 1))
        assertThat(leap.to).isEqualTo(LocalDate.of(2024, 12, 31))
        assertThat(LocalDate.of(2024, 2, 29) in leap.from..leap.to).isTrue()

        val (firstHalf, secondHalf) = leap.split(minWindowMonths = 1)!!
        assertThat(firstHalf.to).isEqualTo(LocalDate.of(2024, 6, 30))
        assertThat(secondHalf.from).isEqualTo(LocalDate.of(2024, 7, 1))
    }

    @Test
    fun `split — 1년 구간을 상반기와 하반기로 절반 나눈다`() {
        val year = KrWindow(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31), "origin")

        val (first, second) = year.split(minWindowMonths = 1)!!

        assertThat(first).isEqualTo(KrWindow(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 6, 30), "origin"))
        assertThat(second).isEqualTo(KrWindow(LocalDate.of(2023, 7, 1), LocalDate.of(2023, 12, 31), "origin"))
    }

    @Test
    fun `split — 홀수 개월 구간은 앞 절반이 짧게 나뉜다`() {
        val quarter = KrWindow(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 3, 31), "origin")

        val (first, second) = quarter.split(minWindowMonths = 1)!!

        assertThat(first).isEqualTo(KrWindow(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 31), "origin"))
        assertThat(second).isEqualTo(KrWindow(LocalDate.of(2023, 2, 1), LocalDate.of(2023, 3, 31), "origin"))
    }

    @Test
    fun `split — minWindowMonths 이하 구간은 더 나누지 않고 null`() {
        val oneMonth = KrWindow(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 31), "origin")
        val twoMonths = KrWindow(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 2, 28), "origin")

        assertThat(oneMonth.split(minWindowMonths = 1)).isNull()
        assertThat(twoMonths.split(minWindowMonths = 2)).isNull()
        assertThat(twoMonths.split(minWindowMonths = 1)).isNotNull() // 1개월 두 조각으로는 나뉜다
    }

    @Test
    fun `payloadKey 인코딩과 파싱 왕복`() {
        val year = KrWindow(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31), "origin")
        val half = KrWindow(LocalDate.of(2020, 7, 1), LocalDate.of(2020, 12, 31), "lang")

        assertThat(year.payloadKey()).isEqualTo("kr:origin:2023-01-01..2023-12-31")
        assertThat(KrWindow.fromPayloadKey(year.payloadKey())).isEqualTo(year)
        assertThat(KrWindow.fromPayloadKey(half.payloadKey())).isEqualTo(half)
    }

    @Test
    fun `fromPayloadKey — 형식이 아니거나 역전된 구간은 null`() {
        assertThat(KrWindow.fromPayloadKey("global:2024")).isNull()
        assertThat(KrWindow.fromPayloadKey("kr:region:2023-01-01..2023-12-31")).isNull()
        assertThat(KrWindow.fromPayloadKey("kr:origin:2023-01-01..2023-12-31:extra")).isNull()
        assertThat(KrWindow.fromPayloadKey("kr:origin:2023-12-31..2023-01-01")).isNull()
    }
}
