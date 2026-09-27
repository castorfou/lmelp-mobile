# Issue #140 — Crash en bas de la page d'un critique (clé LazyColumn dupliquée)

## Symptôme

L'app plantait sur la page d'un critique (signalé sur Frédéric Beigbeder) en
défilant tout en bas de la section « Coups de cœur ». Le haut de la page
fonctionnait.

## Cause racine

`CritiqueDetailContent` identifiait chaque carte de coup de cœur par
`key = { "cdc_${it.livreId}" }`, en supposant **un seul avis par (critique,
livre)**. C'est faux : un critique peut noter le même livre dans deux émissions.
Deux cartes recevaient alors la même clé, et Compose levait
`IllegalArgumentException: Key ... was already used`. Le crash n'apparaissait
qu'en bas de page car une `LazyColumn` ne compose un item qu'au moment où il
devient visible : la clé en double n'est détectée qu'à l'arrivée de la 2e carte.

Exemple réel, **légitime et non une anomalie de données** (confirmé par
l'utilisateur via le back-office) : Beigbeder / « Le voyant d'Étampes »
(Abel Quentin), noté 9 le 29/08/2021 (coup de cœur de rentrée, transcrit
« Le voyant des tempêtes ») puis noté 9 le 31/10/2021 (livre au programme),
avec deux commentaires différents. La base complète compte 14 couples
(critique, livre) de ce type parmi les notes ≥ 9 : Viviant (6), Kapriélian,
Lamberterie, Raspiengeas, Beigbeder (2).

## Correction

Choix produit retenu : **une carte par avis** (les deux avis restent visibles,
chacun avec sa date), et non un dédoublonnage par livre.

- `app/src/main/java/com/lmelp/mobile/data/db/CritiquesDao.kt:9,28` : la
  projection `getAvisByCritique` expose `a.id as avisId`, et
  `AvisParCritiqueRow` gagne `avisId`. `avis.id` est la clé primaire, donc
  unique par construction. C'est plus robuste qu'un couple livre+émission, que
  rien n'empêche en base d'être dupliqué (0 cas aujourd'hui).
- `app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt:95` : champ
  `avisId` ajouté à `AvisParCritiqueUi`.
- `app/src/main/java/com/lmelp/mobile/data/repository/CritiquesRepository.kt:38` :
  tri note décroissante, puis date d'émission décroissante, pour un ordre
  déterministe entre ex æquo.
- `app/src/main/java/com/lmelp/mobile/ui/critiques/CritiqueDetailScreen.kt:90,120` :
  fonction pure `coupDeCoeurKey(avis) = "cdc_${avis.avisId}"`, utilisée comme
  `key = ::coupDeCoeurKey`. Elle est testable unitairement sans Compose UI test.

**Pas de bump de version Room** : `AvisParCritiqueRow` est une projection de
requête, pas une `@Entity`. Ajouter une colonne à un `SELECT` ne change pas le
schéma vérifié par Room.

## Tests

`app/src/test/java/com/lmelp/mobile/CritiquesRepositoryTest.kt` : 3 nouveaux
tests, RED à la compilation puis verts.
- `avisId` propagé dans `coupsDeCoeur`.
- Même livre coup de cœur dans deux émissions : 2 cartes, et toutes les clés
  `coupDeCoeurKey` sont distinctes (reproduit le cas Beigbeder).
- À note égale, l'avis le plus récent arrive en premier.

Suite complète : 283 tests Kotlin et 91 tests Python OK, `lintDebug` et
`assembleDebug` OK. Validé sur device par l'utilisateur, sans crash ni
`FATAL` dans logcat.

## Leçons

- **Toute clé de `LazyColumn` doit être unique par construction** : se baser
  sur une clé primaire de la ligne affichée, jamais sur l'id d'une entité liée
  (livre, émission) qui peut se répéter. Audit fait lors de ce fix : les autres
  écrans sont sûrs (palmarès/recos uniques par livre, requête auteur agrégée par
  `GROUP BY l.id`, `LivreDetailScreen` clé sur `avis.id`).
- Un crash qui n'apparaît qu'« en bas de liste » oriente vers une clé
  dupliquée dans une liste lazy.
- Test sur device : `adb install -r` (et non uninstall) pour garder la base
  complète téléchargée. La mini-DB embarquée (ADR 0002) ne contient pas ces
  doublons et ne reproduirait pas le bug.
