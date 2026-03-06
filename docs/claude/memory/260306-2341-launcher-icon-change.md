# Changement de l'icône de lancement — Issue #5

**Date** : 2026-03-06
**Branche** : `5-changer-licone-de-lancement`
**Issue** : [#5 changer l'icone de lancement](https://github.com/castorfou/lmelp-mobile/issues/5)

## Résumé

Remplacement de l'icône de lancement générique (carré rouge) par l'icône officielle de Le Masque et la Plume (masque de théâtre + plume + base de données).

## Source de l'icône

- **Repo source** : [back-office-lmelp/frontend/public](https://github.com/castorfou/back-office-lmelp/tree/main/frontend/public)
- **Fichier de référence** : `android-chrome-512x512.png` (512×512 RGBA PNG)
- L'icône représente : masque de théâtre doré, plume blanche, cylindre de base de données rouge, fond rose/rouge

## Méthode de génération

Redimensionnement via Python/Pillow depuis la source 512×512 vers toutes les densités Android :

| Dossier | Taille |
|---------|--------|
| `app/src/main/res/mipmap-mdpi/` | 48×48 |
| `app/src/main/res/mipmap-hdpi/` | 72×72 |
| `app/src/main/res/mipmap-xhdpi/` | 96×96 |
| `app/src/main/res/mipmap-xxhdpi/` | 144×144 |
| `app/src/main/res/mipmap-xxxhdpi/` | 192×192 |

Fichiers générés dans chaque dossier : `ic_launcher.png` et `ic_launcher_round.png`

## Aucune modification de code

- `AndroidManifest.xml` référençait déjà `@mipmap/ic_launcher` et `@mipmap/ic_launcher_round`
- Modification purement de ressources (PNG), sans logique métier

## Script de régénération

```python
from PIL import Image
import os

src = "/tmp/android-chrome-512x512.png"  # Télécharger depuis back-office-lmelp
base_dir = "app/src/main/res"

sizes = {
    "mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192,
}
img = Image.open(src)
for density, size in sizes.items():
    resized = img.resize((size, size), Image.LANCZOS)
    for name in ["ic_launcher.png", "ic_launcher_round.png"]:
        resized.save(os.path.join(base_dir, density, name), "PNG")
```

## Notes d'environnement

- Les tests Gradle (`./gradlew test`) nécessitent `JAVA_HOME` configuré — non disponible dans cet environnement de dev
- À automatiser : setup de l'environnement Java pour les builds locaux
