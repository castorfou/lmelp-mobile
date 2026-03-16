"""Tests TDD pour build_onkindle_table — fallback avis pour livres non dans palmares."""

import sqlite3
import sys
from pathlib import Path
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))

import export_mongo_to_sqlite as script


def _make_main_db() -> sqlite3.Connection:
    """Crée une base SQLite en mémoire imitant la structure de lmelp.db."""
    con = sqlite3.connect(":memory:")
    con.row_factory = sqlite3.Row
    cur = con.cursor()

    cur.executescript("""
        CREATE TABLE palmares (
            livre_id    TEXT PRIMARY KEY,
            titre       TEXT NOT NULL,
            note_moyenne REAL,
            nb_avis     INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE livres (
            id          TEXT PRIMARY KEY,
            titre       TEXT NOT NULL,
            auteur_nom  TEXT,
            url_babelio TEXT,
            url_cover   TEXT
        );
        CREATE TABLE avis (
            id          TEXT PRIMARY KEY,
            livre_id    TEXT NOT NULL,
            note        REAL,
            livre_titre TEXT
        );
        CREATE TABLE IF NOT EXISTS onkindle (
            livre_id       TEXT NOT NULL PRIMARY KEY,
            titre          TEXT NOT NULL,
            auteur_nom     TEXT,
            url_babelio    TEXT,
            url_cover      TEXT,
            calibre_lu     INTEGER NOT NULL DEFAULT 0,
            calibre_rating REAL,
            note_moyenne   REAL,
            nb_avis        INTEGER NOT NULL DEFAULT 0
        );
    """)
    return con


def _make_calibre_db(books: list[tuple]) -> sqlite3.Connection:
    """Crée une base Calibre en mémoire avec les livres donnés.

    books: list of (calibre_id, title, extra_tags) où extra_tags est une liste de str optionnelle
    """
    con = sqlite3.connect(":memory:")
    con.row_factory = sqlite3.Row
    cur = con.cursor()
    cur.executescript("""
        CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT);
        CREATE TABLE tags (id INTEGER PRIMARY KEY, name TEXT);
        CREATE TABLE books_tags_link (book INTEGER, tag INTEGER);
        CREATE TABLE ratings (id INTEGER PRIMARY KEY, rating REAL);
        CREATE TABLE books_ratings_link (book INTEGER, rating INTEGER);
        CREATE TABLE custom_columns (id INTEGER PRIMARY KEY, label TEXT);
        CREATE TABLE authors (id INTEGER PRIMARY KEY, name TEXT);
        CREATE TABLE books_authors_link (book INTEGER, author INTEGER);
    """)
    cur.execute("INSERT INTO tags VALUES (1, 'onkindle')")
    tag_id_map = {"onkindle": 1}
    next_tag_id = 2

    for book_tuple in books:
        calibre_id, title = book_tuple[0], book_tuple[1]
        extra_tags: list[str] = book_tuple[2] if len(book_tuple) > 2 else []
        cur.execute("INSERT INTO books VALUES (?, ?)", (calibre_id, title))
        cur.execute("INSERT INTO books_tags_link VALUES (?, 1)", (calibre_id,))
        for tag in extra_tags:
            if tag not in tag_id_map:
                cur.execute("INSERT INTO tags VALUES (?, ?)", (next_tag_id, tag))
                tag_id_map[tag] = next_tag_id
                next_tag_id += 1
            cur.execute(
                "INSERT INTO books_tags_link VALUES (?, ?)",
                (calibre_id, tag_id_map[tag]),
            )
    con.commit()
    return con


def _run_build_with_vlib(
    main_con: sqlite3.Connection,
    calibre_con: sqlite3.Connection,
    virtual_library_tag: str | None = None,
) -> None:
    """Appelle build_onkindle_table avec un virtual_library_tag optionnel."""

    def mock_connect(path):
        return calibre_con

    with patch("export_mongo_to_sqlite.sqlite3") as mock_sqlite3:
        mock_sqlite3.connect = mock_connect
        mock_sqlite3.Row = sqlite3.Row
        cur = main_con.cursor()
        script.build_onkindle_table(cur, "/fake/path/metadata.db", virtual_library_tag)
        main_con.commit()


def _run_build(main_con: sqlite3.Connection, calibre_con: sqlite3.Connection) -> None:
    """Appelle build_onkindle_table en mockant sqlite3.connect pour injecter nos bases."""
    call_count = [0]

    def mock_connect(path):
        call_count[0] += 1
        return calibre_con

    with patch("export_mongo_to_sqlite.sqlite3") as mock_sqlite3:
        # sqlite3.connect → calibre_con
        mock_sqlite3.connect = mock_connect
        mock_sqlite3.Row = sqlite3.Row
        # Pour les requêtes sur main_db on passe directement cur
        cur = main_con.cursor()
        script.build_onkindle_table(cur, "/fake/path/metadata.db")
        main_con.commit()


