package dev.shph.grimoire.controller

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.ModelAndView

data class ErrorResponse(val error: String)

private data class ErrorDetails(
    val status: HttpStatus,
    val message: String,
)

data class ErrorView(
    val status: Int,
    val title: String,
    val message: String,
    val section: String,
    val backHref: String,
    val backLabel: String,
)

private fun RuntimeException.errorDetails() = when (this) {
    is BadRequestException, is IllegalArgumentException ->
        ErrorDetails(HttpStatus.BAD_REQUEST, message ?: "Invalid request")
    is SpellNotFoundException ->
        ErrorDetails(HttpStatus.NOT_FOUND, message ?: "Spell not found")
    is MonsterNotFoundException ->
        ErrorDetails(HttpStatus.NOT_FOUND, message ?: "Monster not found")
    is DataAccessException ->
        ErrorDetails(HttpStatus.SERVICE_UNAVAILABLE, "Search storage is temporarily unavailable")
    else -> error("Unsupported exception type: ${javaClass.name}")
}

@RestControllerAdvice(assignableTypes = [SpellApiController::class, MonsterApiController::class])
class ApiHttpExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BadRequestException::class, IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(cause: RuntimeException) = cause.toResponse()

    @ExceptionHandler(SpellNotFoundException::class, MonsterNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(cause: RuntimeException) = cause.toResponse()

    @ExceptionHandler(DataAccessException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun unavailable(cause: DataAccessException): ErrorResponse {
        log.error("Elasticsearch request failed", cause)
        return cause.toResponse()
    }

    private fun RuntimeException.toResponse() = ErrorResponse(errorDetails().message)
}

@ControllerAdvice(assignableTypes = [SpellController::class, MonsterController::class])
class PageHttpExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(
        BadRequestException::class,
        IllegalArgumentException::class,
        SpellNotFoundException::class,
        MonsterNotFoundException::class,
        DataAccessException::class,
    )
    fun error(cause: RuntimeException, request: HttpServletRequest): ModelAndView {
        if (cause is DataAccessException) {
            log.error("Elasticsearch request failed", cause)
        }
        val details = cause.errorDetails()
        val monsters = request.requestURI.startsWith("/monsters")
        val view = ErrorView(
            status = details.status.value(),
            title = details.status.reasonPhrase,
            message = details.message,
            section = if (monsters) "monsters" else "spells",
            backHref = if (monsters) "/monsters" else "/spells",
            backLabel = if (monsters) "Вернуться в бестиарий" else "Вернуться к заклинаниям",
        )
        val template =
            if (request.getHeader("HX-Request").equals("true", ignoreCase = true)) {
                "errors/fragment"
            } else {
                "errors/error"
            }
        return ModelAndView(template, mapOf("error" to view), details.status)
    }
}
