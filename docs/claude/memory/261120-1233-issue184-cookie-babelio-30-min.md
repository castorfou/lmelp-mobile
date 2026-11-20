# Issue #184 — Écosystème : le cookie Babelio est valable 30 min

## Contexte

Dans back-office-lmelp, `BABELIO_COOKIE_MAX_AGE_SEC` (durée de validité du cookie
Babelio, pour tout cookie, `jstsToken` ou pas) est passée en deux temps :

- 300 s → 240 s : [castorfou/back-office-lmelp#371](https://github.com/castorfou/back-office-lmelp/issues/371),
  PR [#373](https://github.com/castorfou/back-office-lmelp/pull/373) ;
- 240 s → 1800 s : [castorfou/back-office-lmelp#370](https://github.com/castorfou/back-office-lmelp/issues/370),
  PR [#375](https://github.com/castorfou/back-office-lmelp/pull/375), fusionnée le 2026-11-18.

Origine des 30 min : une mesure grandeur nature (IP jetable) a montré qu'un cookie
copié depuis Firefox **connecté** à Babelio tient au moins 1 h 51, avec jusqu'à 30 min
d'inactivité entre deux requêtes, sans 403.

## Changement

Documentation seule, dans `docs/ecosysteme.md` : « valable ~5 min » devient
« valable ~30 min » à deux endroits :

- l'étiquette du lien `FF → BACK` du schéma « Mise à jour des données »
  (`docs/ecosysteme.md:46`) ;
- la ligne « Babelio (Valider avec Babelio, Liaison Babelio, couvertures) » du
  tableau des déclencheurs (`docs/ecosysteme.md:112`).

Aucune autre page ne cite cette durée. Pas de test automatisé (pas de code, valeur
venant d'un autre dépôt) ; vérification par `mkdocs build --strict` et pre-commit.

## À retenir

- Application de la règle de CLAUDE.md « Écosystème lmelp : schéma à tenir à jour » :
  un changement de flux côté back-office-lmelp ouvre une issue ici, et seule la valeur
  finale est reportée dans la page (pas l'étape intermédiaire à 240 s).
- Attendre la fusion de la PR amont avant de mettre la page à jour (vérifier avec
  `gh pr view 375 -R castorfou/back-office-lmelp --json state,mergedAt`).
