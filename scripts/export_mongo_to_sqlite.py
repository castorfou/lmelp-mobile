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
import unicodedata
from datetime import datetime
from pathlib import Path
from typing import Any

import click
from bson import ObjectId
from dotenv import load_dotenv
from pymongo import MongoClient


# Charge les variables depuis scripts/.env si présent (sans écraser les vars d'env existantes)
load_dotenv(Path(__file__).parent / ".env")


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
    section      TEXT,
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
    rank                INTEGER NOT NULL,
    livre_id            TEXT NOT NULL PRIMARY KEY,
    titre               TEXT NOT NULL,
    auteur_nom          TEXT,
    note_moyenne        REAL NOT NULL,
    nb_avis             INTEGER NOT NULL,
    nb_critiques        INTEGER NOT NULL,
    calibre_in_library  INTEGER NOT NULL DEFAULT 0,
    calibre_lu          INTEGER NOT NULL DEFAULT 0,
    calibre_rating      REAL
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

CREATE VIRTUAL TABLE IF NOT EXISTS search_index USING fts5(
    type UNINDEXED,
    ref_id UNINDEXED,
    content,
    tokenize = 'unicode61 remove_diacritics 2'
);

CREATE TABLE IF NOT EXISTS db_metadata (
    key   TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL
);

-- Index required by Room @Index annotation on EmissionLivreEntity
CREATE INDEX IF NOT EXISTS index_emission_livres_livre_id ON emission_livres(livre_id);

