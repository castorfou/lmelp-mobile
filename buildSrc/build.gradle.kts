// Logique de build partagée, testable en JUnit (issue #161 : version de l'APK depuis le tag).
// Gradle 8 ne lance plus les tests de buildSrc à chaque build : la CI les lance
// explicitement avec `./gradlew -p buildSrc test`.
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
