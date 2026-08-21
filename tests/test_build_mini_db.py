"""Tests TDD pour build_mini_db — extrait minimal de lmelp.db embarqué dans l'APK (issue #119)."""

import sqlite3
import sys
from datetime import UTC, datetime
from pathlib import Path


sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))

import build_mini_db as script
import export_mongo_to_sqlite as export_script


def _make_source_db(tmp_path: Path, nb_emissions: int = 5) -> Path:
    """Crée une DB source complète (schéma réel) avec N émissions et données liées."""
    db_path = tmp_path / "source.db"
    con = sqlite3.connect(db_path)
    con.executescript(export_script.SCHEMA_SQL)

    con.execute("INSERT INTO critiques VALUES ('c1', 'Jean Dupont', 1, 0)")
    con.execute("INSERT INTO critiques VALUES ('c2', 'Marie Martin', 0, 0)")
    con.execute("INSERT INTO auteurs VALUES ('au1', 'Victor Hugo', NULL)")

    for i in range(nb_emissions):
        ep_id, em_id, livre_id, avis_id = f"ep{i}", f"em{i}", f"l{i}", f"a{i}"
        date = f"2020-01-{i + 1:02d}T00:00:00Z"
        con.execute(
            "INSERT INTO episodes VALUES (?, ?, ?, ?, ?, ?, 0)",
            (
                ep_id,
                f"Titre épisode {i}",
                date,
                f"Description {i}",
                f"https://url/{i}",
                3600,
            ),
        )
        con.execute(
            "INSERT INTO emissions VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (em_id, ep_id, date, 3600, "c1", 1, 0, date, date),
        )
        con.execute(
            "INSERT INTO livres VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                livre_id,
                f"Livre {i}",
                "au1",
                "Victor Hugo",
                "Gallimard",
                None,
                None,
                date,
                date,
            ),
        )
        con.execute(
            "INSERT INTO emission_livres VALUES (?, ?)",
            (em_id, livre_id),
        )
        con.execute(
            "INSERT INTO avis VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                avis_id,
                em_id,
                livre_id,
                "c1",
                8.0,
                "Bon livre",
                f"Livre {i}",
                "Victor Hugo",
                "Jean Dupont",
                1,
                "critique",
                date,
            ),
        )
        con.execute(
            "INSERT INTO avis_critiques VALUES (?, ?, ?, ?, ?, ?, ?)",
            (
                f"ac{i}",
                em_id,
                f"Titre épisode {i}",
                date,
                f"Résumé {i}",
                "Jean Dupont",
                "{}",
            ),
        )

    con.commit()
    con.close()
    return db_path


class TestSelectionEmissions:
    def test_garde_seulement_n_emissions_les_plus_anciennes(self, tmp_path):
        source = _make_source_db(tmp_path, nb_emissions=10)
        dest = tmp_path / "mini.db"

        script.build_mini_db(source, dest, nb_emissions=3)

        con = sqlite3.connect(dest)
        rows = con.execute("SELECT id FROM emissions ORDER BY date").fetchall()
        assert [r[0] for r in rows] == ["em0", "em1", "em2"]

    def test_erreur_si_moins_demissions_disponibles_que_demande(self, tmp_path):
        source = _make_source_db(tmp_path, nb_emissions=2)
        dest = tmp_path / "mini.db"

        import pytest

        with pytest.raises(ValueError):
            script.build_mini_db(source, dest, nb_emissions=5)


class TestTablesLiees:
    def test_episodes_filtres_sur_emissions_gardees(self, tmp_path):
        source = _make_source_db(tmp_path, nb_emissions=10)
        dest = tmp_path / "mini.db"
        script.build_mini_db(source, dest, nb_emissions=3)

        con = sqlite3.connect(dest)
        count = con.execute("SELECT COUNT(*) FROM episodes").fetchone()[0]
        assert count == 3

    def test_avis_filtres_sur_emissions_gardees(self, tmp_path):
        source = _make_source_db(tmp_path, nb_emissions=10)
        dest = tmp_path / "mini.db"
        script.build_mini_db(source, dest, nb_emissions=3)

        con = sqlite3.connect(dest)
        emission_ids = {
            r[0] for r in con.execute("SELECT DISTINCT emission_id FROM avis")
        }
        assert emission_ids == {"em0", "em1", "em2"}

    def test_livres_filtres_par_reference(self, tmp_path):
        source = _make_source_db(tmp_path, nb_emissions=10)
        dest = tmp_path / "mini.db"
        script.build_mini_db(source, dest, nb_emissions=3)

        con = sqlite3.connect(dest)
        count = con.execute("SELECT COUNT(*) FROM livres").fetchone()[0]
        assert count == 3

    def test_critiques_conservees_entieres(self, tmp_path):
        source = _make_source_db(tmp_path, nb_emissions=10)
        dest = tmp_path / "mini.db"
        script.build_mini_db(source, dest, nb_emissions=3)

        con = sqlite3.connect(dest)
        count = con.execute("SELECT COUNT(*) FROM critiques").fetchone()[0]
        assert count == 2

    def test_avis_critiques_filtres_sur_emissions_gardees(self, tmp_path):
        source = _make_source_db(tmp_path, nb_emissions=10)
        dest = tmp_path / "mini.db"
        script.build_mini_db(source, dest, nb_emissions=3)

        con = sqlite3.connect(dest)
        count = con.execute("SELECT COUNT(*) FROM avis_critiques").fetchone()[0]
        assert count == 3


