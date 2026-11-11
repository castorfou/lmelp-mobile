"""Numéro de la préversion de l'appli (issue #173).

``scripts/preview_version.sh`` affiche ``X.Y.Z+N`` : X.Y.Z est la dernière version
stable ``vX.Y.Z`` (sans tiret), N le nombre de commits depuis. Il n'affiche rien quand
N = 0 (le commit tagué lui-même : ce serait un doublon de la version) ou quand aucune
version n'existe encore. Exécuté pour de vrai sur des dépôts git temporaires.
"""

from __future__ import annotations

import subprocess
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts" / "preview_version.sh"


def _git(repo: Path, *args: str) -> None:
    subprocess.run(
        ["git", "-c", "user.name=t", "-c", "user.email=t@example.org", *args],
        cwd=repo,
        check=True,
        capture_output=True,
    )


def _commit(repo: Path, n: int = 1) -> None:
    for _ in range(n):
        _git(repo, "commit", "--allow-empty", "-m", "c")


@pytest.fixture
def repo(tmp_path: Path) -> Path:
    _git(tmp_path, "init", "-q", "-b", "main")
    _commit(tmp_path)
    return tmp_path


def _preview_version(repo: Path) -> str:
    result = subprocess.run(
        ["bash", str(SCRIPT)], cwd=repo, check=True, capture_output=True, text=True
    )
    return result.stdout.strip()


def test_commits_depuis_la_derniere_version(repo: Path) -> None:
    _git(repo, "tag", "v1.2.0")
    _commit(repo, 3)
    assert _preview_version(repo) == "1.2.0+3"


def test_rien_sur_le_commit_tague(repo: Path) -> None:
    _git(repo, "tag", "v1.2.0")
    assert _preview_version(repo) == ""


def test_rien_sans_aucune_version(repo: Path) -> None:
    _commit(repo, 2)
    assert _preview_version(repo) == ""


def test_la_version_la_plus_proche_compte(repo: Path) -> None:
    _git(repo, "tag", "v1.1.0")
    _commit(repo)
    _git(repo, "tag", "v1.2.0")
    _commit(repo, 2)
    assert _preview_version(repo) == "1.2.0+2"


def test_pre_releases_et_tags_de_donnees_ignores(repo: Path) -> None:
    _git(repo, "tag", "v1.2.0")
    _commit(repo)
    _git(repo, "tag", "v0.0.0-test")
    _git(repo, "tag", "data-v9")
    _git(repo, "tag", "app-preview")
    _commit(repo)
    assert _preview_version(repo) == "1.2.0+2"


def test_tag_annote_pris_en_compte(repo: Path) -> None:
    _git(repo, "tag", "-a", "v1.3.0", "-m", "1.3.0")
    _commit(repo)
    assert _preview_version(repo) == "1.3.0+1"
