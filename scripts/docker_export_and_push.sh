#!/usr/bin/env bash
# docker_export_and_push.sh — Exporte lmelp.db depuis MongoDB et le pousse sur le device Android
#
# Ce script tourne DANS le container lmelp-export.
# Le daemon ADB tourne sur le laptop host, accessible via host-gateway.
#
# Variables d'environnement attendues (depuis docker-compose) :
#   LMELP_MONGO_URI                  — URI MongoDB (ex: mongodb://mongo:27017)
#   LMELP_CALIBRE_DB                 — chemin vers metadata.db dans le container (ex: /calibre/metadata.db)
#   LMELP_CALIBRE_VIRTUAL_LIBRARY    — tag virtual library Calibre (ex: guillaume)
#   ADB_HOST                         — host du daemon ADB (défaut: host-gateway)
#   ADB_PORT                         — port du daemon ADB (défaut: 5037)
#   APP_PACKAGE                      — package Android (défaut: com.lmelp.mobile)

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

DB_OUTPUT="/tmp/lmelp.db"
ADB_HOST="${ADB_HOST:-host-gateway}"
ADB_PORT="${ADB_PORT:-5037}"
APP_PACKAGE="${APP_PACKAGE:-com.lmelp.mobile}"
ADB="adb -H ${ADB_HOST} -P ${ADB_PORT}"

echo -e "\n${BOLD}=== lmelp-mobile : Export et mise à jour de la base ===${NC}\n"

# ---------------------------------------------------------------------------
# 1. Vérifier la connectivité ADB
# ---------------------------------------------------------------------------
info "Connexion au daemon ADB sur ${ADB_HOST}:${ADB_PORT}..."
if ! $ADB devices > /dev/null 2>&1; then
    die "Impossible de joindre le daemon ADB sur ${ADB_HOST}:${ADB_PORT}\nSur le laptop, lancez : adb start-server"
fi

DEVICE_COUNT=$($ADB devices 2>/dev/null | grep -v "^List" | grep -v "^$" | grep "device$" | wc -l)
if [[ "$DEVICE_COUNT" -eq 0 ]]; then
    die "Aucun device Android connecté au laptop.\nVérifiez avec : adb devices"
fi
success "$DEVICE_COUNT device(s) connecté(s)"

# ---------------------------------------------------------------------------
# 2. Vérifier Calibre (issue #42 : critique pour les données lu/rating)
# ---------------------------------------------------------------------------
CALIBRE_DB="${LMELP_CALIBRE_DB:-}"
if [[ -z "$CALIBRE_DB" ]] || [[ ! -f "$CALIBRE_DB" ]]; then
    warn "LMELP_CALIBRE_DB non configuré ou fichier absent : ${CALIBRE_DB:-<vide>}"
    warn "Les données Calibre (lu/rating) seront absentes → filtre 'Lus' vide dans l'app"
    CALIBRE_ARGS=""
else
    success "Calibre : $CALIBRE_DB"
    CALIBRE_ARGS="--calibre-db ${CALIBRE_DB}"
    if [[ -n "${LMELP_CALIBRE_VIRTUAL_LIBRARY:-}" ]]; then
        CALIBRE_ARGS="$CALIBRE_ARGS --calibre-virtual-library ${LMELP_CALIBRE_VIRTUAL_LIBRARY}"
    fi
fi

# ---------------------------------------------------------------------------
# 3. Export MongoDB → SQLite
# ---------------------------------------------------------------------------
info "Export MongoDB → SQLite..."
MONGO_URI="${LMELP_MONGO_URI:-mongodb://mongo:27017}"

python /app/scripts/export_mongo_to_sqlite.py \
    --mongo-uri "$MONGO_URI" \
    --output "$DB_OUTPUT" \
    --force \
    $CALIBRE_ARGS

DB_SIZE=$(du -h "$DB_OUTPUT" | cut -f1)
success "Base générée : $DB_OUTPUT ($DB_SIZE)"

# ---------------------------------------------------------------------------
# 4. Vérification d'intégrité
# ---------------------------------------------------------------------------
info "Vérification de l'intégrité..."
python /app/scripts/export_mongo_to_sqlite.py --verify "$DB_OUTPUT"
success "Vérification OK"

# ---------------------------------------------------------------------------
# 5. Push sur le device via ADB
# ---------------------------------------------------------------------------
info "Push de la base vers le device..."
TMP_REMOTE="/data/local/tmp/lmelp_update_$$.db"

$ADB push "$DB_OUTPUT" "$TMP_REMOTE"

# Vérifier si l'app est installée et en mode debug (run-as disponible)
APP_INSTALLED=$($ADB shell pm list packages 2>/dev/null | grep "^package:${APP_PACKAGE}$" || true)
if [[ -z "$APP_INSTALLED" ]]; then
    $ADB shell rm -f "$TMP_REMOTE" || true
    die "L'app ${APP_PACKAGE} n'est pas installée sur le device."
fi

IS_DEBUGGABLE=$($ADB shell dumpsys package "$APP_PACKAGE" 2>/dev/null | grep "pkgFlags" | grep -c "DEBUGGABLE" || true)
if [[ "$IS_DEBUGGABLE" -eq 0 ]]; then
    $ADB shell rm -f "$TMP_REMOTE" || true
    die "L'app installée n'est pas en mode debug — run-as non disponible.\nInstallez le build debug : scripts/build.sh && scripts/deploy.sh"
fi

info "Copie dans le répertoire privé de l'app..."
$ADB shell run-as "$APP_PACKAGE" cp "$TMP_REMOTE" databases/lmelp.db

info "Nettoyage du fichier temporaire..."
$ADB shell rm -f "$TMP_REMOTE"

# ---------------------------------------------------------------------------
# 6. Redémarrage de l'app
# ---------------------------------------------------------------------------
info "Redémarrage de l'app..."
$ADB shell am force-stop "$APP_PACKAGE"
sleep 1
$ADB shell am start -n "${APP_PACKAGE}/.MainActivity" || true

echo ""
success "=== Mise à jour terminée avec succès ==="
echo ""
