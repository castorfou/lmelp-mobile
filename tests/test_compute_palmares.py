"""Tests TDD pour compute_palmares — inclusion des livres à un seul avis noté (issue #112)."""

import sqlite3
import sys
from pathlib import Path


sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))

import export_mongo_to_sqlite as script


def _make_db() -> sqlite3.Connection:
    """Crée une base SQLite en mémoire avec les tables livres, avis, palmares."""
    con = sqlite3.connect(":memory:")
    con.row_factory = sqlite3.Row
    cur = con.cursor()
    cur.executescript("""
        CREATE TABLE livres (
            id          TEXT PRIMARY KEY,
            titre       TEXT NOT NULL,
            auteur_nom  TEXT
        );
        CREATE TABLE avis (
            id          TEXT PRIMARY KEY,
            livre_id    TEXT NOT NULL,
            critique_id TEXT NOT NULL,
            note        REAL
        );
        CREATE TABLE palmares (
            rank                INTEGER NOT NULL,
            livre_id            TEXT NOT NULL PRIMARY KEY,
            titre               TEXT NOT NULL,
            auteur_nom          TEXT,
            note_moyenne        REAL NOT NULL,
            nb_avis             INTEGER NOT NULL,
            nb_critiques        INTEGER NOT NULL,
            calibre_in_library  INTEGER NOT NULL DEFAULT 0,
            calibre_lu          INTEGER NOT NULL DEFAULT 0,
            calibre_rating      REAL,
            date_lecture        TEXT,
            date_debut_lecture  TEXT
        );
    """)
    con.commit()
    return con


def test_livre_avec_un_seul_avis_note_entre_dans_palmares():
    """Un livre discuté au Masque avec un seul avis noté doit apparaître dans palmares (issue #112)."""
    con = _make_db()
    cur = con.cursor()
    cur.execute(
        "INSERT INTO livres VALUES ('l1', 'La Petite fasciste', 'Jérôme Leroy')"
    )
    cur.execute("INSERT INTO avis VALUES ('a1', 'l1', 'c1', 8.0)")
    con.commit()

    script.compute_palmares(cur)
    con.commit()

    row = cur.execute(
        "SELECT nb_avis, note_moyenne FROM palmares WHERE livre_id = 'l1'"
    ).fetchone()
    assert row is not None, (
        "Le livre avec 1 seul avis noté doit être présent dans palmares"
    )
    assert row["nb_avis"] == 1
    assert row["note_moyenne"] == 8.0


def test_livre_avec_deux_avis_notes_entre_dans_palmares():
    """Non-régression : un livre avec 2 avis notés continue d'entrer dans palmares."""
    con = _make_db()
    cur = con.cursor()
    cur.execute(
        "INSERT INTO livres VALUES ('l2', 'Des souris et des hommes', 'John Steinbeck')"
    )
    cur.execute("INSERT INTO avis VALUES ('a2', 'l2', 'c1', 8.0)")
    cur.execute("INSERT INTO avis VALUES ('a3', 'l2', 'c2', 10.0)")
    con.commit()

    script.compute_palmares(cur)
    con.commit()

    row = cur.execute(
        "SELECT nb_avis, note_moyenne FROM palmares WHERE livre_id = 'l2'"
    ).fetchone()
    assert row is not None
    assert row["nb_avis"] == 2
    assert row["note_moyenne"] == 9.0


def test_livre_sans_avis_note_absent_de_palmares():
    """Non-régression : un livre sans aucun avis noté (note NULL) n'entre pas dans palmares."""
    con = _make_db()
    cur = con.cursor()
    cur.execute("INSERT INTO livres VALUES ('l3', 'Livre sans note', 'Auteur')")
    cur.execute("INSERT INTO avis VALUES ('a4', 'l3', 'c1', ?)", (None,))
    con.commit()

    script.compute_palmares(cur)
    con.commit()

    row = cur.execute("SELECT * FROM palmares WHERE livre_id = 'l3'").fetchone()
    assert row is None


def test_classement_rank_inclut_les_livres_a_un_avis():
    """Le rang (rank) est calculé cohéremment sur l'ensemble, y compris les livres à 1 avis."""
    con = _make_db()
    cur = con.cursor()
    cur.execute(
        "INSERT INTO livres VALUES ('l1', 'Un seul avis, meilleure note', 'Auteur A')"
    )
    cur.execute("INSERT INTO avis VALUES ('a1', 'l1', 'c1', 9.5)")
    cur.execute(
        "INSERT INTO livres VALUES ('l2', 'Deux avis, note moyenne', 'Auteur B')"
    )
    cur.execute("INSERT INTO avis VALUES ('a2', 'l2', 'c1', 7.0)")
    cur.execute("INSERT INTO avis VALUES ('a3', 'l2', 'c2', 7.0)")
    con.commit()

    script.compute_palmares(cur)
    con.commit()

    rows = {
        r["livre_id"]: r["rank"]
        for r in cur.execute("SELECT livre_id, rank FROM palmares")
    }
    assert rows["l1"] == 1
    assert rows["l2"] == 2
