package dev.shph.grimoire.controller

import dev.shph.grimoire.model.CreatureSize
import dev.shph.grimoire.model.CreatureType
import dev.shph.grimoire.model.Monster
import dev.shph.grimoire.model.MonsterSearch
import dev.shph.grimoire.repository.MonsterRepository
import dev.shph.grimoire.view.toDetailView
import dev.shph.grimoire.view.toIndexView
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.ModelAndView

@Controller
class MonsterController(private val repository: MonsterRepository) {
    @GetMapping("/monsters")
    fun index(
        request: HttpServletRequest,
        response: HttpServletResponse,
        model: Model,
        @RequestHeader("HX-Request", required = false) hxRequest: String?,
    ): ModelAndView {
        val cardsView = request.getParameter("view").equals("cards", ignoreCase = true)
        val criteria = request.monsterSearchCriteria(
            pageSize = if (cardsView) MONSTER_CARD_PAGE_SIZE else MONSTER_INDEX_PAGE_SIZE,
        )
        model.addAttribute("view", repository.search(criteria).toIndexView(criteria, cardsView))
        if (hxRequest.equals("true", ignoreCase = true)) {
            response.addHeader(HttpHeaders.VARY, "HX-Request")
        }
        return ModelAndView(
            if (hxRequest.equals("true", ignoreCase = true)) "monsters/results" else "monsters/index",
            model.asMap(),
        )
    }

    @GetMapping("/monsters/{id}")
    fun detail(@PathVariable id: String, model: Model): String {
        if (id.isBlank()) throw BadRequestException("Missing monster id")
        val monster = repository.findById(id) ?: throw MonsterNotFoundException()
        model.addAttribute("view", monster.toDetailView())
        return "monsters/detail"
    }
}

@Controller
@RequestMapping("/api/monsters")
class MonsterApiController(private val repository: MonsterRepository) {
    @GetMapping("/suggestions")
    @ResponseBody
    fun suggestions(@RequestParam("q", defaultValue = "") query: String): List<MonsterSuggestion> =
        query.trim().takeIf(String::isNotEmpty)?.let(repository::suggest).orEmpty()
            .map { MonsterSuggestion(it.id, it.name.ru, it.name.en) }

    @GetMapping("/{id}")
    @ResponseBody
    fun find(@PathVariable id: String): Monster =
        repository.findById(id) ?: throw MonsterNotFoundException()
}

data class MonsterSuggestion(
    val id: String,
    val nameRu: String,
    val nameEn: String,
)

private fun HttpServletRequest.monsterSearchCriteria(pageSize: Int): MonsterSearch {
    val pageValue = getParameter("page")?.takeIf(String::isNotBlank)
    val page = pageValue?.toIntOrNull() ?: 1
    if ((pageValue != null && pageValue.toIntOrNull() == null) || page < 1) {
        throw BadRequestException("Invalid monster search parameters")
    }

    val sizeValues = monsterQueryValues("size")
    val sizes = sizeValues.mapNotNull(CreatureSize::fromSlug)
    val typeValues = monsterQueryValues("type")
    val types = typeValues.mapNotNull(CreatureType::fromSlug)
    val challengeValues = monsterQueryValues("challenge")
    val challenges = challengeValues.mapNotNull(String::toDoubleOrNull)
    if (
        sizes.size != sizeValues.size ||
        types.size != typeValues.size ||
        challenges.size != challengeValues.size ||
        challenges.any { it !in MONSTER_CHALLENGES }
    ) {
        throw BadRequestException("Invalid monster search parameters")
    }

    return MonsterSearch(
        query = getParameter("q")?.trim()?.takeIf(String::isNotEmpty),
        sizes = sizes.toSet(),
        types = types.toSet(),
        challenges = challenges.toSet(),
        page = page,
        pageSize = pageSize,
    )
}

private fun HttpServletRequest.monsterQueryValues(name: String): List<String> =
    getParameterValues(name).orEmpty()
        .flatMap { it.split(',') }
        .map(String::trim)
        .filter(String::isNotEmpty)

private const val MONSTER_CARD_PAGE_SIZE = 30
private const val MONSTER_INDEX_PAGE_SIZE = 1_000
private val MONSTER_CHALLENGES =
    setOf(0.0, 0.125, 0.25, 0.5) + (1..30).map(Int::toDouble)

class MonsterNotFoundException : RuntimeException("Monster not found")
