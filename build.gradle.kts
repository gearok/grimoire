plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("io.ktor.plugin") version "3.5.1"
}

group = "dev.shph"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-server-freemarker")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-compression")
    implementation("io.ktor:ktor-server-auto-head-response")
    implementation("io.ktor:ktor-server-caching-headers")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-mock")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("dev.shph.grimoire.GrimoireApplicationKt")
}

kotlin {
    jvmToolchain(17)
}

ktor {
    fatJar {
        archiveFileName.set("grimoire-all.jar")
    }
}

tasks.test {
    useJUnitPlatform()
}
