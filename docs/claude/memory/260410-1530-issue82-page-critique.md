# Issue #82 — Page de détail d'un critique

## Résumé
Ajout d'une page de détail pour chaque critique (journaliste/chroniqueur), accessible depuis 3 points d'entrée : liste des critiques, fiche livre (avis), recherche.

## Fichiers modifiés

### Nouveaux fichiers
- `app/src/main/java/com/lmelp/mobile/ui/critiques/CritiqueDetailScreen.kt` — écran complet avec en-tête, histogramme vertical des notes, liste des coups de cœur
- `app/src/main/java/com/lmelp/mobile/viewmodel/CritiqueDetailViewModel.kt` — ViewModel + Factory standard
- `app/src/test/java/com/lmelp/mobile/CritiquesRepositoryTest.kt` — 7 tests unitaires TDD

### Fichiers modifiés
- `app/src/main/java/com/lmelp/mobile/data/db/CritiquesDao.kt` — ajout `AvisParCritiqueRow` (data class) + query `getAvisByCritique(critiqueId)`
- `app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt` — ajout `AvisParCritiqueUi`, `CritiqueDetailUi`, champ `critiqueId: String?` dans `AvisUi`
- `app/src/main/java/com/lmelp/mobile/data/repository/CritiquesRepository.kt` — ajout `getCritiqueDetail()` : calcule note moyenne, distribution (Map<Int,Int>), coups de cœur (notes ≥ 9)
- `app/src/main/java/com/lmelp/mobile/data/repository/LivresRepository.kt` — propagation de `critiqueId` dans `AvisUi` (depuis `AvisEntity.critiqueId` déjà présent)
- `app/src/main/java/com/lmelp/mobile/ui/critiques/CritiquesScreen.kt` — cartes cliquables, paramètre `onCritiqueClick: (String) -> Unit`
- `app/src/main/java/com/lmelp/mobile/ui/emissions/LivreDetailScreen.kt` — `AvisCard` : nom du critique affiché en couleur primaire + cliquable si `critiqueId != null`; paramètre `onCritiqueClick` propagé jusqu'à `LivreDetailContent`
- `app/src/main/java/com/lmelp/mobile/Navigation.kt` — route `Routes.CRITIQUE_DETAIL = "critique/{critiqueId}"`, helper `critiqueDetail()`, composable, câblage depuis LivreDetail et Search (`"critique"` → `critiqueDetail`)

## Points saillants

### Histogramme vertical des notes
- Barres **verticales** (axe X = notes 1→10 croissant, axe Y = count), alignées en bas
- Hauteur de chaque barre = `chartHeight * (count / maxCount)` — hauteur fixe 120.dp
- Couleurs : rouge (≤4), jaune-vert (5-6), vert clair (7-8), vert foncé (9-10)
- Notes sans avis : barre invisible (Color.Transparent), étiquette X quand même affichée
- Count affiché au-dessus de chaque barre si > 0

### Coups de cœur
- Seuil : `note >= 9.0`
- Triés par note décroissante (le query SQL trie déjà par note DESC)
- Cartes cliquables → navigation vers `LivreDetailScreen`

### Navigation
- 3 points d'entrée câblés : liste critiques, fiche livre (AvisCard), recherche (type `"critique"`)
- Pattern identique aux autres détails (auteur, livre) : route paramétrique + composable dans `LmelpNavHost`

### Modèle de données
- `AvisParCritiqueRow` : data class Room dans `CritiquesDao.kt` (pas dans Entities), colonnes aliasées avec `@ColumnInfo(name=...)`
- `critiqueId` ajouté à `AvisUi` sans breaking change (nullable, valeur toujours présente en pratique car FK non null dans AvisEntity)

## Tests
7 tests dans `CritiquesRepositoryTest` couvrant :
- retour null si critique inexistant
- infos de base (id, nom, nbAvis)
- calcul note moyenne
- note moyenne null si aucun avis
- distribution des notes (groupBy + count)
- coups de cœur filtrés (notes ≥ 9) et triés
- flag animateur mappé correctement
