package dev.shph.grimoire.controller

import dev.shph.grimoire.repository.ClassRepository
import dev.shph.grimoire.view.toDetailView
import dev.shph.grimoire.view.toIndexView
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@Controller
class ClassController(private val repository: ClassRepository) {
    @GetMapping("/classes")
    fun index(model: Model): String {
        model.addAttribute("view", repository.findAll().toIndexView())
        return "classes/index"
    }

    @GetMapping("/classes/{id}")
    fun detail(@PathVariable id: String, model: Model): String {
        if (id.isBlank()) throw BadRequestException("Missing class id")
        val characterClass = repository.findById(id) ?: throw ClassNotFoundException()
        model.addAttribute("view", characterClass.toDetailView())
        return "classes/detail"
    }
}

class ClassNotFoundException : RuntimeException("Class not found")
