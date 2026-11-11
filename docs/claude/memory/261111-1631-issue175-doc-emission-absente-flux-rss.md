# Issue #175 — Doc mise à jour épisode : émission absente du flux RSS

## Contexte

`docs/user/mise_a_jour_episode.md` ne décrivait que le chemin RSS (Automatisch →
`/api/rss/sync` → `/api/pgx/transcription/start`). Or le flux RSS France Inter ne
garde que les épisodes récents : une émission ancienne absente de lmelp (par exemple
signalée par lmelp-pap) ne peut pas passer par là.

back-office-lmelp a ajouté une page **Ajouter une émission** (rubrique *Podcast Masque
Et La Plume*) dans [castorfou/back-office-lmelp#334](https://github.com/castorfou/back-office-lmelp/issues/334),
fusionnée via la PR [#335](https://github.com/castorfou/back-office-lmelp/pull/335).
On y colle l'URL de la page radiofrance.fr de l'émission ; le back-office télécharge
l'audio complet, insère l'épisode, notifie via ntfy et lance la transcription PGX
(réessai horaire si PGX injoignable), comme Automatisch.

## Changement

Documentation seule, dans `docs/user/mise_a_jour_episode.md` : ajout d'un encadré
`!!! info "Émission absente du flux RSS"` entre la partie automatique (blockquote
Automatisch + backend) et « la suite est manuelle ». Il explique :

- pourquoi le flux RSS ne suffit pas pour une émission ancienne ;
- la page **Ajouter une émission** de lmelp-backoffice - frontend, avec l'URL de la
  **page de l'émission** radiofrance.fr (pas le lien audio d'une séquence de quelques
  minutes, piège signalé par la doc back-office) ;
- que téléchargement, notification ntfy et transcription PGX s'enchaînent comme pour
  un épisode RSS, et que la suite manuelle est inchangée ;
- un lien vers la doc utilisateur back-office :
  https://castorfou.github.io/back-office-lmelp/user/ajout-emission/

## Points notables

- L'issue conditionnait le lien à la fusion de back-office-lmelp#334 : vérifié via
  `gh issue view 334 -R castorfou/back-office-lmelp --json closedByPullRequestsReferences`
  (#334 est une issue, pas une PR — la PR est #335) et `curl` sur la page publiée (200).
- Pas de test unitaire (aucune logique) : validation par `mkdocs build --strict` et
  pre-commit. Le message INFO `#en-cas-de-problème-adb` (ancre absente) du build est
  préexistant, sans lien avec ce changement.
- Pas de `CHANGELOG.md` dans le repo.
