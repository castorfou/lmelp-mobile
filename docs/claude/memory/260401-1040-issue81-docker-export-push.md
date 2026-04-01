# Issue #81 — Docker export et push DB vers téléphone Android

## Contexte

Faciliter la mise à jour de la base de données SQLite sur le téléphone Android après chaque nouvelle émission, sans recompiler l'APK.

## Solution retenue

Un container Docker dédié (`lmelp-export`) qui orchestre tout :
1. Export MongoDB → SQLite (avec données Calibre)
2. Vérification d'intégrité
3. Push via ADB vers le téléphone connecté en USB sur le laptop
4. Redémarrage de l'app Android

## Commande utilisateur finale

```bash
adb -a start-server   # flag -a obligatoire pour écouter sur 0.0.0.0 (pas seulement 127.0.0.1)
docker compose --profile export run --rm lmelp-export
```

## Fichiers créés

- `Dockerfile.export` — image Python 3.11-slim + gcc + android-tools-adb + dépendances via `pip install -e .`
- `scripts/docker_export_and_push.sh` — script entrypoint du container
- `.github/workflows/build-export-image.yml` — CI/CD publication sur `ghcr.io/castorfou/lmelp-mobile-export`

## Fichiers modifiés

- `CLAUDE.md` — section "Mise à jour DB sans rebuild APK"
- `docs/dev/build_deploy_apk.md` — section "mise à jour DB sans rebuild APK (issue #81)"
- `docs/user/mise_a_jour_episode.md` — section "Passer la nouvelle base vers l'application mobile"

## Points techniques importants

### numpy<2 obligatoire
`scikit-surprise` est compilé pour numpy 1.x — incompatible avec numpy 2.x.
Solution : `pip install -e .` depuis `pyproject.toml` qui contraint déjà `numpy>=1.26,<2.0.0`.
Ne pas lister les dépendances manuellement dans le Dockerfile — utiliser `pip install -e .` pour rester synchronisé avec `pyproject.toml`.

### ADB doit écouter sur 0.0.0.0
Par défaut `adb start-server` écoute sur `127.0.0.1:5037` uniquement.
Le container ne peut pas l'atteindre via `host-gateway`.
Solution : `adb -a start-server` (flag `-a` = toutes les interfaces).
Vérification : `ss -tlnp | grep 5037` doit afficher `0.0.0.0:5037`.

### Nom du réseau Docker
Le réseau défini comme `lmelp-network` dans `docker-compose.yml` est préfixé par le nom du projet Docker Compose.
Sur ce laptop : `lmelp-stack_lmelp-network`.
Dans le service docker-compose, utiliser `networks: lmelp-network` suffit (Docker Compose gère le préfixe).

### extra_hosts pour host-gateway
Pour que le container atteigne le daemon ADB du laptop :
```yaml
extra_hosts:
  - "host-gateway:host-gateway"
```
Et dans le script : `adb -H host-gateway -P 5037 ...`

### app debug requise pour run-as
Le script utilise `adb shell run-as com.lmelp.mobile cp ...` pour copier la DB dans le répertoire privé de l'app.
Cela nécessite une **build debug** installée sur le téléphone.
Si l'app n'est pas debuggable, le script échoue avec un message clair.

## Issue liée dans docker-lmelp
[docker-lmelp#41](https://github.com/castorfou/docker-lmelp/issues/41) — ajouter le service `lmelp-export` dans `docker-compose.yml`

## Diagnostic ADB

Si le téléphone n'est pas reconnu :
- Vérifier `lsusb` — le téléphone Google/Pixel apparaît avec vendor ID `18d1`
- Vérifier les permissions : `ls -la /dev/bus/usb/XXX/YYY` doit être `plugdev`
- Vérifier que l'utilisateur est dans le groupe `plugdev` : `groups`
- Règle udev si nécessaire : `echo 'SUBSYSTEM=="usb", ATTR{idVendor}=="18d1", MODE="0666", GROUP="plugdev"' | sudo tee /etc/udev/rules.d/51-android.rules`
- Mode USB téléphone : **Transfert de fichiers** (pas "Aucun transfert de données")
- Débogage USB activé dans Options développeur