CREATE TABLE IF NOT EXISTS onkindle (
    livre_id       TEXT NOT NULL PRIMARY KEY,
    titre          TEXT NOT NULL,
    auteur_nom     TEXT,
    url_babelio    TEXT,
    calibre_lu     INTEGER NOT NULL DEFAULT 0,
    calibre_rating REAL,
    note_moyenne   REAL,
    nb_avis        INTEGER NOT NULL DEFAULT 0
);
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
                a.get("section"),
                iso_date(a.get("created_at")),
            )
        )

    cur.executemany(
        "INSERT OR REPLACE INTO avis VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", rows
    )

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
    logger.info("Computing palmares (nb_avis >= 2)...")
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
        HAVING COUNT(a.id) >= 2
        ORDER BY AVG(a.note) DESC
    """)
    count = cur.execute("SELECT COUNT(*) FROM palmares").fetchone()[0]
    logger.info(f"  → {count} livres in palmares")


def _normalize_title(titre: str) -> str:
    """Normalise un titre pour le matching : minuscules + suppression accents + apostrophes.

    Remplace les apostrophes typographiques (\u2018, \u2019) par l'apostrophe droite
    avant la normalisation Unicode, puis supprime toute apostrophe résiduelle
    pour garantir le matching entre titres Calibre et titres de la base.
    """
    normalised = titre.replace("\u2018", "'").replace("\u2019", "'")
    normalised = normalised.replace("'", "")
    nfkd = unicodedata.normalize("NFKD", normalised)
    ascii_str = nfkd.encode("ascii", "ignore").decode("ascii")
    return ascii_str.lower().strip()


def import_calibre_data(
    cur: sqlite3.Cursor,
    calibre_db_path: str,
    virtual_library_tag: str | None = None,
) -> None:
    """Croise les livres du palmarès avec la bibliothèque Calibre.

    Met à jour calibre_in_library, calibre_lu et calibre_rating dans palmares.
    """
    logger.info(f"Importing Calibre data from: {calibre_db_path}")

    try:
        cal_con = sqlite3.connect(calibre_db_path)
        cal_con.row_factory = sqlite3.Row
        cal_cur = cal_con.cursor()

        # Trouve l'id de la colonne personnalisée 'read'
        read_col_id: int | None = None
        row = cal_cur.execute(
            "SELECT id FROM custom_columns WHERE label = 'read'"
        ).fetchone()
        if row:
            read_col_id = row["id"]
            logger.info(f"  Calibre 'read' custom column id: {read_col_id}")
        else:
            logger.warning("  Colonne 'read' non trouvée dans Calibre")

        # Charge les livres Calibre (avec filtre bibliothèque virtuelle si fourni)
        if virtual_library_tag:
            cal_cur.execute(
                """
                SELECT b.id, b.title FROM books b
                JOIN books_tags_link btl ON b.id = btl.book
                JOIN tags t ON btl.tag = t.id
                WHERE t.name = ?
            """,
                (virtual_library_tag,),
            )
        else:
            cal_cur.execute("SELECT id, title FROM books")

        calibre_books = cal_cur.fetchall()

        # Index Calibre : titre normalisé → id
        calibre_index: dict[str, int] = {}
        for book in calibre_books:
            norm = _normalize_title(book["title"])
            calibre_index[norm] = book["id"]

        logger.info(f"  {len(calibre_index)} livres Calibre chargés")

        # Palmares livres
        palmares_rows = cur.execute("SELECT livre_id, titre FROM palmares").fetchall()
        matched = 0

        for livre_id, titre in palmares_rows:
            norm = _normalize_title(titre)
            calibre_id = calibre_index.get(norm)
            if calibre_id is None:
                continue

            # Statut lu
            calibre_lu = 0
            if read_col_id is not None:
                lu_row = cal_cur.execute(
                    f"SELECT value FROM custom_column_{read_col_id} WHERE book = ?",
                    (calibre_id,),
                ).fetchone()
                if lu_row and lu_row["value"]:
                    calibre_lu = 1

            # Rating
            calibre_rating: float | None = None
            rating_row = cal_cur.execute(
                """SELECT r.rating FROM ratings r
                   JOIN books_ratings_link brl ON r.id = brl.rating
                   WHERE brl.book = ?""",
                (calibre_id,),
            ).fetchone()
            if rating_row and rating_row["rating"] is not None:
                calibre_rating = float(rating_row["rating"])

            cur.execute(
                """UPDATE palmares
                   SET calibre_in_library = 1, calibre_lu = ?, calibre_rating = ?
                   WHERE livre_id = ?""",
                (calibre_lu, calibre_rating, livre_id),
            )
            matched += 1

        cal_con.close()
        logger.info(f"  → {matched} livres matchés avec Calibre")

    except Exception as e:
        logger.error(f"Erreur import Calibre : {e}")


def build_onkindle_table(
    cur: sqlite3.Cursor,
    calibre_db_path: str,
    virtual_library_tag: str | None = None,
) -> None:
    """Construit la table onkindle à partir des livres tagués 'onkindle' dans Calibre.

    Pour chaque livre Calibre avec ce tag :
    - Récupère calibre_lu, calibre_rating
    - Croise avec palmares pour note_moyenne / nb_avis
    - Croise avec livres pour url_babelio et auteur_nom

    Si virtual_library_tag est fourni (ex: 'guillaume'), seuls les livres ayant
    à la fois le tag 'onkindle' ET le tag de la virtual library sont inclus.
    """
    logger.info(f"Building onkindle table from Calibre: {calibre_db_path}")

    try:
        cal_con = sqlite3.connect(calibre_db_path)
        cal_con.row_factory = sqlite3.Row
        cal_cur = cal_con.cursor()

        # Trouve l'id de la colonne personnalisée 'read'
        read_col_id: int | None = None
        row = cal_cur.execute(
            "SELECT id FROM custom_columns WHERE label = 'read'"
        ).fetchone()
        if row:
            read_col_id = row["id"]

        # Charge les livres avec le tag 'onkindle' + auteur Calibre (premier auteur)
        # Si virtual_library_tag fourni : filtre onkindle AND virtual_library_tag
        if virtual_library_tag:
            cal_cur.execute(
                """
                SELECT b.id, b.title, a.name as auteur_calibre
                FROM books b
                JOIN books_tags_link btl_ok ON b.id = btl_ok.book
                JOIN tags t_ok ON btl_ok.tag = t_ok.id AND t_ok.name = 'onkindle'
                JOIN books_tags_link btl_vl ON b.id = btl_vl.book
                JOIN tags t_vl ON btl_vl.tag = t_vl.id AND t_vl.name = ?
                LEFT JOIN books_authors_link bal ON b.id = bal.book
                LEFT JOIN authors a ON bal.author = a.id
                GROUP BY b.id
                """,
                (virtual_library_tag,),
            )
        else:
            cal_cur.execute(
                """
                SELECT b.id, b.title, a.name as auteur_calibre
                FROM books b
                JOIN books_tags_link btl ON b.id = btl.book
                JOIN tags t ON btl.tag = t.id
                LEFT JOIN books_authors_link bal ON b.id = bal.book
                LEFT JOIN authors a ON bal.author = a.id
                WHERE t.name = 'onkindle'
                GROUP BY b.id
                """
            )
        kindle_books = cal_cur.fetchall()
        logger.info(
            f"  {len(kindle_books)} livres avec tag 'onkindle' trouvés dans Calibre"
        )

        # Index des palmares (titre normalisé → row)
        palmares_rows = cur.execute(
            "SELECT livre_id, titre, note_moyenne, nb_avis FROM palmares"
        ).fetchall()
        palmares_index: dict[str, tuple[str, float, int]] = {}
        for livre_id, titre, note_moyenne, nb_avis in palmares_rows:
            norm = _normalize_title(titre)
            palmares_index[norm] = (livre_id, note_moyenne, nb_avis)

        # Index des livres (titre normalisé → row)
        livres_rows = cur.execute(
            "SELECT id, titre, auteur_nom, url_babelio FROM livres"
        ).fetchall()
        livres_index: dict[str, tuple[str, str | None, str | None]] = {}
        for livre_id, titre, auteur_nom, url_babelio in livres_rows:
            norm = _normalize_title(titre)
            livres_index[norm] = (livre_id, auteur_nom, url_babelio)

        # Index des avis (livre_id → (note_moyenne, nb_avis)) — fallback pour les livres
        # ayant des avis mais absents de palmares (coups de cœur, nb_avis < 2, etc.)
        avis_rows = cur.execute(
            "SELECT livre_id, note FROM avis WHERE note IS NOT NULL"
        ).fetchall()
        avis_by_livre: dict[str, list[float]] = {}
        for livre_id, note in avis_rows:
            avis_by_livre.setdefault(livre_id, []).append(note)
        avis_index: dict[str, tuple[float, int]] = {
            lid: (sum(notes) / len(notes), len(notes))
            for lid, notes in avis_by_livre.items()
        }

        inserted = 0
        for book in kindle_books:
            calibre_id = book["id"]
            calibre_titre = book["title"]
            calibre_auteur: str | None = book["auteur_calibre"]
            norm = _normalize_title(calibre_titre)

            # Statut lu
            calibre_lu = 0
            if read_col_id is not None:
                lu_row = cal_cur.execute(
                    f"SELECT value FROM custom_column_{read_col_id} WHERE book = ?",
                    (calibre_id,),
                ).fetchone()
                if lu_row and lu_row["value"]:
                    calibre_lu = 1

            # Rating
            calibre_rating: float | None = None
            rating_row = cal_cur.execute(
                """SELECT r.rating FROM ratings r
                   JOIN books_ratings_link brl ON r.id = brl.rating
                   WHERE brl.book = ?""",
                (calibre_id,),
            ).fetchone()
            if rating_row and rating_row["rating"] is not None:
                calibre_rating = float(rating_row["rating"])

            # Croisement palmares
            note_moyenne: float | None = None
            nb_avis: int = 0
            livre_id_from_palmares: str | None = None
            if norm in palmares_index:
                livre_id_from_palmares, note_moyenne, nb_avis = palmares_index[norm]

            # Croisement livres (url_babelio, auteur_nom)
            auteur_nom: str | None = None
            url_babelio: str | None = None
            final_livre_id: str | None = None
            if norm in livres_index:
                final_livre_id, auteur_nom, url_babelio = livres_index[norm]
            elif livre_id_from_palmares:
                final_livre_id = livre_id_from_palmares

            # Fallback auteur depuis Calibre si non trouvé dans livres
            if auteur_nom is None and calibre_auteur:
                auteur_nom = calibre_auteur

            # Fallback avis : si pas dans palmares mais des avis existent pour ce livre_id
            if (
                note_moyenne is None
                and final_livre_id is not None
                and final_livre_id in avis_index
            ):
                note_moyenne, nb_avis = avis_index[final_livre_id]

            # Utilise un id synthétique si le livre n'est pas dans notre base
            if final_livre_id is None:
                final_livre_id = f"calibre_{calibre_id}"

            cur.execute(
                """INSERT OR REPLACE INTO onkindle
                   (livre_id, titre, auteur_nom, url_babelio, calibre_lu, calibre_rating, note_moyenne, nb_avis)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    final_livre_id,
                    calibre_titre,
                    auteur_nom,
                    url_babelio,
                    calibre_lu,
                    calibre_rating,
                    note_moyenne,
                    nb_avis,
                ),
            )
            inserted += 1

        cal_con.close()
        logger.info(f"  → {inserted} livres insérés dans la table onkindle")

    except Exception as e:
        logger.error(f"Erreur build_onkindle_table : {e}")


