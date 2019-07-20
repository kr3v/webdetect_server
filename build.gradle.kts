import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.21"
}

group = "com.cloudlinux"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    implementation("it.unimi.dsi:fastutil:8.2.3")
    implementation("com.baqend:bloom-filter:2.2.2")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.7")

    implementation("org.fusesource.leveldbjni:leveldbjni-all:1.8")
    implementation("org.postgresql:postgresql:42.2.6")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}