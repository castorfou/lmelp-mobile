# ADR 0002 — Distribution de l'application et mini base de données embarquée

- Statut : Accepté
- Date : 2026-08-20
- Issue : [#119](https://github.com/castorfou/lmelp-mobile/issues/119)

## Contexte

[ADR 0001](0001-separation-maj-appli-donnees.md) a séparé la mise à jour des **données** (`lmelp.db`, désormais téléchargée par l'app elle-même depuis une GitHub Release) de la mise à jour de l'**application** (APK), en traitant ce second point comme périphérique et renvoyé à une issue de suivi.

Cette issue explore la faisabilité de trois pistes de distribution de l'APK évoquées dans l'ADR 0001 : Google Play Store, F-Droid, et ADB sans fil. Une reformulation du besoin en cours d'analyse a changé le scope : la mise à jour de l'app restant **rare** (contrairement aux données, mises à jour ~1x/semaine), le mécanisme USB + `adb install` actuel reste praticable en usage courant — l'auteur développe depuis un laptop et peut brancher le téléphone en USB au moment même où il déploie une nouvelle version. Le besoin réel n'est donc pas un mécanisme de distribution automatisé à court terme, mais deux choses :

1. Documenter que USB reste une solution acceptable pour l'app tant qu'un besoin réel de distribution grand public n'existe pas.
2. Une conséquence directe et non anticipée de la séparation app/données de l'ADR 0001 : `lmelp.db` embarquée dans `app/src/main/assets/` (4.4 Mo actuellement, régénérée par `scripts/export_mongo_to_sqlite.py`) reste une base **complète et à jour au moment du build**. Or son seul rôle désormais est de servir de secours avant le premier téléchargement — la garder complète masque silencieusement une régression du mécanisme de téléchargement : si celui-ci casse, l'app reste malgré tout pleinement fonctionnelle avec la base embarquée, et personne ne le remarque.

## Décision

### Distribution de l'application — USB reste la solution retenue à ce stade

Aucune des pistes explorées n'est retenue comme décision immédiate :

- **Google Play Store** : reste la direction cible à long terme si l'app doit un jour être distribuée à d'autres utilisateurs (cohérent avec la vision multi-utilisateurs de l'ADR 0001). Bloqué net par une question **non technique** : les droits sur le contenu diffusé par l'app (métadonnées d'émissions Le Masque et la Plume / France Inter — voir plus bas). Coût d'entrée : 25$ à l'inscription, et depuis septembre 2026 une vérification d'identité obligatoire pour tout nouveau compte personnel (délai annoncé 1-2 jours). Un compte personnel impose en plus un test fermé de 14 jours avec 12 testeurs minimum avant toute publication publique.
- **F-Droid** : alternative gratuite et sans clarification de droits de diffusion nécessaire pour l'inscription elle-même (soumission communautaire via merge request sur `fdroiddata`), mais exige que **toutes les dépendances soient FOSS** (Firebase/GMS notamment refusés) — non audité sur les dépendances actuelles du projet. Reste une option valable à réévaluer si un besoin de distribution à un public élargi émerge, mais prématurée tant que l'app reste à usage personnel.
- **ADB sans fil (pairing Android 11+)** : confirmé fragile en usage réel — le pairing ne survit généralement pas à un redémarrage du téléphone et le port TCP change à chaque reboot, ce qui rend une automatisation fiable depuis le NAS peu réaliste. Android 17 (version du device de test) introduit « ADB Wi-Fi 2.0 » avec reconnexion automatique sur réseau de confiance, une amélioration réelle mais qui ne change pas la conclusion : reste une solution **manuelle, dev-only**, jamais un mécanisme de distribution utilisateur final. Cette conclusion était déjà celle de l'ADR 0001 ; cette issue la confirme sans la remettre en cause.

**Décision retenue** : garder le mécanisme USB + `adb install` actuel pour l'app (documenté dans `CLAUDE.md` à la racine du dépôt), sans nouvelle infrastructure. Revisiter Play Store le jour où un besoin réel de distribution à d'autres utilisateurs se présente, une fois la question des droits de contenu clarifiée par l'auteur (hors scope technique de cet ADR).

### Mini base de données embarquée — décision technique retenue

Remplacer la `lmelp.db` complète actuellement committée dans `app/src/main/assets/` par un **extrait réel minimal** :

- Contenu : un petit échantillon fixe d'émissions réelles (par exemple les 2-3 émissions les plus anciennes, avec leurs livres/auteurs/critiques/avis liés), plutôt qu'une base vide ou factice — l'app doit rester utilisable et démontrable même sans réseau (mode avion, premier lancement sans connexion), cohérent avec la contrainte offline-first de l'ADR 0001.
- Schéma inchangé : mêmes tables, même `PRAGMA user_version` (`ROOM_VERSION`, actuellement 7 dans `scripts/export_mongo_to_sqlite.py` et `version` dans `LmelpDatabase.kt`) — seul le volume de données change, pas la compatibilité Room.
- Effet recherché : rendre visible immédiatement toute régression du mécanisme de téléchargement (`DataUpdateRepository`, `DatabaseFileReplacer`, issue #118) — avec une base volumineuse mais périmée, une régression du téléchargement au lancement passerait inaperçue puisque l'app resterait pleinement fonctionnelle avec les anciennes données ; avec une base minimale, l'absence de mise à jour se voit tout de suite (peu d'émissions listées).
- Effet secondaire positif : réduit la taille de l'APK et évite de re-committer ~4.4 Mo de données à chaque changement purement logiciel du dépôt.

Implémentation technique (script d'export, génération de l'extrait figé, tests de non-régression) renvoyée à une **issue de suivi dédiée** — hors scope de cet ADR qui documente la décision, pas le détail d'implémentation.

## Contenu diffusé par l'app — état des lieux (non tranché)

L'app n'embarque ni audio ni texte intégral protégé : `docs/dev/data-schema.md` confirme qu'elle expose des **métadonnées dérivées** (titres/dates/descriptions d'épisodes, notes et avis des critiques, liens vers les pages RadioFrance et vers des images de couverture hébergées par Babelio/Amazon). Cela s'apparente à un usage raisonnable, mais reste un jugement et non un fait juridique établi — cet ADR ne tranche pas la question des droits de diffusion. Elle doit être clarifiée par l'auteur avant toute publication publique (Play Store ou autre), indépendamment de tout aspect technique.

## Alternatives rejetées

| Alternative | Raison du rejet |
|---|---|
| Publication immédiate sur Google Play Store | Question des droits de contenu non clarifiée ; coût d'entrée et process de review (identité, test fermé 14 jours/12 testeurs) disproportionnés pour un usage actuellement personnel. |
| Publication immédiate sur F-Droid | Prématuré tant que l'app reste à usage personnel ; dépendances FOSS non auditées ; pas de besoin de distribution à un public élargi aujourd'hui. |
| Automatiser l'ADB sans fil depuis le NAS | Pairing non persistant après reboot, port TCP dynamique — fiabilité insuffisante pour un déclenchement automatique non supervisé. Reste utile en usage manuel ponctuel dev/debug uniquement. |
| Garder la `lmelp.db` complète embarquée | Masque silencieusement une régression du mécanisme de téléchargement (l'app resterait fonctionnelle avec des données périmées) ; alourdit inutilement l'APK et le dépôt Git. |

## Conséquences

**Positives :**

- Aucune nouvelle infrastructure de distribution à maintenir à ce stade.
- La mini-DB rend le mécanisme de téléchargement (issue #118) testable de bout en bout dès le premier lancement, y compris manuellement par l'auteur.
- APK et dépôt Git plus légers.

**Négatives / risques :**

- Un nouvel utilisateur installant l'app sans réseau disponible au premier lancement ne verra qu'un échantillon très restreint de données, plus longtemps qu'aujourd'hui (aujourd'hui la base embarquée est complète). Acceptable : l'app reste actuellement à usage personnel, avec réseau disponible dans l'immense majorité des cas.
- La question des droits de contenu reste un blocage non résolu pour toute publication publique future — cet ADR ne l'évacue pas, il la documente explicitement comme prérequis.
- Revisiter cette décision si le projet évolue vers le multi-utilisateurs (vision long terme de l'ADR 0001), où une vraie distribution (Play Store et/ou F-Droid) redeviendra nécessaire.

## Suivi

- Issue de suivi à créer : implémentation technique de la mini-DB embarquée (génération de l'extrait figé dans `scripts/export_mongo_to_sqlite.py` ou script dédié, mise à jour de `app/src/main/assets/lmelp.db`, test de non-régression vérifiant sa petite taille).
- Clarification des droits de contenu par l'auteur : prérequis bloquant avant toute démarche Play Store, hors scope technique.
