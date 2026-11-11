# lmelp-mobile

Application Android hors ligne pour consulter le contenu de l'émission Le Masque et la Plume. L'appli et ses données évoluent et se distribuent séparément.

## Language

### Appli

**Version de l'appli** :
Release publiée de l'APK, identifiée par un numéro `X.Y.Z` stable. Le mot « version » employé seul désigne toujours celle-ci.
_Avoid_ : release, build

**Préversion** :
APK release publié automatiquement à chaque merge dans `main` qui touche l'appli, numéroté `X.Y.Z+N` (N commits depuis la version X.Y.Z). Une seule à la fois, sur la pré-release `app-preview`. Ce n'est pas une version : ses commits restent « Pas encore publié ».
_Avoid_ : nightly, build de dev, version beta

**Contenu d'une version** :
Les commits compris entre la version de l'appli précédente (non comprise) et celle-ci (comprise). Tout ce qui précède la première version (0.9.0) en fait partie.

**Pas encore publié** :
Commits postérieurs à la dernière version de l'appli, qui n'appartiennent encore à aucune version.
_Avoid_ : unreleased, version en cours

**Historique** :
Liste des commits de l'appli, regroupés par version, telle qu'affichée dans l'écran À propos.
_Avoid_ : changelog

### Données

**Export** :
Instantané daté des données du Masque et la Plume, produit depuis la base source et distribué à l'appli.
_Avoid_ : version des données, version de la base

**Schéma** :
Numéro de structure de la base de données embarquée. Un export n'est compatible qu'avec les appli du même schéma.
_Avoid_ : version de la base, user_version
