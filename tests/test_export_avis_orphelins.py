"""Tests TDD pour export_avis — robustesse face aux avis orphelins (issue #127).

Un avis dont livre_oid/emission_oid/critique_oid ne référence plus aucun
document existant (ex: après fusion de doublons côté back-office-lmelp, voir
castorfou/back-office-lmelp#271) ne doit jamais faire planter tout l'export
via une violation de contrainte FOREIGN KEY — il doit être ignoré et loggé.
"""

import sqlite3
import sys
from pathlib import Path
from unittest.mock import MagicMock


sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))

import export_mongo_to_sqlite as script


def _make_db() -> sqlite3.Connection:
    """Crée une base SQLite en mémoire avec le schéma emissions/livres/critiques/avis, FK actives."""
    con = sqlite3.connect(":memory:")
    con.row_factory = sqlite3.Row
    cur = con.cursor()
    cur.executescript("""
        PRAGMA foreign_keys = ON;

        CREATE TABLE episodes (
            id TEXT NOT NULL PRIMARY KEY
        );
        CREATE TABLE emissions (
            id           TEXT NOT NULL PRIMARY KEY,
            episode_id   TEXT NOT NULL,
            date         TEXT NOT NULL,
            duree        INTEGER,
            animateur_id TEXT,
            nb_avis      INTEGER NOT NULL DEFAULT 0,
            has_summary  INTEGER NOT NULL DEFAULT 0,
            created_at   TEXT,
            updated_at   TEXT
        );
        CREATE TABLE livres (
            id          TEXT NOT NULL PRIMARY KEY,
            titre       TEXT NOT NULL,
            auteur_id   TEXT,
            auteur_nom  TEXT,
            editeur     TEXT,
            url_babelio TEXT,
            url_cover   TEXT,
            created_at  TEXT,
            updated_at  TEXT
        );
        CREATE TABLE critiques (
            id        TEXT NOT NULL PRIMARY KEY,
            nom       TEXT NOT NULL,
            animateur INTEGER NOT NULL DEFAULT 0,
            nb_avis   INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE avis (
            id           TEXT NOT NULL PRIMARY KEY,
            emission_id  TEXT NOT NULL,
            livre_id     TEXT NOT NULL,
            critique_id  TEXT NOT NULL,
            note         REAL,
            commentaire  TEXT,
            livre_titre  TEXT,
            auteur_nom   TEXT,
            critique_nom TEXT,
            match_phase  INTEGER,
            section      TEXT,
            created_at   TEXT,
            FOREIGN KEY (emission_id) REFERENCES emissions(id),
            FOREIGN KEY (livre_id)    REFERENCES livres(id),
            FOREIGN KEY (critique_id) REFERENCES critiques(id)
        );
        CREATE TABLE emission_livres (
            emission_id TEXT NOT NULL,
            livre_id    TEXT NOT NULL,
            PRIMARY KEY (emission_id, livre_id)
        );
    """)
    cur.execute("INSERT INTO episodes VALUES ('ep1')")
    cur.execute(
        "INSERT INTO emissions VALUES ('em1','ep1','2026-08-23',NULL,NULL,0,0,NULL,NULL)"
    )
    cur.execute(
        "INSERT INTO livres VALUES ('l1','Livre valide',NULL,'Auteur',NULL,NULL,NULL,NULL,NULL)"
    )
    cur.execute("INSERT INTO critiques VALUES ('c1','Critique',0,0)")
    con.commit()
    return con


def _mock_mongo_db(avis_docs: list[dict]) -> MagicMock:
    mongo_db = MagicMock()
    mongo_db.avis.find.return_value = avis_docs
    return mongo_db


def test_avis_avec_livre_oid_orphelin_est_ignore_sans_planter():
    """Un avis dont livre_oid ne référence aucun livre existant est skip, pas de crash FK."""
    con = _make_db()
    cur = con.cursor()
    mongo_db = _mock_mongo_db(
        [
            {
                "_id": "a_orphelin",
                "emission_oid": "em1",
                "livre_oid": "livre_fantome",
                "critique_oid": "c1",
                "note": 8,
            }
        ]
    )

    script.export_avis(mongo_db, cur, {}, {"c1": "Critique"})
    con.commit()

    row = cur.execute("SELECT * FROM avis WHERE id = 'a_orphelin'").fetchone()
    assert row is None, "L'avis orphelin ne doit pas être inséré"


def test_avis_valide_est_insere_normalement():
    """Non-régression : un avis dont toutes les FK sont valides est bien inséré."""
    con = _make_db()
    cur = con.cursor()
    mongo_db = _mock_mongo_db(
        [
            {
                "_id": "a_valide",
                "emission_oid": "em1",
                "livre_oid": "l1",
                "critique_oid": "c1",
                "note": 9,
            }
        ]
    )

    script.export_avis(mongo_db, cur, {}, {"c1": "Critique"})
    con.commit()

    row = cur.execute("SELECT * FROM avis WHERE id = 'a_valide'").fetchone()
    assert row is not None
    assert row["note"] == 9


def test_avis_orphelin_nempeche_pas_insertion_avis_valides_suivants():
    """Un avis orphelin dans le batch ne doit pas faire perdre les avis valides du même batch."""
    con = _make_db()
    cur = con.cursor()
    mongo_db = _mock_mongo_db(
        [
            {
                "_id": "a_orphelin",
                "emission_oid": "em1",
                "livre_oid": "livre_fantome",
                "critique_oid": "c1",
                "note": 8,
            },
            {
                "_id": "a_valide",
                "emission_oid": "em1",
                "livre_oid": "l1",
                "critique_oid": "c1",
                "note": 9,
            },
        ]
    )

    script.export_avis(mongo_db, cur, {}, {"c1": "Critique"})
    con.commit()

    assert cur.execute("SELECT * FROM avis WHERE id = 'a_orphelin'").fetchone() is None
    assert (
        cur.execute("SELECT * FROM avis WHERE id = 'a_valide'").fetchone() is not None
    )


def test_avis_avec_emission_oid_orphelin_est_ignore():
    """Un avis dont emission_oid ne référence aucune émission existante est skip."""
    con = _make_db()
    cur = con.cursor()
    mongo_db = _mock_mongo_db(
        [
            {
                "_id": "a_orphelin_em",
                "emission_oid": "emission_fantome",
                "livre_oid": "l1",
                "critique_oid": "c1",
                "note": 7,
            }
        ]
    )

    script.export_avis(mongo_db, cur, {}, {"c1": "Critique"})
    con.commit()

    row = cur.execute("SELECT * FROM avis WHERE id = 'a_orphelin_em'").fetchone()
    assert row is None


def test_avis_avec_critique_oid_orphelin_est_ignore():
    """Un avis dont critique_oid ne référence aucune critique existante est skip."""
    con = _make_db()
    cur = con.cursor()
    mongo_db = _mock_mongo_db(
        [
            {
                "_id": "a_orphelin_crit",
                "emission_oid": "em1",
                "livre_oid": "l1",
                "critique_oid": "critique_fantome",
                "note": 7,
            }
        ]
    )

    script.export_avis(mongo_db, cur, {}, {})

    con.commit()
    row = cur.execute("SELECT * FROM avis WHERE id = 'a_orphelin_crit'").fetchone()
    assert row is None
