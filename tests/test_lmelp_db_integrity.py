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

⚠️  Si TestFraicheurDB échoue : lmelp.db est périmé par rapport aux données
    en base. Regénérer via le même export --force ci-dessus.
    Cause connue (issue #102) : lmelp-update-mobile.sh pousse la DB sur le
    téléphone via ADB mais ne met pas à jour app/src/main/assets/lmelp.db.
"""

import os
import sqlite3
from datetime import datetime
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


class TestFraicheurDB:
    """
    Vérifie que lmelp.db est à jour par rapport à MongoDB.

    Cause de régression connue (issue #102) : lmelp-update-mobile.sh pousse
    la DB directement sur le téléphone via ADB sans mettre à jour le fichier
    app/src/main/assets/lmelp.db dans le repo git. La DB commitée peut donc
    être en retard sur les nouvelles émissions ajoutées dans MongoDB.

    Ces tests nécessitent un accès MongoDB (skip automatique si indisponible).
    """

    @pytest.fixture(scope="class")
    def mongo_db(self):
        """Connexion MongoDB — skip si indisponible."""
        pymongo = pytest.importorskip("pymongo", reason="pymongo non installé")
        mongo_uri = os.environ.get("LMELP_MONGO_URI", "mongodb://localhost:27018")
        try:
            client = pymongo.MongoClient(mongo_uri, serverSelectionTimeoutMS=2000)
            client.server_info()
            yield client["masque_et_la_plume"]
            client.close()
        except Exception:
            pytest.skip(f"MongoDB inaccessible : {mongo_uri}")

    def test_toutes_emissions_mongo_dans_sqlite(self, db, mongo_db):
        """Toutes les émissions MongoDB doivent être présentes dans lmelp.db."""
        mongo_ids = {str(e["_id"]) for e in mongo_db.emissions.find({}, {"_id": 1})}
        sqlite_ids = {
            row[0] for row in db.execute("SELECT id FROM emissions").fetchall()
        }
        manquantes = mongo_ids - sqlite_ids
        assert not manquantes, (
            f"{len(manquantes)} émission(s) MongoDB absente(s) de lmelp.db : "
            f"{manquantes}.\n"
            "lmelp.db est périmé. Regénérer avec :\n"
            "  python scripts/export_mongo_to_sqlite.py --force\n"
            "Cause probable : lmelp-update-mobile.sh a mis à jour le téléphone "
            "sans mettre à jour app/src/main/assets/lmelp.db dans le repo git."
        )

    def test_export_date_posterieure_a_derniere_emission_mongo(self, db, mongo_db):
        """La date d'export de lmelp.db doit être >= à la date de la dernière émission MongoDB."""
        export_date_str = db.execute(
            "SELECT value FROM db_metadata WHERE key = 'export_date'"
        ).fetchone()
        assert export_date_str is not None, "Clé 'export_date' absente de db_metadata"

        latest_mongo = mongo_db.emissions.find_one({}, sort=[("date", -1)])
        assert latest_mongo is not None, "Aucune émission dans MongoDB"

        export_date = datetime.fromisoformat(export_date_str[0]).date()
        latest_mongo_date = latest_mongo["date"].date()

        assert export_date >= latest_mongo_date, (
            f"lmelp.db périmé : export_date={export_date_str[0]} mais "
            f"dernière émission MongoDB={latest_mongo_date} "
            f"(id={latest_mongo['_id']}).\n"
            "Regénérer avec : python scripts/export_mongo_to_sqlite.py --force"
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
