#!/usr/bin/env python3
"""
Export MongoDB masque_et_la_plume → SQLite pour lmelp-mobile.

Usage:
    python export_mongo_to_sqlite.py --output app/src/main/assets/lmelp.db
    python export_mongo_to_sqlite.py --verify lmelp.db
    python export_mongo_to_sqlite.py --help
"""

from __future__ import annotations

import json
import logging
import sqlite3
import time
from datetime import datetime
from pathlib import Path
from typing import Any

import click
import numpy as np
from bson import ObjectId
from pymongo import MongoClient
from scipy.sparse import csr_matrix
from scipy.sparse.linalg import svds


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)

MONGO_DB = "masque_et_la_plume"


# ---------------------------------------------------------------------------
# MongoDB helpers
# ---------------------------------------------------------------------------


def str_id(val: Any) -> str:
    """Convert ObjectId or string to string."""
    if isinstance(val, ObjectId):
        return str(val)
    return str(val) if val is not None else ""


def iso_date(val: Any) -> str | None:
    """Convert datetime or string to ISO 8601 string."""
    if val is None:
        return None
    if isinstance(val, datetime):
        return val.strftime("%Y-%m-%dT%H:%M:%SZ")
    return str(val)


# ---------------------------------------------------------------------------
# SQLite schema
# ---------------------------------------------------------------------------

SCHEMA_SQL = """
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS episodes (
    id          TEXT NOT NULL PRIMARY KEY,
    titre       TEXT NOT NULL,
    date        TEXT,
    description TEXT,
    url         TEXT,
    duree       INTEGER,
    masked      INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS emissions (
    id           TEXT NOT NULL PRIMARY KEY,
    episode_id   TEXT NOT NULL,
    date         TEXT NOT NULL,
    duree        INTEGER,
    animateur_id TEXT,
    nb_avis      INTEGER NOT NULL DEFAULT 0,
    has_summary  INTEGER NOT NULL DEFAULT 0,
    created_at   TEXT,
    updated_at   TEXT,
    FOREIGN KEY (episode_id) REFERENCES episodes(id)
);

CREATE TABLE IF NOT EXISTS auteurs (
    id          TEXT NOT NULL PRIMARY KEY,
    nom         TEXT NOT NULL,
    url_babelio TEXT
);

CREATE TABLE IF NOT EXISTS livres (
    id          TEXT NOT NULL PRIMARY KEY,
    titre       TEXT NOT NULL,
    auteur_id   TEXT,
    auteur_nom  TEXT,
    editeur     TEXT,
    url_babelio TEXT,
    created_at  TEXT,
    updated_at  TEXT,
    FOREIGN KEY (auteur_id) REFERENCES auteurs(id)
);

CREATE TABLE IF NOT EXISTS critiques (
    id        TEXT NOT NULL PRIMARY KEY,
    nom       TEXT NOT NULL,
    animateur INTEGER NOT NULL DEFAULT 0,
    nb_avis   INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS avis (
    id           TEXT NOT NULL PRIMARY KEY,
    emission_id  TEXT NOT NULL,
    livre_id     TEXT NOT NULL,
    critique_id  TEXT NOT NULL,
    note         REAL,
    commentaire  TEXT,
    livre_titre  TEXT,
    auteur_nom   TEXT,
    critique_nom TEXT,
    match_phase  INTEGER,
    created_at   TEXT,
    FOREIGN KEY (emission_id) REFERENCES emissions(id),
    FOREIGN KEY (livre_id)    REFERENCES livres(id),
    FOREIGN KEY (critique_id) REFERENCES critiques(id)
);

CREATE TABLE IF NOT EXISTS emission_livres (
    emission_id TEXT NOT NULL,
    livre_id    TEXT NOT NULL,
    PRIMARY KEY (emission_id, livre_id),
    FOREIGN KEY (emission_id) REFERENCES emissions(id),
    FOREIGN KEY (livre_id)    REFERENCES livres(id)
);

CREATE TABLE IF NOT EXISTS avis_critiques (
    id             TEXT NOT NULL PRIMARY KEY,
    emission_id    TEXT,
    episode_title  TEXT,
    episode_date   TEXT,
    summary        TEXT,
    animateur      TEXT,
    critiques_json TEXT
);

CREATE TABLE IF NOT EXISTS palmares (
    rank         INTEGER NOT NULL,
    livre_id     TEXT NOT NULL PRIMARY KEY,
    titre        TEXT NOT NULL,
    auteur_nom   TEXT,
    note_moyenne REAL NOT NULL,
    nb_avis      INTEGER NOT NULL,
    nb_critiques INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS recommendations (
    rank          INTEGER NOT NULL,
    livre_id      TEXT NOT NULL PRIMARY KEY,
    titre         TEXT NOT NULL,
    auteur_nom    TEXT,
    score_hybride REAL NOT NULL,
    svd_predict   REAL,
    masque_mean   REAL,
    masque_count  INTEGER
);

CREATE VIRTUAL TABLE IF NOT EXISTS search_index USING fts4(
    type,
    ref_id,
    content,
    notindexed=type,
    notindexed=ref_id
);

CREATE TABLE IF NOT EXISTS db_metadata (
    key   TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL
);

-- Index required by Room @Index annotation on EmissionLivreEntity
CREATE INDEX IF NOT EXISTS index_emission_livres_livre_id ON emission_livres(livre_id);
"""


