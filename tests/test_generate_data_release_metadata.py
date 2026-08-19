"""
Tests du script scripts/generate_data_release_metadata.py.

Ce script génère metadata.json à partir d'un lmelp.db, pour publication comme
asset secondaire de la GitHub Release data-latest (voir issue #116 / ADR
docs/dev/adr/0001-separation-maj-appli-donnees.md). metadata.json permet à une
future implémentation app de vérifier si une mise à jour est disponible sans
télécharger tout le fichier lmelp.db (~4-5 Mo).
"""

import hashlib
import sqlite3
import sys
from pathlib import Path

import pytest


sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))

from generate_data_release_metadata import build_metadata


def _make_db(path, metadata: dict):
    con = sqlite3.connect(path)
    con.execute("CREATE TABLE db_metadata (key TEXT PRIMARY KEY, value TEXT)")
    con.executemany(
        "INSERT INTO db_metadata (key, value) VALUES (?, ?)",
        list(metadata.items()),
    )
    con.commit()
    con.close()


@pytest.fixture
def db_path(tmp_path):
    path = tmp_path / "lmelp.db"
    _make_db(
        path,
        {
            "export_date": "2026-08-19",
            "export_datetime": "2026-08-19T10:00:00",
            "version": "1755600000",
            "nb_emissions": "42",
            "nb_livres": "123",
            "nb_avis": "456",
        },
    )
    return path


class TestBuildMetadata:
    def test_contient_les_champs_attendus(self, db_path):
        metadata = build_metadata(db_path)

        assert metadata["schema_version"] == 1
        assert metadata["export_date"] == "2026-08-19"
        assert metadata["export_datetime"] == "2026-08-19T10:00:00"
        assert metadata["export_version"] == "1755600000"
        assert metadata["nb_emissions"] == 42
        assert metadata["nb_livres"] == 123
        assert metadata["nb_avis"] == 456
        assert metadata["filename"] == "lmelp.db"

    def test_file_size_bytes_correspond_a_la_taille_reelle(self, db_path):
        metadata = build_metadata(db_path)
        assert metadata["file_size_bytes"] == db_path.stat().st_size

    def test_sha256_correspond_au_contenu_du_fichier(self, db_path):
        metadata = build_metadata(db_path)
        expected = hashlib.sha256(db_path.read_bytes()).hexdigest()
        assert metadata["sha256"] == expected

    def test_sha256_change_si_le_contenu_change(self, db_path):
        metadata_before = build_metadata(db_path)

        con = sqlite3.connect(db_path)
        con.execute(
            "UPDATE db_metadata SET value = ? WHERE key = 'export_date'",
            ("2026-08-20",),
        )
        con.commit()
        con.close()

        metadata_after = build_metadata(db_path)
        assert metadata_after["sha256"] != metadata_before["sha256"]

    def test_erreur_claire_si_db_metadata_absente(self, tmp_path):
        path = tmp_path / "empty.db"
        con = sqlite3.connect(path)
        con.execute("CREATE TABLE autre_table (id INTEGER)")
        con.commit()
        con.close()

        with pytest.raises(ValueError, match="db_metadata"):
            build_metadata(path)

    def test_erreur_claire_si_db_metadata_vide(self, tmp_path):
        path = tmp_path / "vide.db"
        _make_db(path, {})

        with pytest.raises(ValueError, match="db_metadata"):
            build_metadata(path)
