"""Tests de scripts/notify_ntfy.py (issue #153).

Notifications ntfy du pipeline de publication de lmelp.db : une notif à chaque
publication réelle d'une release data-v{N}, et une notif au premier échec du
job horaire (puis une notif « rétablie » au retour à la normale), sans spammer
une notif par heure pendant une panne.
"""

import json
import sys
import urllib.error
from pathlib import Path
from unittest.mock import patch

import pytest


sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))

import notify_ntfy


ROOT = Path(__file__).parent.parent

METADATA = {
    "export_datetime": "2026-10-04T14:00:00",
    "export_version": "1790000000",
    "nb_emissions": 312,
    "nb_livres": 2104,
    "nb_avis": 8020,
}
PREVIOUS = {
    "export_datetime": "2026-09-27T10:00:00",
    "export_version": "1789000000",
    "nb_emissions": 311,
    "nb_livres": 2099,
    "nb_avis": 7998,
}


# ---------------------------------------------------------------------------
# Messages
# ---------------------------------------------------------------------------


class TestBuildPublishedMessage:
    def test_titre_mentionne_le_tag(self):
        title, _ = notify_ntfy.build_published_message(METADATA, None, "data-v12")
        assert "data-v12" in title

    def test_titres_prefixes_comme_back_office(self):
        """Topic partagé avec back-office-lmelp (titres « PGX - ... ») : le
        préfixe « lmelp-mobile - » identifie la source de la notification."""
        titles = [
            notify_ntfy.build_published_message(METADATA, None, "data-v12")[0],
            notify_ntfy.build_failure_message("boom")[0],
        ]
        assert all(t.startswith("lmelp-mobile - ") for t in titles)

    def test_corps_contient_compteurs_et_date(self):
        _, body = notify_ntfy.build_published_message(METADATA, None, "data-v12")
        assert "312 émissions" in body
        assert "2104 livres" in body
        assert "8020 avis" in body
        assert "2026-10-04" in body

    def test_sans_previous_pas_de_delta(self):
        _, body = notify_ntfy.build_published_message(METADATA, None, "data-v12")
        assert "(+" not in body

    def test_avec_previous_affiche_les_deltas(self):
        _, body = notify_ntfy.build_published_message(METADATA, PREVIOUS, "data-v12")
        assert "312 émissions (+1)" in body
        assert "2104 livres (+5)" in body
        assert "8020 avis (+22)" in body

    def test_delta_nul_non_affiche(self):
        previous = {**PREVIOUS, "nb_emissions": 312}
        _, body = notify_ntfy.build_published_message(METADATA, previous, "data-v12")
        assert "312 émissions" in body
        assert "312 émissions (" not in body

    def test_delta_negatif_affiche_avec_signe(self):
        previous = {**PREVIOUS, "nb_avis": 8030}
        _, body = notify_ntfy.build_published_message(METADATA, previous, "data-v12")
        assert "8020 avis (-10)" in body


class TestBuildFailureMessage:
    def test_corps_contient_la_fin_du_log(self):
        log = "\n".join(f"ligne {i}" for i in range(100))
        title, body = notify_ntfy.build_failure_message(log)
        assert "échec" in title.lower()
        assert "ligne 99" in body
        assert "ligne 0\n" not in body


# ---------------------------------------------------------------------------
# Envoi HTTP
# ---------------------------------------------------------------------------


