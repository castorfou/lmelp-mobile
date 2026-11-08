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

Les deux portent le même identifiant `com.lmelp.mobile` mais pas la même signature : l'un ne peut pas remplacer l'autre. Passer d'un APK debug à un APK release, ou l'inverse, impose de désinstaller l'app, ce qui efface son état local (épingles, palmarès personnel…) et la base téléchargée.

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
3. il compile l'APK release, signé par Gradle (`signingConfigs` de `app/build.gradle.kts`) ;
4. il vérifie la signature avec `apksigner` ;
5. il crée la release GitHub `v1.1.0` avec `lmelp-mobile-v1.1.0.apk` en pièce jointe.

Un tag qui contient un tiret (`v1.1.0-rc1`, `v0.0.0-test`) produit une **pré-release**, pratique pour tester le workflow. Pour la supprimer ensuite :

```bash
gh release delete v0.0.0-test --yes --cleanup-tag
git tag -d v0.0.0-test
```

!!! note "Numéro de version"
    Le numéro affiché par Android (`versionName`) est fixé à `1.0` dans `app/build.gradle.kts` : il ne suit pas encore le tag ([issue #161](https://github.com/castorfou/lmelp-mobile/issues/161)).

## En tant qu'utilisateur : mettre à jour

- **Les données** : l'app vérifie au lancement et affiche un badge sur l'icône des réglages quand une mise à jour est disponible ; l'utilisateur l'applique depuis l'écran À propos, puis rouvre l'app.
- **L'appli** : sur le téléphone, ouvrir la page [Releases](https://github.com/castorfou/lmelp-mobile/releases) du dépôt, télécharger le dernier `lmelp-mobile-vX.Y.Z.apk`, l'ouvrir et choisir « Mettre à jour ». La signature est la même, l'état local est conservé. L'app ne signale pas encore d'elle-même une nouvelle version de l'appli ([issue #162](https://github.com/castorfou/lmelp-mobile/issues/162)).

Le guide utilisateur [Installer et mettre à jour l'appli](../user/installer_mettre_a_jour.md) reprend ces étapes pas à pas.

## Debug et release sur le même téléphone

Un seul des deux APK peut être installé à la fois. Tester un APK debug fait perdre la version release installée, et inversement : `adb uninstall com.lmelp.mobile` d'abord, puis réinstallation. L'installation côte à côte des deux versions est suivie dans l'[issue #160](https://github.com/castorfou/lmelp-mobile/issues/160).

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
