"""
Tests TDD pour la recherche insensible aux accents (issue #55).

Stratégie : FTS4 avec contenu pré-normalisé (accents supprimés à l'indexation).
La query Android est aussi normalisée avant d'interroger FTS4.

Ces tests vérifient que :
1. La table search_index contient du contenu sans accents
2. La recherche "Aliene" (sans accent) trouve "Aliène" (avec accent dans le livre original)
3. La recherche avec accents fonctionne aussi (grâce à la normalisation côté query)

⚠️  Si ces tests échouent, regénérer lmelp.db avec :
    python scripts/export_mongo_to_sqlite.py --force
"""

import os
import sqlite3
import unicodedata
from pathlib import Path

import pytest


DB_PATH = Path(
    os.environ.get(
        "LMELP_DB_PATH",
        str(Path(__file__).parent.parent / "app/src/main/assets/lmelp.db"),
    )
)


def strip_accents(text: str) -> str:
    """Supprime les accents (même logique que _strip_accents dans le script d'export)."""
    nfd = unicodedata.normalize("NFD", text)
    return "".join(c for c in nfd if unicodedata.category(c) != "Mn")


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
def fts4_normalized_db():
    """Base SQLite in-memory avec FTS4 et contenu pré-normalisé."""
    con = sqlite3.connect(":memory:")
    con.execute("""
        CREATE VIRTUAL TABLE search_index USING fts4(
            type,
            ref_id,
            content,
            notindexed=type,
            notindexed=ref_id
        )
    """)
    # Contenu indexé avec accents supprimés (comme le fait _strip_accents)
    con.execute(
        "INSERT INTO search_index VALUES (?, ?, ?)",
        ("livre", "1", strip_accents("Aliène - Marie-Hélène Lafon")),
    )
    con.execute(
        "INSERT INTO search_index VALUES (?, ?, ?)",
        ("auteur", "2", strip_accents("Hélène Carrère")),
    )
    con.execute(
        "INSERT INTO search_index VALUES (?, ?, ?)",
        ("livre", "3", strip_accents("Le nom sur le mur - Hervé Le Tellier")),
    )
    con.commit()
    yield con
    con.close()


class TestFts4NormalizedBehavior:
    """Vérifie le comportement attendu de FTS4 avec contenu pré-normalisé."""

    def test_search_without_accent_finds_normalized_content(self, fts4_normalized_db):
        """'Aliene' (sans accent) doit trouver l'entrée pour 'Aliène'."""
        query = strip_accents("Aliene") + "*"
        results = fts4_normalized_db.execute(
            "SELECT COUNT(*) FROM search_index WHERE search_index MATCH ?",
            (query,),
        ).fetchone()[0]
        assert results > 0, "FTS4 normalisé doit trouver 'Aliene*'"

    def test_search_with_accent_finds_same_content(self, fts4_normalized_db):
        """'Aliène' (avec accent) doit aussi trouver l'entrée (query normalisée)."""
        query = strip_accents("Aliène") + "*"
        results = fts4_normalized_db.execute(
            "SELECT COUNT(*) FROM search_index WHERE search_index MATCH ?",
            (query,),
        ).fetchone()[0]
        assert results > 0, "FTS4 normalisé + query normalisée doit trouver 'Aliène*'"

    def test_search_uppercase_without_accent(self, fts4_normalized_db):
        """'HELENE' doit trouver 'Hélène' (query normalisée + FTS case-insensitive)."""
        query = strip_accents("HELENE") + "*"
        results = fts4_normalized_db.execute(
            "SELECT COUNT(*) FROM search_index WHERE search_index MATCH ?",
            (query,),
        ).fetchone()[0]
        assert results > 0, "FTS4 est insensible à la casse"

    def test_search_partial_without_accent(self, fts4_normalized_db):
        """'Herve' doit trouver 'Hervé Le Tellier'."""
        query = strip_accents("Herve") + "*"
        results = fts4_normalized_db.execute(
            "SELECT COUNT(*) FROM search_index WHERE search_index MATCH ?",
            (query,),
        ).fetchone()[0]
        assert results > 0, "FTS4 normalisé doit trouver 'Herve*' → 'Hervé'"


class TestProductionDbAccentSearch:
    """Vérifie que la base lmelp.db de production supporte la recherche sans accents."""

    def test_search_index_content_is_accent_free(self, db):
        """Le contenu de search_index ne doit pas contenir de caractères accentués."""
        # Vérifie sur un échantillon de 100 entrées
        rows = db.execute("SELECT content FROM search_index LIMIT 100").fetchall()
        assert len(rows) > 0, "search_index est vide"
        for row in rows:
            content = row["content"]
            normalized = strip_accents(content)
            assert content == normalized, (
                f"Le contenu FTS4 contient des accents : '{content}'\n"
                "Regénérer lmelp.db avec : python scripts/export_mongo_to_sqlite.py --force"
            )

    def test_accent_insensitive_search_in_production_db(self, db):
        """Rechercher 'Aliene' (sans accent, comme Android le ferait) doit retourner des résultats."""
        query = strip_accents("Aliene") + "*"
        results = db.execute(
            "SELECT COUNT(*) FROM search_index WHERE search_index MATCH ?",
            (query,),
        ).fetchone()[0]
        assert results > 0, (
            "La recherche 'Aliene*' (sans accent) ne retourne rien dans lmelp.db.\n"
            "Regénérer lmelp.db avec : python scripts/export_mongo_to_sqlite.py --force"
        )

    def test_accent_search_works_via_normalization(self, db):
        """Rechercher 'Aliène' (avec accent, normalisé en query) doit aussi retourner des résultats."""
        query = strip_accents("Aliène") + "*"
        results = db.execute(
            "SELECT COUNT(*) FROM search_index WHERE search_index MATCH ?",
            (query,),
        ).fetchone()[0]
        assert results > 0, (
            "La recherche 'Aliene*' (normalisée depuis 'Aliène') ne retourne rien."
        )
