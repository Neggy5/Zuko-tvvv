import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(key: String, default: String = ""): String =
    (localProps.getProperty(key) ?: System.getenv(key) ?: default)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH") ?: System.getenv("ZUKO_KEYSTORE_FILE")

android {
    signingConfigs {
        create("release") {
            storeFile = releaseKeystorePath?.let { file(it) }
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: System.getenv("ZUKO_KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: System.getenv("ZUKO_KEY_ALIAS") ?: "zukotv"
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD") ?: System.getenv("ZUKO_KEY_PASSWORD") ?: ""
        }
    }

    namespace = "com.zuko.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zuko.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "0.8.0"

        buildConfigField(
            "String",
            "TMDB_PROXY_BASE",
            "\"${prop("TMDB_PROXY_BASE", "https://athletic-consideration-production-c842.up.railway.app/api/tmdb/3/")}\""
        )
        buildConfigField("String", "STREAM_API_BASE", "\"${prop("STREAM_API_BASE")}\"")
        buildConfigField(
            "String",
            "RUNFLIX_ADDON_BASE",
            "\"${prop("RUNFLIX_ADDON_BASE", "https://athletic-consideration-production-c842.up.railway.app")}\""
        )

        // Runflix/DaraTech football live API — free key: https://apimovie.runflix.name.ng/request-key
        buildConfigField(
            "String",
            "RUNFLIX_API_BASE",
            "\"${prop("RUNFLIX_API_BASE", "https://apimovie.runflix.name.ng")}\""
        )
        buildConfigField(
            "String",
            "RUNFLIX_API_KEY",
            "\"${prop("RUNFLIX_API_KEY", "zukoxmdapi")}\""
        )
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            // Strip debug symbols from native libs when present
            ndk {
                debugSymbolLevel = "NONE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (releaseKeystorePath.isNullOrBlank()) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin/**",
                "**.properties"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
