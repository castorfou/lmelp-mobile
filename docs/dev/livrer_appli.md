# Développer, livrer, mettre à jour

Cette page décrit le cycle de vie complet de l'application : comment on la développe, comment on publie une nouvelle version, et comment elle arrive sur le téléphone.

## Deux choses qui évoluent séparément

| | **Les données** (émissions, avis, palmarès…) | **L'appli** (écrans, fonctions, code Kotlin) |
|---|---|---|
| Change | à chaque nouvelle émission | à chaque évolution du code |
| Publiée par | le container `lmelp-export` du NAS, automatiquement, toutes les heures | le développeur, en poussant un tag `vX.Y.Z` |
| Release GitHub | `data-v{N}` (`N` = version du schéma Room) | `vX.Y.Z` |
| Arrive sur le téléphone | l'app la détecte au lancement, l'utilisateur l'applique depuis À propos | l'utilisateur installe le nouvel APK |

La mise à jour des données est décrite dans l'[ADR 0001](adr/0001-separation-maj-appli-donnees.md) et dans le guide utilisateur [Mise à jour d'un épisode](../user/mise_a_jour_episode.md). Le reste de cette page concerne l'appli.

## La signature des APK

Android n'accepte de mettre à jour une app installée que si le nouvel APK porte **la même signature** que l'ancien. Il existe deux familles d'APK :

| | APK **debug** | APK **release** |
|---|---|---|
| Produit par | `scripts/build.sh` (`./gradlew assembleDebug`) | `release.yml`, sur un tag `vX.Y.Z` |
| Signé avec | une clé debug générée automatiquement par la machine qui compile | la clé de release `lmelp-release.jks` |
| Usage | tester une modification sur le téléphone, en USB | distribuer une version |
| Identifiant | `com.lmelp.mobile.debug` | `com.lmelp.mobile` |
| Sur le téléphone | « LMELP debug », icône sur fond orange | « Le Masque et la Plume », icône sur fond bleu nuit |

Les deux APK ayant des identifiants différents, Android les traite comme deux applications distinctes : elles s'installent côte à côte, chacune avec ses propres données (épingles, palmarès personnel, base téléchargée). Tester un APK debug ne touche jamais à la version release installée.

L'identifiant debug vient de `applicationIdSuffix = ".debug"` dans `app/build.gradle.kts` ; le `namespace` du code reste `com.lmelp.mobile`. Le nom et l'icône debug sont des ressources de `app/src/debug/res`, générées par `scripts/generate_launcher_icon.py` pour l'icône.

## En tant que développeur : la boucle quotidienne

```
coder → scripts/build.sh && scripts/deploy.sh → tester sur le téléphone (USB)
```

`build.sh` compile un APK debug dans le devcontainer, `deploy.sh` l'installe en USB via `adb` et relance l'app. Rien n'est publié. Les détails (connexion `adb`, dépannage, base locale complète) sont dans [Build / Deploy apk](build_deploy_apk.md).

## En tant que développeur : publier une nouvelle version de l'appli

Une fois les évolutions mergées dans `main` :

```bash
git checkout main && git pull
git tag v1.1.0
git push origin v1.1.0
```

Le workflow `.github/workflows/release.yml` prend le relais (environ 5 minutes) :

1. il vérifie la mini-DB embarquée (`app/src/main/assets/lmelp.db`) ;
2. il décode le keystore depuis le secret `KEYSTORE_BASE64` ;
3. il compile l'APK release, signé par Gradle (`signingConfigs` de `app/build.gradle.kts`), avec la version du tag (`./gradlew assembleRelease -PversionName=1.1.0`) ;
4. il vérifie la signature avec `apksigner`, et avec `aapt2` que l'APK porte bien la version du tag ;
5. il crée la release GitHub `v1.1.0` avec `lmelp-mobile-v1.1.0.apk` en pièce jointe.

Un tag qui contient un tiret (`v1.1.0-rc1`, `v0.0.0-test`) produit une **pré-release**, pratique pour tester le workflow. Pour la supprimer ensuite :

```bash
gh release delete v0.0.0-test --yes --cleanup-tag
git tag -d v0.0.0-test
```

### Numéro de version de l'APK

Le tag fixe les deux numéros de version de l'APK (issue #161) :