# ---------------------------------------------------------------------------
# Export functions
# ---------------------------------------------------------------------------


def export_episodes(mongo_db: Any, cur: sqlite3.Cursor) -> None:
    logger.info("Exporting episodes...")
    episodes = list(mongo_db.episodes.find({}))
    rows = [
        (
            str_id(ep["_id"]),
            ep.get("titre", ""),
            iso_date(ep.get("date")),
            ep.get("description"),
            ep.get("episode_page_url") or ep.get("url"),
            ep.get("duree"),
            1 if ep.get("masked") else 0,
        )
        for ep in episodes
    ]
    cur.executemany("INSERT OR REPLACE INTO episodes VALUES (?,?,?,?,?,?,?)", rows)
    logger.info(f"  → {len(rows)} episodes")


def export_auteurs(mongo_db: Any, cur: sqlite3.Cursor) -> dict[str, str]:
    """Export auteurs and return id → nom mapping."""
    logger.info("Exporting auteurs...")
    auteurs = list(mongo_db.auteurs.find({}))
    auteur_noms: dict[str, str] = {}
    rows = []
    for a in auteurs:
        aid = str_id(a["_id"])
        nom = a.get("nom", "")
        auteur_noms[aid] = nom
        rows.append((aid, nom, a.get("url_babelio")))
    cur.executemany("INSERT OR REPLACE INTO auteurs VALUES (?,?,?)", rows)
    logger.info(f"  → {len(rows)} auteurs")
    return auteur_noms


def export_livres(
    mongo_db: Any, cur: sqlite3.Cursor, auteur_noms: dict[str, str]
) -> None:
    logger.info("Exporting livres...")
    livres = list(mongo_db.livres.find({}))
    rows = []
    for livre in livres:
        lid = str_id(livre["_id"])
        auteur_id = str_id(livre["auteur_id"]) if livre.get("auteur_id") else None
        auteur_nom = auteur_noms.get(auteur_id, "") if auteur_id else ""
        editeur = livre.get("editeur") or ""
        rows.append(
            (
                lid,
                livre.get("titre", ""),
                auteur_id,
                auteur_nom,
                editeur,
                livre.get("url_babelio"),
                iso_date(livre.get("created_at")),
                iso_date(livre.get("updated_at")),
            )
        )
    cur.executemany("INSERT OR REPLACE INTO livres VALUES (?,?,?,?,?,?,?,?)", rows)
    logger.info(f"  → {len(rows)} livres")


