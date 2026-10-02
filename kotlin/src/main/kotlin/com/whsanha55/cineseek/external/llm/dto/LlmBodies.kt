package com.whsanha55.cineseek.external.llm.dto

/**
 * POST /chat/completions 와이어 계약 DTO(OpenAI 호환) — LlmClient 밖으로 내보내지 않는다.
 * response_format(json_object)은 지원하지 않는 서버가 있어 넣지 않는다 — 프롬프트로 JSON을 강제하고,
 * 마크다운 펜스는 응답 파싱부에서 걷어낸다
 */
internal data class LlmChatRequest(val model: String, val messages: List<LlmMessage>, val temperature: Double)

internal data class LlmMessage(val role: String, val content: String)

internal data class LlmChatResponse(val choices: List<LlmChoice>)

internal data class LlmChoice(val message: LlmMessage)
