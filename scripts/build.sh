#!/usr/bin/env bash
# build.sh — Build l'APK debug de lmelp-mobile
# Peut être appelé depuis n'importe où dans l'arborescence du projet

set -euo pipefail

# Trouver la racine du projet (répertoire contenant gradlew)
find_project_root() {
    local dir="$PWD"
    while [[ "$dir" != "/" ]]; do
        if [[ -f "$dir/gradlew" ]]; then
            echo "$dir"
            return 0
        fi
        dir="$(dirname "$dir")"
    done
    echo "Erreur : impossible de trouver la racine du projet (gradlew introuvable)" >&2
    return 1
}

PROJECT_ROOT="$(find_project_root)"
echo "Racine du projet : $PROJECT_ROOT"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-/home/vscode/android-sdk}"

echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ANDROID_HOME"
echo ""
echo "Build de l'APK debug..."

cd "$PROJECT_ROOT"
./gradlew assembleDebug

APK_PATH="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [[ -f "$APK_PATH" ]]; then
    echo ""
    echo "APK généré : $APK_PATH"
else
    echo "Erreur : APK non trouvé à $APK_PATH" >&2
    exit 1
fi
