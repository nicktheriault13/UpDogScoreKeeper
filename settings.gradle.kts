pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

    plugins {
        id("com.android.application").version("8.2.0")
        id("com.android.library").version("8.2.0")
        id("org.jetbrains.kotlin.multiplatform").version("1.9.22")
        id("org.jetbrains.kotlin.android").version("1.9.22")
        id("org.jetbrains.compose").version("1.6.0")
        id("org.jetbrains.kotlin.plugin.serialization").version("1.9.22")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.pkg.jetbrains.space/public/p/firebase-kotlin-sdk/maven")
    }
}

rootProject.name = "UpDogScoreKeeper"
include(":composeApp")