class TestTablesRecalculees:
    def test_palmares_recalcule_sur_le_sous_ensemble(self, tmp_path):
        source = _make_source_db(tmp_path, nb_emissions=10)
        dest = tmp_path / "mini.db"
        script.build_mini_db(source, dest, nb_emissions=3)

        con = sqlite3.connect(dest)
        count = con.execute("SELECT COUNT(*) FROM palmares").fetchone()[0]
        assert count == 3

    def test_search_index_reconstruit_sur_le_sous_ensemble(self, tmp_path):
        source = _make_source_db(tmp_path, nb_emissions=10)
        dest = tmp_path / "mini.db"
        script.build_mini_db(source, dest, nb_emissions=3)

        con = sqlite3.connect(dest)
        count = con.execute("SELECT COUNT(*) FROM search_index").fetchone()[0]
        assert count > 0


class TestTablesVides:
    def test_recommendations_vide(self, tmp_path):
        source = _make_source_db(tmp_path, nb_emissions=10)
        dest = tmp_path / "mini.db"
        script.build_mini_db(source, dest, nb_emissions=3)

        con = sqlite3.connect(dest)
        count = con.execute("SELECT COUNT(*) FROM recommendations").fetchone()[0]
        assert count == 0

    def test_onkindle_vide(self, tmp_path):
        source = _make_source_db(tmp_path, nb_emissions=10)
        dest = tmp_path / "mini.db"
        script.build_mini_db(source, dest, nb_emissions=3)

        con = sqlite3.connect(dest)
        count = con.execute("SELECT COUNT(*) FROM onkindle").fetchone()[0]
        assert count == 0

    def test_calibre_hors_masque_vide(self, tmp_path):
        source = _make_source_db(tmp_path, nb_emissions=10)
        dest = tmp_path / "mini.db"
        script.build_mini_db(source, dest, nb_emissions=3)

        con = sqlite3.connect(dest)
        count = con.execute("SELECT COUNT(*) FROM calibre_hors_masque").fetchone()[0]
        assert count == 0


class TestMetadata:
    def test_user_version_egale_room_version(self, tmp_path):
        source = _make_source_db(tmp_path, nb_emissions=10)
        dest = tmp_path / "mini.db"
        script.build_mini_db(source, dest, nb_emissions=3)

        con = sqlite3.connect(dest)
        user_version = con.execute("PRAGMA user_version").fetchone()[0]
        assert user_version == export_script.ROOM_VERSION

    def test_db_metadata_reflete_le_sous_ensemble(self, tmp_path):
        source = _make_source_db(tmp_path, nb_emissions=10)
        dest = tmp_path / "mini.db"
        script.build_mini_db(source, dest, nb_emissions=3)

        con = sqlite3.connect(dest)
        con.row_factory = sqlite3.Row
        meta = dict(con.execute("SELECT key, value FROM db_metadata").fetchall())
        assert meta["nb_emissions"] == "3"

    def test_version_datee_de_la_derniere_emission_gardee_pas_du_moment_de_generation(
        self, tmp_path
    ):
        """La mini-DB doit toujours sembler périmée face à une vraie release (issue #119).

        db_metadata.version est comparé (DataUpdateRepository.checkForUpdate) à un
        timestamp Unix distant : si on y met le moment de génération de la mini-DB
        (aujourd'hui), elle paraît plus "fraîche" que n'importe quelle release déjà
        publiée, et l'app affiche "à jour" à tort. Il faut dater la mini-DB de ses
        propres données (la plus récente émission qu'elle contient), pas du moment
        où le fichier a été construit.
        """
        source = _make_source_db(tmp_path, nb_emissions=10)
        dest = tmp_path / "mini.db"

        script.build_mini_db(source, dest, nb_emissions=3)

        con = sqlite3.connect(dest)
        con.row_factory = sqlite3.Row
        meta = dict(con.execute("SELECT key, value FROM db_metadata").fetchall())

        # em0, em1, em2 ont pour dates 2020-01-01, 02, 03T00:00:00Z (voir _make_source_db)
        expected_version = int(datetime(2020, 1, 3, tzinfo=UTC).timestamp())
        assert int(meta["version"]) == expected_version, (
            f"version={meta['version']} devrait être le timestamp de la dernière "
            f"émission gardée ({expected_version}), pas le moment de génération "
            "du fichier — sinon la mini-DB paraît plus fraîche qu'une vraie release."
        )
