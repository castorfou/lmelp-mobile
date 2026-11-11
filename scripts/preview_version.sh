#!/usr/bin/env bash
# Numéro de la préversion de l'appli (issue #173), lancé par .github/workflows/app-preview.yml.
#
# Affiche X.Y.Z+N : X.Y.Z = dernière version stable vX.Y.Z (sans tiret, donc pas
# v0.0.0-test), N = nombre de commits depuis. N'affiche rien si N = 0 (commit tagué :
# doublon de la version) ou si aucune version n'existe. Exige l'historique complet et
# les tags (checkout en fetch-depth: 0, même piège que #170).
set -euo pipefail

describe=$(git describe --tags --long --match 'v[0-9]*.[0-9]*.[0-9]*' --exclude '*-*' 2>/dev/null) || exit 0

# v1.2.0-3-gabc1234 → 1.2.0 et 3
if [[ "$describe" =~ ^v([0-9]+\.[0-9]+\.[0-9]+)-([0-9]+)-g[0-9a-f]+$ ]]; then
    version="${BASH_REMATCH[1]}"
    commits="${BASH_REMATCH[2]}"
else
    echo "preview_version.sh : sortie de git describe inattendue : $describe" >&2
    exit 1
fi

if [ "$commits" -gt 0 ]; then
    echo "${version}+${commits}"
fi
