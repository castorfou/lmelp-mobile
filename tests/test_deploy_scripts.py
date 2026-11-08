"""Scripts de déploiement de l'APK debug (issue #160).

L'APK debug a l'identifiant ``com.lmelp.mobile.debug`` pour cohabiter avec l'APK
de release (``com.lmelp.mobile``). Les scripts qui pilotent le téléphone par adb
doivent viser le paquet debug, et ne jamais toucher à la release.

``scripts/deploy.sh`` est exécuté pour de vrai, dans un faux projet, avec un faux
``adb`` qui journalise ses appels.
"""

from __future__ import annotations

import os
import shlex
import shutil
import subprocess
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parent.parent
DEPLOY_SH = ROOT / "scripts" / "deploy.sh"
EXPORT_AND_PUSH_SH = ROOT / "scripts" / "docker_export_and_push.sh"

DEBUG_PACKAGE = "com.lmelp.mobile.debug"
RELEASE_PACKAGE = "com.lmelp.mobile"
DEBUG_COMPONENT = f"{DEBUG_PACKAGE}/com.lmelp.mobile.MainActivity"

FAKE_ADB = """#!/usr/bin/env bash
echo "$*" >> "$FAKE_ADB_LOG"
case "$*" in
  devices) printf 'List of devices attached\\nSERIAL123\\tdevice\\n' ;;
  "shell pm list packages")
    for p in $FAKE_INSTALLED; do echo "package:$p"; done ;;
  "install -r "*) echo "${FAKE_INSTALL_R_OUTPUT:-Success}" ;;
  *) echo "Success" ;;
esac
"""


def _run_deploy(
    tmp_path: Path, installed: list[str], install_r_output: str = "Success"
) -> list[str]:
    project = tmp_path / "project"
    (project / "app/build/outputs/apk/debug").mkdir(parents=True)
    (project / "app/build/outputs/apk/debug/app-debug.apk").write_bytes(b"apk")
    (project / "gradlew").write_text("#!/bin/sh\n")
    scripts = project / "scripts"
    scripts.mkdir()
    shutil.copy(DEPLOY_SH, scripts / "deploy.sh")

    sdk = tmp_path / "sdk" / "platform-tools"
    sdk.mkdir(parents=True)
    adb = sdk / "adb"
    adb.write_text(FAKE_ADB)
    adb.chmod(0o755)
    log = tmp_path / "adb.log"
    log.touch()

    env = {
        **os.environ,
        "ANDROID_HOME": str(tmp_path / "sdk"),
        "FAKE_ADB_LOG": str(log),
        "FAKE_INSTALLED": " ".join(installed),
        "FAKE_INSTALL_R_OUTPUT": install_r_output,
    }
    result = subprocess.run(
        ["bash", str(scripts / "deploy.sh")],
        cwd=project,
        env=env,
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode == 0, result.stdout + result.stderr
    return log.read_text().splitlines()


def _targets_release(call: str) -> bool:
    return RELEASE_PACKAGE in shlex.split(call)


@pytest.mark.parametrize(
    "installed",
    [[], [RELEASE_PACKAGE], [RELEASE_PACKAGE, DEBUG_PACKAGE]],
    ids=["rien", "release seule", "release et debug"],
)
def test_deploy_installe_et_lance_la_version_debug(tmp_path, installed):
    calls = _run_deploy(tmp_path, installed)

    assert any(c.startswith("install") and "app-debug.apk" in c for c in calls), calls
    assert f"shell am start -n {DEBUG_COMPONENT}" in calls, calls
    assert not any(_targets_release(c) for c in calls), calls
    assert not any(c.startswith("uninstall") for c in calls), calls


def test_deploy_signature_incompatible_desinstalle_seulement_la_debug(tmp_path):
    calls = _run_deploy(
        tmp_path,
        [RELEASE_PACKAGE, DEBUG_PACKAGE],
        install_r_output="Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: ...]",
    )

    assert f"uninstall {DEBUG_PACKAGE}" in calls, calls
    assert not any(_targets_release(c) for c in calls), calls
    assert f"shell am start -n {DEBUG_COMPONENT}" in calls, calls


def test_export_and_push_vise_la_version_debug():
    script = EXPORT_AND_PUSH_SH.read_text(encoding="utf-8")

    # run-as n'existe que sur une app debuggable : le défaut est le paquet debug
    assert f'APP_PACKAGE="${{APP_PACKAGE:-{DEBUG_PACKAGE}}}"' in script
    # « .MainActivity » se résoudrait en com.lmelp.mobile.debug.MainActivity
    assert "/.MainActivity" not in script
    assert '"${APP_PACKAGE}/com.lmelp.mobile.MainActivity"' in script