def export_critiques(mongo_db: Any, cur: sqlite3.Cursor) -> dict[str, str]:
    """Export critiques and return id → nom mapping."""
    logger.info("Exporting critiques...")
    critiques = list(mongo_db.critiques.find({}))
    critique_noms: dict[str, str] = {}
    rows = []
    for c in critiques:
        cid = str_id(c["_id"])
        nom = c.get("nom", "")
        critique_noms[cid] = nom
        rows.append((cid, nom, 1 if c.get("animateur") else 0, 0))
    cur.executemany("INSERT OR REPLACE INTO critiques VALUES (?,?,?,?)", rows)
    logger.info(f"  → {len(rows)} critiques")
    return critique_noms


def export_emissions(mongo_db: Any, cur: sqlite3.Cursor) -> dict[str, str]:
    """Export emissions and return episode_oid_str → emission_id mapping."""
    logger.info("Exporting emissions...")
    emissions = list(mongo_db.emissions.find({}))
    episode_to_emission: dict[str, str] = {}
    rows = []
    for e in emissions:
        eid = str_id(e["_id"])
        episode_id = str_id(e.get("episode_id", ""))
        episode_to_emission[episode_id] = eid
        rows.append(
            (
                eid,
                episode_id,
                iso_date(e.get("date")) or "",
                e.get("duree"),
                str_id(e["animateur_id"]) if e.get("animateur_id") else None,
                0,  # nb_avis — will be updated
                0,  # has_summary — will be updated
                iso_date(e.get("created_at")),
                iso_date(e.get("updated_at")),
            )
        )
    cur.executemany("INSERT OR REPLACE INTO emissions VALUES (?,?,?,?,?,?,?,?,?)", rows)
    logger.info(f"  → {len(rows)} emissions")
    return episode_to_emission


def export_avis(
    mongo_db: Any,
    cur: sqlite3.Cursor,
    episode_to_emission: dict[str, str],
    critique_noms: dict[str, str],
) -> None:
    logger.info("Exporting avis...")
    avis_list = list(mongo_db.avis.find({}))

    # avis.emission_oid is a String in MongoDB (not ObjectId)
    rows = []
    emission_livres_pairs: set[tuple[str, str]] = set()

    for a in avis_list:
        aid = str_id(a["_id"])
        emission_oid = a.get("emission_oid", "")
        livre_oid = a.get("livre_oid", "")
        critique_oid = a.get("critique_oid", "")

        # emission_oid in avis is a String = str(ObjectId)
        emission_id = emission_oid  # already a string

        if emission_id and livre_oid:
            emission_livres_pairs.add((emission_id, livre_oid))

        critique_nom = critique_noms.get(
            critique_oid, a.get("critique_nom_extrait", "")
        )

        rows.append(
            (
                aid,
                emission_id,
                livre_oid,
                critique_oid,
                a.get("note"),
                a.get("commentaire"),
                a.get("livre_titre_extrait"),
                a.get("auteur_nom_extrait"),
                critique_nom,
                a.get("match_phase"),
                iso_date(a.get("created_at")),
            )
        )

    cur.executemany("INSERT OR REPLACE INTO avis VALUES (?,?,?,?,?,?,?,?,?,?,?)", rows)

    # Populate emission_livres junction table
    cur.executemany(
        "INSERT OR IGNORE INTO emission_livres VALUES (?,?)",
        list(emission_livres_pairs),
    )

    # Update nb_avis on emissions
    cur.execute("""
        UPDATE emissions SET nb_avis = (
            SELECT COUNT(*) FROM avis WHERE avis.emission_id = emissions.id
        )
    """)

    logger.info(
        f"  → {len(rows)} avis, {len(emission_livres_pairs)} emission-livre pairs"
    )


