package dev.shph.grimoire.controller

import dev.shph.grimoire.model.Monster
import dev.shph.grimoire.model.MonsterSearch
import dev.shph.grimoire.model.SearchResultMode
import dev.shph.grimoire.repository.MonsterRepository
import dev.shph.grimoire.view.MonsterIndexView
import dev.shph.grimoire.view.SuggestionView
import dev.shph.grimoire.view.monsterSearchForm
import dev.shph.grimoire.view.toDetailView
import dev.shph.grimoire.view.toIndexView
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.util.MultiValueMap
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.ModelAndView

@Controller
class MonsterController(private val repository: MonsterRepository) {
    private val allLinksView: MonsterIndexView by lazy {
        val criteria = MonsterSearch(pageSize = null)
        repository.search(criteria).toIndexView(criteria, SearchResultMode.INDEX)
    }

    @GetMapping("/monsters")
    fun index(
        servletRequest: HttpServletRequest,
        @RequestParam parameters: MultiValueMap<String, String>,
        model: Model,
    ): ModelAndView {
        val searchRequest = MonsterSearchRequest.from(parameters)
        val criteria = searchRequest.toSearch()
        val view = if (searchRequest.resultMode == SearchResultMode.INDEX && criteria.isUnfiltered) {
            allLinksView
        } else {
            repository.search(criteria).toIndexView(criteria, searchRequest.resultMode)
        }
        model.addAttribute("view", view)
        return ModelAndView(
            if (servletRequest.isHtmxFragmentRequest()) "monsters/results" else "monsters/index",
            model.asMap(),
        )
    }

    @GetMapping("/monsters/{id}")
    fun detail(@PathVariable id: String, model: Model): String {
        if (id.isBlank()) throw BadRequestException("Missing monster id")
        val monster = repository.findById(id) ?: throw MonsterNotFoundException()
        model.addAttribute("view", monster.toDetailView())
        model.addAttribute("searchForm", monsterSearchForm())
        return "monsters/detail"
    }

    @GetMapping("/monsters/suggestions")
    fun suggestions(
        @RequestParam("q", defaultValue = "") query: String,
        model: Model,
    ): String {
        val suggestions = query.trim()
            .takeIf(String::isNotEmpty)
            ?.let(repository::suggest)
            .orEmpty()
            .map { SuggestionView(it.id, it.name.ru, it.name.en) }
        model.addAttribute("suggestions", suggestions)
        return "fragments/suggestions :: suggestionList(suggestions=${'$'}{suggestions}, prefix='monster', label='Подсказки существ')"
    }
}

@Controller
@RequestMapping("/api/monsters")
class MonsterApiController(private val repository: MonsterRepository) {
    @GetMapping("/suggestions")
    @ResponseBody
    fun suggestions(@RequestParam("q", defaultValue = "") query: String): List<SuggestionView> =
        query.trim().takeIf(String::isNotEmpty)?.let(repository::suggest).orEmpty()
            .map { SuggestionView(it.id, it.name.ru, it.name.en) }

    @GetMapping("/{id}")
    @ResponseBody
    fun find(@PathVariable id: String): Monster {
        if (id.isBlank()) throw BadRequestException("Missing monster id")
        return repository.findById(id) ?: throw MonsterNotFoundException()
    }
}

class MonsterNotFoundException : RuntimeException("Monster not found")
