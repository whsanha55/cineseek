package com.whsanha55.cineseek.external.llm.client

import com.whsanha55.cineseek.global.exception.BaseException
import com.whsanha55.cineseek.global.exception.ErrorCodeEnum

/**
 * LLM 콘텐츠 필터에 걸린 요청 — 같은 입력으로 다시 호출해도 결과가 같은 영구 실패다.
 * 일시 실패(429·5xx·타임아웃 등)는 ExternalApiException으로 구분한다
 */
class LlmContentFilteredException(cause: Throwable) :
    BaseException(ErrorCodeEnum.EXTERNAL_API_ERROR, "LLM 콘텐츠 필터에 걸렸다. target=llm", cause)
