#!/usr/bin/env bash
# docker_publish_loop.sh — Boucle de publication périodique de lmelp.db (issue #153).
#
# Lancé en arrière-plan par l'entrypoint du container lmelp-export (voir
# Dockerfile.export), remplace l'ancien job anacron quotidien (issue #116) :
# anacron ne descend pas sous une période d'un jour.
#
# À chaque cycle : export-and-publish-release (qui ne publie que si le contenu
# a changé, issue #128, et notifie ntfy dans ce cas), puis notify_ntfy.py
# status, qui notifie le premier échec et le retour à la normale.
#
# Variables d'environnement :
#   PUBLISH_INTERVAL — secondes entre deux runs (défaut : 3600) ; 0 = boucle
#                      désactivée (publication manuelle uniquement, via
#                      docker exec lmelp-export export-and-publish-release)
#   NTFY_*           — voir scripts/notify_ntfy.py

set -uo pipefail

INTERVAL="${PUBLISH_INTERVAL:-3600}"
RUN_LOG="/tmp/publish-data-release.run.log"
LOG_FILE="/var/log/publish-data-release.log"

if [[ "$INTERVAL" == "0" ]]; then
    echo "[INFO] PUBLISH_INTERVAL=0 : publication périodique désactivée"
    exit 0
fi

while true; do
    if export-and-publish-release > "$RUN_LOG" 2>&1; then
        status=ok
    else
        status=failed
    fi
    { echo "=== $(date -Is) : ${status} ==="; cat "$RUN_LOG"; } >> "$LOG_FILE"
    python3 /app/scripts/notify_ntfy.py status --result "$status" --log "$RUN_LOG" || true
    sleep "$INTERVAL"
done
