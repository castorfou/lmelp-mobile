# Issue #12 — Fix triple activation du venv Python dans VSCode

Date : 2026-03-10
Branche : `12-tech-venv-activate`

## Problème

À l'ouverture d'un terminal VSCode dans le devcontainer, le venv s'activait 3 fois :
```
(venv) (venv) (venv) user@host:~$
```

## Causes identifiées

**3 sources d'activation indépendantes :**

1. **`~/.bashrc` / `~/.zshrc`** — `postCreateCommand.sh` ajoutait `source .venv/bin/activate` au shell rc au `postCreate`. Déclenché à chaque ouverture de terminal bash.

2. **`python.terminal.activateEnvironment: true`** dans `.vscode/settings.json` — l'extension Python VSCode active automatiquement le venv détecté.

3. **`python.terminal.activateEnvInCurrentTerminal: true`** + **`terminal.integrated.env.linux`** avec `VIRTUAL_ENV` et `PATH` venv — dans `.devcontainer/devcontainer.json`, duplication des paramètres VSCode.

## Fix appliqué

### `postCreateCommand.sh`
Suppression du bloc (lignes 65-71 de l'original) :
```bash
# SUPPRIMÉ :
echo "Configuration de l'activation automatique..."
PROJECT_PATH=$(pwd)
for shell_config in "$HOME/.bashrc" "$HOME/.zshrc"; do
    if [[ -f "$shell_config" ]] && ! grep -q "source $PROJECT_PATH/.venv/bin/activate" "$shell_config"; then
        echo "source $PROJECT_PATH/.venv/bin/activate" >> "$shell_config"
    fi
done
```
→ VSCode gère l'activation nativement, pas besoin de polluer le shell rc.

### `.devcontainer/devcontainer.json`
Suppression de deux paramètres redondants dans `customizations.vscode.settings` :
- `python.terminal.activateEnvInCurrentTerminal: true` (doublon de `activateEnvironment`)
- `terminal.integrated.env.linux` avec `VIRTUAL_ENV` et PATH venv (forçait une 3e activation)

## Résultat

**1 seule activation** via `python.terminal.activateEnvironment: true` dans `.vscode/settings.json`.

## Règle à retenir

Dans un devcontainer VSCode + Python :
- **Utiliser uniquement** `python.terminal.activateEnvironment: true` (dans `settings.json` ou `devcontainer.json`, pas les deux)
- **Ne pas** ajouter `source .venv/bin/activate` dans `~/.bashrc` depuis `postCreateCommand.sh`
- **Ne pas** utiliser `terminal.integrated.env.linux` pour forcer `VIRTUAL_ENV` si l'extension Python gère déjà l'activation
