package com.whsanha55.cineseek.collect.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * cineseek.collect — 수집 코어 설정. 초기 집중 수집(BOOTSTRAP) 틱과 일일 증분(DAILY) 배치가 함께 쓴다.
 * 틱마다 큐에서 batchSize만큼 클레임해 maxRuntime 안에 처리하고, 못 끝낸 작업은 체크포인트를 남기고 다음 틱에 이어서 한다
 */
@ConfigurationProperties("cineseek.collect")
data class CollectProperties(
    val enabled: Boolean = false, // 수집 스케줄러 활성 — 기본 비활성
    val interval: Duration = DEFAULT_INTERVAL, // 수집 틱 주기 (초기 집중 수집)
    val batchSize: Int = 100, // 틱당 최대 작업 수
    val maxRuntime: Duration = DEFAULT_MAX_RUNTIME, // 한 틱 최대 실행 시간 — 도달하면 작업을 놓고 다음 틱에 이어서
    val concurrency: Int = 4, // 상세 수집 동시성
    val leaseTimeout: Duration = DEFAULT_LEASE_TIMEOUT, // 이보다 오래 잡힌 RUNNING lease는 죽은 워커로 보고 회수
    val retryBase: Duration = DEFAULT_RETRY_BASE, // 재시도 백오프 기본 — base * 2^attempts + 지터
    val retryMaxAttempts: Int = 8, // 재시도 예약 횟수가 이 값 이상이면 FAILED(영구)로 확정
    val kr: Kr = Kr(),
    val seedPersonIds: List<Long> = DEFAULT_SEED_PERSON_IDS, // 시드 인물 — 초기 검증용 한국 배우 10명
    val filmoTtlDays: Int = 30, // 필모그래피 재확인 주기(일)
    val dailyCron: String = "0 0 4 * * *", // 일일 증분 배치 시각
    val zone: String = "Asia/Seoul", // 구간 계산 기준 타임존 — 시각 자체는 Instant로 다룬다
) {
    /** 한국 발굴(KR_DISCOVER) 구간 규칙 */
    data class Kr(
        val yearsBack: Int = 30, // 오늘 기준 몇 년 전 연도까지 수집할지
        val pageCap: Int = 400, // 구간당 discover 페이지 상한 — 도달하면 구간을 반분할해 재수집
        val minWindowMonths: Int = 1, // 이 월 수 이하 구간은 더 분할하지 않는다
    )

    companion object {
        private val DEFAULT_INTERVAL: Duration = Duration.ofMinutes(30)
        private val DEFAULT_MAX_RUNTIME: Duration = Duration.ofMinutes(25)
        private val DEFAULT_LEASE_TIMEOUT: Duration = Duration.ofMinutes(30) // max-runtime보다 길게
        private val DEFAULT_RETRY_BASE: Duration = Duration.ofSeconds(60)

        /** TMDB person id — 수집 보고(collect-report)의 seed 확보율 검증 대상 */
        private val DEFAULT_SEED_PERSON_IDS: List<Long> = listOf(
            20738L, // 송강호
            20737L, // 전도연
            64880L, // 최민식
            25002L, // 이병헌
            1024395L, // 마동석
            587634L, // 박보검
            1537768L, // 김태리
            73249L, // 이정재
            75913L, // 하정우
            75912L, // 김윤석
        )
    }
}