class TestSend:
    """Publication JSON ntfy (POST sur l'URL de base) : UTF-8 sans souci, là où
    les en-têtes HTTP Title/Tags d'urllib n'acceptent que du latin-1."""

    def _payload(self, request):
        return json.loads(request.data.decode("utf-8"))

    def test_post_json_sur_url_de_base(self):
        with patch.object(notify_ntfy.urllib.request, "urlopen") as urlopen:
            ok = notify_ntfy.send(
                "https://ntfy.sh",
                "mon-topic",
                "Titre é 📚",
                "Corps",
                priority=4,
                tags=["warning"],
            )
        assert ok is True
        request = urlopen.call_args.args[0]
        assert request.full_url == "https://ntfy.sh"
        assert request.get_method() == "POST"
        assert self._payload(request) == {
            "topic": "mon-topic",
            "title": "Titre é 📚",
            "message": "Corps",
            "priority": 4,
            "tags": ["warning"],
        }
        headers = {k.lower(): v for k, v in request.header_items()}
        assert "authorization" not in headers

    def test_url_avec_slash_final(self):
        with patch.object(notify_ntfy.urllib.request, "urlopen") as urlopen:
            notify_ntfy.send("https://ntfy.sh/", "t", "x", "y")
        assert urlopen.call_args.args[0].full_url == "https://ntfy.sh"

    def test_token_ajoute_authorization(self):
        with patch.object(notify_ntfy.urllib.request, "urlopen") as urlopen:
            notify_ntfy.send("https://ntfy.sh", "t", "x", "y", token="tk_abc")
        headers = {k.lower(): v for k, v in urlopen.call_args.args[0].header_items()}
        assert headers["authorization"] == "Bearer tk_abc"

    def test_erreur_reseau_ne_leve_pas(self):
        with patch.object(
            notify_ntfy.urllib.request,
            "urlopen",
            side_effect=urllib.error.URLError("down"),
        ):
            ok = notify_ntfy.send("https://ntfy.sh", "t", "x", "y")
        assert ok is False


# ---------------------------------------------------------------------------
# Configuration par variables d'environnement
# ---------------------------------------------------------------------------


class TestNotify:
    def test_no_op_si_topic_absent(self, monkeypatch):
        monkeypatch.delenv("NTFY_TOPIC", raising=False)
        with patch.object(notify_ntfy, "send") as send:
            assert notify_ntfy.notify("t", "b") is False
        send.assert_not_called()

    def test_url_par_defaut_ntfy_sh(self, monkeypatch):
        monkeypatch.setenv("NTFY_TOPIC", "mon-topic")
        monkeypatch.delenv("NTFY_SERVER_URL", raising=False)
        monkeypatch.delenv("NTFY_TOKEN", raising=False)
        with patch.object(notify_ntfy, "send", return_value=True) as send:
            notify_ntfy.notify("t", "b")
        assert send.call_args.args[0] == "https://ntfy.sh"
        assert send.call_args.args[1] == "mon-topic"
        assert send.call_args.kwargs["token"] is None

    def test_url_et_token_depuis_env(self, monkeypatch):
        monkeypatch.setenv("NTFY_TOPIC", "mon-topic")
        monkeypatch.setenv("NTFY_SERVER_URL", "https://ntfy.example.org")
        monkeypatch.setenv("NTFY_TOKEN", "tk_abc")
        with patch.object(notify_ntfy, "send", return_value=True) as send:
            notify_ntfy.notify("t", "b")
        assert send.call_args.args[0] == "https://ntfy.example.org"
        assert send.call_args.kwargs["token"] == "tk_abc"


# ---------------------------------------------------------------------------
# Anti-spam des échecs : machine à états ok/failed
# ---------------------------------------------------------------------------


