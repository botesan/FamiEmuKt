import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    kotlin("multiplatform") version "2.2.21" apply false
    kotlin("jvm") version "2.2.21" apply false
    //id("dev.mokkery") version "2.10.2" apply false
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
