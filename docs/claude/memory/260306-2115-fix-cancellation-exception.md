# Mémoire : Fix CancellationException dans les ViewModels (Issue #3)

## Contexte du projet

Application Android **lmelp-mobile** — offline-first, Kotlin + Jetpack Compose + Room + MVVM.

## Bug corrigé

**Issue #3** : "StandaloneCoroutine was cancelled" affiché dans l'écran Recherche lors de la frappe rapide.

**Cause** : Le `SearchViewModel` utilise un debounce (300ms) qui annule la coroutine précédente via `searchJob?.cancel()`. L'annulation lève une `CancellationException`. Le `catch (e: Exception)` générique l'attrapait et la mettait dans `uiState.error` → visible dans l'UI.

**Anti-pattern** : `CancellationException` est une sous-classe de `Exception`. Ne JAMAIS la swallower — toujours la re-thrower.

## Fix appliqué

Dans chaque `catch (e: Exception)` de tous les ViewModels :

```kotlin
} catch (e: Exception) {
    if (e is CancellationException) throw e  // Convention Kotlin coroutines
    _uiState.update { it.copy(isLoading = false, error = e.message) }
}
```

Import ajouté : `import kotlinx.coroutines.CancellationException`

**Fichiers modifiés** (6 ViewModels) :
- `app/src/main/java/com/lmelp/mobile/viewmodel/SearchViewModel.kt`
- `app/src/main/java/com/lmelp/mobile/viewmodel/EmissionsViewModel.kt` (2 occurrences)
- `app/src/main/java/com/lmelp/mobile/viewmodel/PalmaresViewModel.kt`
- `app/src/main/java/com/lmelp/mobile/viewmodel/CritiquesViewModel.kt`
- `app/src/main/java/com/lmelp/mobile/viewmodel/RecommendationsViewModel.kt`
- `app/src/main/java/com/lmelp/mobile/viewmodel/LivreDetailViewModel.kt`

## Tests TDD

**Fichier** : `app/src/test/java/com/lmelp/mobile/SearchViewModelTest.kt`

Approche : mock du DAO qui `thenThrow(CancellationException(...))` → vérifie que `uiState.error` ne contient pas "cancelled".

**Dépendances ajoutées** dans `gradle/libs.versions.toml` et `app/build.gradle.kts` :
- `kotlinx-coroutines-test = "1.8.1"`
- `mockito-kotlin = "5.4.0"`

## Configuration environnement Android (devcontainer)

Java et SDK Android ne sont pas installés dans le devcontainer par défaut. Commandes manuelles validées :

```bash
# Java 17
sudo apt-get install -y openjdk-17-jdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Android SDK (cmdline-tools)
mkdir -p ~/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip
unzip /tmp/cmdline-tools.zip -d ~/android-sdk/cmdline-tools/
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest
export ANDROID_HOME=/home/vscode/android-sdk

# Licences + composants
printf 'y\ny\ny\ny\ny\ny\ny\n' | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
~/android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0"
```

**Build / Tests** :
```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/vscode/android-sdk ./gradlew assembleDebug
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/vscode/android-sdk ./gradlew :app:testDebugUnitTest
```

**Deploy sur téléphone via ADB** :
```bash
~/android-sdk/platform-tools/adb devices
~/android-sdk/platform-tools/adb install app/build/outputs/apk/debug/app-debug.apk
# Si erreur INSTALL_FAILED_UPDATE_INCOMPATIBLE :
~/android-sdk/platform-tools/adb uninstall com.lmelp.mobile
```

**TODO** : Intégrer l'installation Java + SDK Android dans `.devcontainer/postCreateCommand.sh`.

Documentation créée : `docs/dev/setup-mobile.md`, `docs/dev/build_deploy_apk.md`.

## Branche de travail

`3-bug-erreur-standalonecoroutine-was-cancelled-lors-de-la-recherche`

Commits principaux : `8bdbf53` (fix bug) + commits précédents du scaffold Android (bbab983 → 0d45095).