class TestHandleStatus:
    @pytest.fixture
    def state_file(self, tmp_path):
        return tmp_path / "sub" / "last_status"

    def _run(self, state_file, result, log=""):
        with patch.object(notify_ntfy, "notify", return_value=True) as notify:
            notify_ntfy.handle_status(result, state_file, log)
        return notify

    def test_premier_echec_depuis_ok_notifie(self, state_file):
        state_file.parent.mkdir(parents=True)
        state_file.write_text("ok")
        notify = self._run(state_file, "failed", "boom")
        notify.assert_called_once()
        assert "échec" in notify.call_args.args[0].lower()
        assert state_file.read_text().strip() == "failed"

    def test_fichier_absent_traite_comme_ok(self, state_file):
        notify = self._run(state_file, "failed")
        notify.assert_called_once()
        assert state_file.read_text().strip() == "failed"

    def test_echecs_suivants_silencieux(self, state_file):
        state_file.parent.mkdir(parents=True)
        state_file.write_text("failed")
        notify = self._run(state_file, "failed")
        notify.assert_not_called()
        assert state_file.read_text().strip() == "failed"

    def test_retour_a_la_normale_notifie(self, state_file):
        state_file.parent.mkdir(parents=True)
        state_file.write_text("failed")
        notify = self._run(state_file, "ok")
        notify.assert_called_once()
        assert "rétabli" in notify.call_args.args[0].lower()
        assert state_file.read_text().strip() == "ok"

    def test_ok_vers_ok_silencieux(self, state_file):
        state_file.parent.mkdir(parents=True)
        state_file.write_text("ok")
        notify = self._run(state_file, "ok")
        notify.assert_not_called()
        assert state_file.read_text().strip() == "ok"


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


class TestCli:
    def test_published_lit_metadata_et_previous(self, tmp_path):
        meta = tmp_path / "metadata.json"
        prev = tmp_path / "previous.json"
        meta.write_text(json.dumps(METADATA))
        prev.write_text(json.dumps(PREVIOUS))
        with patch.object(notify_ntfy, "notify", return_value=True) as notify:
            code = notify_ntfy.main(
                [
                    "published",
                    "--metadata",
                    str(meta),
                    "--previous",
                    str(prev),
                    "--tag",
                    "data-v12",
                ]
            )
        assert code == 0
        assert "(+1)" in notify.call_args.args[1]

    def test_published_previous_absent_ignore(self, tmp_path):
        meta = tmp_path / "metadata.json"
        meta.write_text(json.dumps(METADATA))
        with patch.object(notify_ntfy, "notify", return_value=True) as notify:
            code = notify_ntfy.main(
                [
                    "published",
                    "--metadata",
                    str(meta),
                    "--previous",
                    str(tmp_path / "absent.json"),
                    "--tag",
                    "data-v12",
                ]
            )
        assert code == 0
        assert "(+" not in notify.call_args.args[1]

    def test_status_utilise_state_file_env(self, tmp_path, monkeypatch):
        state = tmp_path / "last_status"
        log = tmp_path / "run.log"
        log.write_text("Traceback: boom")
        monkeypatch.setenv("NTFY_STATE_FILE", str(state))
        with patch.object(notify_ntfy, "notify", return_value=True) as notify:
            code = notify_ntfy.main(["status", "--result", "failed", "--log", str(log)])
        assert code == 0
        assert state.read_text().strip() == "failed"
        assert "boom" in notify.call_args.args[1]


# ---------------------------------------------------------------------------
# Câblage dans l'image Docker (tests statiques)
# ---------------------------------------------------------------------------


class TestDockerWiring:
    def test_dockerfile_sans_anacron(self):
        """anacron (période minimale d'un jour) est remplacé par publish-loop."""
        dockerfile = (ROOT / "Dockerfile.export").read_text()
        assert "anacron \\" not in dockerfile  # paquet apt
        assert "/etc/anacrontab" not in dockerfile
        assert "anacron -d" not in dockerfile
        assert "publish-loop &" in dockerfile

    def test_dockerfile_copie_notify_et_boucle(self):
        dockerfile = (ROOT / "Dockerfile.export").read_text()
        assert "scripts/notify_ntfy.py" in dockerfile
        assert "/usr/local/bin/publish-loop" in dockerfile

    def test_publish_script_verrou_et_notification(self):
        script = (ROOT / "scripts/docker_export_and_publish_release.sh").read_text()
        assert "flock -n" in script
        assert "notify_ntfy.py published" in script

    def test_boucle_horaire_configurable(self):
        loop = (ROOT / "scripts/docker_publish_loop.sh").read_text()
        assert "PUBLISH_INTERVAL:-3600" in loop
        assert "notify_ntfy.py status" in loop
        assert "export-and-publish-release" in loop
