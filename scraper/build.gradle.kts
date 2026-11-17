plugins {
    kotlin("jvm") version "2.4.10"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.gradleup.shadow") version "9.6.1"
    application
}

extra["kotlin.version"] = "2.4.10"

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
    }
}

dependencies {
    implementation(project(":"))
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("tools.jackson.module:jackson-module-kotlin:3.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("io.ktor:ktor-client-cio:3.5.1")

    testImplementation(kotlin("test"))
}

// Ktor 3.5.x is compiled against coroutines 1.11.0 (its HttpTimeout plugin calls the newer
// Job.cancel signature); at runtime an older 1.10.2 throws NoSuchMethodError. The Spring Boot
// BOM pins coroutines to 1.10.2, so force the whole group to 1.11.0 to keep them compatible.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-coroutines")) {
            useVersion("1.11.0")
            because("Ktor 3.5.x requires kotlinx-coroutines 1.11.0")
        }
    }
}

kotlin {
    jvmToolchain(26)
}

application {
    mainClass = "dev.shph.grimoire.scraper.ScraperMainKt"
}

tasks.shadowJar {
    archiveFileName.set("grimoire-scraper.jar")
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.INCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    mergeServiceFiles()
}

tasks.test {
    useJUnitPlatform()
}
