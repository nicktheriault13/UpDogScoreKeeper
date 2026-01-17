import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.file.DuplicatesStrategy

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    
    jvm("desktop")

    // Web (PWA)
    js(IR) {
        moduleName = "composeApp"
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    val isMacOs = org.gradle.internal.os.OperatingSystem.current().isMacOsX
    if (isMacOs) {
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            resources.srcDirs("src/commonMain/resources")
            dependencies {
                // Core Compose + coroutines/serialization are fine for JS.
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)

                // Voyager
                implementation(libs.voyager.navigator)
                implementation(libs.voyager.screenModel)
                implementation(libs.voyager.tabNavigator)
                implementation(libs.voyager.transitions)
                implementation(libs.voyager.koin)

                // Koin
                implementation(libs.koin.core)
                implementation(libs.koin.compose)

                // Firebase Auth is not available on Kotlin/JS in this setup.
                // implementation(libs.firebase.auth)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.core.ktx)
                implementation(platform("com.google.firebase:firebase-bom:32.8.1"))
                implementation("com.google.firebase:firebase-auth-ktx")
                implementation("com.google.firebase:firebase-common-ktx")
                implementation("org.apache.poi:poi:5.2.5")
                implementation("org.apache.poi:poi-ooxml:5.2.5")

                // Firebase (Android)
                // Keep Android Firebase on Android source set.
                implementation(libs.firebase.auth)
            }
            languageSettings {
                optIn("androidx.compose.foundation.layout.ExperimentalLayoutApi")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.apache.poi:poi:5.2.5")
                implementation("org.apache.poi:poi-ooxml:5.2.5")
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)

                // JavaFX Media provides reliable MP3 playback on Windows.
                // (Java Sound / javax.sound.sampled typically can't decode MP3 out of the box.)
                val javafxVersion = "21.0.2"
                implementation("org.openjfx:javafx-base:$javafxVersion:win")
                implementation("org.openjfx:javafx-graphics:$javafxVersion:win")
                implementation("org.openjfx:javafx-media:$javafxVersion:win")
                implementation("org.openjfx:javafx-swing:$javafxVersion:win")

                // Firebase (Desktop/JVM)
                implementation(libs.firebase.auth)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(compose.html.core)
                implementation(compose.runtime)
                // no voyager/koin on web for now
            }
        }

        // iOS source sets exist only when we create iOS targets (macOS).
        if (isMacOs) {
            val iosMain by creating {
                dependsOn(commonMain)
            }
            val iosX64Main by getting { dependsOn(iosMain) }
            val iosArm64Main by getting { dependsOn(iosMain) }
            val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
        }
    }
}

android {
    namespace = "com.ddsk.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets {
        getByName("main") {
            // Map common assets to Android assets so they can be accessed via AssetManager
            assets.srcDirs("src/commonMain/resources/assets")
        }
    }

    defaultConfig {
        applicationId = "com.ddsk.app"
        minSdk = 26
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    dependencies {
        debugImplementation(libs.compose.ui.tooling)
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "UpDogScoreKeeper"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<Copy>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
