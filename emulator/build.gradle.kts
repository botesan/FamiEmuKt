plugins {
    kotlin("multiplatform")
    //id("dev.mokkery")
}

kotlin {
    jvm()
    js {
        nodejs {
            testTask {
                useMocha {
                    timeout = "5min"
                }
            }
        }
    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    @Suppress("unused") val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64()
        hostOs == "Linux" -> linuxX64()
        isMingwX64 -> mingwX64()
        else -> throw GradleException("Host OS($hostOs) is not supported in Kotlin/Native.")
    }

    sourceSets {
        @Suppress("unused") val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.7.0")
            }
        }
        @Suppress("unused") val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        @Suppress("unused") val jvmMain by getting {
        }
        @Suppress("unused") val jvmTest by getting {
            dependencies {
                implementation("io.mockk:mockk:1.14.6")
            }
        }
    }
}
