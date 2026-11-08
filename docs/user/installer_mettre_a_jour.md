# Installer et mettre à jour l'appli

L'application se met à jour en deux temps, indépendants l'un de l'autre :

- **les données** (émissions, avis, palmarès) se mettent à jour depuis l'application elle-même ;
- **l'appli** (écrans, fonctions) se met à jour en installant un nouveau fichier APK.

## Installer l'appli

1. Sur le téléphone, ouvrir la page [Releases](https://github.com/castorfou/lmelp-mobile/releases) du dépôt.
2. Repérer la dernière release dont le nom commence par `lmelp-mobile v` (les releases « données » `data-v…` ne contiennent pas l'appli).
3. Télécharger le fichier `lmelp-mobile-vX.Y.Z.apk` de cette release.
4. Ouvrir le fichier téléchargé. Au premier essai, Android demande d'autoriser l'installation d'applications depuis le navigateur ou le gestionnaire de fichiers : accepter.
5. Lancer l'appli, puis télécharger la base complète (section suivante). L'APK n'embarque que quelques émissions anciennes.

## Mettre à jour les données

À chaque lancement, l'appli vérifie si de nouvelles données sont publiées. Si c'est le cas, un badge apparaît sur l'icône des réglages. Pour appliquer la mise à jour :

1. ouvrir l'écran **À propos** ;
2. appuyer sur le bouton de mise à jour (**Vérifier les mises à jour** relance la vérification si aucune n'est proposée) ;
3. une fois la mise à jour appliquée, l'appli se ferme : la rouvrir.

Le détail de la publication des données est décrit dans [Mise à jour d'un épisode](mise_a_jour_episode.md).

## Mettre à jour l'appli

Refaire les étapes 1 à 4 de l'installation avec la nouvelle version, puis choisir **Mettre à jour**. L'appli installée est remplacée, les données et les réglages sont conservés.

La version installée est affichée en haut de l'écran **À propos** (par exemple « Version 1.2.3 »), et dans les paramètres Android de l'appli. Une version qui commence par `0.0.0-dev` est une version de développement.

L'appli ne signale pas encore d'elle-même qu'une nouvelle version est disponible : consulter la page [Releases](https://github.com/castorfou/lmelp-mobile/releases).

## Dépannage

**Deux icônes, « Le Masque et la Plume » et « LMELP debug »** : « LMELP debug » (icône orange) est une version de développement, installée depuis un ordinateur. C'est une application distincte, avec ses propres données : elle n'interfère pas avec l'appli et peut être désinstallée sans conséquence pour celle-ci.

**« Application non installée » ou « conflit avec un paquet existant »** : la version installée a été signée avec une autre clé de release. Désinstaller l'appli (appui long sur l'icône → Désinstaller), puis installer l'APK. L'état local (épingles, palmarès personnel) est perdu, et la base est à retélécharger.
