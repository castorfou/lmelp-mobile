"""
Tests TDD pour la recherche FTS5 insensible aux accents (issue #55).

Ces tests vérifient que :
1. La table search_index utilise FTS5 (et non FTS4)
2. La recherche sans accents trouve des résultats avec accents (ex: "Aliene" → "Aliène")
3. La recherche avec accents fonctionne toujours

⚠️  Si ces tests échouent, regénérer lmelp.db avec :
    python scripts/export_mongo_to_sqlite.py --force
"""

import os
import sqlite3
from pathlib import Path

import pytest


DB_PATH = Path(
    os.environ.get(
        "LMELP_DB_PATH",
        str(Path(__file__).parent.parent / "app/src/main/assets/lmelp.db"),
    )
)


@pytest.fixture(scope="module")
def db():
    """Connexion à lmelp.db embarqué (lecture seule)."""
    if not DB_PATH.exists():
        pytest.skip(f"Base de données absente : {DB_PATH}")
    con = sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True)
    con.row_factory = sqlite3.Row
    yield con
    con.close()


@pytest.fixture(scope="module")
def fts5_db():
    """Base SQLite in-memory avec FTS5 pour tester le comportement attendu."""
    con = sqlite3.connect(":memory:")
    con.execute("""
        CREATE VIRTUAL TABLE search_index USING fts5(
            type UNINDEXED,
            ref_id UNINDEXED,
            content,
            tokenize = 'unicode61 remove_diacritics 2'
        )
    """)
    con.execute(
        "INSERT INTO search_index VALUES (?, ?, ?)",
        ("livre", "1", "Aliène - Marie-Hélène Lafon"),
    )
    con.execute(
        "INSERT INTO search_index VALUES (?, ?, ?)",
        ("auteur", "2", "Hélène Carrère"),
    )
    con.execute(
        "INSERT INTO search_index VALUES (?, ?, ?)",
        ("livre", "3", "Le nom sur le mur - Hervé Le Tellier"),
    )
    con.commit()
    yield con
    con.close()


class TestFts5TokenizerBehavior:
    """Vérifie le comportement attendu de FTS5 avec unicode61 remove_diacritics."""

    def test_search_without_accent_finds_accented_content(self, fts5_db):
        """'Aliene' (sans accent) doit trouver 'Aliène' (avec accent)."""
        results = fts5_db.execute(
            "SELECT COUNT(*) FROM search_index WHERE search_index MATCH 'Aliene*'"
        ).fetchone()[0]
        assert results > 0, (
            "FTS5 avec remove_diacritics doit trouver 'Aliène' avec 'Aliene'"
        )

    def test_search_with_accent_still_works(self, fts5_db):
        """'Aliène' (avec accent) doit toujours trouver 'Aliène'."""
        results = fts5_db.execute(
            "SELECT COUNT(*) FROM search_index WHERE search_index MATCH 'Aliène*'"
        ).fetchone()[0]
        assert results > 0, "La recherche avec accents doit toujours fonctionner"

    def test_search_uppercase_without_accent_finds_accented(self, fts5_db):
        """'HELENE' (majuscules, sans accent) doit trouver 'Hélène'."""
        results = fts5_db.execute(
            "SELECT COUNT(*) FROM search_index WHERE search_index MATCH 'HELENE*'"
        ).fetchone()[0]
        assert results > 0, "FTS5 doit être insensible à la casse et aux accents"

    def test_search_partial_without_accent(self, fts5_db):
        """'Herve' (sans accent) doit trouver 'Hervé Le Tellier'."""
        results = fts5_db.execute(
            "SELECT COUNT(*) FROM search_index WHERE search_index MATCH 'Herve*'"
        ).fetchone()[0]
        assert results > 0, "FTS5 doit trouver 'Hervé' avec 'Herve'"


class TestProductionDbFts5:
    """Vérifie que la base lmelp.db de production utilise FTS5."""

    def test_search_index_uses_fts5(self, db):
        """La table search_index doit utiliser FTS5 (pas FTS4)."""
        row = db.execute(
            "SELECT sql FROM sqlite_master WHERE name='search_index'"
        ).fetchone()
        assert row is not None, "La table search_index est absente"
        sql = row["sql"].lower()
        assert "fts5" in sql, (
            f"search_index utilise encore FTS4 — migration vers FTS5 requise.\n"
            f"DDL actuel : {row['sql']}"
        )
        assert "remove_diacritics" in sql, (
            "Le tokenizer 'remove_diacritics 2' est absent — "
            "la recherche sans accents ne fonctionnera pas."
        )

    def test_accent_insensitive_search_in_production_db(self, db):
        """Rechercher 'Aliene' (sans accent) doit retourner des résultats dans lmelp.db."""
        results = db.execute(
            "SELECT COUNT(*) FROM search_index WHERE search_index MATCH 'Aliene*'"
        ).fetchone()[0]
        assert results > 0, (
            "La recherche 'Aliene' (sans accent) ne retourne rien dans lmelp.db — "
            "FTS5 avec remove_diacritics non configuré."
        )

    def test_accent_search_still_works_in_production_db(self, db):
        """Rechercher 'Aliène' (avec accent) doit aussi retourner des résultats."""
        results = db.execute(
            "SELECT COUNT(*) FROM search_index WHERE search_index MATCH 'Aliène*'"
        ).fetchone()[0]
        assert results > 0, (
            "La recherche 'Aliène' (avec accent) ne retourne rien — "
            "problème d'indexation FTS."
        )
