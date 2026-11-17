plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "dev.shph"
version = "0.0.1-SNAPSHOT"
extra["kotlin.version"] = "2.4.10"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("tools.jackson.module:jackson-module-kotlin:3.1.4")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-elasticsearch-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(26)
}

tasks.test {
    useJUnitPlatform()
}

tasks.bootJar {
    archiveFileName.set("grimoire.jar")
}
