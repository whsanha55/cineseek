package com.whsanha55.cineseek.global.exception

open class BaseException(val errorCode: ErrorCodeEnum) : RuntimeException(errorCode.message)
