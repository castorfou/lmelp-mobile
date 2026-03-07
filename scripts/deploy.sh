#!/usr/bin/env bash
# deploy.sh — Déploie l'APK debug de lmelp-mobile sur un device Android connecté
# Peut être appelé depuis n'importe où dans l'arborescence du projet
# Si INSTALL_FAILED_UPDATE_INCOMPATIBLE (signatures différentes), désinstalle automatiquement avant de réinstaller

set -euo pipefail

APP_PACKAGE="com.lmelp.mobile"

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
ADB="${ANDROID_HOME:-/home/vscode/android-sdk}/platform-tools/adb"

APK_PATH="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"

# Vérifier que l'APK existe
if [[ ! -f "$APK_PATH" ]]; then
    echo "APK introuvable : $APK_PATH"
    echo "Lance d'abord scripts/build.sh pour builder l'APK."
    exit 1
fi

# Vérifier qu'un device est connecté
DEVICES=$("$ADB" devices | grep -v "^List" | grep -v "^$" | grep "device$" | wc -l)
if [[ "$DEVICES" -eq 0 ]]; then
    echo "Aucun device Android connecté."
    echo "Vérifie avec : $ADB devices"
    exit 1
fi

echo "Device(s) connecté(s) : $DEVICES"
echo "APK : $APK_PATH"
echo ""

# Vérifier si l'app est déjà installée et si les signatures sont incompatibles
APP_INSTALLED=$("$ADB" shell pm list packages 2>/dev/null | grep "^package:$APP_PACKAGE$" || true)

if [[ -n "$APP_INSTALLED" ]]; then
    echo "L'app $APP_PACKAGE est déjà installée."
    echo "Tentative d'installation (mise à jour)..."

    INSTALL_OUTPUT=$("$ADB" install -r "$APK_PATH" 2>&1 || true)

    if echo "$INSTALL_OUTPUT" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
        echo ""
        echo "Signatures incompatibles détectées — désinstallation de l'ancienne version..."
        "$ADB" uninstall "$APP_PACKAGE"
        echo "Ancienne version désinstallée. Installation de la nouvelle version..."
        "$ADB" install "$APK_PATH"
    elif echo "$INSTALL_OUTPUT" | grep -q "Success"; then
        echo "Mise à jour réussie."
    else
        echo "Erreur lors de l'installation :" >&2
        echo "$INSTALL_OUTPUT" >&2
        exit 1
    fi
else
    echo "Première installation de $APP_PACKAGE..."
    "$ADB" install "$APK_PATH"
fi

echo ""
echo "Déploiement terminé."
