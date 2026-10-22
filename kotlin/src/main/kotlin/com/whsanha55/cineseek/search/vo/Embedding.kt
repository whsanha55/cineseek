package com.whsanha55.cineseek.search.vo

/** dense(1024, L2 정규화) + sparse(토큰 id → 가중치) */
data class Embedding(val dense: List<Float>, val sparse: Sparse) {
    /** tokens — indices와 같은 순서의 토큰 문자열(검색 근거 표시용). embed 서버가 주지 않으면 null */
    data class Sparse(val indices: List<Long>, val values: List<Float>, val tokens: List<String>? = null)
}
