"""Garde-fous sur la chaîne de build Android (issue #158).

Gradle 9 + AGP 9 : AGP 9.5 exige Gradle 9.6 au minimum, et active le Kotlin
intégré (« built-in Kotlin ») : le plugin ``org.jetbrains.kotlin.android`` ne doit
plus être appliqué au module, et ``kotlinOptions`` disparaît au profit de
``kotlin { compilerOptions { ... } }``. Les opt-out ``android.builtInKotlin=false``
et ``android.newDsl=false`` ne font que repousser la migration : interdits ici.
"""

from __future__ import annotations

import re
import tomllib
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
WRAPPER_PROPERTIES = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"
VERSION_CATALOG = ROOT / "gradle" / "libs.versions.toml"
APP_GRADLE = ROOT / "app" / "build.gradle.kts"
GRADLE_PROPERTIES = ROOT / "gradle.properties"

MIN_GRADLE = (9, 6)
MIN_AGP_MAJOR = 9


def _version_tuple(version: str) -> tuple[int, ...]:
    return tuple(int(part) for part in re.findall(r"\d+", version))


def _catalog_versions() -> dict[str, str]:
    with VERSION_CATALOG.open("rb") as f:
        versions: dict[str, str] = tomllib.load(f)["versions"]
    return versions


def test_gradle_wrapper_is_gradle_9():
    text = WRAPPER_PROPERTIES.read_text(encoding="utf-8")
    match = re.search(r"gradle-([\d.]+)-(?:bin|all)\.zip", text)
    assert match, "distributionUrl introuvable dans gradle-wrapper.properties"
    version = _version_tuple(match.group(1))
    assert version[:2] >= MIN_GRADLE, (
        f"Gradle {match.group(1)} : AGP 9.5 exige Gradle ≥ {'.'.join(map(str, MIN_GRADLE))}"
    )


def test_agp_is_9():
    agp = _catalog_versions()["agp"]
    assert _version_tuple(agp)[0] >= MIN_AGP_MAJOR, f"AGP {agp} : attendu AGP 9.x"


def test_compose_plugin_matches_kotlin_version():
    # Le plugin Compose est versionné avec Kotlin : les deux doivent rester alignés.
    with VERSION_CATALOG.open("rb") as f:
        plugins = tomllib.load(f)["plugins"]
    assert plugins["kotlin-compose"]["version"]["ref"] == "kotlin"


def test_app_uses_built_in_kotlin():
    gradle = APP_GRADLE.read_text(encoding="utf-8")
    assert "libs.plugins.kotlin.android" not in gradle, (
        "AGP 9 intègre Kotlin : ne plus appliquer org.jetbrains.kotlin.android"
    )
    assert "kotlinOptions" not in gradle, (
        "kotlinOptions n'existe plus avec le Kotlin intégré : kotlin { compilerOptions }"
    )
    assert "compilerOptions" in gradle


def test_no_agp9_opt_out():
    props = GRADLE_PROPERTIES.read_text(encoding="utf-8")
    for opt_out in ("android.builtInKotlin=false", "android.newDsl=false"):
        assert opt_out not in props.replace(" ", ""), (
            f"opt-out AGP 9 interdit : {opt_out}"
        )
