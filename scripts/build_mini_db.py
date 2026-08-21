#!/usr/bin/env python3
"""
Génère une mini base de données à partir d'une lmelp.db complète déjà exportée.

Ne se connecte pas à MongoDB : dérive un extrait réel minimal (N émissions les
plus anciennes + tables liées) d'une lmelp.db existante, pour servir de base
embarquée par défaut dans l'APK (app/src/main/assets/lmelp.db). Voir ADR 0002
(docs/dev/adr/0002-distribution-app-mini-db-embarquee.md) : cet extrait n'a
plus besoin d'être complet ni frais, la vraie base étant téléchargée par
l'app au lancement (issue #118). Le garder minimal rend visible toute
régression de ce mécanisme de téléchargement.

Usage:
    python build_mini_db.py --source lmelp.db --output mini.db --nb-emissions 3
"""

from __future__ import annotations

import sqlite3
from datetime import UTC, datetime
from pathlib import Path

import click
import export_mongo_to_sqlite as export_script


def build_mini_db(source_path: Path, dest_path: Path, nb_emissions: int) -> None:
    """Dérive une mini-DB de `nb_emissions` émissions les plus anciennes depuis `source_path`."""
    src = sqlite3.connect(source_path)
    total = src.execute("SELECT COUNT(*) FROM emissions").fetchone()[0]
    if total < nb_emissions:
        raise ValueError(
            f"La base source ne contient que {total} émission(s), "
            f"impossible d'en extraire {nb_emissions}."
        )

    emission_ids = [
        row[0]
        for row in src.execute(
            "SELECT id FROM emissions ORDER BY date LIMIT ?", (nb_emissions,)
        ).fetchall()
    ]

    dest_path.unlink(missing_ok=True)
    dest = sqlite3.connect(dest_path)
    dest.executescript(export_script.SCHEMA_SQL)
    dest.execute(f"ATTACH DATABASE '{source_path}' AS src")

    placeholders = ",".join("?" for _ in emission_ids)

    dest.execute("INSERT INTO critiques SELECT * FROM src.critiques")

    dest.execute(
        f"""
        INSERT INTO episodes
        SELECT * FROM src.episodes WHERE id IN (
            SELECT episode_id FROM src.emissions WHERE id IN ({placeholders})
        )
        """,
        emission_ids,
    )
    dest.execute(
        f"INSERT INTO emissions SELECT * FROM src.emissions WHERE id IN ({placeholders})",
        emission_ids,
    )
    dest.execute(
        f"""
        INSERT INTO auteurs
        SELECT * FROM src.auteurs WHERE id IN (
            SELECT DISTINCT auteur_id FROM src.livres WHERE auteur_id IS NOT NULL
            AND id IN (
                SELECT DISTINCT livre_id FROM src.emission_livres
                WHERE emission_id IN ({placeholders})
            )
        )
        """,
        emission_ids,
    )
    dest.execute(
        f"""
        INSERT INTO livres
        SELECT * FROM src.livres WHERE id IN (
            SELECT DISTINCT livre_id FROM src.emission_livres
            WHERE emission_id IN ({placeholders})
        )
        """,
        emission_ids,
    )
    dest.execute(
        f"""
        INSERT INTO emission_livres
        SELECT * FROM src.emission_livres WHERE emission_id IN ({placeholders})
        """,
        emission_ids,
    )
    dest.execute(
        f"""
        INSERT INTO avis
        SELECT * FROM src.avis WHERE emission_id IN ({placeholders})
        """,
        emission_ids,
    )
    dest.execute(
        f"""
        INSERT INTO avis_critiques
        SELECT * FROM src.avis_critiques WHERE emission_id IN ({placeholders})
        """,
        emission_ids,
    )

    dest.commit()
    dest.execute("DETACH DATABASE src")

    cur = dest.cursor()
    export_script.compute_palmares(cur)
    export_script.build_search_index(cur)
    export_script.update_critique_stats(cur)
    _write_mini_db_metadata(cur)

    dest.commit()
    dest.close()
    src.close()


def _write_mini_db_metadata(cur: sqlite3.Cursor) -> None:
    """Écrit db_metadata avec version = date de la dernière émission gardée.

    Ne réutilise pas export_mongo_to_sqlite.write_metadata (qui date `version`
    du moment de génération du fichier) : DataUpdateRepository.checkForUpdate
    compare ce timestamp à celui de la vraie release distante pour décider si
    une mise à jour est disponible. Si `version` reflétait le moment où la
    mini-DB a été construite (aujourd'hui), elle paraîtrait toujours plus
    "fraîche" qu'une release déjà publiée, et l'app afficherait "à jour" à
    tort alors qu'elle ne contient qu'un extrait minimal (constaté en test
    device sur l'issue #119).
    """
    last_emission_date = cur.execute("SELECT MAX(date) FROM emissions").fetchone()[0]
    dt = datetime.fromisoformat(last_emission_date)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=UTC)
    version = int(dt.timestamp())

    nb_emissions = cur.execute("SELECT COUNT(*) FROM emissions").fetchone()[0]
    nb_livres = cur.execute("SELECT COUNT(*) FROM livres").fetchone()[0]
    nb_avis = cur.execute("SELECT COUNT(*) FROM avis").fetchone()[0]

    metadata = [
        ("version", str(version)),
        ("export_date", dt.strftime("%Y-%m-%d")),
        ("export_datetime", dt.isoformat()),
        ("source_db", "mini-db (extrait, voir ADR 0002)"),
        ("nb_emissions", str(nb_emissions)),
        ("nb_livres", str(nb_livres)),
        ("nb_avis", str(nb_avis)),
    ]
    cur.executemany("INSERT OR REPLACE INTO db_metadata VALUES (?,?)", metadata)
    cur.execute(f"PRAGMA user_version = {export_script.ROOM_VERSION}")


@click.command()
@click.option(
    "--source",
    required=True,
    type=click.Path(exists=True, path_type=Path),
    help="Chemin vers la lmelp.db complète source",
)
@click.option(
    "--output",
    required=True,
    type=click.Path(path_type=Path),
    help="Chemin de sortie de la mini-DB",
)
@click.option(
    "--nb-emissions",
    default=3,
    show_default=True,
    help="Nombre d'émissions (les plus anciennes) à conserver",
)
def main(source: Path, output: Path, nb_emissions: int) -> None:
    build_mini_db(source, output, nb_emissions)
    export_script.verify_database(output)


if __name__ == "__main__":
    main()
