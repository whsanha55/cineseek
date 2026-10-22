package com.whsanha55.cineseek.search.enums

/**
 * 평가 쿼리 유형 — 유형별로 지표를 나눠 본다.
 * [separate]인 유형은 전체 평균에 넣지 않고 기준값과 따로 비교한다 (내용 검색 지표의 의미를 지키려고)
 */
enum class EvalQueryTypeEnum(val separate: Boolean = false) {
    /** 줄거리형 */
    PLOT,

    /** 분위기형 */
    MOOD,

    /** 영어 */
    EN,

    /** 제목 검색 — 정답은 그 제목의 영화 하나 (#95) */
    TITLE(separate = true),
}
