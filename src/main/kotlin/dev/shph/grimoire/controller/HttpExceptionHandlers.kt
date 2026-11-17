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
    is ClassNotFoundException ->
        ErrorDetails(HttpStatus.NOT_FOUND, message ?: "Class not found")
    is RaceNotFoundException ->
        ErrorDetails(HttpStatus.NOT_FOUND, message ?: "Race not found")
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

@ControllerAdvice(
    assignableTypes = [
        SpellController::class,
        MonsterController::class,
        ClassController::class,
        RaceController::class,
    ],
)
class PageHttpExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(
        BadRequestException::class,
        IllegalArgumentException::class,
        SpellNotFoundException::class,
        MonsterNotFoundException::class,
        ClassNotFoundException::class,
        RaceNotFoundException::class,
        DataAccessException::class,
    )
    fun error(cause: RuntimeException, request: HttpServletRequest): ModelAndView {
        if (cause is DataAccessException) {
            log.error("Elasticsearch request failed", cause)
        }
        val details = cause.errorDetails()
        val (section, backHref, backLabel) = when {
            request.requestURI.startsWith("/monsters") ->
                Triple("monsters", "/monsters", "Вернуться в бестиарий")
            request.requestURI.startsWith("/classes") ->
                Triple("classes", "/classes", "Вернуться к классам")
            request.requestURI.startsWith("/races") ->
                Triple("races", "/races", "Вернуться к расам")
            else -> Triple("spells", "/spells", "Вернуться к заклинаниям")
        }
        val view = ErrorView(
            status = details.status.value(),
            title = details.status.reasonPhrase,
            message = details.message,
            section = section,
            backHref = backHref,
            backLabel = backLabel,
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
