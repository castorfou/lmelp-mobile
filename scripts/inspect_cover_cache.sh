#!/usr/bin/env bash
# inspect_cover_cache.sh — Inspecte l'état du cache de couvertures sur le device Android
#
# Usage: bash scripts/inspect_cover_cache.sh [PACKAGE]
# Défaut PACKAGE: com.lmelp.mobile
#
# Ce script affiche :
#   1. Le contenu du SharedPreferences home_cache (clés couverture_ → URLs d'images)
#   2. Les fichiers présents dans le disk cache Coil (getExternalFilesDir/coil_image_cache)
#   3. Les logs Coil récents (adb logcat)

PACKAGE="${1:-com.lmelp.mobile}"

echo "=============================================="
echo "  Cache couvertures — $PACKAGE"
echo "=============================================="

# ── 1. SharedPreferences (URLs Babelio → URL image) ─────────────────────────
echo ""
echo "── 1. SharedPreferences home_cache ──────────────────────────"
PREFS_PATH="/data/data/$PACKAGE/shared_prefs/home_cache.xml"
PREFS_CONTENT=$(adb shell "run-as $PACKAGE cat $PREFS_PATH 2>/dev/null || echo 'FICHIER_ABSENT'")

if echo "$PREFS_CONTENT" | grep -q "FICHIER_ABSENT"; then
    echo "  [ABSENT] $PREFS_PATH"
    echo "  → Aucune URL de couverture en cache (SharedPreferences vide ou app jamais lancée)"
else
    COUNT=$(echo "$PREFS_CONTENT" | grep -c 'couverture_' || true)
    echo "  Entrées couverture_ : $COUNT"
    echo ""
    # Extraire et afficher les clés et valeurs
    echo "$PREFS_CONTENT" | grep -oP 'name="couverture_[^"]*"[^>]*>[^<]*' | while read -r line; do
        KEY=$(echo "$line" | grep -oP 'name="\K[^"]+')
        VAL=$(echo "$line" | grep -oP '>\K.*')
        if [ -n "$VAL" ]; then
            echo "  ✓ $KEY → $VAL"
        else
            echo "  ✗ $KEY → (vide — Babelio n'a pas retourné d'image)"
        fi
    done
fi

# ── 1b. Fichier JSON couvertures_cache.json (persistant, getExternalFilesDir) ────
echo ""
echo "── 1b. Cache JSON URLs couvertures (getExternalFilesDir) ────"
JSON_CACHE="/sdcard/Android/data/$PACKAGE/files/couvertures_cache.json"
JSON_CONTENT=$(adb shell "cat $JSON_CACHE 2>/dev/null || echo 'FICHIER_ABSENT'")
if echo "$JSON_CONTENT" | grep -q "FICHIER_ABSENT"; then
    echo "  [ABSENT] $JSON_CACHE"
    echo "  → Cache URL persistant vide (première installation ou cache effacé)"
else
    echo "$JSON_CONTENT" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    total = len(data)
    ok    = sum(1 for v in data.values() if v)
    print(f'  Total: {total} | Avec image: {ok} | Vides (pas de couverture Babelio): {total-ok}')
    print()
    for k, v in list(data.items())[:15]:
        status = '✓' if v else '✗'
        label  = v[:60] if v else '(aucune image Babelio trouvée)'
        print(f'  {status} {k[:50]} → {label}')
    if total > 15:
        print(f'  ... ({total-15} entrées supplémentaires)')
except: print('  (JSON invalide)')
" 2>/dev/null || echo "  $JSON_CONTENT"
fi

# ── 2. Disk cache Coil dans getExternalFilesDir (persistant) ────────────────
echo ""
echo "── 2. Disk cache Coil (getExternalFilesDir/coil_image_cache) ─"
FILES_CACHE="/sdcard/Android/data/$PACKAGE/files/coil_image_cache"
FILES_LIST=$(adb shell "run-as $PACKAGE ls -lh $FILES_CACHE 2>/dev/null || echo 'DOSSIER_ABSENT'")

if echo "$FILES_LIST" | grep -q "DOSSIER_ABSENT"; then
    echo "  [ABSENT] $FILES_CACHE"
    echo "  → Cache persistant vide (nouvelles images pas encore chargées)"
else
    FILE_COUNT=$(adb shell "run-as $PACKAGE ls $FILES_CACHE 2>/dev/null | wc -l")
    TOTAL_SIZE=$(adb shell "run-as $PACKAGE du -sh $FILES_CACHE 2>/dev/null | cut -f1")
    echo "  Fichiers : $FILE_COUNT   Taille totale : $TOTAL_SIZE"
    echo ""
    adb shell "run-as $PACKAGE ls -lh $FILES_CACHE 2>/dev/null" | head -20
fi

# ── 3. Journal adb logcat (dernières entrées Coil) ───────────────────────────
echo ""
echo "── 3. Logs Coil récents (dernières 20 lignes) ───────────────"
adb logcat -d -s "Coil" 2>/dev/null | tail -20 || echo "  (aucun log Coil disponible)"

echo ""
echo "=============================================="
echo "Pour vider le cache JSON persistant (URLs Babelio) :"
echo "  adb shell rm /sdcard/Android/data/$PACKAGE/files/couvertures_cache.json"
echo "Pour vider le disk cache Coil persistant (images) :"
echo "  adb shell rm -rf /sdcard/Android/data/$PACKAGE/files/coil_image_cache"
echo "Pour tout vider d'un coup :"
echo "  adb shell rm -rf /sdcard/Android/data/$PACKAGE/files/"
echo "=============================================="
