"""Tests TDD pour write_metadata — version réutilisée si contenu inchangé (issue #128).

Sans previous_content_hash/previous_version, comportement inchangé : un
nouveau timestamp Unix est généré à chaque export (non-régression). Avec ces
paramètres, si le contenu exporté est identique au précédent, `version` est
réutilisée telle quelle plutôt que régénérée — évite que l'app mobile ne
détecte une "mise à jour disponible" chaque jour même sans nouvelle donnée.
"""

import sqlite3
import sys
import time
from pathlib import Path


sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))

import export_mongo_to_sqlite as script


def _make_db() -> sqlite3.Connection:
    con = sqlite3.connect(":memory:")
    cur = con.cursor()
    cur.executescript(script.SCHEMA_SQL)
    cur.execute("INSERT INTO episodes VALUES ('ep1','Titre',NULL,NULL,NULL,NULL,0)")
    con.commit()
    return con


def _get_metadata(cur: sqlite3.Cursor) -> dict[str, str]:
    return dict(cur.execute("SELECT key, value FROM db_metadata").fetchall())


def test_sans_previous_genere_un_nouveau_timestamp():
    """Non-régression : sans previous_content_hash/version, comportement actuel inchangé."""
    con = _make_db()
    cur = con.cursor()
    before = int(time.time())

    script.write_metadata(cur)
    con.commit()

    meta = _get_metadata(cur)
    assert int(meta["version"]) >= before
    assert "content_hash" in meta


def test_previous_content_hash_identique_reutilise_previous_version():
    """Si le contenu est identique au précédent, version est réutilisée telle quelle."""
    con = _make_db()
    cur = con.cursor()
    content_hash = script.compute_content_hash(cur)

    script.write_metadata(
        cur, previous_content_hash=content_hash, previous_version="1700000000"
    )
    con.commit()

    meta = _get_metadata(cur)
    assert meta["version"] == "1700000000"


def test_previous_content_hash_different_genere_nouveau_timestamp():
    """Si le contenu diffère du précédent, un nouveau timestamp est généré."""
    con = _make_db()
    cur = con.cursor()
    before = int(time.time())

    script.write_metadata(
        cur,
        previous_content_hash="hash_completement_different",
        previous_version="1700000000",
    )
    con.commit()

    meta = _get_metadata(cur)
    assert meta["version"] != "1700000000"
    assert int(meta["version"]) >= before


def test_previous_content_hash_sans_previous_version_genere_nouveau_timestamp():
    """Si previous_version est absent, on ne peut pas réutiliser une version : nouveau timestamp."""
    con = _make_db()
    cur = con.cursor()
    content_hash = script.compute_content_hash(cur)
    before = int(time.time())

    script.write_metadata(
        cur, previous_content_hash=content_hash, previous_version=None
    )
    con.commit()

    meta = _get_metadata(cur)
    assert int(meta["version"]) >= before
