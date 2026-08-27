"""Tests TDD pour compute_content_hash — empreinte du contenu réel (issue #128).

Le but est de détecter si le contenu métier exporté a changé d'un run à
l'autre, indépendamment de l'heure d'exécution du script, pour éviter que
`db_metadata.version` n'avance à chaque export même quand rien n'a changé
(voir write_metadata dans export_mongo_to_sqlite.py).
"""

import sqlite3
import sys
from pathlib import Path


sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))

import export_mongo_to_sqlite as script


def _make_db() -> sqlite3.Connection:
    con = sqlite3.connect(":memory:")
    cur = con.cursor()
    cur.executescript(script.SCHEMA_SQL)
    con.commit()
    return con


def _insert_base_content(cur: sqlite3.Cursor) -> None:
    cur.execute("INSERT INTO episodes VALUES ('ep1','Titre',NULL,NULL,NULL,NULL,0)")
    cur.execute(
        "INSERT INTO emissions VALUES ('em1','ep1','2026-08-23',NULL,NULL,0,0,NULL,NULL)"
    )
    cur.execute("INSERT INTO auteurs VALUES ('a1','Auteur',NULL)")
    cur.execute(
        "INSERT INTO livres VALUES ('l1','Livre',NULL,'Auteur',NULL,NULL,NULL,NULL,NULL)"
    )
    cur.execute("INSERT INTO critiques VALUES ('c1','Critique',0,0)")
    cur.execute(
        "INSERT INTO avis VALUES ('av1','em1','l1','c1',8.0,NULL,NULL,NULL,NULL,NULL,NULL,NULL)"
    )


def test_meme_contenu_donne_le_meme_hash():
    """Deux bases avec exactement le même contenu produisent le même hash (déterminisme)."""
    con1 = _make_db()
    _insert_base_content(con1.cursor())
    con1.commit()

    con2 = _make_db()
    _insert_base_content(con2.cursor())
    con2.commit()

    hash1 = script.compute_content_hash(con1.cursor())
    hash2 = script.compute_content_hash(con2.cursor())
    assert hash1 == hash2


def test_ajout_avis_change_le_hash():
    """Ajouter un avis change le hash de contenu."""
    con = _make_db()
    cur = con.cursor()
    _insert_base_content(cur)
    con.commit()
    hash_before = script.compute_content_hash(cur)

    cur.execute("INSERT INTO critiques VALUES ('c2','Critique 2',0,0)")
    cur.execute(
        "INSERT INTO avis VALUES ('av2','em1','l1','c2',9.0,NULL,NULL,NULL,NULL,NULL,NULL,NULL)"
    )
    con.commit()
    hash_after = script.compute_content_hash(cur)

    assert hash_before != hash_after


def test_modification_url_cover_change_le_hash():
    """Modifier url_cover d'un livre existant change le hash (cas réel issue #127)."""
    con = _make_db()
    cur = con.cursor()
    _insert_base_content(cur)
    con.commit()
    hash_before = script.compute_content_hash(cur)

    cur.execute(
        "UPDATE livres SET url_cover = 'https://example.com/cover.jpg' WHERE id = 'l1'"
    )
    con.commit()
    hash_after = script.compute_content_hash(cur)

    assert hash_before != hash_after


def test_ordre_insertion_differe_mais_hash_identique():
    """L'ordre d'insertion des lignes (non déterministe côté Mongo) ne doit pas changer le hash."""
    con1 = _make_db()
    cur1 = con1.cursor()
    cur1.execute("INSERT INTO auteurs VALUES ('a1','Auteur 1',NULL)")
    cur1.execute("INSERT INTO auteurs VALUES ('a2','Auteur 2',NULL)")
    con1.commit()

    con2 = _make_db()
    cur2 = con2.cursor()
    cur2.execute("INSERT INTO auteurs VALUES ('a2','Auteur 2',NULL)")
    cur2.execute("INSERT INTO auteurs VALUES ('a1','Auteur 1',NULL)")
    con2.commit()

    assert script.compute_content_hash(cur1) == script.compute_content_hash(cur2)


def test_db_metadata_nest_pas_pris_en_compte():
    """Le contenu de db_metadata (timestamps) ne doit jamais influencer le hash."""
    con = _make_db()
    cur = con.cursor()
    _insert_base_content(cur)
    con.commit()
    hash_before = script.compute_content_hash(cur)

    cur.execute("INSERT OR REPLACE INTO db_metadata VALUES ('version', '999999999')")
    con.commit()
    hash_after = script.compute_content_hash(cur)

    assert hash_before == hash_after
