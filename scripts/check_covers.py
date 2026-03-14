#!/usr/bin/env python3
"""
Génère un rapport HTML des couvertures de livres attendues vs présentes en cache.

Affiche pour chaque catégorie (Émissions, Palmarès, Conseils, OnKindle) :
- Titre + auteur du livre
- Statut cache (✓ en cache / ✗ absent / ⚠ URL Babelio manquante)
- L'image de couverture chargée depuis le cache JSON
- Un lien vers la page Babelio

Usage:
    python scripts/check_covers.py                          # téléphone connecté via adb
    python scripts/check_covers.py --cache-file /tmp/c.json # cache JSON fourni manuellement
    python scripts/check_covers.py --db autre/lmelp.db      # base SQLite différente
    python scripts/check_covers.py --output /tmp/out.html   # fichier de sortie
    python scripts/check_covers.py --no-open                # ne pas ouvrir le navigateur

Mode --fetch (pré-remplissage du cache depuis le PC) :
    python scripts/check_covers.py --fetch                  # fetche les manquants → écrit assets/ + push adb
    python scripts/check_covers.py --fetch --force          # re-fetche même les entrées déjà en cache
    python scripts/check_covers.py --fetch --push           # force le push adb même si aucun ajout
    python scripts/check_covers.py --fetch --no-open        # sans ouvrir le navigateur

Workflow typique après export DB :
    python scripts/export_mongo_to_sqlite.py --force
    python scripts/check_covers.py --fetch --no-open        # enrichit assets/couvertures_cache.json
"""

import argparse
import json
import os
import random
import re
import sqlite3
import subprocess
import sys
import time
import urllib.request
import webbrowser
from pathlib import Path


# Charger scripts/.env si disponible (BABELIO_COOKIE, etc.)
try:
    from dotenv import load_dotenv

    load_dotenv(Path(__file__).parent / ".env")
except ImportError:
    pass


DEFAULT_DB = Path(__file__).parent.parent / "app/src/main/assets/lmelp.db"
DEFAULT_OUTPUT = Path(__file__).parent.parent / "data/processed/cover_report.html"
DEFAULT_ASSETS_CACHE = (
    Path(__file__).parent.parent / "app/src/main/assets/couvertures_cache.json"
)
ADB_CACHE_PATH = "/sdcard/Android/data/com.lmelp.mobile/files/couvertures_cache.json"
CACHE_VERSION = 2
BABELIO_MIN_INTERVAL_S = 3.0  # délai minimum entre requêtes Babelio


# ── Lecture de la DB ──────────────────────────────────────────────────────────


def get_emissions_slides(cur: sqlite3.Cursor, limit: int = 10) -> list[dict]:
    cur.execute(
        """
        SELECT em.id AS emission_id, em.date AS emission_date,
               a.livre_id, l.titre AS livre_titre, l.auteur_nom,
               l.url_babelio, AVG(a.note) AS note_moyenne
        FROM emissions em
        JOIN avis a ON a.emission_id = em.id
        JOIN livres l ON l.id = a.livre_id
        WHERE a.note IS NOT NULL AND a.section = 'programme'
        GROUP BY em.id, a.livre_id
        HAVING AVG(a.note) = (
            SELECT MAX(avg_inner) FROM (
                SELECT AVG(a2.note) AS avg_inner FROM avis a2
                WHERE a2.emission_id = em.id AND a2.note IS NOT NULL AND a2.section = 'programme'
                GROUP BY a2.livre_id
            )
        )
        ORDER BY em.date DESC
        LIMIT ?
    """,
        (limit * 3,),
    )
    rows = cur.fetchall()
    seen: set[str] = set()
    result = []
    for row in rows:
        emission_id = row[0]
        if emission_id not in seen:
            seen.add(emission_id)
            result.append(
                {
                    "livre_id": row[2],
                    "titre": row[3],
                    "auteur": row[4] or "",
                    "url_babelio": row[5],
                    "note": f"{row[6]:.1f}/10",
                    "date": row[1][:10] if row[1] else "",
                }
            )
        if len(result) == limit:
            break
    return result