# ---------------------------------------------------------------------------
# Precalculation: recommendations (SVD)
# ---------------------------------------------------------------------------


def compute_recommendations(cur: sqlite3.Cursor, n_factors: int = 20) -> None:
    """
    Collaborative filtering SVD sur la matrice critiques × livres.
    Score hybride : 70% SVD + 30% moyenne Masque.
    """
    import numpy as np
    from scipy.sparse import csr_matrix
    from scipy.sparse.linalg import svds

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

    # user_version = version du schéma Room — doit correspondre à @Database(version=N) dans LmelpDatabase.kt
    # v3 : ajout table onkindle (issue #52)
    cur.execute("PRAGMA user_version = 3")

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
        "onkindle",
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
    envvar="LMELP_MONGO_URI",
    help="MongoDB connection URI",
)
@click.option(
    "--output",
    default="lmelp.db",
    show_default=True,
    type=click.Path(),
    envvar="LMELP_OUTPUT",
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
@click.option(
    "--calibre-db",
    type=click.Path(exists=True),
    default=None,
    envvar="LMELP_CALIBRE_DB",
    help="Path to Calibre metadata.db (optional, enriches palmares with lu/rating)",
)
@click.option(
    "--calibre-virtual-library",
    default=None,
    envvar="LMELP_CALIBRE_VIRTUAL_LIBRARY",
    help="Calibre virtual library tag to filter books (e.g. 'guillaume')",
)
def main(
    mongo_uri: str,
    output: str,
    force: bool,
    verify: str | None,
    svd_factors: int,
    calibre_db: str | None,
    calibre_virtual_library: str | None,
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
    if calibre_db:
        import_calibre_data(cur, calibre_db, calibre_virtual_library)
        build_onkindle_table(cur, calibre_db, calibre_virtual_library)
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
