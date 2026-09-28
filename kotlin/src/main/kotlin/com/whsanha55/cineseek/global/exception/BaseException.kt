package com.whsanha55.cineseek.global.exception

/** message·cause는 로그용 — 응답에는 errorCode.message만 나간다 */
open class BaseException(val errorCode: ErrorCodeEnum, message: String = errorCode.message, cause: Throwable? = null) :
    RuntimeException(message, cause)