def get_palmares_slides(cur: sqlite3.Cursor, limit: int = 10) -> list[dict]:
    cur.execute(
        """
        SELECT p.livre_id, p.titre, p.auteur_nom, p.note_moyenne, l.url_babelio
        FROM palmares p
        JOIN livres l ON l.id = p.livre_id
        WHERE p.nb_avis >= 2
          AND (COALESCE(p.calibre_in_library, 0) = 0 OR COALESCE(p.calibre_lu, 0) = 0)
        ORDER BY p.rank ASC
        LIMIT ?
    """,
        (limit,),
    )
    return [
        {
            "livre_id": r[0],
            "titre": r[1],
            "auteur": r[2] or "",
            "note": f"{r[3]:.1f}/10",
            "url_babelio": r[4],
        }
        for r in cur.fetchall()
    ]


def get_conseils_slides(cur: sqlite3.Cursor, limit: int = 10) -> list[dict]:
    cur.execute(
        """
        SELECT r.livre_id, r.titre, r.auteur_nom, l.url_babelio
        FROM recommendations r
        JOIN livres l ON l.id = r.livre_id
        LEFT JOIN palmares p ON r.livre_id = p.livre_id
        WHERE COALESCE(p.calibre_in_library, 0) = 0
           OR COALESCE(p.calibre_lu, 0) = 0
        ORDER BY r.rank ASC
        LIMIT ?
    """,
        (limit,),
    )
    return [
        {"livre_id": r[0], "titre": r[1], "auteur": r[2] or "", "url_babelio": r[3]}
        for r in cur.fetchall()
    ]


def get_onkindle_slides(cur: sqlite3.Cursor) -> list[dict]:
    cur.execute("""
        SELECT livre_id, titre, auteur_nom, url_babelio
        FROM onkindle
        WHERE url_babelio IS NOT NULL
        ORDER BY titre
    """)
    return [
        {"livre_id": r[0], "titre": r[1], "auteur": r[2] or "", "url_babelio": r[3]}
        for r in cur.fetchall()
    ]


# ── Lecture du cache JSON ─────────────────────────────────────────────────────


def load_cache_from_adb() -> dict[str, str] | None:
    try:
        result = subprocess.run(
            ["adb", "shell", "cat", ADB_CACHE_PATH],
            capture_output=True,
            text=True,
            timeout=10,
        )
        if result.returncode != 0 or not result.stdout.strip():
            return None
        return json.loads(result.stdout)
    except Exception:
        return None


def load_cache_from_file(path: str) -> dict[str, str]:
    with open(path) as f:
        return json.load(f)


# ── Génération HTML ───────────────────────────────────────────────────────────


def card_html(livre: dict, cache: dict[str, str]) -> str:
    titre = livre["titre"]
    auteur = livre["auteur"]
    url_babelio = livre.get("url_babelio") or ""
    note = livre.get("note", "")
    date = livre.get("date", "")

    if not url_babelio:
        status_class = "no-babelio"
        status_icon = "⚠"
        status_label = "URL Babelio manquante"
        img_html = '<div class="placeholder">?</div>'
    elif url_babelio in cache:
        url_image = cache[url_babelio]
        if url_image:
            status_class = "cached"
            status_icon = "✓"
            status_label = "En cache"
            img_html = f'<img src="{url_image}" loading="lazy" alt="{titre}" />'
        else:
            status_class = "empty"
            status_icon = "⚠"
            status_label = "Aucune image trouvée sur Babelio"
            img_html = '<div class="placeholder">∅</div>'
    else:
        status_class = "missing"
        status_icon = "✗"
        status_label = "Absent du cache"
        img_html = '<div class="placeholder">?</div>'

    babelio_link = (
        f'<a href="{url_babelio}" target="_blank" class="babelio-link">Babelio ↗</a>'
        if url_babelio
        else '<span class="no-link">—</span>'
    )
    meta = " · ".join(filter(None, [note, date]))

    return f"""
    <div class="card {status_class}">
      <div class="img-wrap">{img_html}</div>
      <div class="info">
        <span class="status-icon" title="{status_label}">{status_icon}</span>
        <p class="title">{titre}</p>
        <p class="author">{auteur}</p>
        {"<p class='meta'>" + meta + "</p>" if meta else ""}
        {babelio_link}
      </div>
    </div>"""


