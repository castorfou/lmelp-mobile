# Installer et mettre à jour l'appli

L'application se met à jour en deux temps, indépendants l'un de l'autre :

- **les données** (émissions, avis, palmarès) se mettent à jour depuis l'application elle-même ;
- **l'appli** (écrans, fonctions) se met à jour en installant un nouveau fichier APK.

## Installer l'appli

1. Sur le téléphone, ouvrir la page [Releases](https://github.com/castorfou/lmelp-mobile/releases) du dépôt.
2. Repérer la dernière release dont le nom commence par `lmelp-mobile v` (les releases « données » `data-v…` ne contiennent pas l'appli, la release `app-preview` contient la préversion).
3. Télécharger le fichier `lmelp-mobile-vX.Y.Z.apk` de cette release.
4. Ouvrir le fichier téléchargé. Au premier essai, Android demande d'autoriser l'installation d'applications depuis le navigateur ou le gestionnaire de fichiers : accepter.
5. Lancer l'appli, puis télécharger la base complète (section suivante). L'APK n'embarque que quelques émissions anciennes.

## Mettre à jour les données

À chaque lancement, l'appli vérifie si de nouvelles données sont publiées. Si c'est le cas, un badge apparaît sur l'icône des réglages. Pour appliquer la mise à jour :

1. ouvrir l'écran **À propos** ;
2. appuyer sur le bouton de mise à jour (**Vérifier les mises à jour** relance la vérification si aucune n'est proposée) ;
3. une fois la mise à jour appliquée, l'appli se ferme : la rouvrir.

Si GitHub, qui héberge les données, répond mal, l'appli réessaie d'elle-même deux fois, quelques secondes plus tard. Si le problème persiste, l'écran **À propos** affiche l'un de ces messages :

| Message | Signification |
|---|---|
| « Le serveur de mises à jour (GitHub) est momentanément indisponible. Réessayez plus tard. » | Panne passagère de GitHub, rien à corriger sur le téléphone. |
| « Pas de connexion Internet. » | Le téléphone n'est pas connecté. |
| « Aucune mise à jour disponible pour cette version de l'application. » | Aucune donnée n'est publiée pour cette version de l'appli, ce qui arrive juste après une évolution de la base. Mettre l'appli à jour (section suivante). |
| « Erreur : … » | Autre problème, détaillé dans le message. |

Le détail de la publication des données est décrit dans [Mise à jour d'un épisode](mise_a_jour_episode.md).

## Mettre à jour l'appli

À chaque lancement, l'appli vérifie aussi si une nouvelle version d'elle-même est publiée. Si c'est le cas, le badge apparaît sur l'icône des réglages, et l'écran **À propos** affiche un bloc **Application** : « Nouvelle version X.Y.Z disponible ». Pour l'installer :

1. appuyer sur **Télécharger et installer** ;
2. la première fois, Android ouvre le réglage « Installer des applis inconnues » : autoriser l'appli, revenir, puis appuyer sur **Autoriser LMELP à installer des applications, puis réessayer** ;
3. dans l'installateur Android, choisir **Mettre à jour**.

Entre deux versions, l'appli propose aussi les **préversions** : « Préversion 1.2.0+3 disponible ». Une préversion contient les dernières évolutions terminées, avant qu'elles soient regroupées dans une version (ici, 3 évolutions de code depuis la version 1.2.0). Elle s'installe de la même façon, et la version suivante (1.2.1, 1.3.0…) est ensuite proposée par-dessus. Il n'y a pas de réglage pour les refuser : il suffit de ne pas appuyer sur le bouton.

Comme pour les données, l'appli réessaie d'elle-même si GitHub répond mal. Si le téléchargement échoue, le bouton affiche l'un des messages du tableau ci-dessus (par exemple « Pas de connexion Internet. ») ; appuyer dessus relance le téléchargement.

L'appli installée est remplacée, les données et les réglages sont conservés. Le bouton **Vérifier les mises à jour** relance la vérification des données et de l'appli.

Sans passer par l'appli, on peut aussi refaire les étapes 1 à 4 de l'installation avec la nouvelle version, puis choisir **Mettre à jour**.

La version installée est affichée en haut de l'écran **À propos** (par exemple « Version 1.2.3 », ou « Version 1.2.0+3 (préversion) »), et dans les paramètres Android de l'appli. Une version qui commence par `0.0.0-dev` est une version de développement : elle ne cherche jamais de nouvelle version de l'appli.

## Dépannage

**Deux icônes, « Le Masque et la Plume » et « LMELP debug »** : « LMELP debug » (icône orange) est une version de développement, installée depuis un ordinateur. C'est une application distincte, avec ses propres données : elle n'interfère pas avec l'appli et peut être désinstallée sans conséquence pour celle-ci.

**« Application non installée » ou « conflit avec un paquet existant »** : la version installée a été signée avec une autre clé de release. Désinstaller l'appli (appui long sur l'icône → Désinstaller), puis installer l'APK. L'état local (épingles, palmarès personnel) est perdu, et la base est à retélécharger.
