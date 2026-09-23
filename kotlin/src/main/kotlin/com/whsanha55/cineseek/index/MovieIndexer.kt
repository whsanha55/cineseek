package com.whsanha55.cineseek.index

import com.whsanha55.cineseek.config.QdrantProperties
import com.whsanha55.cineseek.embedding.EmbeddingClient
import io.qdrant.client.PointIdFactory
import io.qdrant.client.QdrantClient
import io.qdrant.client.ValueFactory
import io.qdrant.client.VectorFactory
import io.qdrant.client.VectorsFactory
import io.qdrant.client.grpc.Collections
import io.qdrant.client.grpc.JsonWithInt
import io.qdrant.client.grpc.Points
import org.springframework.stereotype.Component

/** Qdrant payload — 필터 대상 사본 (pipeline.build_payload 이식) */
data class MoviePayload(
	val title: String?,
	val releaseYear: Int?,
	val rating: Double?,
	val genres: List<String>,
	val directors: List<String>, // 상위 3명
	val cast: List<String>, // 상위 5명
)

/** 색인 단위 입력 — PG(SoT)에서 만든다 */
data class IndexedMovie(
	val movieId: Long,
	val overview: String,
	val payload: MoviePayload,
)

/**
 * Qdrant 파생 인덱스 관리 — 컬렉션 재생성(dense 1024 cosine + sparse) + 배치 upsert.
 * PG=SoT → 재색인마다 컬렉션을 지우고 다시 만든다 (pipeline.ensure_collection 이식)
 */
@Component
class MovieIndexer(
	private val qdrantClient: QdrantClient,
	private val qdrantProperties: QdrantProperties,
	private val embeddingClient: EmbeddingClient,
) {

	fun reindex(movies: List<IndexedMovie>) {
		ensureCollection()

		val embeddings = embeddingClient.embed(movies.map { it.overview })
		val points = movies.mapIndexed { i, movie ->
			Points.PointStruct.newBuilder()
				.setId(PointIdFactory.id(movie.movieId)) // movie_id(PG PK)를 포인트 id로
				.setVectors(
					VectorsFactory.namedVectors(
						mapOf(
							"dense" to VectorFactory.vector(embeddings[i].dense),
							"sparse" to VectorFactory.vector(
								embeddings[i].sparse.values,
								embeddings[i].sparse.indices.map(Long::toInt),
							),
						),
					),
				)
				.putAllPayload(movie.payload.toGrpc())
				.build()
		}
		points.chunked(UPSERT_BATCH).forEach { batch ->
			qdrantClient.upsertAsync(qdrantProperties.collection, batch).get()
		}
	}

	/** PG=SoT → 재색인 시 재생성 */
	private fun ensureCollection() {
		if (qdrantClient.collectionExistsAsync(qdrantProperties.collection).get()) {
			qdrantClient.deleteCollectionAsync(qdrantProperties.collection).get()
		}
		qdrantClient.createCollectionAsync(
			Collections.CreateCollection.newBuilder()
				.setCollectionName(qdrantProperties.collection)
				.setVectorsConfig(
					Collections.VectorsConfig.newBuilder().setParamsMap(
						Collections.VectorParamsMap.newBuilder().putMap(
							"dense",
							Collections.VectorParams.newBuilder().setSize(DENSE_DIM).setDistance(Collections.Distance.Cosine).build(),
						),
					),
				)
				.setSparseVectorsConfig(
					Collections.SparseVectorConfig.newBuilder()
						.putMap("sparse", Collections.SparseVectorParams.newBuilder().build()),
				)
				.build(),
		).get()
		println("컬렉션 재생성(dense+sparse): ${qdrantProperties.collection}")
	}

	private fun MoviePayload.toGrpc(): Map<String, JsonWithInt.Value> = buildMap {
		title?.let { put("title", ValueFactory.value(it)) }
		releaseYear?.let { put("release_year", ValueFactory.value(it.toLong())) }
		rating?.let { put("rating", ValueFactory.value(it)) }
		put("genres", ValueFactory.list(genres.map { ValueFactory.value(it) }))
		put("directors", ValueFactory.list(directors.map { ValueFactory.value(it) }))
		put("cast", ValueFactory.list(cast.map { ValueFactory.value(it) }))
	}

	companion object {
		private const val DENSE_DIM = 1024L // bge-m3
		private const val UPSERT_BATCH = 256 // Qdrant 단일 요청 한계 방지 (python과 동일)
	}
}
