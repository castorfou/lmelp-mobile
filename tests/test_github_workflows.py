"""Garde-fous sur les workflows GitHub Actions (issue #159).

Les actions doivent tourner sous Node 24 : les runners GitHub ne supportent plus
Node 20 (avertissement « Node.js 20 is deprecated »). La table ``NODE24_MIN_MAJOR``
donne, pour chaque action utilisée, la première version majeure sous Node 24.
Une action absente de la table fait échouer le test : vérifier son ``runs.using``
dans son ``action.yml`` avant de l'ajouter.
"""

from __future__ import annotations

import re
from pathlib import Path

import pytest
import yaml  # type: ignore[import-untyped]


ROOT = Path(__file__).resolve().parent.parent
WORKFLOWS_DIR = ROOT / ".github" / "workflows"
DEPENDABOT = ROOT / ".github" / "dependabot.yml"
APP_GRADLE = ROOT / "app" / "build.gradle.kts"

NODE24_MIN_MAJOR = {
    # Actions propres à lmelp-mobile
    "actions/checkout": 7,
    "actions/setup-java": 6,
    "actions/setup-python": 7,
    "actions/upload-artifact": 7,
    "android-actions/setup-android": 4,
    "softprops/action-gh-release": 3,
    "docker/login-action": 4,
    "docker/metadata-action": 6,
    "docker/build-push-action": 7,
    # Actions venant du template PyFoundry (déjà sous Node 24 depuis 1.1.2)
    "astral-sh/setup-uv": 10,
    "actions/configure-pages": 6,
    "actions/upload-pages-artifact": 5,
    "actions/deploy-pages": 5,
}

USES_RE = re.compile(r"^\s*-?\s*uses:\s*([^@\s]+)@v?(\d+)", re.MULTILINE)


def _workflow_uses() -> list[tuple[str, str, int]]:
    uses = []
    for wf in sorted(WORKFLOWS_DIR.glob("*.yml")):
        for action, major in USES_RE.findall(wf.read_text(encoding="utf-8")):
            uses.append((wf.name, action, int(major)))
    return uses


def test_workflows_reference_actions():
    assert _workflow_uses(), "aucune action trouvée dans .github/workflows"


@pytest.mark.parametrize(("workflow", "action", "major"), _workflow_uses())
def test_action_runs_on_node24(workflow: str, action: str, major: int) -> None:
    assert action in NODE24_MIN_MAJOR, (
        f"{workflow} : action {action} inconnue, vérifier son runtime Node "
        "puis l'ajouter à NODE24_MIN_MAJOR"
    )
    assert major >= NODE24_MIN_MAJOR[action], (
        f"{workflow} : {action}@v{major} tourne sous Node < 24, "
        f"passer à v{NODE24_MIN_MAJOR[action]} ou plus"
    )


def test_no_abandoned_sign_android_release_action():
    for wf in WORKFLOWS_DIR.glob("*.yml"):
        actions = {a for a, _ in USES_RE.findall(wf.read_text(encoding="utf-8"))}
        assert "r0adkll/sign-android-release" not in actions, (
            f"{wf.name} : r0adkll/sign-android-release tourne sous Node 12, "
            "signer via signingConfigs Gradle"
        )


def test_dependabot_updates_github_actions():
    assert DEPENDABOT.exists(), ".github/dependabot.yml manquant"
    config = yaml.safe_load(DEPENDABOT.read_text(encoding="utf-8"))
    ecosystems = {
        (u["package-ecosystem"], u["directory"]) for u in config.get("updates", [])
    }
    assert ("github-actions", "/") in ecosystems


def test_release_signing_config_read_from_env():
    gradle = APP_GRADLE.read_text(encoding="utf-8")
    assert "signingConfigs" in gradle
    for var in (
        "LMELP_KEYSTORE_FILE",
        "LMELP_KEYSTORE_PASSWORD",
        "LMELP_KEY_ALIAS",
        "LMELP_KEY_PASSWORD",
    ):
        assert f'System.getenv("{var}")' in gradle, (
            f"{var} non lu dans build.gradle.kts"
        )


def test_release_workflow_signs_with_gradle():
    release = (WORKFLOWS_DIR / "release.yml").read_text(encoding="utf-8")
    assert "KEYSTORE_BASE64" in release
    assert "LMELP_KEYSTORE_FILE" in release
    assert "apksigner" in release
    assert "verify --print-certs" in release


def test_release_workflow_passes_tag_version_to_gradle():
    """Issue #161 : la version de l'APK vient du tag, pas d'une valeur fixe."""
    release = (WORKFLOWS_DIR / "release.yml").read_text(encoding="utf-8")
    assert re.search(r"assembleRelease\s+-PversionName=", release), (
        "release.yml doit passer -PversionName=<tag sans v> à assembleRelease"
    )
    assert re.search(r"aapt2\"? dump badging", release), (
        "release.yml doit vérifier le versionName de l'APK produit"
    )


def test_app_version_not_hardcoded():
    gradle = APP_GRADLE.read_text(encoding="utf-8")
    assert "AppVersion.resolve(" in gradle
    assert 'versionName = "1.0"' not in gradle


def test_ci_runs_buildsrc_tests():
    """Gradle 8 ne lance plus les tests de buildSrc pendant le build."""
    ci = (WORKFLOWS_DIR / "ci.yml").read_text(encoding="utf-8")
    assert "-p buildSrc test" in ci
