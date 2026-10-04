"""Notifications ntfy du pipeline de publication de lmelp.db (issue #153).

Appelé dans le container lmelp-export :
- par docker_export_and_publish_release.sh, juste après une publication réelle
  de la release data-v{N} (sous-commande ``published``) ;
- par docker_publish_loop.sh, après chaque run horaire (sous-commande
  ``status``) : notif au premier échec, puis notif « rétablie » au retour à la
  normale. Les échecs suivants restent silencieux, pour ne pas recevoir une
  notif par heure pendant une panne (MongoDB arrêté, GH_TOKEN expiré...).

Configuration par variables d'environnement, mêmes noms que dans
back-office-lmelp pour partager le même topic (titres préfixés par
« lmelp-mobile - » pour distinguer la source) :
  NTFY_TOPIC       — topic ntfy ; absent = notifications désactivées (no-op)
  NTFY_SERVER_URL  — serveur ntfy (défaut : https://ntfy.sh)
  NTFY_TOKEN       — token d'accès (optionnel, serveur ntfy protégé)
  NTFY_STATE_FILE  — dernier statut du job (défaut :
                     /var/lib/lmelp-export/last_status)

Une notification ne fait jamais échouer le pipeline : toute erreur réseau est
signalée sur stderr et ignorée.
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path


DEFAULT_URL = "https://ntfy.sh"
DEFAULT_STATE_FILE = "/var/lib/lmelp-export/last_status"
LOG_TAIL_LINES = 20
TITLE_PREFIX = "lmelp-mobile - "

COUNTERS = (
    ("nb_emissions", "émissions"),
    ("nb_livres", "livres"),
    ("nb_avis", "avis"),
)


def _format_counter(value: int, previous: int | None, label: str) -> str:
    text = f"{value} {label}"
    if previous is not None and value != previous:
        text += f" ({value - previous:+d})"
    return text


def build_published_message(
    metadata: dict, previous: dict | None, tag: str
) -> tuple[str, str]:
    counters = []
    for key, label in COUNTERS:
        value = int(metadata.get(key, 0))
        prev_value = int(previous[key]) if previous and key in previous else None
        counters.append(_format_counter(value, prev_value, label))
    title = f"{TITLE_PREFIX}nouvelles données publiées ({tag})"
    body = f"{' · '.join(counters)}\nExport du {metadata.get('export_datetime')}"
    return title, body


def build_failure_message(log: str) -> tuple[str, str]:
    tail = "\n".join(log.strip().splitlines()[-LOG_TAIL_LINES:])
    return f"{TITLE_PREFIX}échec de publication des données", tail or "(log vide)"


def send(
    url: str,
    topic: str,
    title: str,
    message: str,
    priority: int = 3,
    tags: list[str] | None = None,
    token: str | None = None,
) -> bool:
    """Publie une notification via l'API JSON de ntfy. Ne lève jamais."""
    payload = {
        "topic": topic,
        "title": title,
        "message": message,
        "priority": priority,
        "tags": tags or [],
    }
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(
        url.rstrip("/"),
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    try:
        urllib.request.urlopen(request, timeout=10)
    except (urllib.error.URLError, OSError) as exc:
        print(f"[WARN] notification ntfy non envoyée : {exc}", file=sys.stderr)
        return False
    return True


def notify(
    title: str, message: str, priority: int = 3, tags: list[str] | None = None
) -> bool:
    topic = os.environ.get("NTFY_TOPIC")
    if not topic:
        return False
    return send(
        os.environ.get("NTFY_SERVER_URL") or DEFAULT_URL,
        topic,
        title,
        message,
        priority=priority,
        tags=tags,
        token=os.environ.get("NTFY_TOKEN") or None,
    )


def handle_status(result: str, state_file: Path, log: str = "") -> None:
    """Notifie les transitions ok → failed et failed → ok, puis mémorise l'état."""
    previous = state_file.read_text().strip() if state_file.exists() else "ok"
    if result == "failed" and previous != "failed":
        title, body = build_failure_message(log)
        notify(title, body, priority=4, tags=["warning"])
    elif result == "ok" and previous == "failed":
        notify(
            f"{TITLE_PREFIX}publication des données rétablie",
            "Le job de publication fonctionne de nouveau.",
            tags=["white_check_mark"],
        )
    state_file.parent.mkdir(parents=True, exist_ok=True)
    state_file.write_text(result)


def _read_json(path: Path | None) -> dict | None:
    if path is None or not path.is_file():
        return None
    data: dict = json.loads(path.read_text())
    return data


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="command", required=True)

    published = sub.add_parser("published", help="notifie une publication réelle")
    published.add_argument("--metadata", required=True, type=Path)
    published.add_argument("--previous", type=Path)
    published.add_argument("--tag", required=True)

    status = sub.add_parser("status", help="notifie les transitions ok/échec")
    status.add_argument("--result", required=True, choices=["ok", "failed"])
    status.add_argument("--log", type=Path)

    args = parser.parse_args(argv)

    if args.command == "published":
        metadata = _read_json(args.metadata) or {}
        title, body = build_published_message(
            metadata, _read_json(args.previous), args.tag
        )
        notify(title, body, tags=["books"])
    else:
        log = args.log.read_text(errors="replace") if args.log else ""
        state_file = Path(os.environ.get("NTFY_STATE_FILE") or DEFAULT_STATE_FILE)
        handle_status(args.result, state_file, log)
    return 0


if __name__ == "__main__":
    sys.exit(main())