def section_html(title: str, livres: list[dict], cache: dict[str, str]) -> str:
    cached = sum(
        1
        for livre in livres
        if livre.get("url_babelio")
        and livre["url_babelio"] in cache
        and cache[livre["url_babelio"]]
    )
    cards = "\n".join(card_html(livre, cache) for livre in livres)
    return f"""
  <section>
    <h2>{title} <span class="count">({len(livres)} livres · {cached} en cache)</span></h2>
    <div class="grid">{cards}</div>
  </section>"""


def generate_html(
    sections: list[tuple[str, list[dict]]], cache: dict[str, str], cache_source: str
) -> str:
    total = sum(len(livres) for _, livres in sections)
    total_cached = sum(
        1
        for _, livres in sections
        for livre in livres
        if livre.get("url_babelio")
        and livre["url_babelio"] in cache
        and cache[livre["url_babelio"]]
    )
    sections_html = "\n".join(
        section_html(title, livres, cache) for title, livres in sections
    )

    return f"""<!DOCTYPE html>
<html lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>lmelp — Rapport couvertures</title>
  <style>
    * {{ box-sizing: border-box; margin: 0; padding: 0; }}
    body {{ font-family: system-ui, sans-serif; background: #1a1a2e; color: #eee; padding: 20px; }}
    h1 {{ font-size: 1.4rem; margin-bottom: 4px; color: #fff; }}
    .subtitle {{ color: #aaa; font-size: .85rem; margin-bottom: 24px; }}
    section {{ margin-bottom: 32px; }}
    h2 {{ font-size: 1.1rem; color: #90caf9; margin-bottom: 12px; border-bottom: 1px solid #333; padding-bottom: 6px; }}
    .count {{ font-size: .8rem; color: #aaa; font-weight: normal; }}
    .grid {{ display: flex; flex-wrap: wrap; gap: 12px; }}
    .card {{ width: 130px; border-radius: 8px; overflow: hidden; border: 2px solid #444; background: #16213e; font-size: .75rem; }}
    .card.cached {{ border-color: #4caf50; }}
    .card.missing {{ border-color: #ff9800; }}
    .card.empty {{ border-color: #f44336; }}
    .card.no-babelio {{ border-color: #9e9e9e; }}
    .img-wrap {{ width: 130px; height: 180px; background: #0f3460; display: flex; align-items: center; justify-content: center; overflow: hidden; }}
    .img-wrap img {{ width: 100%; height: 100%; object-fit: cover; }}
    .placeholder {{ font-size: 2rem; color: #555; }}
    .info {{ padding: 6px 8px 8px; }}
    .status-icon {{ font-size: .85rem; }}
    .card.cached .status-icon {{ color: #4caf50; }}
    .card.missing .status-icon {{ color: #ff9800; }}
    .card.empty .status-icon {{ color: #f44336; }}
    .card.no-babelio .status-icon {{ color: #9e9e9e; }}
    .title {{ font-weight: bold; margin-top: 2px; line-height: 1.3; color: #fff; }}
    .author {{ color: #aaa; margin-top: 2px; }}
    .meta {{ color: #90caf9; margin-top: 2px; }}
    .babelio-link {{ display: inline-block; margin-top: 4px; color: #90caf9; text-decoration: none; font-size: .7rem; }}
    .babelio-link:hover {{ text-decoration: underline; }}
    .no-link {{ color: #555; font-size: .7rem; }}
    .legend {{ display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 20px; font-size: .8rem; }}
    .legend span {{ padding: 2px 8px; border-radius: 4px; border: 1px solid; }}
    .leg-cached {{ border-color: #4caf50; color: #4caf50; }}
    .leg-missing {{ border-color: #ff9800; color: #ff9800; }}
    .leg-empty {{ border-color: #f44336; color: #f44336; }}
    .leg-nobabelio {{ border-color: #9e9e9e; color: #9e9e9e; }}
  </style>
</head>
<body>
  <h1>lmelp — Rapport couvertures</h1>
  <p class="subtitle">Cache : {cache_source} · {total_cached}/{total} couvertures en cache</p>
  <div class="legend">
    <span class="leg-cached">✓ En cache</span>
    <span class="leg-missing">✗ Absent du cache</span>
    <span class="leg-empty">⚠ Aucune image Babelio</span>
    <span class="leg-nobabelio">⚠ URL Babelio manquante</span>
  </div>
{sections_html}
</body>
</html>"""


