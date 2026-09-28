package com.whsanha55.cineseek.external.embedding.dto

/** POST /embed 와이어 계약 DTO — EmbeddingClient 밖으로 내보내지 않는다 */
internal data class EmbedRequestBody(val texts: List<String>)

internal data class EmbedResponseBody(val model: String, val items: List<Item>) {
    data class Item(val dense: List<Float>, val sparse: Sparse)

    data class Sparse(val indices: List<Long>, val values: List<Float>)
}