def export_avis_critiques(
    mongo_db: Any,
    cur: sqlite3.Cursor,
    episode_to_emission: dict[str, str],
) -> None:
    logger.info("Exporting avis_critiques (LLM summaries)...")
    avis_critiques = list(mongo_db.avis_critiques.find({}))
    rows = []
    emission_ids_with_summary: set[str] = set()

    for ac in avis_critiques:
        acid = str_id(ac["_id"])
        episode_oid = ac.get("episode_oid", "")
        # episode_oid in avis_critiques is a String
        emission_id = episode_to_emission.get(episode_oid)

        if emission_id:
            emission_ids_with_summary.add(emission_id)

        meta = ac.get("metadata_source") or {}
        critiques_list = meta.get("critiques", [])

        rows.append(
            (
                acid,
                emission_id,
                ac.get("episode_title"),
                ac.get("episode_date"),
                ac.get("summary"),
                meta.get("animateur"),
                json.dumps(critiques_list, ensure_ascii=False),
            )
        )

    cur.executemany(
        "INSERT OR REPLACE INTO avis_critiques VALUES (?,?,?,?,?,?,?)", rows
    )

    # Update has_summary on emissions
    for eid in emission_ids_with_summary:
        cur.execute("UPDATE emissions SET has_summary = 1 WHERE id = ?", (eid,))

    logger.info(f"  → {len(rows)} avis_critiques")


# ---------------------------------------------------------------------------
# Precalculation: palmares
# ---------------------------------------------------------------------------


def compute_palmares(cur: sqlite3.Cursor) -> None:
    logger.info("Computing palmares...")
    cur.execute("""
        INSERT INTO palmares (rank, livre_id, titre, auteur_nom,
                              note_moyenne, nb_avis, nb_critiques)
        SELECT
            ROW_NUMBER() OVER (ORDER BY AVG(a.note) DESC, COUNT(DISTINCT a.critique_id) DESC),
            l.id,
            l.titre,
            l.auteur_nom,
            ROUND(AVG(a.note), 2),
            COUNT(a.id),
            COUNT(DISTINCT a.critique_id)
        FROM avis a
        JOIN livres l ON l.id = a.livre_id
        WHERE a.note IS NOT NULL
        GROUP BY l.id
        ORDER BY AVG(a.note) DESC
    """)
    count = cur.execute("SELECT COUNT(*) FROM palmares").fetchone()[0]
    logger.info(f"  → {count} livres in palmares")


# ---------------------------------------------------------------------------
# Precalculation: recommendations (SVD)
# ---------------------------------------------------------------------------


def compute_recommendations(cur: sqlite3.Cursor, n_factors: int = 20) -> None:
    """
    Collaborative filtering SVD sur la matrice critiques × livres.
    Score hybride : 70% SVD + 30% moyenne Masque.
    """
    logger.info("Computing SVD recommendations...")

    # Load avis matrix
    rows_data = cur.execute(
        "SELECT critique_id, livre_id, note FROM avis WHERE note IS NOT NULL"
    ).fetchall()

    if not rows_data:
        logger.warning("No avis with notes — skipping recommendations")
        return

    # Build critique_id / livre_id index
    critique_ids = sorted({r[0] for r in rows_data})
    livre_ids = sorted({r[1] for r in rows_data})
    c_idx = {c: i for i, c in enumerate(critique_ids)}
    l_idx = {lid: i for i, lid in enumerate(livre_ids)}

    n_critiques = len(critique_ids)
    n_livres = len(livre_ids)

    # Sparse matrix
    data, row_idx, col_idx = [], [], []
    for critique_id, livre_id, note in rows_data:
        row_idx.append(c_idx[critique_id])
        col_idx.append(l_idx[livre_id])
        data.append(float(note))

    matrix = csr_matrix((data, (row_idx, col_idx)), shape=(n_critiques, n_livres))

    # Center by livre mean
    livre_means = np.array(matrix.mean(axis=0)).flatten()
    matrix_centered = matrix.copy().toarray()
    matrix_centered -= livre_means

    # SVD
    k = min(n_factors, min(n_critiques, n_livres) - 1)
    u, sigma, vt = svds(csr_matrix(matrix_centered), k=k)
    # Global critic (average over all critiques — simulates a "new user")
    global_critic_idx = np.mean(u, axis=0)
    svd_scores = global_critic_idx.dot(np.diag(sigma)).dot(vt) + livre_means

    # Build hybrid scores
    masque_means: dict[str, float] = {}
    masque_counts: dict[str, int] = {}
    for _, livre_id, note in rows_data:
        masque_means.setdefault(livre_id, 0.0)
        masque_counts.setdefault(livre_id, 0)
        masque_means[livre_id] += note
        masque_counts[livre_id] += 1
    for lid in masque_means:
        masque_means[lid] /= masque_counts[lid]

    scored = []
    for i, livre_id in enumerate(livre_ids):
        svd_score = float(svd_scores[i])
        masque_mean = masque_means.get(livre_id, svd_score)
        masque_count = masque_counts.get(livre_id, 0)
        hybrid = 0.7 * svd_score + 0.3 * masque_mean
        scored.append((livre_id, hybrid, svd_score, masque_mean, masque_count))

    scored.sort(key=lambda x: x[1], reverse=True)

    # Fetch livre titles
    livre_titles: dict[str, tuple[str, str]] = {
        row[0]: (row[1], row[2])
        for row in cur.execute("SELECT id, titre, auteur_nom FROM livres").fetchall()
    }

    rows_to_insert = []
    for rank, (livre_id, hybrid, svd, masque_mean, masque_count) in enumerate(
        scored[:500], start=1
    ):
        titre, auteur_nom = livre_titles.get(livre_id, ("", ""))
        rows_to_insert.append(
            (
                rank,
                livre_id,
                titre,
                auteur_nom,
                round(hybrid, 4),
                round(svd, 4),
                round(masque_mean, 2),
                masque_count,
            )
        )

    cur.executemany(
        "INSERT INTO recommendations VALUES (?,?,?,?,?,?,?,?)", rows_to_insert
    )
    logger.info(f"  → {len(rows_to_insert)} recommendations computed")


