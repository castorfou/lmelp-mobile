# Issue #93 — Vitesse de lecture dans Mon Palmarès + fiche livre

## Fonctionnalité ajoutée

Ajout d'un 3e mode de tri "Vitesse lecture" dans **Mon Palmarès**, et affichage du nombre de jours de lecture dans la **fiche livre**.

---

## Logique de calcul

- Tous les livres lus (Masque + hors Masque) sont triés chronologiquement par `date_lecture`
- Pour le livre N : `joursLecture = date_N - date_(N-1)` en jours (via `java.time.LocalDate` + `ChronoUnit.DAYS`)
- Le 1er livre chronologique est **exclu** (pas de référence précédente → `null`)
- Les livres sans `date_lecture` sont exclus du calcul et de l'affichage vitesse
- Le filtre "Hors Masque" s'applique à l'**affichage uniquement** — le calcul utilise toujours tous les livres

---

## Fichiers modifiés

### `app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt`
- `MonPalmaresItemUi` : nouveau champ `joursLecture: Int? = null`
- `LivreDetailUi` : nouveau champ `joursLecture: Int? = null`

### `app/src/main/java/com/lmelp/mobile/viewmodel/PalmaresViewModel.kt`
- `MonPalmaresTriMode` étendu : `VITESSE_ASC`, `VITESSE_DESC`
- `setMonPalmaresTriMode()` : logique de bascule — si VITESSE_ASC déjà actif → VITESSE_DESC, et vice-versa
- `loadPalmares()` : deux nouveaux cas dans le `when`

### `app/src/main/java/com/lmelp/mobile/data/repository/PalmaresRepository.kt`
- `getMonPalmaresUnifieParVitesse(ascendant: Boolean)` : fusionne Masque + hors Masque, calcule les jours, trie
- `getJoursLecturePourLivre(livreId: String): Int?` : retourne les jours pour un livre spécifique (pour la fiche livre)
- `calculerVitesse()` (private) : logique centrale de calcul
- `calculerJoursEntre()` (private) : `java.time.LocalDate.parse()` sans formatter (ISO par défaut)

### `app/src/main/java/com/lmelp/mobile/ui/palmares/PalmaresScreen.kt`
- Nouveau `FilterChip` "Vitesse ↓" / "Vitesse ↑" (label dynamique selon direction)
- `MonPalmaresCard` : colonne droite affichant `"${joursLecture}j"` en gris au-dessus de la note

### `app/src/main/java/com/lmelp/mobile/data/repository/LivresRepository.kt`
- Injection optionnelle de `PalmaresRepository? = null`
- `getLivreDetail()` : appelle `getJoursLecturePourLivre()` si le livre est lu et a une date

### `app/src/main/java/com/lmelp/mobile/LmelpApp.kt`
- `palmaresRepository` déplacé **avant** `livresRepository` (dépendance lazy)
- `livresRepository` instancié avec `palmaresRepository` en paramètre

### `app/src/main/java/com/lmelp/mobile/ui/emissions/LivreDetailScreen.kt`
- Ligne "Lu le XX/XX/XXXX" enrichie avec `" (Xj)"` quand `joursLecture != null`

---

## Tests ajoutés (17 nouveaux tests)

### `VitesseLectureRepositoryTest.kt` (8 tests)
- Calcul de base 2 livres, exclusion 1er livre, participation hors Masque, livres sans date, tri ASC, tri DESC, moins de 2 livres → vide, joursLecture null hors mode vitesse

### `VitesseLectureViewModelTest.kt` (4 tests)
- Clic → VITESSE_ASC, reclique → VITESSE_DESC, 3e clic → retour ASC, filtre hors Masque après calcul

### `JoursLectureLivreDetailTest.kt` (5 tests)
- Jours corrects pour livre Masque, 1er livre → null, livre sans date → null, livre inconnu → null, hors Masque participe au calcul d'un livre Masque

---

## Pattern architectural clé

`LivresRepository` injecte `PalmaresRepository` optionnellement pour enrichir `LivreDetailUi` sans coupler fortement les deux repositories. Le paramètre par défaut `= null` préserve la compatibilité des tests existants de `LivresRepository`.

---

## Points d'attention

- `java.time.LocalDate.parse(dateStr)` fonctionne sans formatter car les dates sont déjà en format ISO (`YYYY-MM-DD`)
- La bascule ASC/DESC se fait en passant toujours `VITESSE_ASC` à `setMonPalmaresTriMode()` — le ViewModel gère la bascule interne
- L'ordre `palmaresRepository` avant `livresRepository` dans `LmelpApp.kt` est critique (lazy dependency)
