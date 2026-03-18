---
name: issue70-svd-recommendations-fix
description: Fix bug SVD recommandations mobile/desktop — remplacement scipy par surprise.SVD
type: project
---

# Issue #70 — Fix SVD recommandations (Conseils)

## Problème

Les scores SVD dans la section Conseils du mobile étaient dans la mauvaise échelle (~1-5
au lieu de ~7-9) et l'ordre des livres était différent du desktop.

Deux causes racines :
1. **Mauvais algorithme** : le mobile utilisait `scipy.sparse.linalg.svds` (SVD tronquée
   mathématique) alors que le desktop utilise `surprise.SVD` (collaborative filtering par
   gradient descent). Ce sont deux algorithmes fondamentalement différents.
2. **Mauvais centrage** : `matrix.mean(axis=0)` incluait les zéros (absences de notes),
   produisant des moyennes sous-estimées.
3. **Pas d'utilisateur réel** : le mobile utilisait un "critique moyen" (`np.mean(u, axis=0)`)
   au lieu de prédire pour l'utilisateur réel avec ses notes Calibre.
4. **Seed non fixée** : variabilité entre exécutions sans `random_state=42`.

## Fix appliqué

`scripts/export_mongo_to_sqlite.py` — fonction `compute_recommendations()` :

- Remplace `scipy.sparse.linalg.svds` par `surprise.SVD`
- Paramètres identiques au desktop : `n_factors=20, n_epochs=50, lr_all=0.01, reg_all=0.1, random_state=42`
- Lit les notes Calibre directement depuis la base Calibre (350+ livres notés) via
  matching titre→livre_id avec `_normalize_title` alignée sur `normalize_for_matching` du desktop
- Fallback sur `palmares.calibre_rating` si Calibre DB indisponible
- Filtres identiques au desktop : critiques ≥10 avis, livres ≥2 critiques
- Prédiction `algo.predict("Moi", livre_id).est` pour l'utilisateur réel
- Score hybride : 70% SVD + 30% masque_mean (inchangé)

**Signature** : `compute_recommendations(cur, n_factors=20, calibre_db_path=None)`
**Appelé** avec `calibre_db_path=calibre_db` depuis la fonction principale d'export.

## _normalize_title alignée sur le desktop

Ancienne implémentation (buggée pour le matching) :
- Supprimait les apostrophes → `l'anachronique` devenait `lanachronique`
- 3 livres ratés dans le matching Calibre

Nouvelle implémentation (alignée sur `normalize_for_matching` de back-office-lmelp) :
- Ligatures : `œ→oe`, `æ→ae`
- NFD + retrait accents
- Apostrophes typographiques → droites (conservées, pas supprimées)
- Tirets typographiques → simples
- Minuscules + collapse whitespace

**Why:** Sans alignement, 131 notes Calibre matchées au lieu de 134, différence de
scores SVD de ~0.05-0.1.

## Dépendances ajoutées

Dans `pyproject.toml` :
- `scikit-surprise>=1.1.4`
- `numpy<2.0.0` (scikit-surprise compilé avec NumPy 1.x, incompatible NumPy 2.x)
- Installé via `uv add --active scikit-surprise` puis `uv sync --active --all-extras`

## Tests

`tests/test_svd_recommendations.py` (7 tests) :
- `TestLivreMeans` : vérifie le bug du centrage (avec/sans zéros)
- `TestSvdRealData` : test d'intégration — SVD de "Le lièvre" doit être proche de 7.77 ±1.5
- `TestSvdScoreScale` : vérifie l'échelle des scores (≥4.0 avec bon centrage, <5.0 avec bugué)

## Résultats après fix

Top 10 mobile quasi-identique au desktop (différence ≤ 0.03 sur les scores) :
- #1 L'Anachronique : hybrid=8.55 (desktop 8.57) ✓
- #2 La seule histoire : hybrid=8.44 (desktop 8.45) ✓
- #3 Les Forces : hybrid=8.34 (desktop 8.37) ✓

## Architecture desktop (back-office-lmelp)

`src/back_office_lmelp/services/recommendation_service.py` :
- Calcul temps réel à chaque requête (pas de cache)
- Lit avis depuis MongoDB + notes Calibre en live
- `normalize_for_matching` pour le matching titre→livre_oid
- `SVD_PARAMS = {n_factors:20, n_epochs:50, lr_all:0.01, reg_all:0.1, random_state:42}`
  (random_state ajouté dans la branche `240-livres-onkindle-...`)
