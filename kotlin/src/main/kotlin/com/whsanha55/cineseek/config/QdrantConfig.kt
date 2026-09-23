package com.whsanha55.cineseek.config

import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Qdrant gRPC 클라이언트 빈 */
@Configuration
class QdrantConfig {

	@Bean
	fun qdrantClient(properties: QdrantProperties): QdrantClient {
		val (host, port) = properties.grpcUrl.split(":")
		return QdrantClient(QdrantGrpcClient.newBuilder(host, port.toInt(), false).build())
	}
}
