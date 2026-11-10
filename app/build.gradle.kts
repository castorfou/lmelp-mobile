import java.text.SimpleDateFormat
import java.util.Date
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Sortie d'une commande git, ou null si git échoue (pas de dépôt, git absent).
// Via providers.exec et non ProcessBuilder : lancer un process à la configuration
// est déprécié depuis Gradle 9 et échouera avec Gradle 11 (issue #158).
fun gitOutput(vararg args: String): String? = try {
    val result = providers.exec {
        commandLine("git", *args)
        workingDir = rootDir
        isIgnoreExitValue = true
    }
    if (result.result.get().exitValue == 0) result.standardOutput.asText.get().trim() else null
} catch (e: Exception) {
    null
}

// Hash court du commit git courant
val gitCommit: String by lazy {
    gitOutput("rev-parse", "--short", "HEAD")?.lineSequence()?.firstOrNull() ?: "unknown"
}

// Date du build au format dd/MM/yy
val buildDate: String by lazy {
    SimpleDateFormat("dd/MM/yy").format(Date())
}

// Historique complet des commits (hors merges, ordre topologique pour que chaque
// commit tombe sous la version qui le contient), issue #170 : une ligne par commit,
// "hash␟sujet␟date␟tags" (séparateur U+001F, jamais présent dans un sujet ; tags = %D restreint
// à refs/tags/). Écrit dans un asset généré et non dans BuildConfig : une constante de chaîne
// Java est plafonnée à 65 535 octets. Format lu par parseHistorique (ui/about/Historique.kt).
val gitHistorique: Provider<String> = provider {
    gitOutput(
        "log",
        "--no-merges",
        "--topo-order",
        "--pretty=format:%h%x1f%s%x1f%ad%x1f%D",
        "--date=format:%d/%m/%y",
        "--decorate-refs=refs/tags/"
    ) ?: ""
}

abstract class GenerateHistoriqueTask : DefaultTask() {
    @get:Input
    abstract val historique: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        outputDir.file("historique.txt").get().asFile.writeText(historique.get())
    }
}

// Version de l'APK (issue #161) : release.yml passe -PversionName=X.Y.Z (tag sans le v) ;
// sans propriété, version de dev 0.0.0-dev+<sha>, versionCode 1. Règle : buildSrc/AppVersion.kt.
val appVersion = AppVersion.resolve(findProperty("versionName") as String?, gitCommit)

android {
    namespace = "com.lmelp.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lmelp.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersion.code
        versionName = appVersion.name

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
    }

    // Signature de l'APK de release (issue #159) : keystore fourni par l'environnement
    // (release.yml le décode depuis le secret KEYSTORE_BASE64). Sans LMELP_KEYSTORE_FILE,
    // assembleRelease produit un APK non signé (app-release-unsigned.apk).
    val releaseKeystore = System.getenv("LMELP_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("LMELP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LMELP_KEY_ALIAS")
                keyPassword = System.getenv("LMELP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // APK debug installable à côté de l'APK de release (issue #160) : signatures
        // différentes, donc identifiants différents. Le namespace (code, R, BuildConfig)
        // reste com.lmelp.mobile ; nom et icône debug dans src/debug/res.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Asset historique.txt de l'écran À propos (issue #170), généré pour chaque variante
androidComponents {
    onVariants { variant ->
        val task = tasks.register<GenerateHistoriqueTask>(
            "generate${variant.name.replaceFirstChar { it.uppercase() }}Historique"
        ) {
            historique.set(gitHistorique)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, GenerateHistoriqueTask::outputDir)
    }
}

// Kotlin intégré à AGP 9 (issue #158) : plus de plugin org.jetbrains.kotlin.android
// ni d'options Kotlin dans le bloc android : la cible JVM se règle ici.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
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
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.sqlite)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.car.app)
    implementation(libs.okhttp)
    implementation(libs.org.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
