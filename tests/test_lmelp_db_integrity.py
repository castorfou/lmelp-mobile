"""
Tests d'intégrité de lmelp.db embarqué.

Depuis l'ADR 0002 (docs/dev/adr/0002-distribution-app-mini-db-embarquee.md),
app/src/main/assets/lmelp.db est volontairement un extrait minimal (quelques
émissions réelles), pas la base complète — la vraie base est téléchargée par
l'app au lancement (issue #118). Ces tests vérifient donc uniquement que le
schéma reste cohérent et que la base reste bien minimale, plus la présence
de palmares. Les anciens tests de complétude/fraîcheur vs MongoDB et de
ratio Calibre n'ont plus de sens pour cet asset volontairement partiel et
périmé — supprimés (issues #42/#102, résolues autrement par le
téléchargement au lancement).
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


class TestTailleMinimale:
    """
    Vérifie que lmelp.db embarqué reste un extrait minimal (ADR 0002).

    Garde-fou contre une régression où l'asset serait accidentellement
    regénéré comme une base complète (ex: export --force sans passer par
    scripts/build_mini_db.py) — ce qui masquerait silencieusement toute
    panne du mécanisme de téléchargement au lancement (issue #118).
    """

    NB_EMISSIONS_MAX = 10

    def test_nb_emissions_reste_minimal(self, db):
        count = db.execute("SELECT COUNT(*) FROM emissions").fetchone()[0]
        assert count <= self.NB_EMISSIONS_MAX, (
            f"lmelp.db contient {count} émissions, plus que le seuil minimal "
            f"({self.NB_EMISSIONS_MAX}) attendu pour l'asset embarqué (ADR 0002). "
            "Régénérer avec scripts/build_mini_db.py, pas export_mongo_to_sqlite.py directement."
        )

    def test_recommendations_vide(self, db):
        """Table indépendante des émissions embarquées, sans sens sur un extrait minimal."""
        count = db.execute("SELECT COUNT(*) FROM recommendations").fetchone()[0]
        assert count == 0, (
            "La table 'recommendations' devrait être vide dans l'extrait minimal "
            "(voir scripts/build_mini_db.py)."
        )


class TestVersionConsistance:
    """
    Vérifie que PRAGMA user_version dans lmelp.db correspond à la version Room
    déclarée dans LmelpDatabase.kt.

    Cause de régression connue (issue #100) : oublier de mettre à jour
    PRAGMA user_version dans le script d'export après avoir incrémenté version=N
    dans LmelpDatabase.kt. Room détecte la discordance, détruit les tables via
    fallbackToDestructiveMigration() et crée un schéma vide → app vide.
    """

    LMELP_DATABASE_KT = Path(__file__).parent.parent / (
        "app/src/main/java/com/lmelp/mobile/data/db/LmelpDatabase.kt"
    )
    EXPORT_SCRIPT = Path(__file__).parent.parent / "scripts/export_mongo_to_sqlite.py"

    def _room_version(self) -> int:
        import re

        text = self.LMELP_DATABASE_KT.read_text()
        m = re.search(r"version\s*=\s*(\d+)", text)
        assert m, f"Impossible de lire la version Room dans {self.LMELP_DATABASE_KT}"
        return int(m.group(1))

    def _script_version(self) -> int:
        import re

        text = self.EXPORT_SCRIPT.read_text()
        m = re.search(r"^ROOM_VERSION\s*=\s*(\d+)", text, re.MULTILINE)
        assert m, f"Impossible de lire ROOM_VERSION dans {self.EXPORT_SCRIPT}"
        return int(m.group(1))

    def test_user_version_db_egale_room_version(self, db):
        """PRAGMA user_version de lmelp.db doit égaler version=N dans LmelpDatabase.kt."""
        db_version = db.execute("PRAGMA user_version").fetchone()[0]
        room_version = self._room_version()
        assert db_version == room_version, (
            f"lmelp.db user_version={db_version} ≠ LmelpDatabase.kt version={room_version}.\n"
            "Room va détruire les tables (fallbackToDestructiveMigration) → app vide.\n"
            "Regénérer avec : python scripts/export_mongo_to_sqlite.py --force"
        )

    def test_script_version_egale_room_version(self):
        """PRAGMA user_version dans le script d'export doit égaler version=N dans LmelpDatabase.kt."""
        script_version = self._script_version()
        room_version = self._room_version()
        assert script_version == room_version, (
            f"export_mongo_to_sqlite.py PRAGMA user_version={script_version} "
            f"≠ LmelpDatabase.kt version={room_version}.\n"
            "Mettre à jour PRAGMA user_version dans le script et regénérer lmelp.db."
        )
