package dev.shph.grimoire.controller

import dev.shph.grimoire.model.Spell
import dev.shph.grimoire.repository.SpellRepository
import dev.shph.grimoire.view.toDetailView
import dev.shph.grimoire.view.toIndexView
import org.springframework.stereotype.Controller
import org.springframework.util.MultiValueMap
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.ModelAndView

@Controller
class SpellController(private val repository: SpellRepository) {
    @GetMapping("/")
    fun home() = "redirect:/spells"

    @GetMapping("/spells")
    fun index(
        @RequestParam parameters: MultiValueMap<String, String>,
        model: Model,
        @RequestHeader("HX-Request", required = false) hxRequest: String?,
    ): ModelAndView {
        val request = SpellSearchRequest.from(parameters)
        val criteria = request.toSearch()
        model.addAttribute("view", repository.search(criteria).toIndexView(criteria, request.resultMode))
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
    fun find(@PathVariable id: String): Spell {
        if (id.isBlank()) throw BadRequestException("Missing spell id")
        return repository.findById(id) ?: throw SpellNotFoundException()
    }
}

data class SpellSuggestion(
    val id: String,
    val nameRu: String,
    val nameEn: String,
)

class BadRequestException(message: String) : RuntimeException(message)
class SpellNotFoundException : RuntimeException("Spell not found")
