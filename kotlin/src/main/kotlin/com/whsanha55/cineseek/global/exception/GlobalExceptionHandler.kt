package com.whsanha55.cineseek.global.exception

import com.whsanha55.cineseek.global.filter.RequestIdFilter.Companion.REQUEST_ID
import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

private val log = KotlinLogging.logger {}

/** 모든 에러를 ProblemDetail(RFC 9457) + code·requestId로 응답한다 */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(BaseException::class)
    fun handleBaseException(e: BaseException): ProblemDetail = problemOf(e.errorCode)

    @ExceptionHandler(Exception::class)
    fun handleUnknown(e: Exception): ProblemDetail {
        log.error(e) { "처리되지 않은 예외" }
        return problemOf(ErrorCodeEnum.INTERNAL_ERROR)
    }

    /** Spring 기본 예외에도 사용자 메시지와 code·requestId를 붙인다 */
    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        if (body is ProblemDetail) {
            val errorCode = errorCodeOf(statusCode)
            body.detail = errorCode.message
            body.setProperty("code", errorCode.name)
            body.setProperty("requestId", MDC.get(REQUEST_ID))
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request)
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errors = ex.bindingResult.fieldErrors.map { mapOf("field" to it.field, "message" to it.defaultMessage) }
        ex.body.setProperty("errors", errors)
        return handleExceptionInternal(ex, ex.body, headers, status, request)
    }

    private fun problemOf(errorCode: ErrorCodeEnum) =
        ProblemDetail.forStatusAndDetail(errorCode.status, errorCode.message).apply {
            setProperty("code", errorCode.name)
            setProperty("requestId", MDC.get(REQUEST_ID))
        }

    private fun errorCodeOf(statusCode: HttpStatusCode): ErrorCodeEnum =
        ErrorCodeEnum.entries.firstOrNull { it.status.value() == statusCode.value() }
            ?: if (statusCode.is4xxClientError) ErrorCodeEnum.INVALID_REQUEST else ErrorCodeEnum.INTERNAL_ERROR
}
