---
name: issue53-fiche-livre-couverture
description: Ajout de la couverture dans la fiche livre (bandeau + plein écran)
type: project
---

# Issue #53 — Couverture dans la fiche livre

## Ce qui a été fait

### Propagation de url_cover jusqu'à l'UI

`LivreEntity` avait déjà `urlCover: String?` mais ce champ n'était pas exposé à l'UI.

**Fichiers modifiés :**

1. `app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt`
   - Ajout de `urlCover: String?` dans `LivreDetailUi`

2. `app/src/main/java/com/lmelp/mobile/data/repository/LivresRepository.kt`
   - Mapping de `livre.urlCover` dans le `return LivreDetailUi(...)` de `getLivreDetail()`

3. `app/src/main/java/com/lmelp/mobile/ui/emissions/LivreDetailScreen.kt`
   - Bandeau : `AsyncImage` (80dp×140dp, `RoundedCornerShape(8.dp)`, `ContentScale.Crop`) à gauche du Row
   - Tap sur la miniature → Dialog plein écran fond noir (`usePlatformDefaultWidth = false`)
   - Tap n'importe où sur le Dialog → fermeture
   - Cache Coil automatique, partagé avec HomeScreen (même URL = même fichier disque)

4. `app/src/test/java/com/lmelp/mobile/LivresRepositoryTest.kt`
   - 2 tests RED→GREEN : `urlCover est propagee depuis LivreEntity` et `urlCover est null si LivreEntity na pas de cover`

### Layout bandeau fiche livre

```
┌─────────────────────────────────────────────┐  height=140dp
│ [cover 80×∞] │ Auteur (cliquable si auteurId)│
│              │ Éditeur                       │
│              │                      [note]   │
└─────────────────────────────────────────────┘
```

### Dialog plein écran

```kotlin
Dialog(
    onDismissRequest = { showCoverFullscreen = false },
    properties = DialogProperties(usePlatformDefaultWidth = false)
) {
    Box(Modifier.fillMaxSize().background(Color.Black).clickable { dismiss }) {
        AsyncImage(model = url, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}
```

**Why:** `usePlatformDefaultWidth = false` est nécessaire pour que le Dialog occupe tout l'écran sur Android. Sans ça, le Dialog garde sa largeur par défaut Material3.

**How to apply:** Utiliser ce pattern chaque fois qu'on veut un Dialog plein écran dans l'app.
