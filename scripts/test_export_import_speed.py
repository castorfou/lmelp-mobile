"""
TDD - Test : le script export_mongo_to_sqlite ne doit pas importer scipy/numpy
au niveau module (top-level), pour éviter un chargement lent des extensions
natives C/Fortran lors du premier lancement (cold start) ou pour des opérations
légères comme --verify.

Ces imports doivent être lazy (à l'intérieur de compute_recommendations()).
"""

import ast
import sys
from pathlib import Path


SCRIPT_PATH = Path(__file__).parent / "export_mongo_to_sqlite.py"


def _get_top_level_imports(script_path: Path) -> list[str]:
    """Retourne la liste des noms importés au niveau module (top-level)."""
    source = script_path.read_text()
    tree = ast.parse(source)
    top_level_imports = []
    for node in ast.iter_child_nodes(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                top_level_imports.append(alias.name.split(".")[0])
        elif isinstance(node, ast.ImportFrom) and node.module:
            top_level_imports.append(node.module.split(".")[0])
    return top_level_imports


def test_scipy_not_imported_at_top_level():
    """scipy ne doit pas être importé au niveau module."""
    top_level = _get_top_level_imports(SCRIPT_PATH)
    assert "scipy" not in top_level, (
        "scipy est importé au niveau module — cela provoque un chargement lent "
        "des extensions natives même pour --verify ou --help. "
        "Déplacer l'import dans compute_recommendations()."
    )


def test_numpy_not_imported_at_top_level():
    """numpy ne doit pas être importé au niveau module."""
    top_level = _get_top_level_imports(SCRIPT_PATH)
    assert "numpy" not in top_level, (
        "numpy est importé au niveau module — cela ralentit le démarrage inutilement. "
        "Déplacer l'import dans compute_recommendations()."
    )


if __name__ == "__main__":
    try:
        test_scipy_not_imported_at_top_level()
        print("OK - scipy n'est pas importé au niveau module")
    except AssertionError as e:
        print(f"FAIL - {e}")
        sys.exit(1)

    try:
        test_numpy_not_imported_at_top_level()
        print("OK - numpy n'est pas importé au niveau module")
    except AssertionError as e:
        print(f"FAIL - {e}")
        sys.exit(1)