# ── Fetch depuis Babelio (mode --fetch) ───────────────────────────────────────


def normalize_title(s: str) -> str:
    s = s.lower()
    for old, new in [
        ("àáâãäå", "a"),
        ("éèêë", "e"),
        ("îï", "i"),
        ("ôö", "o"),
        ("ùûü", "u"),
        ("ç", "c"),
    ]:
        for c in old:
            s = s.replace(c, new)
    return s


def page_title_matches(page_title: str, titre_livre: str) -> bool:
    norm_page = normalize_title(page_title)
    all_words = normalize_title(titre_livre).split()
    words = [w for w in all_words if len(w) >= 4] or [
        w for w in all_words if len(w) >= 2
    ]
    return any(w in norm_page for w in words)


def extract_cover_url(html: str) -> str | None:
    # og:image — source canonique (property avant content)
    m = re.search(r"""property=['"]og:image['"][^>]*content=['"]([^'"]+)['"]""", html)
    if m:
        return m.group(1)
    # og:image — forme alternative (content avant property)
    m = re.search(r"""content=['"]([^'"]+)['"][^>]*property=['"]og:image['"]""", html)
    if m:
        return m.group(1)
    # Fallback : src relatif /couv/CVT_
    m = re.search(r"""src=['"](/couv/CVT_[^'"]+)['"]""", html)
    if m:
        return "https://www.babelio.com" + m.group(1)
    # Fallback : src absolu Amazon
    m = re.search(
        r"""src=['"]\s*(https?://m\.media-amazon\.com/images/[^'"]+)['"]""", html
    )
    if m:
        return m.group(1).strip()
    # Fallback : src absolu Babelio
    m = re.search(r"""src=['"]\s*(https?://www\.babelio\.com/couv/[^'"]+)['"]""", html)
    if m:
        return m.group(1).strip()
    return None


def fetch_cover_url(url_babelio: str, titre: str) -> str | None:
    """Fetche la page Babelio et retourne l'URL CVT_ de couverture, ou None."""
    import gzip as gzip_mod

    try:
        headers = {
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Encoding": "gzip, deflate",
            "Accept-Language": "fr,en-US;q=0.9,en;q=0.8",
            "Connection": "keep-alive",
            "DNT": "1",
            "Sec-Fetch-Dest": "document",
            "Sec-Fetch-Mode": "navigate",
            "Sec-Fetch-Site": "cross-site",
            "Upgrade-Insecure-Requests": "1",
            "User-Agent": "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:148.0) Gecko/20100101 Firefox/148.0",
        }
        cookie = os.environ.get("BABELIO_COOKIE", "")
        if cookie:
            headers["Cookie"] = cookie
        req = urllib.request.Request(url_babelio, headers=headers)
        with urllib.request.urlopen(req, timeout=15) as resp:
            # Vérifier que l'URL finale correspond au livre attendu (pas de redirection vers un autre livre)
            final_url = resp.geturl()
            expected_id = url_babelio.rstrip("/").rsplit("/", 1)[-1]
            if f"/{expected_id}" not in final_url:
                return None
            raw = resp.read()
            content_encoding = resp.headers.get("Content-Encoding", "")
            if content_encoding == "gzip":
                raw = gzip_mod.decompress(raw)
            # Détecter le charset depuis Content-Type ou le HTML
            charset = "utf-8"
            content_type = resp.headers.get("Content-Type", "")
            ct_match = re.search(r"charset=([^\s;]+)", content_type, re.I)
            if ct_match:
                charset = ct_match.group(1).strip()
            html = raw.decode(charset, errors="replace")

        # Valider que le titre de la page correspond au livre
        page_title_m = re.search(r"<title>(.*?)</title>", html, re.IGNORECASE)
        page_title = page_title_m.group(1) if page_title_m else ""
        if not page_title_matches(page_title, titre):
            return None

        image_url = extract_cover_url(html)
        if not image_url:
            return None
        if "couv-defaut-grande" in image_url:
            return None
        return image_url
    except Exception:
        return None


