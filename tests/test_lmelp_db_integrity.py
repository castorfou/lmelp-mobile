"""
Tests d'intégrité de lmelp.db embarqué.

Ces tests vérifient que la base SQLite commitée dans les assets contient
des données cohérentes et complètes — en particulier les données Calibre
qui nécessitent l'option --calibre-db lors de l'export.

⚠️  Si ces tests échouent en CI, c'est que lmelp.db a été regénéré sans
    l'option --calibre-db. Relancer l'export avec :

    python scripts/export_mongo_to_sqlite.py \\
      --mongo-uri mongodb://localhost:27018 \\
      --output app/src/main/assets/lmelp.db \\
      --calibre-db "/home/vscode/Calibre Library/metadata.db" \\
      --force
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


class TestTablesPresentes:
    def test_table_palmares_existe(self, db):
        row = db.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='palmares'"
        ).fetchone()
        assert row is not None, "La table 'palmares' est absente de lmelp.db"

    def test_palmares_non_vide(self, db):
        count = db.execute("SELECT COUNT(*) FROM palmares").fetchone()[0]
        assert count > 0, "La table 'palmares' est vide"


class TestDonneesCalibr:
    """
    Vérifie que les données Calibre sont présentes dans lmelp.db.

    Cause de régression connue (issue #42) : export lancé sans --calibre-db
    → calibre_in_library = 0 et calibre_lu = 0 pour tous les livres
    → filtre "Lus" vide dans l'app, aucun ✓ affiché.
    """

    def test_au_moins_un_livre_dans_calibre(self, db):
        """Au moins un livre doit avoir calibre_in_library = 1."""
        count = db.execute(
            "SELECT COUNT(*) FROM palmares WHERE calibre_in_library = 1"
        ).fetchone()[0]
        assert count > 0, (
            "Aucun livre avec calibre_in_library=1 dans palmares. "
            "lmelp.db a probablement été regénéré sans --calibre-db. "
            "Voir le commentaire en tête de ce fichier."
        )

    def test_au_moins_un_livre_lu_dans_calibre(self, db):
        """Au moins un livre doit avoir calibre_lu = 1."""
        count = db.execute(
            "SELECT COUNT(*) FROM palmares WHERE calibre_lu = 1"
        ).fetchone()[0]
        assert count > 0, (
            "Aucun livre avec calibre_lu=1 dans palmares. "
            "lmelp.db a probablement été regénéré sans --calibre-db. "
            "Voir le commentaire en tête de ce fichier."
        )

    def test_livres_lus_sont_dans_calibre(self, db):
        """Un livre marqué lu (calibre_lu=1) doit aussi être in_library (calibre_in_library=1)."""
        count = db.execute(
            "SELECT COUNT(*) FROM palmares WHERE calibre_lu = 1 AND calibre_in_library = 0"
        ).fetchone()[0]
        assert count == 0, (
            f"{count} livre(s) avec calibre_lu=1 mais calibre_in_library=0 — "
            "incohérence de données."
        )

    def test_ratio_calibre_plausible(self, db):
        """Au moins 5 % des livres du palmarès sont dans Calibre (seuil minimal de cohérence)."""
        total = db.execute("SELECT COUNT(*) FROM palmares").fetchone()[0]
        in_calibre = db.execute(
            "SELECT COUNT(*) FROM palmares WHERE calibre_in_library = 1"
        ).fetchone()[0]
        ratio = in_calibre / total if total > 0 else 0
        assert ratio >= 0.05, (
            f"Seulement {in_calibre}/{total} livres ({ratio:.0%}) dans Calibre — "
            "export probablement sans --calibre-db."
        )
