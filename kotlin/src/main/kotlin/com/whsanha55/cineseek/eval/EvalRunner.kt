package com.whsanha55.cineseek.eval

import com.whsanha55.cineseek.embedding.EmbeddingClient
import com.whsanha55.cineseek.search.SearchService
import kotlin.system.exitProcess
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 고정 쿼리 10종 top-5 출력 — eval.py 이식. 회귀감지 기준점.
 * 실행: docker compose run --rm api --cineseek.job=eval (출력 후 종료)
 */
@Component
@ConditionalOnProperty(prefix = "cineseek", name = ["job"], havingValue = "eval")
class EvalRunner(
	private val searchService: SearchService,
	private val embeddingClient: EmbeddingClient,
) : ApplicationRunner {

	override fun run(args: ApplicationArguments) {
		val embeddings = embeddingClient.embed(QUERIES) // python처럼 한 번의 배치로
		QUERIES.forEachIndexed { i, query ->
			println("\n▶ $query")
			searchService.search(embeddings[i], null, null, 5).forEachIndexed { j, r ->
				println("  ${j + 1}. ${r.title} (${r.releaseYear})  score=${"%.3f".format(r.score)}  genres=${r.genres}")
			}
		}
		exitProcess(0) // 배치 성격 — 출력 후 종료
	}

	companion object {
		private val QUERIES = listOf(
			"감옥에서 탈출하는 이야기",
			"가족을 지키려는 아버지의 사투",
			"피의 복수극",
			"우주 정거장에서 벌어지는 재난",
			"사랑과 배신의 로맨스",
			"연쇄살인범을 쫓는 형사",
			"전쟁 속 전우애와 희생",
			"밀실에서 일어난 미스터리",
			"초능력 히어로들의 세계를 지키는 전투",
			"시간을 거슬러 가는 모험",
		)
	}
}
