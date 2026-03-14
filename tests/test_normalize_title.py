"""Tests TDD pour _normalize_title et build_onkindle_table."""

import sys
from pathlib import Path


sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))

from export_mongo_to_sqlite import _normalize_title  # noqa: E402


class TestNormalizeTitle:
    def test_supprime_accents(self):
        assert _normalize_title("éàü") == "eau"

    def test_minuscules(self):
        assert _normalize_title("Frapper") == "frapper"

    def test_apostrophe_typographique(self):
        """L'apostrophe typographique ' doit être traitée comme '."""
        assert _normalize_title("Frapper l\u2019épopée") == _normalize_title(
            "Frapper l'épopée"
        )

    def test_apostrophe_droite_vs_typographique_match(self):
        """Un titre Calibre avec ' doit matcher le même titre avec '."""
        calibre = _normalize_title(
            "Frapper l\u2019épopée"
        )  # apostrophe typographique '
        db = _normalize_title("Frapper l'épopée")  # apostrophe droite
        assert calibre == db

    def test_leternite_match(self):
        """L\u2019éternité (Calibre) doit matcher L'éternité (DB)."""
        calibre = _normalize_title("L\u2019Éternité n\u2019est pas de trop")
        db = _normalize_title("L'éternité n'est pas de trop")
        assert calibre == db
