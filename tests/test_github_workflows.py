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
from typing import Any

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


@pytest.mark.parametrize("workflow", ["release.yml", "app-preview.yml"])
def test_release_checkout_fetches_full_history(workflow: str) -> None:
    """Issue #162 : l'historique de l'écran À propos vient de `git log` au build.

    Avec le clone superficiel par défaut d'actions/checkout (fetch-depth 1), l'APK de
    release (v0.9.0) n'affichait qu'un seul commit. La préversion (#173) calcule en plus
    N par `git describe`, qui a besoin des tags.
    """
    release = yaml.safe_load((WORKFLOWS_DIR / workflow).read_text(encoding="utf-8"))
    checkouts = [
        step
        for job in release["jobs"].values()
        for step in job["steps"]
        if str(step.get("uses", "")).startswith("actions/checkout@")
    ]
    assert checkouts, f"{workflow} doit faire un actions/checkout"
    for step in checkouts:
        assert (step.get("with") or {}).get("fetch-depth") == 0, (
            f"{workflow} : actions/checkout doit avoir fetch-depth: 0"
        )


# --- Préversion de l'appli (issue #173) -------------------------------------------

APP_PREVIEW = WORKFLOWS_DIR / "app-preview.yml"


def _app_preview() -> dict[Any, Any]:
    workflow: dict[Any, Any] = yaml.safe_load(APP_PREVIEW.read_text(encoding="utf-8"))
    return workflow


def _app_preview_trigger() -> dict[Any, Any]:
    wf = _app_preview()
    # PyYAML lit la clé `on` comme le booléen True
    trigger: dict[Any, Any] = wf["on"] if "on" in wf else wf[True]
    return trigger


def test_app_preview_on_push_to_main_only():
    trigger = _app_preview_trigger()
    assert set(trigger) == {"push"}, "app-preview.yml : déclenché par push uniquement"
    assert trigger["push"]["branches"] == ["main"]
    assert "tags" not in trigger["push"]


def test_app_preview_paths_limited_to_the_app():
    paths = set(_app_preview_trigger()["push"]["paths"])
    for path in (
        "app/**",
        "buildSrc/**",
        "gradle/**",
        "*.gradle.kts",
        "gradle.properties",
    ):
        assert path in paths, f"app-preview.yml : {path} doit déclencher une préversion"
    assert not any(p.startswith("docs") for p in paths), (
        "un merge qui ne touche que la doc ne doit rien publier"
    )


def test_app_preview_only_last_of_close_merges_is_published():
    concurrency = _app_preview()["concurrency"]
    assert concurrency["group"] == "app-preview"
    assert concurrency["cancel-in-progress"] is True


def test_app_preview_can_write_releases():
    wf = _app_preview()
    permissions = (
        wf.get("permissions") or next(iter(wf["jobs"].values()))["permissions"]
    )
    assert permissions["contents"] == "write"


def test_app_preview_version_from_script_and_skipped_when_empty():
    text = APP_PREVIEW.read_text(encoding="utf-8")
    assert "scripts/preview_version.sh" in text
    assert re.search(r"assembleRelease\s+-PversionName=", text)
    # Rien n'est publié quand le script n'affiche rien (N = 0 ou pas de version)
    steps = next(iter(_app_preview()["jobs"].values()))["steps"]
    after_version = steps[
        [
            i
            for i, s in enumerate(steps)
            if "preview_version.sh" in str(s.get("run", ""))
        ][0]
        + 1 :
    ]
    assert after_version
    for step in after_version:
        assert "if" in step, (
            f"étape « {step.get('name')} » à sauter quand il n'y a pas de préversion"
        )


def test_app_preview_signed_and_verified_like_release():
    text = APP_PREVIEW.read_text(encoding="utf-8")
    assert "KEYSTORE_BASE64" in text
    assert "LMELP_KEYSTORE_FILE" in text
    assert "verify --print-certs" in text
    assert re.search(r"aapt2\"? dump badging", text)


def test_app_preview_rotating_prerelease_named_after_version():
    text = APP_PREVIEW.read_text(encoding="utf-8")
    assert "gh release delete app-preview" in text
    assert "--cleanup-tag" in text
    # Commande complète, lignes continuées par « \ » comprises
    create = re.search(r"gh release create app-preview(?:[^\n]*\\\n)*[^\n]*", text)
    assert create, "app-preview.yml doit recréer la release app-preview"
    cmd = create.group(0)
    assert "--prerelease" in cmd
    # La version X.Y.Z+N est lue dans le nom de la release, pas dans l'asset (+ réécrit)
    assert re.search(
        r"--title\s+\"?\$\{?\{?\s*(steps\.version\.outputs\.VERSION|VERSION)", cmd
    )
    assert "--target" in cmd


def test_app_preview_asset_name_has_no_plus():
    text = APP_PREVIEW.read_text(encoding="utf-8")
    names = re.findall(r"dist/(lmelp-mobile-[^\s\"']+\.apk)", text)
    assert names, "l'APK de la préversion doit être copié dans dist/"
    for name in names:
        # ${VERSION/+/.} : substitution bash qui remplace le « + » par un point
        assert "+" not in name.replace("${VERSION/+/.}", "")
        assert "VERSION" not in name or "${VERSION/+/.}" in name