| | Valeur | Exemple, tag `v1.2.3` |
|---|---|---|
| `versionName` (affiché par Android et par l'écran À propos) | le tag sans le `v` | `1.2.3` |
| `versionCode` (comparé par Android pour une mise à jour) | `major*10000 + minor*100 + patch` | `10203` |

Android refuse d'installer un APK dont le `versionCode` est inférieur à celui de l'app installée. La formule le fait croître d'une version à la suivante, à condition que **minor et patch restent ≤ 99** : un tag `v1.100.0` fait échouer le build, comme un tag mal formé (`v1.2`). Un suffixe de pré-release est conservé dans `versionName` (`v1.2.3-rc1` → `1.2.3-rc1`, `versionCode` 10203).

Sans `-PversionName` (build local, `scripts/build.sh`), l'APK porte une version de développement : `versionName` `0.0.0-dev+<commit>` (suivi de `-debug` pour l'APK debug), `versionCode` 1.

La règle est dans `buildSrc/src/main/kotlin/AppVersion.kt`, testée par `buildSrc/src/test/kotlin/AppVersionTest.kt`. Depuis Gradle 8, les tests de `buildSrc` ne sont plus lancés pendant un build : les lancer avec `./gradlew -p buildSrc test` (la CI le fait).

## En tant qu'utilisateur : mettre à jour

