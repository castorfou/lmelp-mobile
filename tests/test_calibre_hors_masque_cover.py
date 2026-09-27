"""Tests TDD pour les vignettes de couverture Calibre des livres hors Masque (issue #145)."""

import io
import sqlite3
import sys
from pathlib import Path

from PIL import Image


sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))

import export_mongo_to_sqlite as script


def _write_jpeg(path: Path, size: tuple[int, int]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.new("RGB", size, (200, 30, 30)).save(path, "JPEG")


def _make_main_db() -> sqlite3.Connection:
    con = sqlite3.connect(":memory:")
    con.execute(
        "CREATE TABLE palmares (livre_id TEXT PRIMARY KEY, titre TEXT NOT NULL)"
    )
    con.execute(
        """CREATE TABLE calibre_hors_masque (
            id TEXT NOT NULL PRIMARY KEY, titre TEXT NOT NULL, auteur_nom TEXT,
            calibre_rating REAL, date_lecture TEXT, date_debut_lecture TEXT, cover BLOB
        )"""
    )
    return con


def _make_calibre_library(tmp_path: Path, books: list[tuple[int, str, str]]) -> Path:
    """Crée une bibliothèque Calibre sur disque : metadata.db + dossiers de livres.

    books : liste de (calibre_id, titre, path relatif du dossier du livre).
    Tous les livres sont marqués lus et tagués 'onkindle'.
    """
    library = tmp_path / "Calibre Library"
    library.mkdir()
    db_path = library / "metadata.db"
    con = sqlite3.connect(db_path)
    con.executescript(
        """
        CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT, path TEXT);
        CREATE TABLE tags (id INTEGER PRIMARY KEY, name TEXT);
        CREATE TABLE books_tags_link (book INTEGER, tag INTEGER);
        CREATE TABLE ratings (id INTEGER PRIMARY KEY, rating REAL);
        CREATE TABLE books_ratings_link (book INTEGER, rating INTEGER);
        CREATE TABLE custom_columns (id INTEGER PRIMARY KEY, label TEXT);
        CREATE TABLE custom_column_1 (id INTEGER PRIMARY KEY, book INTEGER, value BOOL);
        CREATE TABLE authors (id INTEGER PRIMARY KEY, name TEXT);
        CREATE TABLE books_authors_link (book INTEGER, author INTEGER);
        INSERT INTO custom_columns VALUES (1, 'read');
        INSERT INTO tags VALUES (1, 'onkindle');
        """
    )
    for calibre_id, title, book_path in books:
        con.execute(
            "INSERT INTO books VALUES (?, ?, ?)", (calibre_id, title, book_path)
        )
        con.execute(
            "INSERT INTO custom_column_1 (book, value) VALUES (?, 1)", (calibre_id,)
        )
        con.execute("INSERT INTO books_tags_link VALUES (?, 1)", (calibre_id,))
    con.commit()
    con.close()
    return db_path


def _cover(con: sqlite3.Connection, titre: str) -> bytes | None:
    row = con.execute(
        "SELECT cover FROM calibre_hors_masque WHERE titre = ?", (titre,)
    ).fetchone()
    assert row is not None, f"{titre} absent de calibre_hors_masque"
    return row[0]


class TestMakeCoverThumbnail:
    def test_reduit_en_largeur_225_px_jpeg(self, tmp_path):
        cover = tmp_path / "cover.jpg"
        _write_jpeg(cover, (600, 900))

        data = script.make_cover_thumbnail(cover)

        assert data is not None
        assert data[:2] == b"\xff\xd8", "doit être un JPEG"
        img = Image.open(io.BytesIO(data))
        assert img.size == (225, 338)

    def test_n_agrandit_pas_une_petite_image(self, tmp_path):
        cover = tmp_path / "cover.jpg"
        _write_jpeg(cover, (100, 150))

        data = script.make_cover_thumbnail(cover)

        assert data is not None
        assert Image.open(io.BytesIO(data)).size == (100, 150)

    def test_fichier_absent_renvoie_none(self, tmp_path):
        assert script.make_cover_thumbnail(tmp_path / "absent.jpg") is None

    def test_fichier_illisible_renvoie_none(self, tmp_path):
        cover = tmp_path / "cover.jpg"
        cover.write_bytes(b"pas une image")
        assert script.make_cover_thumbnail(cover) is None


class TestBuildCalibreHorsMasqueCover:
    def test_stocke_la_vignette_en_blob(self, tmp_path):
        db_path = _make_calibre_library(
            tmp_path,
            [(1, "Du côté de chez Swann", "Marcel Proust/Du cote de chez Swann (1)")],
        )
        _write_jpeg(
            db_path.parent / "Marcel Proust/Du cote de chez Swann (1)/cover.jpg",
            (600, 900),
        )
        con = _make_main_db()

        script.build_calibre_hors_masque_table(con.cursor(), str(db_path))

        cover = _cover(con, "Du côté de chez Swann")
        assert cover is not None
        assert Image.open(io.BytesIO(cover)).width == 225

    def test_sans_cover_jpg_insere_le_livre_avec_cover_null(self, tmp_path):
        db_path = _make_calibre_library(
            tmp_path, [(1, "Impact", "Olivier Norek/Impact (1)")]
        )
        con = _make_main_db()

        script.build_calibre_hors_masque_table(con.cursor(), str(db_path))

        assert _cover(con, "Impact") is None


def test_schema_contient_la_colonne_cover():
    con = sqlite3.connect(":memory:")
    con.executescript(script.SCHEMA_SQL)
    columns = {
        row[1]: row[2] for row in con.execute("PRAGMA table_info(calibre_hors_masque)")
    }
    assert columns.get("cover") == "BLOB"


def test_room_version_9():
    assert script.ROOM_VERSION == 9


def _onkindle_cover(con: sqlite3.Connection, titre: str) -> bytes | None:
    row = con.execute("SELECT cover FROM onkindle WHERE titre = ?", (titre,)).fetchone()
    assert row is not None, f"{titre} absent de onkindle"
    return row[0]


class TestBuildOnkindleCover:
    """Vignette Calibre des livres de la liseuse sans couverture Babelio (issue #145)."""

    def _main_db(self) -> sqlite3.Connection:
        con = sqlite3.connect(":memory:")
        con.executescript(script.SCHEMA_SQL)
        return con

    def test_livre_hors_masque_recoit_la_vignette(self, tmp_path):
        db_path = _make_calibre_library(
            tmp_path, [(1, "Team Topologies", "Matthew Skelton/Team Topologies (1)")]
        )
        _write_jpeg(
            db_path.parent / "Matthew Skelton/Team Topologies (1)/cover.jpg", (600, 900)
        )
        con = self._main_db()

        script.build_onkindle_table(con.cursor(), str(db_path))

        cover = _onkindle_cover(con, "Team Topologies")
        assert cover is not None
        assert Image.open(io.BytesIO(cover)).width == 225

    def test_livre_avec_url_cover_babelio_ne_stocke_pas_de_vignette(self, tmp_path):
        db_path = _make_calibre_library(
            tmp_path, [(1, "Constellation", "Adrien Bosc/Constellation (1)")]
        )
        _write_jpeg(
            db_path.parent / "Adrien Bosc/Constellation (1)/cover.jpg", (600, 900)
        )
        con = self._main_db()
        con.execute(
            "INSERT INTO livres (id, titre, url_cover) VALUES ('l1', 'Constellation', 'https://babelio/c.jpg')"
        )

        script.build_onkindle_table(con.cursor(), str(db_path))

        assert _onkindle_cover(con, "Constellation") is None