class TestBuildOnkindleAvisFallback:
    """Vérifie que build_onkindle_table utilise avis comme fallback quand le livre
    n'est pas dans palmares (ex: coups de cœur avec nb_avis < 2)."""

    def test_livre_dans_palmares_a_note(self):
        """Livre dans palmares → note_moyenne et nb_avis depuis palmares."""
        main_con = _make_main_db()
        cur = main_con.cursor()
        cur.execute("INSERT INTO palmares VALUES ('l1', 'Kafka, tome 1', 8.5, 3)")
        cur.execute(
            "INSERT INTO livres VALUES ('l1', 'Kafka, tome 1', 'Kafka Franz', 'http://babelio/kafka', NULL)"
        )
        main_con.commit()

        calibre_con = _make_calibre_db([(101, "Kafka, tome 1")])
        _run_build(main_con, calibre_con)

        row = main_con.execute(
            "SELECT * FROM onkindle WHERE livre_id = 'l1'"
        ).fetchone()
        assert row is not None
        assert row["note_moyenne"] == 8.5
        assert row["nb_avis"] == 3

    def test_livre_avec_avis_mais_pas_dans_palmares_a_note(self):
        """Livre avec avis mais absent de palmares (coup de cœur) → note_moyenne depuis avis."""
        main_con = _make_main_db()
        cur = main_con.cursor()
        # Pas dans palmares
        cur.execute(
            "INSERT INTO livres VALUES ('l2', 'Kafka, tome 1', 'Kafka Franz', NULL, NULL)"
        )
        cur.execute("INSERT INTO avis VALUES ('a1', 'l2', 9.0, 'Kafka, tome 1')")
        main_con.commit()

        calibre_con = _make_calibre_db([(101, "Kafka, tome 1")])
        _run_build(main_con, calibre_con)

        row = main_con.execute(
            "SELECT * FROM onkindle WHERE livre_id = 'l2'"
        ).fetchone()
        assert row is not None, "Le livre doit être inséré dans onkindle"
        assert row["note_moyenne"] is not None, "note_moyenne ne doit pas être NULL"
        assert row["note_moyenne"] == 9.0
        assert row["nb_avis"] == 1

    def test_livre_sans_avis_ni_palmares_a_note_nulle(self):
        """Livre sans avis et absent de palmares → note_moyenne NULL."""
        main_con = _make_main_db()
        cur = main_con.cursor()
        cur.execute(
            "INSERT INTO livres VALUES ('l3', 'Livre inconnu', NULL, NULL, NULL)"
        )
        main_con.commit()

        calibre_con = _make_calibre_db([(101, "Livre inconnu")])
        _run_build(main_con, calibre_con)

        row = main_con.execute(
            "SELECT * FROM onkindle WHERE livre_id = 'l3'"
        ).fetchone()
        assert row is not None
        assert row["note_moyenne"] is None
        assert row["nb_avis"] == 0

    def test_livre_avec_plusieurs_avis_moyenne_calculee(self):
        """Livre avec 2 avis (mais pas dans palmares) → note_moyenne = moyenne des notes."""
        main_con = _make_main_db()
        cur = main_con.cursor()
        titre = "Les guerriers de l'hiver"
        cur.execute(
            "INSERT INTO livres VALUES (?, ?, ?, NULL, NULL)", ("l4", titre, "Auteur X")
        )
        cur.execute("INSERT INTO avis VALUES (?, ?, ?, ?)", ("a2", "l4", 8.0, titre))
        cur.execute("INSERT INTO avis VALUES (?, ?, ?, ?)", ("a3", "l4", 6.0, titre))
        main_con.commit()

        calibre_con = _make_calibre_db([(102, "Les guerriers de l\u2019hiver")])
        _run_build(main_con, calibre_con)

        row = main_con.execute(
            "SELECT * FROM onkindle WHERE livre_id = 'l4'"
        ).fetchone()
        assert row is not None
        assert row["nb_avis"] == 2
        assert abs(row["note_moyenne"] - 7.0) < 0.01


class TestBuildOnkindleVirtualLibrary:
    """Vérifie que build_onkindle_table filtre par virtual_library_tag quand fourni.

    Seuls les livres avec onkindle ET le tag de la virtual library sont inclus.
    """

    def test_sans_virtual_library_inclut_tous_les_onkindle(self):
        """Sans virtual_library_tag → tous les livres tagués onkindle sont inclus."""
        main_con = _make_main_db()
        # Livre onkindle sans tag guillaume
        calibre_con = _make_calibre_db([(101, "Livre A"), (102, "Livre B")])
        _run_build_with_vlib(main_con, calibre_con, virtual_library_tag=None)

        rows = main_con.execute("SELECT titre FROM onkindle ORDER BY titre").fetchall()
        titres = [r["titre"] for r in rows]
        assert "Livre A" in titres
        assert "Livre B" in titres

    def test_avec_virtual_library_exclut_livres_hors_lib(self):
        """Avec virtual_library_tag='guillaume' → seuls les livres avec les deux tags."""
        main_con = _make_main_db()
        # Livre A : onkindle + guillaume → inclus
        # Livre B : onkindle seulement → exclu
        calibre_con = _make_calibre_db(
            [
                (101, "Livre A", ["guillaume"]),
                (102, "Livre B"),
            ]
        )
        _run_build_with_vlib(main_con, calibre_con, virtual_library_tag="guillaume")

        rows = main_con.execute("SELECT titre FROM onkindle").fetchall()
        titres = [r["titre"] for r in rows]
        assert "Livre A" in titres
        assert "Livre B" not in titres

    def test_avec_virtual_library_livre_sans_aucun_tag_exclu(self):
        """Livre sans tag onkindle ni guillaume → exclu même sans filtre vlib."""
        main_con = _make_main_db()
        calibre_con = _make_calibre_db([(101, "Livre A", ["guillaume"])])
        # On insère manuellement un livre sans tag onkindle dans Calibre
        cal_cur = calibre_con.cursor()
        cal_cur.execute("INSERT INTO books VALUES (999, 'Livre sans tags')")
        calibre_con.commit()

        _run_build_with_vlib(main_con, calibre_con, virtual_library_tag="guillaume")

        rows = main_con.execute("SELECT titre FROM onkindle").fetchall()
        titres = [r["titre"] for r in rows]
        assert "Livre A" in titres
        assert "Livre sans tags" not in titres
