import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Hash court du commit git courant
val gitCommit: String by lazy {
    try {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readLine()?.trim() ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }
}

// Date du build au format dd/MM/yy
val buildDate: String by lazy {
    SimpleDateFormat("dd/MM/yy").format(Date())
}

// Changelog des N derniers commits (hors merges), format "hash|message|date" séparé par \n
val gitChangelog: String by lazy {
    try {
        val process = ProcessBuilder(
            "git", "log",
            "--pretty=format:%h|%s|%ad",
            "--date=format:%d/%m/%y",
            "--no-merges", "-30"
        ).directory(rootDir).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().readText().trim()
            .replace("\\", "\\\\")   // échapper les backslashes
            .replace("\"", "\\\"")   // échapper les guillemets
            .replace("\n", "\\n")    // transformer les sauts de ligne réels en \n littéral
            .replace("\r", "")       // supprimer les CR éventuels
    } catch (e: Exception) {
        ""
    }
}

android {
    namespace = "com.lmelp.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lmelp.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
        buildConfigField("String", "CHANGELOG", "\"$gitChangelog\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.sqlite)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
