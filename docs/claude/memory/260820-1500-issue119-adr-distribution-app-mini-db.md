# Issue #119 — ADR 0002 : distribution de l'app et mini base de données embarquée

## Contexte

Issue de suivi de #116/ADR 0001, initialement scopée sur la faisabilité de trois pistes de distribution APK (Google Play Store, F-Droid, ADB sans fil) suite à la migration `docker-lmelp` vers un NAS. Le scope a été reformulé en cours d'analyse : l'app se met à jour rarement (contrairement aux données, ~1x/semaine), et le laptop reste disponible en USB au moment du déploiement — donc pas de besoin réel de distribution automatisée à court terme.

## Décisions actées (ADR 0002)

Documentées dans `docs/dev/adr/0002-distribution-app-mini-db-embarquee.md` :

1. **Distribution app : USB reste la solution retenue.** Aucune des 3 pistes explorées n'est adoptée immédiatement :
   - **Google Play Store** : direction cible à long terme si multi-utilisateurs, mais bloqué par la question **non tranchée** des droits sur le contenu diffusé (métadonnées Le Masque et la Plume/France Inter). Coût 25$, vérification d'identité obligatoire dès septembre 2026 pour les comptes personnels, test fermé 14 jours/12 testeurs avant publication.
   - **F-Droid** : alternative gratuite viable en théorie (pas de blocage sur les droits pour la soumission elle-même), mais exige des dépendances 100% FOSS — non audité sur ce projet. Prématuré tant que l'app reste à usage personnel.
   - **ADB sans fil** : confirmé fragile (pairing ne survit pas à un reboot, port TCP dynamique) — reste conclusion de l'ADR 0001 : dev-only, jamais un mécanisme de distribution grand public.

2. **Mini base de données embarquée** (conséquence non anticipée de l'ADR 0001) : remplacer la `lmelp.db` complète (4.4 Mo) committée dans `app/src/main/assets/` par un extrait réel minimal (quelques émissions réelles, ex. les plus anciennes). Objectif : rendre visible immédiatement toute régression du mécanisme de téléchargement au lancement (issue #118) — une base complète mais périmée masquerait silencieusement une panne du téléchargement puisque l'app resterait fonctionnelle avec d'anciennes données. Implémentation technique (script d'export, extrait figé, test de non-régression sur la taille) renvoyée à une **issue de suivi séparée**, non créée dans cette session.

3. **Contenu diffusé — état des lieux, non tranché** : l'app n'expose que des métadonnées dérivées (titres/dates/descriptions, notes/avis, liens RadioFrance, couvertures Babelio/Amazon), pas d'audio ni de texte intégral protégé. Ça s'apparente à un usage raisonnable mais reste un jugement, pas un fait juridique établi — la clarification des droits reste un prérequis bloquant pour Play Store, à la charge de l'auteur, hors scope technique.

## Nature de ce travail — pas de TDD

Cette issue n'appelait ni RED tests ni code : c'est une exploration/recherche documentée sous forme d'ADR. Le workflow `/fix-issue` standard a été adapté en conséquence (étapes 4-6 remplacées par rédaction/relecture de l'ADR au lieu de TDD).

## Point d'apprentissage (déjà noté en mémoire long terme séparément)

`awesome-nav` (plugin mkdocs) n'auto-découvre pas les nouvelles pages une fois qu'un `.nav.yml` existe déjà dans un dossier avec des entrées manuelles — il faut ajouter l'entrée explicitement. Correction faite par l'utilisateur sur `docs/dev/.nav.yml` (ajout de `adr/0002-distribution-app-mini-db-embarquee.md`). Mémoire long terme : `mkdocs_nav_yml_explicit.md` dans le système de mémoire globale Claude.

## Fichiers modifiés (non encore commités au moment de cette note)

- `docs/dev/adr/0002-distribution-app-mini-db-embarquee.md` (nouveau)
- `docs/dev/adr/README.md` (ajout entrée liste)
- `docs/dev/.nav.yml` (ajout entrée nav explicite)
- Commentaire posté sur l'issue #119 avec le résumé des spécifications retenues.

## Suite

- Créer l'issue de suivi pour l'implémentation technique de la mini-DB.
- Commit, push, vérif CI/CD, PR — pas encore faits à ce stade de la session.
