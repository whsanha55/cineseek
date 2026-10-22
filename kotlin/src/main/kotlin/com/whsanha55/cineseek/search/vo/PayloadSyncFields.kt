package com.whsanha55.cineseek.search.vo

import java.time.LocalDate

/** payload 동기화(PayloadSyncJob)로 색인된 포인트에 덮어쓰는 PG 파생 필드 — 임베딩 없이 바꿀 수 있는 것만 담는다 */
data class PayloadSyncFields(val moodTags: List<String>, val releaseDate: LocalDate?)
