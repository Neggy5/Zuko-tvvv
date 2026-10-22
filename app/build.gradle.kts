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

android {
    namespace = "com.zuko.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zuko.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.4.0-search-download"

        buildConfigField("String", "TMDB_API_KEY", "\"${prop("TMDB_API_KEY")}\"")
        buildConfigField("String", "STREAM_API_BASE", "\"${prop("STREAM_API_BASE")}\"")
        buildConfigField(
            "String",
            "RUNFLIX_ADDON_BASE",
            "\"${prop("RUNFLIX_ADDON_BASE", "https://fuzzy-fortnight-production.up.railway.app")}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
