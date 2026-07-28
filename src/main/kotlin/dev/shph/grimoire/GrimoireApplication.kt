package dev.shph.grimoire

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GrimoireApplication

fun main(args: Array<String>) {
    runApplication<GrimoireApplication>(*args)
}
