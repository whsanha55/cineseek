package com.whsanha55.cineseek.search.vo

import com.whsanha55.cineseek.search.enums.EvalQueryTypeEnum
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class EvalQueryTest {
    private val mapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()

    @Test
    fun `평가 쿼리 파일을 읽으면 18개 쿼리가 유형별로 로딩된다`() {
        // given
        val resource = "eval/queries.json"

        // when
        val queries = EvalQuery.load(mapper, resource)

        // then
        assertThat(queries).hasSize(18)
        val countsByType = queries.groupingBy { it.type }.eachCount()
        assertThat(countsByType).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                EvalQueryTypeEnum.PLOT to 10,
                EvalQueryTypeEnum.MOOD to 5,
                EvalQueryTypeEnum.EN to 3,
            ),
        )
    }

    @Test
    fun `grade가 범위를 벗어나면 예외가 발생한다`() {
        // given
        val resource = "eval/invalid-grade.json"

        // when
        val exception = assertThrows<IllegalArgumentException> { EvalQuery.load(mapper, resource) }

        // then
        assertThat(exception.message).contains("grade").contains("감옥에서 탈출하는 이야기")
    }

    @Test
    fun `쿼리가 중복되면 예외가 발생한다`() {
        // given
        val resource = "eval/duplicate-query.json"

        // when
        val exception = assertThrows<IllegalArgumentException> { EvalQuery.load(mapper, resource) }

        // then
        assertThat(exception.message).contains("쿼리가 중복").contains("잔잔한 영화")
    }

    @Test
    fun `한 쿼리 안에서 tmdbId가 중복되면 예외가 발생한다`() {
        // given
        val resource = "eval/duplicate-tmdb-id.json"

        // when
        val exception = assertThrows<IllegalArgumentException> { EvalQuery.load(mapper, resource) }

        // then
        assertThat(exception.message).contains("tmdbId가 중복").contains("278")
    }
}
