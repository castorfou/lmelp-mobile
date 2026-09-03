# Épingler des livres "en cours de lecture"

L'écran **Sur ma liseuse** permet de marquer un livre comme **en cours de lecture**.
Les livres épinglés remontent automatiquement en tête de liste, quelle que soit l'option de tri active.

## Épinglage automatique

Un livre dont la liseuse (KOReader) indique une **progression de lecture en cours**
(déjà commencé, pas encore terminé) est **épinglé automatiquement**, sans action de
votre part — même si la case *Read* de Calibre n'est pas cochée. C'est le cas typique
d'un livre en cours de lecture qui n'a pas encore été marqué "lu".

Vous gardez la main : vous pouvez retirer cet épinglage automatique à tout moment
(voir ci-dessous), et ce choix est respecté jusqu'à la prochaine mise à jour de la
base de données.

## Comment épingler un livre manuellement

1. Ouvrir l'écran **Sur ma liseuse** (accessible depuis l'accueil)
2. Faire un **appui long** sur la carte du livre à épingler
3. Un menu apparaît en bas de l'écran — sélectionner **📌 En cours de lecture**

Le livre remonte en tête de liste avec une **icône punaise verte** à gauche de sa note.

## Comment désépingler un livre

Deux façons de désépingler, que l'épinglage soit manuel ou automatique :

- **Tap sur l'icône punaise** (🖊 vert, à gauche de la note) sur la carte du livre
- **Appui long** sur la carte → sélectionner **Retirer de 'En cours de lecture'**

Pour un livre épinglé automatiquement (progression en cours détectée), ce retrait
reste effectif tant que les données Calibre associées ne changent pas (nouvelle mise
à jour de la base) — le livre ne réapparaît pas épinglé à chaque ouverture de l'écran.

## Comportement avec les tris

Les livres épinglés restent toujours **en tête de liste**, séparés des autres par un trait horizontal.
Parmi les livres épinglés eux-mêmes, le tri sélectionné (a→z, Note masque, Note conseil) s'applique normalement.

## Désépinglage automatique après une mise à jour

Quand la base de données est mise à jour via `lmelp-update-mobile` :

- Si un livre était épinglé **et** a été marqué comme **lu dans Calibre**, il est **automatiquement désépinglé** au prochain chargement de l'écran.
- Cela correspond au cas naturel : vous terminez votre lecture, vous le marquez lu dans Calibre, vous régénérez la base → le livre quitte la liste "en cours".
- De la même façon, si vous aviez retiré manuellement un épinglage automatique et que
  le livre n'a plus de progression de lecture en cours (ou a été terminé), ce retrait
  est oublié — il n'a plus lieu d'être.

## Persistance

Les épingles sont **conservées entre les sessions** et les mises à jour de l'app.
Elles sont stockées localement sur le téléphone et ne dépendent pas de la base de données lmelp.db.
