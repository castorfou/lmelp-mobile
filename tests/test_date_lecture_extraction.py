"""
Tests TDD pour l'extraction de la date de lecture depuis custom_column_3 (Commentaire Calibre).

Format attendu : DD/MM/YYYY extrait du texte libre.
Résultat : date ISO YYYY-MM-DD ou None si non trouvée.
"""

import sys
from pathlib import Path


sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))

import export_mongo_to_sqlite as script  # noqa: E402


extract_date_lecture = script.extract_date_lecture


class TestExtractDateLecture:
    def test_date_simple(self):
        assert extract_date_lecture("01/08/2025") == "2025-08-01"

    def test_date_dans_texte(self):
        assert extract_date_lecture("cadeau eloi 28/07/2025") == "2025-07-28"

    def test_premiere_date_si_plusieurs(self):
        assert (
            extract_date_lecture("lu le 10/01/2025 et relu 20/03/2025") == "2025-01-10"
        )

    def test_none_si_valeur_booleenne(self):
        assert extract_date_lecture("1") is None

    def test_none_si_texte_sans_date(self):
        assert extract_date_lecture("Xavier, noel 2026") is None

    def test_none_si_none(self):
        assert extract_date_lecture(None) is None

    def test_none_si_vide(self):
        assert extract_date_lecture("") is None

    def test_date_avec_zero_padding(self):
        assert extract_date_lecture("06/09/2025") == "2025-09-06"
