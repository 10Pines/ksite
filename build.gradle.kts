import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.31"
    id("com.github.johnrengelman.shadow") version "5.0.0"
}

group = "com.10pines.kotlingreenhouse"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    val http4kVersion = "3.140.0"

    implementation("org.http4k", "http4k-core", http4kVersion)
    implementation("org.http4k", "http4k-server-jetty", http4kVersion)
    implementation("org.http4k", "http4k-client-websocket", http4kVersion)
    implementation("org.http4k", "http4k-format-gson", http4kVersion)

    implementation("com.typesafe.akka", "akka-actor-typed_2.12", "2.5.22")
    implementation("com.typesafe.akka", "akka-slf4j_2.12", "2.5.22")
    implementation("ch.qos.logback", "logback-classic", "1.2.3")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<ShadowJar> {
    manifest {
        attributes["Main-Class"] = "ServerKt"
    }
    mergeServiceFiles("reference.conf")
    mergeServiceFiles()
}