- **Les données** : l'app vérifie au lancement et affiche un badge sur l'icône des réglages quand une mise à jour est disponible ; l'utilisateur l'applique depuis l'écran À propos, puis rouvre l'app.
- **L'appli** : l'app vérifie aussi au lancement si une release `vX.Y.Z` plus récente existe (même badge) ; l'écran À propos propose alors **Télécharger et installer**, qui télécharge l'APK et ouvre l'installateur Android. La signature est la même, l'état local est conservé. À défaut, télécharger le dernier `lmelp-mobile-vX.Y.Z.apk` depuis la page [Releases](https://github.com/castorfou/lmelp-mobile/releases).

Le guide utilisateur [Installer et mettre à jour l'appli](../user/installer_mettre_a_jour.md) reprend ces étapes pas à pas.

## Détection d'une nouvelle version de l'appli

Introduite par l'[issue #162](https://github.com/castorfou/lmelp-mobile/issues/162), sur le modèle de la mise à jour des données (ADR 0001) :

| Étape | Code | Règle |
|---|---|---|
| Lister les releases | `OkHttpAppReleaseApi` | `GET /repos/castorfou/lmelp-mobile/releases?per_page=30`, sans authentification |
| Choisir la release | `selectLatestAppRelease` (`data/update/AppReleaseVersion.kt`) | tag `vX.Y.Z` strict, ni brouillon ni pré-release, avec un asset `.apk`, `versionCode` strictement supérieur à celui installé |
| Comparer | `appVersionCodeFromTag` | même formule que `buildSrc/AppVersion.kt`, dupliquée car `buildSrc` n'existe qu'au build |
| Télécharger | `AppUpdateRepository.downloadApk` | vers `cacheDir/updates/`, SHA-256 vérifié si GitHub fournit le champ `digest` de l'asset |
| Installer | `ApkInstaller` | `FileProvider` + `ACTION_VIEW` ; permission `REQUEST_INSTALL_PACKAGES`, réglage « sources inconnues » ouvert si l'autorisation manque |

`releases/latest` n'est pas utilisable : GitHub marque les releases de données `data-v{N}` comme « Latest ». Une version de dev (`0.0.0-dev…`) ne vérifie jamais. Pour tester le parcours complet, installer un debug « non dev » face à une release publiée plus récente :

```bash
./gradlew installDebug -PversionName=0.0.1
```

L'installateur installe alors la release à côté du debug (identifiants différents) ; une vraie mise à jour se vérifie d'une release à la suivante.


## Debug et release sur le même téléphone

Les deux apps cohabitent. Les commandes `adb` de développement visent la version debug :

```bash
ADB=~/android-sdk/platform-tools/adb
$ADB uninstall com.lmelp.mobile.debug                                   # désinstaller la debug
$ADB shell am start -n com.lmelp.mobile.debug/com.lmelp.mobile.MainActivity   # la lancer
```

`am start` exige le nom de classe complet `com.lmelp.mobile.MainActivity` : la forme courte `com.lmelp.mobile.debug/.MainActivity` se résout en `com.lmelp.mobile.debug.MainActivity`, qui n'existe pas.

Les icônes thématiques d'Android 13+ (monochromes) sont identiques pour les deux apps : seul le nom les distingue dans ce mode.

## Le keystore de release

`lmelp-release.jks` contient la clé privée qui signe tous les APK de release. Le workflow n'en utilise qu'une copie, stockée dans les secrets GitHub du dépôt :

| Secret | Contenu |
|---|---|
| `KEYSTORE_BASE64` | le fichier `lmelp-release.jks`, encodé en base64 |
| `KEY_ALIAS` | l'alias de la clé : `lmelp` |
| `KEYSTORE_PASSWORD` | le mot de passe du keystore |
| `KEY_PASSWORD` | le mot de passe de la clé, identique au précédent (format PKCS12) |

### À conserver précieusement

**Le fichier `lmelp-release.jks` et son mot de passe se gardent dans un gestionnaire de mots de passe** (le `.jks` en pièce jointe de l'entrée). Ni le token `gh` ni aucun autre accès GitHub ne permet de les retrouver :

- un secret GitHub est en écriture seule : ni l'interface, ni l'API, ni `gh` ne permettent de relire sa valeur ;
- la clé est générée aléatoirement par `keytool` : relancer la même commande produit une autre clé, donc une autre signature.

La copie dans `~/lmelp-release.jks` vit dans le HOME du devcontainer. Ce HOME survit à un rebuild, mais il est propre à un identifiant de devcontainer : un dépôt recloné ailleurs ou une nouvelle machine repartent d'un HOME vide.

### En cas de perte

Créer un nouveau keystore et remplacer les quatre secrets (procédure ci-dessous). Les APK de release suivants seront signés avec une nouvelle clé : chaque téléphone devra désinstaller l'app une fois avant d'installer la nouvelle version.

### Créer le keystore et les secrets

La phrase de passe est saisie une seule fois, dans une variable, pour que le keystore et les secrets reçoivent exactement la même valeur. Elle doit être **en ASCII** : lettres sans accent, chiffres, `- _ .`. L'Android Gradle Plugin ne sait pas lire une clé PKCS12 protégée par un mot de passe non ASCII (`getSecretKey failed: Password is not ASCII`), et `release.yml` refuse un tel mot de passe dès le décodage du keystore.

```bash
# bash : read -rs -p "Phrase ASCII : " P
read -rs "P?Phrase ASCII : "; echo; export P      # zsh

keytool -genkeypair -keystore ~/lmelp-release.jks -alias lmelp -keyalg RSA -keysize 4096 \
  -validity 10000 -storepass:env P -keypass:env P -dname "CN=lmelp-mobile, O=castorfou"
chmod 600 ~/lmelp-release.jks

# Vérifie que la clé privée s'ouvre avec ce mot de passe (affiche OK)
keytool -certreq -keystore ~/lmelp-release.jks -alias lmelp \
  -storepass:env P -keypass:env P -file /dev/null && echo OK

gh secret set KEYSTORE_BASE64 -R castorfou/lmelp-mobile < <(base64 -w0 ~/lmelp-release.jks)
gh secret set KEY_ALIAS -R castorfou/lmelp-mobile -b lmelp
printf '%s' "$P" | gh secret set KEYSTORE_PASSWORD -R castorfou/lmelp-mobile
printf '%s' "$P" | gh secret set KEY_PASSWORD -R castorfou/lmelp-mobile
unset P
```

!!! warning "Ne pas changer le mot de passe avec `keytool -storepasswd`"
    Sur un keystore PKCS12, `keytool -storepasswd` change le mot de passe du fichier mais laisse la clé privée chiffrée avec l'ancien : Gradle échoue ensuite avec `keystore password was incorrect`. Pour changer de mot de passe, recréer le keystore avec la procédure ci-dessus (tant qu'aucun APK n'a été publié avec l'ancienne clé), ou exporter puis réimporter la clé avec `keytool -importkeystore`.

### Signer un APK release en local

`app/build.gradle.kts` lit le keystore dans l'environnement. Sans `LMELP_KEYSTORE_FILE`, `./gradlew assembleRelease` produit un APK non signé (`app-release-unsigned.apk`).

```bash
read -rs "P?Mot de passe keystore : "; echo
LMELP_KEYSTORE_FILE=~/lmelp-release.jks LMELP_KEYSTORE_PASSWORD="$P" \
LMELP_KEY_ALIAS=lmelp LMELP_KEY_PASSWORD="$P" ./gradlew assembleRelease
unset P
~/android-sdk/build-tools/34.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

## Mise à jour des actions GitHub

Dependabot (`.github/dependabot.yml`) ouvre chaque semaine une PR groupée quand une action utilisée par les workflows propres au projet change de version. Les actions qui ne servent qu'aux parties venant du template PyFoundry (`setup-uv`, actions GitHub Pages) sont ignorées : le template les met à jour, et `cruft update` les apporte.

`tests/test_github_workflows.py` échoue si une action tourne sous une version de Node obsolète, ou si une action inconnue apparaît : vérifier alors son `runs.using` dans son `action.yml`, puis l'ajouter à `NODE24_MIN_MAJOR`.
