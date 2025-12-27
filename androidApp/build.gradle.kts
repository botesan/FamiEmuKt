import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.*

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "jp.mito.famiemukt"
    compileSdk = 36
    defaultConfig {
        applicationId = "jp.mito.famiemukt"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        project.setProperty("archivesBaseName", "${rootProject.name}_$versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    androidResources {
        @Suppress("UnstableApiUsage")
        localeFilters += listOf("ja")
    }
    signingConfigs {
        val localProperties = Properties()
            .apply { load(project.rootProject.file("local.properties").inputStream()) }
        named("debug") {
            val signStoreFile = localProperties.getProperty("sign.debug.storeFile")
            if (signStoreFile != null) {
                storeFile = file(signStoreFile)
            }
        }
        create("release") {
            val signStoreFile = localProperties.getProperty("sign.release.storeFile")
            val signStorePassword = localProperties.getProperty("sign.release.storePassword")
            val signKeyAlias = localProperties.getProperty("sign.release.keyAlias")
            val signKeyPassword = localProperties.getProperty("sign.release.keyPassword")
            if (signStoreFile != null && signStorePassword != null && signKeyAlias != null && signKeyPassword != null) {
                storeFile = file(signStoreFile)
                storePassword = signStorePassword
                keyAlias = signKeyAlias
                keyPassword = signKeyPassword
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            multiDexEnabled = true
            if (signingConfigs["debug"].storeFile != null) {
                signingConfig = signingConfigs["debug"]
            }
        }
        release {
            isMinifyEnabled = true
            isCrunchPngs = true
            isShrinkResources = true
            multiDexEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingConfigs["release"].storeFile != null) {
                signingConfig = signingConfigs["release"]
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
    buildFeatures {
        buildConfig = true
        resValues = true
        viewBinding = true
    }
}

dependencies {
    implementation(project(":emulator"))
    implementation("co.touchlab:kermit:2.0.8")
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
    androidTestImplementation(kotlin("test"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
