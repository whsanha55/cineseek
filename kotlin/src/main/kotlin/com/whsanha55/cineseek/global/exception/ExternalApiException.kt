package com.whsanha55.cineseek.global.exception

/** 외부 시스템(TMDB, 임베딩, Qdrant) 호출 실패 — 503 EXTERNAL_API_ERROR로 응답한다 */
class ExternalApiException(target: String, cause: Throwable? = null) :
    BaseException(ErrorCodeEnum.EXTERNAL_API_ERROR, "외부 API 호출 실패. target=$target", cause)
