"""Tests TDD pour l'option CLI --print-room-version (issue #132).

Permet à docker_export_and_publish_release.sh de résoudre le tag de release
GitHub data-v{ROOM_VERSION} sans connexion MongoDB/Calibre.
"""

import sys
from pathlib import Path

from click.testing import CliRunner


sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))

import export_mongo_to_sqlite as script


def test_print_room_version_affiche_room_version_et_sort_sans_erreur():
    runner = CliRunner()
    result = runner.invoke(script.main, ["--print-room-version"])

    assert result.exit_code == 0
    assert result.output.strip() == str(script.ROOM_VERSION)


def test_print_room_version_ne_necessite_pas_mongo_uri():
    """--print-room-version doit fonctionner sans MONGO_URI ni connexion réseau."""
    runner = CliRunner()
    result = runner.invoke(
        script.main, ["--print-room-version"], env={"LMELP_MONGO_URI": ""}
    )

    assert result.exit_code == 0
