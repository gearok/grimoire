package dev.shph.grimoire.controller

import dev.shph.grimoire.model.MagicSchool
import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.model.SpellSearch
import dev.shph.grimoire.repository.SpellRepository
import dev.shph.grimoire.view.toDetailView
import dev.shph.grimoire.view.toIndexView
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.ModelAndView

@Controller
class SpellController(private val repository: SpellRepository) {
    @GetMapping("/")
    fun home() = "redirect:/spells"

    @GetMapping("/spells")
    fun index(
        request: HttpServletRequest,
        response: HttpServletResponse,
        model: Model,
        @RequestHeader("HX-Request", required = false) hxRequest: String?,
    ): ModelAndView {
        val cardsView = request.getParameter("view").equals("cards", ignoreCase = true)
        val criteria = request.searchCriteria(
            pageSize = if (cardsView) CARD_PAGE_SIZE else LINK_INDEX_PAGE_SIZE,
        )
        model.addAttribute("view", repository.search(criteria).toIndexView(criteria, cardsView))
        if (hxRequest.equals("true", ignoreCase = true)) {
            response.addHeader(HttpHeaders.VARY, "HX-Request")
        }
        return ModelAndView(
            if (hxRequest.equals("true", ignoreCase = true)) "spells/results" else "spells/index",
            model.asMap(),
        )
    }

    @GetMapping("/spells/{id}")
    fun detail(@PathVariable id: String, model: Model): String {
        if (id.isBlank()) throw BadRequestException("Missing spell id")
        val spell = repository.findById(id) ?: throw SpellNotFoundException()
        model.addAttribute("view", spell.toDetailView())
        return "spells/detail"
    }
}

@Controller
@RequestMapping("/api/spells")
class SpellApiController(private val repository: SpellRepository) {
    @GetMapping("/suggestions")
    @ResponseBody
    fun suggestions(@RequestParam("q", defaultValue = "") query: String): List<SpellSuggestion> =
        query.trim()
            .takeIf(String::isNotEmpty)
            ?.let(repository::suggest)
            .orEmpty()
            .map { SpellSuggestion(it.id, it.name.ru, it.name.en) }

    @GetMapping("/{id}")
    @ResponseBody
    fun find(@PathVariable id: String): Spell =
        repository.findById(id) ?: throw SpellNotFoundException()
}

data class SpellSuggestion(
    val id: String,
    val nameRu: String,
    val nameEn: String,
)

private fun HttpServletRequest.searchCriteria(pageSize: Int = CARD_PAGE_SIZE): SpellSearch {
    val levelValues = queryValues("level")
    val levels = levelValues.mapNotNull(String::toIntOrNull)
    if (levels.size != levelValues.size || levels.any { it !in 0..9 }) {
        throw BadRequestException("Invalid spell search parameters")
    }

    val pageValue = getParameter("page")?.takeIf(String::isNotBlank)
    val page = pageValue?.toIntOrNull() ?: 1
    if ((pageValue != null && pageValue.toIntOrNull() == null) || page < 1) {
        throw BadRequestException("Invalid spell search parameters")
    }

    val schoolValues = queryValues("school")
    val schools = schoolValues.mapNotNull(MagicSchool::fromSlug)
    if (schools.size != schoolValues.size) {
        throw BadRequestException("Invalid spell search parameters")
    }

    return SpellSearch(
        query = getParameter("q")?.trim()?.takeIf(String::isNotEmpty),
        levels = levels.toSet(),
        schools = schools.toSet(),
        characterClasses = queryValues("class").map(String::lowercase).toSet(),
        page = page,
        pageSize = pageSize,
    )
}

private fun HttpServletRequest.queryValues(name: String): List<String> =
    getParameterValues(name)
        .orEmpty()
        .flatMap { it.split(',') }
        .map(String::trim)
        .filter(String::isNotEmpty)

private const val CARD_PAGE_SIZE = 30
private const val LINK_INDEX_PAGE_SIZE = 1_000

class BadRequestException(message: String) : RuntimeException(message)
class SpellNotFoundException : RuntimeException("Spell not found")

data class ErrorResponse(val error: String)

@RestControllerAdvice
class HttpExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BadRequestException::class, IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun badRequest(cause: RuntimeException) = ErrorResponse(cause.message ?: "Invalid request")

    @ExceptionHandler(SpellNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(cause: SpellNotFoundException) = ErrorResponse(cause.message ?: "Spell not found")

    @ExceptionHandler(DataAccessException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun unavailable(cause: DataAccessException): ErrorResponse {
        log.error("Elasticsearch request failed", cause)
        return ErrorResponse("Spell storage is temporarily unavailable")
    }
}
