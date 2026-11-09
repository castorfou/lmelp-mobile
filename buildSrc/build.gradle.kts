// Logique de build partagée, testable en JUnit (issue #161 : version de l'APK depuis le tag).
// Depuis Gradle 8, les tests de buildSrc ne sont plus lancés à chaque build : la CI les lance
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
