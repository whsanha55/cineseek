package com.whsanha55.cineseek.search.vo

/** dense(1024, L2 정규화) + sparse(토큰 id → 가중치) */
data class Embedding(val dense: List<Float>, val sparse: Sparse) {
    data class Sparse(val indices: List<Long>, val values: List<Float>)
}
