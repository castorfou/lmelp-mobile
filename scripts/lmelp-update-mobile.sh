#!/usr/bin/env bash
# lmelp-update-mobile.sh — Met à jour la base lmelp sur le téléphone Android
#
# Usage : copier ce script sur le laptop et l'invoquer à la demande
#   chmod +x lmelp-update-mobile.sh
#   ./lmelp-update-mobile.sh
#
# Pré-requis sur le laptop :
#   - adb installé
#   - Docker installé, stack docker-lmelp démarrée (container lmelp-export actif)
#   - Téléphone branché en USB, mode Transfert de fichiers, débogage USB activé

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m'

info()    { echo -e "${BLUE}[INFO]${NC} $*"; }
success() { echo -e "${GREEN}[OK]${NC}   $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
die()     { echo -e "${RED}[ERR]${NC}  $*" >&2; exit 1; }

echo -e "\n${BOLD}=== lmelp-mobile : Mise à jour de la base ===${NC}\n"

# --- Vérifier adb ---
if ! command -v adb &>/dev/null; then
    die "adb non trouvé. Installez android-tools : sudo apt install android-tools-adb"
fi

# --- Démarrer le daemon ADB en mode réseau (0.0.0.0) ---
info "Démarrage du daemon ADB sur 0.0.0.0:5037..."
adb kill-server 2>/dev/null || true
adb -a start-server 2>/dev/null || true
success "Daemon ADB démarré"

# --- Vérifier qu'un device est connecté ---
info "Recherche d'un device Android..."
ADB_OUTPUT=$(adb devices 2>/dev/null)
echo "$ADB_OUTPUT"
DEVICE_COUNT=$(echo "$ADB_OUTPUT" | grep -v "^List" | grep -v "^$" | grep "device$" | wc -l || true)
if [[ "$DEVICE_COUNT" -eq 0 ]]; then
    die "Aucun device Android connecté.\nVérifiez :\n  - câble USB branché\n  - mode USB = Transfert de fichiers (pas Aucun transfert)\n  - Débogage USB activé (Options développeur)\n  - Accepter la popup d'autorisation sur le téléphone"
fi
success "$DEVICE_COUNT device(s) connecté(s)"

# --- Vérifier que le container lmelp-export tourne ---
if ! docker inspect lmelp-export &>/dev/null; then
    die "Le container lmelp-export n'existe pas.\nDémarrez la stack docker-lmelp : docker compose up -d"
fi
if [[ "$(docker inspect -f '{{.State.Running}}' lmelp-export)" != "true" ]]; then
    die "Le container lmelp-export n'est pas démarré.\nDémarrez la stack docker-lmelp : docker compose up -d"
fi
success "Container lmelp-export actif"

# --- Lancer l'export et le push ---
info "Lancement de l'export et du push..."
docker exec lmelp-export export-and-push
