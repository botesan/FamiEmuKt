import com.android.build.api.dsl.androidLibrary

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("dev.mokkery")
    id("com.goncalossilva.resources")
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
    @Suppress("UnstableApiUsage")
    androidLibrary {
        withJava()
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            execution = "HOST"
        }
        namespace = "jp.mito.famiemukt.emurator"
        compileSdk = 36
        minSdk = 24
    }
//    androidNativeArm32 {
//        binaries.staticLib()
//    }
//    androidNativeArm64 {
//        binaries.staticLib()
//    }
//    androidNativeX64 {
//        binaries.staticLib()
//    }
//    androidNativeX86 {
//        binaries.staticLib()
//    }
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
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.2")
                implementation("co.touchlab:kermit:2.0.8")
            }
        }
        @Suppress("unused") val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("com.goncalossilva:resources:0.15.0")
            }
        }
        @Suppress("unused") val androidDeviceTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("com.goncalossilva:resources:0.15.0")
                implementation("androidx.test.ext:junit:1.3.0")
                implementation("androidx.test.espresso:espresso-core:3.7.0")
            }
        }
    }
}
