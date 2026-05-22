# Issue #110 — Partage livre (bouton share sur fiche livre)

## Résumé

Ajout d'un bouton de partage sur la page de détail d'un livre, permettant de partager l'URL Babelio via le menu Android natif (SMS, WhatsApp, email...).

## Fichiers modifiés

- `app/src/main/java/com/lmelp/mobile/ui/emissions/LivreDetailScreen.kt` — ajout du bouton partage
- `app/src/test/java/com/lmelp/mobile/LivreDetailShareTest.kt` — tests unitaires (nouveau fichier)

## Implémentation

### Layout final

Dans `LivreDetailContent()`, la colonne de droite du bandeau header contient une `Row` qui regroupe l'icône de partage et le `NoteBadge` côte à côte (centrés verticalement), avec `CalibreBadge` en dessous :

```
Column(horizontalAlignment = End)
  ├── Row(verticalAlignment = CenterVertically)
  │     ├── IconButton(Share)   ← visible si urlBabelio != null
  │     └── NoteBadge(note)     ← visible si noteMoyenne != null
  └── CalibreBadge
```

### Intent de partage

```kotlin
val sendIntent = Intent(Intent.ACTION_SEND).apply {
    putExtra(Intent.EXTRA_TEXT, url)
    type = "text/plain"
}
context.startActivity(Intent.createChooser(sendIntent, null))
```

`context` obtenu via `LocalContext.current` en haut de `LivreDetailContent()`.

### Couleur de l'icône

Utiliser `tint = MaterialTheme.colorScheme.onSurface` (noir/blanc selon thème).
**Ne pas utiliser** `colorScheme.primary` qui est rouge dans ce thème.

### Données

`urlBabelio` était déjà présent dans `LivreAvecCalibreRow` et `LivreDetailUi` — aucun changement data layer nécessaire.

## Tests

`app/src/test/java/com/lmelp/mobile/LivreDetailShareTest.kt` — 2 tests unitaires :
- `urlBabelio` propagée correctement depuis `LivreAvecCalibreRow` vers `LivreDetailUi`
- `urlBabelio` null quand absent

## Leçons apprises

### Gradle cache bloquant la recompilation

Symptôme : `build.sh` affiche "38 actionable tasks: 38 up-to-date" alors que des fichiers sources ont changé.
Solution : forcer avec `--rerun-tasks` sur la tâche de compilation :
```bash
./gradlew :app:compileDebugKotlin --rerun-tasks
./gradlew assembleDebug
```

### ADB vide après mise à jour Android

Symptôme : `adb devices` vide, téléphone visible dans `lsusb` avec `18d1:4ee1`.
Mauvaise piste : règle udev (inutile dans ce cas).
Solution réelle : **rebooter le téléphone** — la mise à jour Android l'avait laissé dans un état USB intermédiaire.
Leçon : toujours essayer le reboot du téléphone en premier avant de chercher des causes système complexes.

### Préférences USB Android réinitialisées après OTA

Une mise à jour Android peut remettre les préférences USB à "Aucun transfert de données". Si "Appareil connecté" échoue avec "Échec du changement", c'est un symptôme d'état intermédiaire post-OTA — le reboot résout.
