import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    kotlin("multiplatform") version "2.3.0" apply false
    kotlin("jvm") version "2.3.0" apply false
    kotlin("android") version "2.3.0" apply false
    id("com.android.kotlin.multiplatform.library") version "8.12.3" apply false
    id("com.android.application") version "8.12.3" apply false
    id("dev.mokkery") version "3.2.0" apply false
    id("com.goncalossilva.resources") version "0.15.0" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    id("com.github.ben-manes.versions") version "0.53.0"
}

allprojects {
    repositories {
        mavenCentral()
        google()
    }

    apply(plugin = "com.github.ben-manes.versions")
    tasks.named(name = "dependencyUpdates", type = DependencyUpdatesTask::class) {
        resolutionStrategy {
            componentSelection {
                val rejects = arrayOf("alpha", "beta", "rc", "cr", "m", "preview", "b", "ea", "eap", "dev", "snapshot")
                    .map { "(?i).*[.-]$it[.\\d-+]*[.\\d\\w-+]*".toRegex() }
                all(selectionAction = {
                    val rejected = rejects.any { candidate.version.matches(it) }
                    if (rejected) reject("Release candidate")
                })
            }
        }
    }
}