def fetch_all_covers(
    books: list[dict],
    cache: dict[str, str],
    cache_file_path: Path,
    *,
    force: bool = False,
) -> int:
    """
    Pour chaque livre de `books` avec un url_babelio non encore en cache,
    fetche la couverture depuis Babelio et met à jour `cache`.
    Persiste le cache dans `cache_file_path` après chaque ajout.
    Retourne le nombre de nouvelles entrées ajoutées.
    """
    added = 0
    last_fetch_time = 0.0

    # Construire la liste unique des URLs à fetcher
    to_fetch = []
    seen_urls: set[str] = set()
    for book in books:
        url = book.get("url_babelio")
        if not url or url in seen_urls:
            continue
        seen_urls.add(url)
        if not force and url in cache and cache[url]:
            continue  # déjà en cache avec une image
        to_fetch.append((url, book["titre"]))

    total = len(to_fetch)
    if total == 0:
        print("  → Toutes les couvertures sont déjà en cache.")
        return 0

    print(f"  → {total} couvertures à fetcher...")

    for i, (url_babelio, titre) in enumerate(to_fetch, 1):
        # Respecter le délai minimum + jitter aléatoire
        elapsed = time.time() - last_fetch_time
        wait = BABELIO_MIN_INTERVAL_S + random.uniform(0.0, 1.0) - elapsed
        if wait > 0:
            time.sleep(wait)

        print(f"  [{i}/{total}] {titre[:50]!r}...", end=" ", flush=True)
        last_fetch_time = time.time()
        image_url = fetch_cover_url(url_babelio, titre)

        if image_url:
            cache[url_babelio] = image_url
            added += 1
            # Persister après chaque succès
            _write_cache(cache, cache_file_path)
            print(f"✓ {image_url[-40:]}")
        else:
            print("✗ (introuvable ou titre non vérifié)")

    return added


def _write_cache(cache: dict[str, str], path: Path) -> None:
    data = {"_version": CACHE_VERSION}
    data.update({k: v for k, v in cache.items() if k.startswith("http")})
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def push_cache_to_device(cache_file_path: Path) -> bool:
    """Pousse le fichier cache JSON sur le device via adb. Retourne True si succès."""
    try:
        result = subprocess.run(
            ["adb", "push", str(cache_file_path), ADB_CACHE_PATH],
            capture_output=True,
            text=True,
            timeout=30,
        )
        return result.returncode == 0
    except Exception:
        return False


# ── Main ──────────────────────────────────────────────────────────────────────


