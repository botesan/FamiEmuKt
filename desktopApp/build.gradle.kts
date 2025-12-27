plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow")
}

group = "jp.mito.famiemukt"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":emulator"))
    implementation("co.touchlab:kermit:2.0.8")
}

application {
    mainClass.set("jp.mito.famiemukt.MainKt")
}
