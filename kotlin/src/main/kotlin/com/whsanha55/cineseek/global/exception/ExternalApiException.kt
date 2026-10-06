package com.whsanha55.cineseek.global.exception

/**
 * 외부 시스템(TMDB, 임베딩, Qdrant) 호출 실패 — 503 EXTERNAL_API_ERROR로 응답한다.
 * status는 HTTP 상태 코드(타임아웃·연결 실패면 null)
 */
class ExternalApiException(target: String, cause: Throwable? = null, val status: Int? = null) :
    BaseException(ErrorCodeEnum.EXTERNAL_API_ERROR, messageOf(target, status), cause) {

    private companion object {
        fun messageOf(target: String, status: Int?) =
            "외부 API 호출 실패. target=$target${status?.let { ", status=$it" }.orEmpty()}"
    }
}