def main() -> None:
    parser = argparse.ArgumentParser(description="Rapport visuel des couvertures lmelp")
    parser.add_argument("--db", default=str(DEFAULT_DB), help="Chemin vers lmelp.db")
    parser.add_argument(
        "--cache-file", help="Fichier JSON du cache couvertures (sinon via adb)"
    )
    parser.add_argument(
        "--output", default=str(DEFAULT_OUTPUT), help="Fichier HTML de sortie"
    )
    parser.add_argument(
        "--no-open", action="store_true", help="Ne pas ouvrir le navigateur"
    )
    parser.add_argument(
        "--fetch",
        action="store_true",
        help="Fetche les couvertures manquantes depuis Babelio et met à jour le cache",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="Avec --fetch : re-fetche même les entrées déjà en cache",
    )
    parser.add_argument(
        "--push",
        action="store_true",
        help="Avec --fetch : pousse le cache JSON mis à jour sur le device via adb",
    )
    args = parser.parse_args()

    # Lire la DB en premier (toujours nécessaire)
    db_path = Path(args.db)
    if not db_path.exists():
        print(f"Erreur : base de données introuvable : {db_path}", file=sys.stderr)
        sys.exit(1)
    print(f"Lecture de {db_path}...")
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    emissions = get_emissions_slides(cur)
    palmares = get_palmares_slides(cur)
    conseils = get_conseils_slides(cur)
    onkindle = get_onkindle_slides(cur)
    conn.close()

    print(
        f"  → {len(emissions)} émissions, {len(palmares)} palmarès, {len(conseils)} conseils, {len(onkindle)} onkindle"
    )

    # Charger le cache — priorité : --cache-file > assets/ bundlé > adb
    if args.cache_file:
        print(f"Lecture du cache depuis {args.cache_file}...")
        cache = load_cache_from_file(args.cache_file)
        cache_source = args.cache_file
        local_cache_path = Path(args.cache_file)
    elif DEFAULT_ASSETS_CACHE.exists():
        print(f"Lecture du cache depuis assets/ ({DEFAULT_ASSETS_CACHE.name})...")
        cache = load_cache_from_file(str(DEFAULT_ASSETS_CACHE))
        cache_source = f"assets/ ({len(cache)} entrées)"
        local_cache_path = DEFAULT_ASSETS_CACHE
    else:
        print("Récupération du cache via adb...")
        cache = load_cache_from_adb()
        if cache is None:
            print("⚠  adb indisponible ou cache absent — démarrage avec cache vide.")
            cache = {}
            cache_source = "aucun (adb indisponible)"
        else:
            cache_source = f"téléphone ({len(cache)} entrées)"
        local_cache_path = DEFAULT_ASSETS_CACHE
    print(f"  → {len(cache)} entrées dans le cache")

    # Mode --fetch : remplir les couvertures manquantes depuis Babelio
    if args.fetch:
        all_books = emissions + palmares + conseils + onkindle
        print(f"\nMode --fetch : {len(all_books)} livres au total")
        print(f"  Écriture du cache dans : {local_cache_path}")
        added = fetch_all_covers(all_books, cache, local_cache_path, force=args.force)
        print(
            f"\n  → {added} nouvelles couvertures ajoutées (cache total : {len(cache)} entrées)"
        )
        if local_cache_path == DEFAULT_ASSETS_CACHE:
            print(
                "  ✓ Cache mis à jour dans assets/ — sera embarqué dans l'APK au prochain build"
            )
        cache_source = f"assets/ enrichi ({len(cache)} entrées)"

        if args.push or added > 0:
            print("\nEnvoi du cache sur le device via adb...")
            if push_cache_to_device(local_cache_path):
                print(f"  ✓ Cache poussé sur le device : {ADB_CACHE_PATH}")
            else:
                print(
                    f"  ✗ Échec du push adb — cache disponible dans {local_cache_path}"
                )

    # Générer le HTML
    sections = [
        ("Émissions", emissions),
        ("Palmarès", palmares),
        ("Conseils", conseils),
        ("Liseuse (OnKindle)", onkindle),
    ]
    html = generate_html(sections, cache, cache_source)

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(html, encoding="utf-8")
    print(f"\nRapport généré : {output}")

    if not args.no_open:
        webbrowser.open(output.as_uri())


if __name__ == "__main__":
    main()
