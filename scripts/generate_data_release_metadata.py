#!/usr/bin/env python3
"""Génère metadata.json pour une GitHub Release de données lmelp (issue #116).

Le fichier metadata.json est publié comme asset secondaire à côté de lmelp.db
sur la Release "data-v{N}" (N = ROOM_VERSION, voir issue #132). Il permet à
l'app de vérifier si une mise à jour est disponible (via export_date/sha256)
sans télécharger tout le fichier lmelp.db.

Usage:
    python scripts/generate_data_release_metadata.py --db lmelp.db --output metadata.json
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
from pathlib import Path


SCHEMA_VERSION = 1


def compute_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_db_metadata(db_path: Path) -> dict[str, str]:
    con = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    try:
        rows = con.execute("SELECT key, value FROM db_metadata").fetchall()
    except sqlite3.OperationalError as exc:
        raise ValueError(f"table db_metadata absente de {db_path}") from exc
    finally:
        con.close()

    if not rows:
        raise ValueError(f"table db_metadata vide dans {db_path}")

    return dict(rows)


def build_metadata(db_path: Path) -> dict:
    db_meta = read_db_metadata(db_path)
    return {
        "schema_version": SCHEMA_VERSION,
        "export_date": db_meta.get("export_date"),
        "export_datetime": db_meta.get("export_datetime"),
        "export_version": db_meta.get("version"),
        "content_hash": db_meta.get("content_hash"),
        "nb_emissions": int(db_meta.get("nb_emissions", 0)),
        "nb_livres": int(db_meta.get("nb_livres", 0)),
        "nb_avis": int(db_meta.get("nb_avis", 0)),
        "file_size_bytes": db_path.stat().st_size,
        "sha256": compute_sha256(db_path),
        "filename": "lmelp.db",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    metadata = build_metadata(args.db)
    args.output.write_text(json.dumps(metadata, indent=2, ensure_ascii=False))
    print(f"metadata.json généré : {args.output}")
    print(json.dumps(metadata, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
