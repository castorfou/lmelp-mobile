#!/usr/bin/env bash
# docker_export_and_publish_release.sh — Exporte lmelp.db et le publie en asset
# de la GitHub Release data-latest (issue #116).
#
# Ce script tourne DANS le container lmelp-export (voir Dockerfile.export),
# invoqué manuellement (docker exec lmelp-export export-and-publish-release)
# ou automatiquement via le job anacron embarqué dans l'image.
#
# Contrairement à docker_export_and_push.sh (mécanisme ADB legacy, USB +
# build debug), ce script ne dépend d'aucun accès au téléphone : il publie
# la base sur GitHub, l'app la récupérera elle-même en HTTP (issue de suivi
# lmelp-mobile#118).
#
# Variables d'environnement attendues :
#   LMELP_MONGO_URI                  — URI MongoDB (ex: mongodb://mongo:27017)
#   LMELP_CALIBRE_DB                 — chemin vers metadata.db dans le container
#   LMELP_CALIBRE_VIRTUAL_LIBRARY    — tag virtual library Calibre (ex: guillaume)
#   GH_TOKEN                         — token GitHub avec droit contents:write
#                                       sur castorfou/lmelp-mobile (voir
#                                       docker-lmelp#56 pour le provisioning)
#   GH_REPO                          — repo cible (défaut: castorfou/lmelp-mobile)
#   RELEASE_TAG                      — tag de la release à publier (défaut:
#                                       data-latest ; utile pour tester sur un
#                                       tag jetable sans toucher data-latest)

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
METADATA_OUTPUT="/tmp/metadata.json"
GH_REPO="${GH_REPO:-castorfou/lmelp-mobile}"
RELEASE_TAG="${RELEASE_TAG:-data-latest}"

echo -e "\n${BOLD}=== lmelp-mobile : Export et publication de la Release ${RELEASE_TAG} ===${NC}\n"

# ---------------------------------------------------------------------------
# 1. Vérifier les pré-requis
# ---------------------------------------------------------------------------
command -v gh >/dev/null 2>&1 || die "gh CLI introuvable dans le container"
[[ -n "${GH_TOKEN:-}" ]] || die "GH_TOKEN non configuré (voir docker-lmelp#56)"

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
# 3. Récupérer les métadonnées de la dernière release publiée (issue #128)
# ---------------------------------------------------------------------------
# Permet à export_mongo_to_sqlite.py de réutiliser `version` si le contenu
# exporté est identique à celui déjà publié, au lieu d'avancer un nouveau
# timestamp à chaque run anacron même sans nouvelle donnée.
PREVIOUS_METADATA="/tmp/previous_metadata.json"
PREVIOUS_ARGS=""
if gh release download "$RELEASE_TAG" --repo "$GH_REPO" \
        --pattern metadata.json --output "$PREVIOUS_METADATA" --clobber 2>/dev/null; then
    PREVIOUS_CONTENT_HASH=$(python3 -c "import json; print(json.load(open('$PREVIOUS_METADATA')).get('content_hash') or '')")
    PREVIOUS_VERSION=$(python3 -c "import json; print(json.load(open('$PREVIOUS_METADATA')).get('export_version') or '')")
    if [[ -n "$PREVIOUS_CONTENT_HASH" && -n "$PREVIOUS_VERSION" ]]; then
        info "Métadonnées précédentes récupérées (version=${PREVIOUS_VERSION})"
        PREVIOUS_ARGS="--previous-content-hash ${PREVIOUS_CONTENT_HASH} --previous-version ${PREVIOUS_VERSION}"
    fi
else
    info "Pas de release ${RELEASE_TAG} existante (premier export) ou metadata.json indisponible"
fi

# ---------------------------------------------------------------------------
# 4. Export MongoDB → SQLite
# ---------------------------------------------------------------------------
info "Export MongoDB → SQLite..."
MONGO_URI="${LMELP_MONGO_URI:-mongodb://mongo:27017}"

python /app/scripts/export_mongo_to_sqlite.py \
    --mongo-uri "$MONGO_URI" \
    --output "$DB_OUTPUT" \
    --force \
    $CALIBRE_ARGS \
    $PREVIOUS_ARGS

DB_SIZE=$(du -h "$DB_OUTPUT" | cut -f1)
success "Base générée : $DB_OUTPUT ($DB_SIZE)"

# ---------------------------------------------------------------------------
# 5. Vérification d'intégrité
# ---------------------------------------------------------------------------
info "Vérification de l'intégrité..."
python /app/scripts/export_mongo_to_sqlite.py --verify "$DB_OUTPUT"
success "Vérification OK"

# ---------------------------------------------------------------------------
# 6. Génération des métadonnées
# ---------------------------------------------------------------------------
info "Génération de metadata.json..."
python /app/scripts/generate_data_release_metadata.py \
    --db "$DB_OUTPUT" \
    --output "$METADATA_OUTPUT"
success "Métadonnées générées : $METADATA_OUTPUT"

# ---------------------------------------------------------------------------
# 7. Publication sur GitHub Release (skip si contenu inchangé, issue #128)
# ---------------------------------------------------------------------------
NEW_VERSION=$(python3 -c "import json; print(json.load(open('$METADATA_OUTPUT'))['export_version'])")

if [[ -n "${PREVIOUS_VERSION:-}" && "$NEW_VERSION" == "$PREVIOUS_VERSION" ]]; then
    info "Contenu inchangé depuis le dernier export (version=${NEW_VERSION}), publication ignorée"
else
    info "Publication sur ${GH_REPO} (release ${RELEASE_TAG})..."

    if ! gh release view "$RELEASE_TAG" --repo "$GH_REPO" >/dev/null 2>&1; then
        info "Release ${RELEASE_TAG} absente, création..."
        gh release create "$RELEASE_TAG" \
            --repo "$GH_REPO" \
            --title "lmelp-mobile — données" \
            --notes "Base de données lmelp, publiée automatiquement. Voir metadata.json pour la date d'export et le SHA-256."
    fi

    gh release upload "$RELEASE_TAG" "$DB_OUTPUT" "$METADATA_OUTPUT" \
        --repo "$GH_REPO" \
        --clobber

    success "=== Publication terminée avec succès ==="
fi
echo ""
