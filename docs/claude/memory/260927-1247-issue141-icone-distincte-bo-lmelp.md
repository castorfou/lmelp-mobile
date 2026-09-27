# Icône de lancement distincte de BO LMELP — Issue #141

**Date** : 2026-09-27
**Branche** : `141-changer-licone-pour-differencier-de-lmelp-bo`
**Issue** : [#141 Changer l'icone pour differencier de lmelp-bo](https://github.com/castorfou/lmelp-mobile/issues/141)
**Issue liée (back-office)** : [back-office-lmelp#315](https://github.com/castorfou/back-office-lmelp/issues/315)

## Problème

Depuis l'issue #5 (voir `260306-2341-launcher-icon-change.md`), l'icône de l'app était une simple copie redimensionnée de `android-chrome-512x512.png` du back-office : fond rose, masque, plume et base de données. La PWA « BO LMELP », installée depuis Chrome, avait exactement la même icône, et on ne pouvait pas distinguer les deux sur l'écran d'accueil.

## Choix validés avec l'utilisateur

- **Fond bleu nuit** : dégradé vertical `#12192C` → `#1E2D4A`, les mêmes couleurs que le bandeau hero de l'accueil (`LmelpNightBlue` / `LmelpNightBlueEnd` dans `Theme.kt`).
- **Smartphone à la place de la base de données**, en bas à droite, derrière la plume. Le cadre est clair `#E3E8F0` pour ressortir sur le bleu nuit ; l'écran est en bleu Émissions `#1565C0`, avec des lignes de texte `#90CAF9`.
- **Icône adaptive** et PNG legacy, avec un calque `monochrome` pour les icônes thémées d'Android 13+.

## Implémentation

### Génération reproductible par script

- **Script** : `scripts/generate_launcher_icon.py`.
- **Source versionnée** : `scripts/icon/source_lmelp_green.png`. C'est une copie de `frontend/public/gimp_favicon/favicon.png` du repo back-office-lmelp : l'icône lmelp **verte**, en 1327×1328. On part de la verte plutôt que de la rose parce que son fond vert uni se détoure par colorimétrie ; le rose partage des teintes avec le masque et la base de données.
- **Commande** : `python scripts/generate_launcher_icon.py`, qui régénère toutes les ressources. Ne jamais éditer les PNG à la main.

Fonctions pures, testées dans `tests/test_launcher_icon.py` (29 tests) :

- **`extract_foreground`** :
  - alpha progressif selon la « verdeur » `G - max(R, B)`, entre les seuils 5 et 35, pour garder des bords anti-aliasés ;
  - despill : sur les pixels verdâtres, G est ramené à `max(R, B)` ;
  - **pelage itératif** (25 passes au plus, avec `scipy.ndimage.binary_dilation`) : les pixels teintés (vert résiduel, ou magenta `min(R,B) - G > 10`) qui touchent la transparence sont retirés de proche en proche. Cela supprime le liseré rose hérité de la version rouge et le flou vert foncé de l'ombre, tout en épargnant les yeux et la bouche bordeaux, enclos dans le masque jaune ;
  - seule la plus grande composante connexe est conservée (`ndimage.label`).
- **`draw_smartphone`** : dessine le téléphone dans le repère de la source (`_PHONE_BOX`).
- **`build_foreground`** : calcule le centre de la bounding box et le rayon maximal du motif, puis le met à l'échelle pour qu'il tienne dans la **safe zone de 66/108**. Le rendu se fait en 1080 px, puis est réduit en LANCZOS.
- **`build_background`** : produit le dégradé.
- **`build_monochrome`** : alpha seulement. Les détails dont la luminance est sous 110 (yeux, bouche, écran du téléphone) sont évidés pour rester lisibles une fois l'icône teintée.
- **`build_legacy_icon`** : prend la partie visible 72/108 de fond + premier plan, puis applique un masque carré arrondi ou un disque (pour `_round`).

### Ressources produites (`app/src/main/res/`)

| Ressource | Détail |
|-----------|--------|
| `mipmap-*dpi/ic_launcher.png`, `ic_launcher_round.png` | legacy, 48/72/96/144/192 px |
| `mipmap-*dpi/ic_launcher_foreground.png`, `ic_launcher_monochrome.png` | calques adaptive, 108/162/216/324/432 px |
| `drawable/ic_launcher_background.xml` | `<shape>` avec gradient `angle=270` |
| `mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml` | `<adaptive-icon>` : background, foreground, monochrome |

`AndroidManifest.xml` n'a pas changé.

## Pièges rencontrés

- **`mipmap-anydpi-v26` → `mipmap-anydpi`** : avec `minSdk = 26`, le lint signale le qualifieur `-v26` comme superflu (`ObsoleteSdkInt`). On utilise donc `mipmap-anydpi` ; aapt le compile en `mipmap-anydpi-v21`.
- **Build incrémental Gradle qui ignore un nouveau dossier de ressources** : le premier `installDebug` a produit un APK **sans** `mipmap-anydpi`. Le launcher Pixel a alors affiché l'icône legacy (un petit carré) posée dans un **disque blanc**, au lieu d'une icône plein cercle. Le diagnostic se fait avec `aapt2 dump badging app-debug.apk | grep application:` ; le résultat attendu est `icon='res/mipmap-anydpi-v21/ic_launcher.xml'`. La correction : `./gradlew clean assembleDebug`.
- **Icône legacy dans un disque blanc** : c'est le symptôme d'une icône non adaptive sur un launcher récent. La PWA du BO a le même défaut, parce que son manifest ne déclare pas de `"purpose": "maskable"`. C'est ajouté à back-office-lmelp#315.
- **mypy et scipy** : scipy ne fournit pas de stubs, et un override `ignore_missing_imports` dans `pyproject.toml` ne suffisait pas contre `import-untyped`. Solution retenue : `# type: ignore[import-untyped]` sur l'import. mypy ne tourne que sur `src/` dans la CI et le hook pre-commit, mais le script passe proprement.
- **`Image.getdata()` est déprécié** (retrait prévu dans Pillow 14) : les tests passent par `np.asarray(...)`.
- Pillow est disponible via matplotlib (déjà en dépendance, `uv.lock` 12.1.1) ; aucune nouvelle dépendance n'a été ajoutée.

## Volet back-office

Création de [back-office-lmelp#315](https://github.com/castorfou/back-office-lmelp/issues/315), qui demande :

- de renommer l'app « BO LMELP » en **lmelp**, puisque lmelp est décommissionné depuis back-office-lmelp#302 ;
- de changer l'icône, par exemple en reprenant la verte ;
- de fournir des icônes **maskable** (fond plein bord à bord, motif dans les 80 % centraux, `"purpose": "maskable"` dans `site.webmanifest`) pour remplir le cercle du launcher.

L'issue suggère de réutiliser `scripts/generate_launcher_icon.py`.

## Validation

- Tests : 120 passés et 5 ignorés (suite complète) ; `ruff` et `pre-commit` OK ; `mypy` OK sur le script.
- `./gradlew assembleDebug lintDebug` : aucun avertissement sur l'icône.
- Test sur device (Pixel) : l'utilisateur juge l'icône « parfaite ». Elle remplit le cercle et se distingue nettement de BO LMELP.
