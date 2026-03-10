# Issue #12 — Fix triple activation du venv Python dans VSCode

Date : 2026-03-10
Branche : `12-tech-venv-activate`

## Problème

À l'ouverture d'un terminal VSCode dans le devcontainer, le venv s'activait 3 fois :
```
source /workspaces/lmelp-mobile/.venv/bin/activate
vscode ➜ /workspaces/lmelp-mobile $ source /workspaces/lmelp-mobile/.venv/bin/activate
(.venv) vscode ➜ /workspaces/lmelp-mobile $  source /workspaces/lmelp-mobile/.venv/bin/activate
(.venv) vscode ➜ /workspaces/lmelp-mobile $
```

## Causes identifiées (investigation complète)

**3 sources d'activation indépendantes :**

1. **Extension `ms-python.python`** — `python.terminal.activateEnvironment: true` active le venv détecté via `python.defaultInterpreterPath`.

2. **Extension `ms-python.vscode-python-envs`** — nouvelle extension Python Envs, active via `VSCODE_PYTHON_BASH_ACTIVATE` injectée dans le shell integration VSCode. Paramètre : `python-envs.terminal.autoActivationType` (valeurs : `command`, `shellStartup`, `off`).

3. **`~/.bashrc`** — lors des tentatives précédentes de fix, `source .venv/bin/activate` avait été ajouté au shell rc.

## Solution finale

### Désactiver les 2 extensions Python (pérenne via devcontainer)

Dans `.devcontainer/devcontainer.json`, section `customizations.vscode.settings` :
```json
"python.terminal.activateEnvironment": false,
"python-envs.terminal.autoActivationType": "off"
```

Ces settings vont aussi dans `/home/vscode/.vscode-server/data/Machine/settings.json` pour la session courante (scope `machine`, non persisté au rebuild sans devcontainer.json).

### Guard dans `~/.bashrc` (unique et contrôlé)

Ajouté manuellement pour la session courante, et via `postCreateCommand.sh` pour le rebuild :
```bash
# Activation automatique du venv Python (une seule fois)
if [ -f "/workspaces/lmelp-mobile/.venv/bin/activate" ] && [ -z "$VIRTUAL_ENV" ]; then
    source /workspaces/lmelp-mobile/.venv/bin/activate
fi
```

Le guard dans `postCreateCommand.sh` vérifie que la ligne n'est pas déjà présente avant d'ajouter.

## Résultat

**0 activation automatique par VSCode** + **1 activation via guard `.bashrc`** = prompt propre `(.venv)`.

## Règles à retenir

Dans un devcontainer VSCode + Python avec extensions ms-python.python ET ms-python.vscode-python-envs :
- **Désactiver les deux** : `python.terminal.activateEnvironment: false` et `python-envs.terminal.autoActivationType: "off"`
- **Utiliser un guard dans `.bashrc`** avec `[ -z "$VIRTUAL_ENV" ]` pour une activation unique et contrôlée
- **Persister via `devcontainer.json`** (settings) et `postCreateCommand.sh` (guard bashrc) pour survivre au rebuild
- Les Machine settings (`/home/vscode/.vscode-server/data/Machine/settings.json`) sont perdus au rebuild → toujours dupliquer dans `devcontainer.json`
