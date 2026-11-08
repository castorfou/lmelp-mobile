# Issue #159 — Actions CI sous Node 24, signature Gradle de l'APK de release

## Contexte

PyFoundry 1.1.2 (#157) avait passé sous Node 24 les actions venant du template. Les actions propres à lmelp-mobile restaient sous Node 20 (avertissement « Node.js 20 is deprecated ») : job `android` de `.github/workflows/ci.yml`, `.github/workflows/release.yml`, `.github/workflows/build-export-image.yml`. `r0adkll/sign-android-release@v1` tournait sous Node 12, sans version plus récente.

Constat en cours de route : `release.yml` n'avait **jamais** tourné sur un tag `v*` (aucun tag `v*`, aucun secret de keystore). Aucune version de l'appli n'avait été publiée sur GitHub ; l'appli était installée uniquement en APK debug via `scripts/build.sh` + `scripts/deploy.sh`.

## Ce qui a été fait

1. **Montées de version** : checkout v7, setup-java v6, setup-android v4, upload-artifact v7, action-gh-release v3, docker login v4 / metadata v6 / build-push v7. `setup-python` est remplacé par `astral-sh/setup-uv@v10.3.3` dans `release.yml`. Pas de changement cassant pour notre usage (setup-android v4 : `packages` toujours présent).
2. **Signature par Gradle** (`app/build.gradle.kts`) : `signingConfigs.release` créé seulement si `LMELP_KEYSTORE_FILE` pointe sur un fichier existant ; mots de passe et alias lus via `System.getenv("LMELP_KEYSTORE_PASSWORD" / "LMELP_KEY_ALIAS" / "LMELP_KEY_PASSWORD")`. Sans keystore : `app-release-unsigned.apk`, comme avant.
3. **`release.yml` réécrit** :
   - `permissions: contents: write` (sinon 403 « Resource not accessible by integration » à la création de la release) ;
   - dépendances Python par `uv sync --frozen` (la liste `pip install` écrite à la main n'avait ni `python-dotenv` ni `pillow` → `ModuleNotFoundError`) ;
   - étape d'export MongoDB **supprimée** : avec un secret `MONGO_URI`, elle aurait embarqué la base complète, contre l'ADR 0002 ;
   - décodage du keystore (`KEYSTORE_BASE64` → `$RUNNER_TEMP/release.jks`), échec immédiat si le secret manque ou si un mot de passe n'est pas ASCII ;
   - `apksigner verify --print-certs` (dernier `build-tools` du SDK), APK renommé `dist/lmelp-mobile-<tag>.apk` ;
   - `prerelease` si le tag contient un tiret ; texte de la release mis à jour (mini-DB + téléchargement de la base).
4. **`.github/dependabot.yml`** pour `github-actions`, hebdomadaire, groupé. Actions ignorées : `astral-sh/setup-uv`, `actions/configure-pages`, `actions/upload-pages-artifact`, `actions/deploy-pages` (servent surtout aux parties du template ; un bump Dependabot créerait des rejets au `cruft update`).
5. **`build-export-image.yml`** ajouté à ses propres `paths` de `pull_request` pour qu'une PR qui le modifie le teste.
6. **Test `tests/test_github_workflows.py`** : table `NODE24_MIN_MAJOR` (première majeure Node 24 de chaque action) ; échoue sur une majeure trop ancienne ou une action absente de la table. Vérifie aussi l'absence de `r0adkll/...` dans les `uses:`, la présence de `dependabot.yml` et des variables `LMELP_*` dans `build.gradle.kts`.
7. **Docs** : nouvelle page `docs/dev/livrer_appli.md` (données vs appli, signature debug/release, boucle dev, publier un tag, keystore et secrets, procédure de création, pièges) et `docs/user/installer_mettre_a_jour.md` ; `docs/dev/build_deploy_apk.md` nettoyé (ancien modèle « nouvelle db = nouvel apk » retiré, push ADB requalifié en outil de dev) ; README (vision, architecture, tableau du cycle, export hors assets) ; CLAUDE.md (section CI/CD).
8. **Issues de suivi** : [#160](https://github.com/castorfou/lmelp-mobile/issues/160) APK debug et release côte à côte (`applicationIdSuffix ".debug"`), [#161](https://github.com/castorfou/lmelp-mobile/issues/161) `versionName`/`versionCode` dérivés du tag (aujourd'hui figés à `1.0`/`1`), [#162](https://github.com/castorfou/lmelp-mobile/issues/162) signaler dans l'app une nouvelle version de l'appli (dépend de #161).

## Validation

Tag `v0.0.0-test` poussé sur la branche : run `release.yml` vert, sans avertissement Node, pré-release créée avec l'APK signé (`CN=lmelp-mobile, O=castorfou`), installé avec succès sur le téléphone de l'utilisateur. Pré-release et tag supprimés ensuite (`gh release delete v0.0.0-test --yes --cleanup-tag`).

## Pièges rencontrés

- **Mot de passe PKCS12 non ASCII** : `keytool` l'accepte, mais l'Android Gradle Plugin échoue en lisant la clé (`getSecretKey failed: Password is not ASCII`). D'où le contrôle `LC_ALL=C grep -q '[^ -~]'` dans l'étape de décodage.
- **`keytool -storepasswd` sur PKCS12** change le mot de passe du fichier mais pas celui de la clé privée → `keystore password was incorrect`. Solution : recréer le keystore.
- **Saisies masquées divergentes** : la procédure documentée lit la phrase **une fois** dans une variable (`-storepass:env P -keypass:env P`, `printf '%s' "$P" | gh secret set`), vérifie la clé avec `keytool -certreq ... -file /dev/null`, puis pose les secrets.
- **zsh** (terminal de l'utilisateur) : `read -p` y signifie « coprocess » ; utiliser `read -rs "P?invite : "`.
- **Secrets GitHub en écriture seule** : le `.jks` et son mot de passe ne sont récupérables que depuis le gestionnaire de mots de passe de l'utilisateur. Perte = nouvelle clé = désinstallation unique sur chaque téléphone.
- **APK debug ↔ release** : signatures différentes, même paquet → `adb uninstall com.lmelp.mobile` pour passer de l'un à l'autre (#160 pour lever la friction).
- **Gradle wrapper** : `services.gradle.org` en 503 et cache du conteneur vide ; distribution 8.9 récupérée depuis `github.com/gradle/gradle-distributions/releases` et déposée dans `~/.gradle/wrapper/dists/gradle-8.9-bin/<hash>/`.
- **Liens du README** : le README est inclus dans `docs/index.md` ; un lien relatif `docs/...` casse `mkdocs build --strict`. Utiliser les URL absolues `https://castorfou.github.io/lmelp-mobile/...`.
- **Check de MAJ données au lancement** : il ne fait que détecter (badge sur l'icône réglages) ; l'application se fait depuis À propos. La doc utilisateur le décrit ainsi.
