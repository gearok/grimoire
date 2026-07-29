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

    testImplementation(kotlin("test"))
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