# ---------------------------------------------------------------------------
# Full-text search index (FTS5)
# ---------------------------------------------------------------------------


def build_search_index(cur: sqlite3.Cursor) -> None:
    logger.info("Building FTS5 search index...")

    # Emissions (titre de l'épisode + description)
    cur.execute("""
        INSERT INTO search_index(type, ref_id, content)
        SELECT 'emission', em.id,
               COALESCE(ep.titre, '') || ' ' || COALESCE(ep.description, '')
        FROM emissions em
        JOIN episodes ep ON ep.id = em.episode_id
    """)

    # Livres
    cur.execute("""
        INSERT INTO search_index(type, ref_id, content)
        SELECT 'livre', id,
               COALESCE(titre, '') || ' ' || COALESCE(auteur_nom, '') || ' ' || COALESCE(editeur, '')
        FROM livres
    """)

    # Auteurs
    cur.execute("""
        INSERT INTO search_index(type, ref_id, content)
        SELECT 'auteur', id, COALESCE(nom, '')
        FROM auteurs
    """)

    # Critiques
    cur.execute("""
        INSERT INTO search_index(type, ref_id, content)
        SELECT 'critique', id, COALESCE(nom, '')
        FROM critiques
    """)

    count = cur.execute("SELECT COUNT(*) FROM search_index").fetchone()[0]
    logger.info(f"  → {count} entries in search index")


# ---------------------------------------------------------------------------
# Update nb_avis on critiques
# ---------------------------------------------------------------------------


def update_critique_stats(cur: sqlite3.Cursor) -> None:
    cur.execute("""
        UPDATE critiques SET nb_avis = (
            SELECT COUNT(*) FROM avis WHERE avis.critique_id = critiques.id
        )
    """)


# ---------------------------------------------------------------------------
# Metadata
# ---------------------------------------------------------------------------


def write_metadata(cur: sqlite3.Cursor) -> None:
    now = datetime.now()
    version = int(time.time())
    nb_emissions = cur.execute("SELECT COUNT(*) FROM emissions").fetchone()[0]
    nb_livres = cur.execute("SELECT COUNT(*) FROM livres").fetchone()[0]
    nb_avis = cur.execute("SELECT COUNT(*) FROM avis").fetchone()[0]

    metadata = [
        ("version", str(version)),
        ("export_date", now.strftime("%Y-%m-%d")),
        ("export_datetime", now.isoformat()),
        ("source_db", MONGO_DB),
        ("nb_emissions", str(nb_emissions)),
        ("nb_livres", str(nb_livres)),
        ("nb_avis", str(nb_avis)),
    ]
    cur.executemany("INSERT OR REPLACE INTO db_metadata VALUES (?,?)", metadata)

    # user_version = 1 (version du schéma Room — fixe, indépendant de la date d'export)
    # La date d'export est dans db_metadata("export_date")
    cur.execute("PRAGMA user_version = 1")

    logger.info(
        f"  Metadata: version={version}, date={now.strftime('%Y-%m-%d')}, "
        f"emissions={nb_emissions}, livres={nb_livres}, avis={nb_avis}"
    )


