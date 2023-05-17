plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow")
}

group = "jp.mito.famiemukt"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":emulator"))
}

application {
    mainClass.set("jp.mito.famiemukt.MainKt")
}
