import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Credenciais do ScreenScraper vêm de local.properties (fora do git), nunca hardcoded.
// Ausentes -> string vazia, para não quebrar o build antes de termos credenciais reais.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun screenScraperProperty(key: String): String = localProperties.getProperty(key) ?: ""

android {
    namespace = "com.thalys.catalogosnes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.thalys.catalogosnes"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        buildConfigField("String", "SCREENSCRAPER_DEVID", "\"${screenScraperProperty("SCREENSCRAPER_DEVID")}\"")
        buildConfigField("String", "SCREENSCRAPER_DEVPASSWORD", "\"${screenScraperProperty("SCREENSCRAPER_DEVPASSWORD")}\"")
        buildConfigField("String", "SCREENSCRAPER_SOFTNAME", "\"${screenScraperProperty("SCREENSCRAPER_SOFTNAME")}\"")
        buildConfigField("String", "SCREENSCRAPER_USER_ID", "\"${screenScraperProperty("SCREENSCRAPER_USER_ID")}\"")
        buildConfigField("String", "SCREENSCRAPER_USER_PASSWORD", "\"${screenScraperProperty("SCREENSCRAPER_USER_PASSWORD")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
