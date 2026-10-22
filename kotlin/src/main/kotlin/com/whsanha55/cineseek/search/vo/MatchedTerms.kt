package com.whsanha55.cineseek.search.vo

/**
 * 검색 근거 — 쿼리 sparse와 문서(색인 텍스트) sparse의 공통 토큰을 쿼리 원문의 단어로 되돌린다.
 * bge-m3(XLM-R sentencepiece)는 한국어를 음절 조각으로 쪼개므로(감옥 → `▁감` + `옥`) 토큰을 그대로 보여주지 않고,
 * 공통 토큰이 덮는 쿼리 글자를 이어 붙인 구간을 기여도(쿼리 가중치 × 문서 가중치) 합 순으로 고른다.
 * 조사·범용어(STOPWORDS)는 빼고, 한 글자 구간은 그 자체가 한 단어일 때만 남긴다
 */
object MatchedTerms {

    /** 공통 토큰이 없거나(dense만으로 걸린 결과) 쿼리 토큰 문자열이 없으면(이전 embed 서버) 빈 목록 */
    fun extract(query: String, querySparse: Embedding.Sparse, docWeights: Map<Long, Float>): List<String> {
        val tokens = querySparse.tokens ?: return emptyList()
        val owners = List(query.length) { mutableSetOf<Int>() }
        val contributions = mutableMapOf<Int, Double>()
        querySparse.indices.forEachIndexed { i, id ->
            val text = tokens[i].removePrefix(WORD_START)
            val docWeight = docWeights[id]
            if (docWeight == null || text.isBlank() || text.lowercase() in STOPWORDS) return@forEachIndexed
            contributions[i] = querySparse.values[i].toDouble() * docWeight
            val wordStartOnly = if (tokens[i].startsWith(WORD_START)) "(?<!\\S)" else ""
            Regex(wordStartOnly + Regex.escape(text)).findAll(query).forEach { match ->
                match.range.forEach { owners[it] += i }
            }
        }

        val scores = mutableMapOf<String, Double>()
        WORD.findAll(query).forEach { word ->
            runs(word.range, owners).filter { it.count() >= 2 || it == word.range }.forEach { run ->
                val score = run.flatMap { owners[it] }.toSet().sumOf { contributions.getValue(it) }
                scores.merge(query.substring(run), score, ::maxOf)
            }
        }
        return scores.entries.sortedByDescending { it.value }.take(LIMIT).map { it.key }
    }

    /** 단어 안에서 공통 토큰이 덮은 연속 구간 */
    private fun runs(word: IntRange, owners: List<Set<Int>>): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var start = -1
        for (p in word) {
            if (owners[p].isEmpty()) {
                if (start >= 0) runs += start until p
                start = -1
            } else if (start < 0) {
                start = p
            }
        }
        if (start >= 0) runs += start..word.last
        return runs
    }

    private const val LIMIT = 3
    private const val WORD_START = "▁"
    private val WORD = Regex("\\S+")

    /** 근거로 보여줘도 의미 없는 조사·어미 조각과 범용어 */
    private val STOPWORDS = setOf(
        "은", "는", "이", "가", "을", "를", "의", "에", "에서", "에게", "와", "과", "로", "으로", "도", "만",
        "한", "하는", "하고", "지는", "들", "들의", "영화", "이야기",
        "a", "an", "the", "to", "of", "in", "on", "and", "his", "her", "is", "for", "with", "at", "by", "from",
        "movie", "film", "story",
    )
}
