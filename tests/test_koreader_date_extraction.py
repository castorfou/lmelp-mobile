"""
Tests TDD pour l'extraction des dates KOReader depuis Calibre.

KOReader stocke les dates en UTC :
  "2026-05-08 22:00:00+00:00"

On convertit en heure locale (Europe/Paris) avant d'extraire la date YYYY-MM-DD,
car 22h UTC = minuit heure française (UTC+2 en été) → la date locale est le 09/05.

Fallback sur extract_date_lecture(commentaire) si KOReader indisponible.
"""

import sys
from pathlib import Path


sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))

import export_mongo_to_sqlite as script  # noqa: E402


extract_date_koreader = script.extract_date_koreader


class TestExtractDateKoreader:
    def test_date_utc_converti_en_heure_locale(self):
        # 22h UTC = 00h00 heure française (UTC+2 en été) → date locale = 2026-05-09
        assert extract_date_koreader("2026-05-08 22:00:00+00:00") == "2026-05-09"

    def test_date_iso_avec_microsecondes(self):
        # 10h41 UTC = 12h41 heure française → même date
        assert extract_date_koreader("2026-05-09 10:41:10.361000+00:00") == "2026-05-09"

    def test_date_utc_heure_elevee_pas_de_changement(self):
        # 12h00 UTC = 14h00 heure française → même date
        assert extract_date_koreader("2025-08-01 12:00:00+00:00") == "2025-08-01"

    def test_date_utc_hiver_22h(self):
        # En hiver UTC+1 : 22h UTC = 23h locale → même date
        assert extract_date_koreader("2025-12-15 22:00:00+00:00") == "2025-12-15"

    def test_date_utc_hiver_23h(self):
        # En hiver UTC+1 : 23h UTC = 00h locale le lendemain
        assert extract_date_koreader("2025-12-15 23:00:00+00:00") == "2025-12-16"

    def test_none_si_none(self):
        assert extract_date_koreader(None) is None

    def test_none_si_vide(self):
        assert extract_date_koreader("") is None

    def test_none_si_format_invalide(self):
        assert extract_date_koreader("pas une date") is None
