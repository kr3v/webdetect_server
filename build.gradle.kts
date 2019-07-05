import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.21"
}

group = "com.cloudlinux"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation("it.unimi.dsi:fastutil:8.2.3")

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv:2.9.9")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.7")

}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}