# ---------------------------------------------------------------------------
# Verify
# ---------------------------------------------------------------------------


def verify_database(db_path: Path) -> None:
    """Print a summary of the generated database."""
    con = sqlite3.connect(db_path)
    cur = con.cursor()

    tables = [
        "episodes",
        "emissions",
        "livres",
        "auteurs",
        "critiques",
        "avis",
        "palmares",
        "recommendations",
        "avis_critiques",
    ]

    click.echo(f"\n{'=' * 50}")
    click.echo(f"Database: {db_path}")
    click.echo(f"Size: {db_path.stat().st_size / 1024 / 1024:.1f} MB")
    click.echo(f"{'=' * 50}")
    for table in tables:
        count = cur.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
        click.echo(f"  {table:<25} {count:>6} rows")

    meta = dict(cur.execute("SELECT key, value FROM db_metadata").fetchall())
    click.echo(f"\nExport date  : {meta.get('export_date', 'unknown')}")
    click.echo(f"Version      : {meta.get('version', 'unknown')}")
    click.echo(f"{'=' * 50}\n")
    con.close()


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


@click.command()
@click.option(
    "--mongo-uri",
    default="mongodb://localhost:27017",
    show_default=True,
    help="MongoDB connection URI",
)
@click.option(
    "--output",
    default="lmelp.db",
    show_default=True,
    type=click.Path(),
    help="Output SQLite file path",
)
@click.option(
    "--force",
    is_flag=True,
    default=False,
    help="Overwrite output file if it exists",
)
@click.option(
    "--verify",
    type=click.Path(exists=True),
    default=None,
    help="Verify existing SQLite file and exit",
)
@click.option(
    "--svd-factors",
    default=20,
    show_default=True,
    help="Number of SVD factors for recommendations",
)
def main(
    mongo_uri: str,
    output: str,
    force: bool,
    verify: str | None,
    svd_factors: int,
) -> None:
    """Export MongoDB masque_et_la_plume to SQLite for lmelp-mobile."""

    if verify:
        verify_database(Path(verify))
        return

    output_path = Path(output)

    if output_path.exists():
        if not force:
            click.echo(
                f"Error: {output_path} already exists. Use --force to overwrite."
            )
            raise SystemExit(1)
        output_path.unlink()

    output_path.parent.mkdir(parents=True, exist_ok=True)

    logger.info(f"Connecting to MongoDB: {mongo_uri}")
    client = MongoClient(mongo_uri)
    mongo_db = client[MONGO_DB]

    logger.info(f"Creating SQLite database: {output_path}")
    con = sqlite3.connect(output_path)
    cur = con.cursor()

    # Schema
    cur.executescript(SCHEMA_SQL)

    # Export collections
    export_episodes(mongo_db, cur)
    auteur_noms = export_auteurs(mongo_db, cur)
    export_livres(mongo_db, cur, auteur_noms)
    critique_noms = export_critiques(mongo_db, cur)
    episode_to_emission = export_emissions(mongo_db, cur)
    export_avis(mongo_db, cur, episode_to_emission, critique_noms)
    export_avis_critiques(mongo_db, cur, episode_to_emission)

    # Precalculations
    compute_palmares(cur)
    compute_recommendations(cur, n_factors=svd_factors)
    build_search_index(cur)
    update_critique_stats(cur)
    write_metadata(cur)

    con.commit()
    con.close()
    client.close()

    logger.info("Export complete.")
    verify_database(output_path)


if __name__ == "__main__":
    main()
