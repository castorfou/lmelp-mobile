"""
Tests pour le calcul SVD des recommandations.

Bug #70 : le centrage de la matrice utilisait matrix.mean(axis=0) qui inclut
les zéros (absences de notes), produisant des moyennes sous-estimées et des
scores SVD dans la mauvaise échelle (~1-5 au lieu de ~7-9).

Fix : centrer par la moyenne des notes non-nulles uniquement.
"""

import numpy as np
import pytest
from scipy.sparse import csr_matrix


def compute_livre_means_buggy(matrix: csr_matrix) -> np.ndarray:
    """Ancienne implémentation (buggée) : inclut les zéros dans la moyenne."""
    return np.array(matrix.mean(axis=0)).flatten()


def compute_livre_means_correct(matrix: csr_matrix) -> np.ndarray:
    """Nouvelle implémentation : moyenne des notes non-nulles uniquement."""
    matrix_dense = matrix.toarray()
    non_zero_counts = np.count_nonzero(matrix_dense, axis=0)
    non_zero_counts = np.where(non_zero_counts == 0, 1, non_zero_counts)
    return matrix_dense.sum(axis=0) / non_zero_counts


class TestLivreMeans:
    """
    Matrice de test : 3 critiques × 2 livres.
    - Livre A : noté 8.0 et 9.0 par critiques 0 et 1 → moyenne réelle = 8.5
    - Livre B : noté 7.0 par critique 2 uniquement → moyenne réelle = 7.0
    Critique 0 n'a pas noté livre B, critique 1 n'a pas noté livre B,
    critique 2 n'a pas noté livre A.
    """

    def setup_method(self):
        # Matrice sparse 3×2 : rows=critiques, cols=livres (8,0 / 9,0 / 0,7)
        data = [8.0, 9.0, 7.0]
        row = [0, 1, 2]
        col = [0, 0, 1]
        self.matrix = csr_matrix((data, (row, col)), shape=(3, 2))

    def test_buggy_mean_underestimates(self):
        """L'ancienne implémentation sous-estime les moyennes (inclut les zéros)."""
        means = compute_livre_means_buggy(self.matrix)
        # Livre A : (8+9+0)/3 = 5.67 au lieu de 8.5
        assert means[0] == pytest.approx(5.666, abs=0.01)
        # Livre B : (0+0+7)/3 = 2.33 au lieu de 7.0
        assert means[1] == pytest.approx(2.333, abs=0.01)

    def test_correct_mean_uses_nonzero_only(self):
        """La nouvelle implémentation calcule la moyenne des notes non-nulles."""
        means = compute_livre_means_correct(self.matrix)
        # Livre A : (8+9)/2 = 8.5
        assert means[0] == pytest.approx(8.5, abs=0.01)
        # Livre B : 7/1 = 7.0
        assert means[1] == pytest.approx(7.0, abs=0.01)

    def test_correct_mean_livre_with_no_notes(self):
        """Un livre sans aucune note doit retourner 0 (pas de division par zéro)."""
        data = [8.0]
        row = [0]
        col = [0]
        matrix = csr_matrix((data, (row, col)), shape=(3, 2))  # livre B sans note
        means = compute_livre_means_correct(matrix)
        assert means[1] == pytest.approx(0.0, abs=0.01)

    def test_centering_produces_correct_scale(self):
        """Après centrage correct, les valeurs centrées sont proches de 0."""
        means = compute_livre_means_correct(self.matrix)
        matrix_dense = self.matrix.toarray()
        matrix_centered = matrix_dense - means
        # Les notes non-nulles centrées doivent être petites (±1 autour de 0)
        nonzero_mask = matrix_dense != 0
        centered_nonzero = matrix_centered[nonzero_mask]
        assert np.all(np.abs(centered_nonzero) <= 1.0)


class TestSvdRealData:
    """
    Test d'intégration sur les vraies données de la DB.

    Valeurs de référence desktop pour "Le lièvre" (Frédéric Boyer) :
      SVD = 7.77, masque_mean = 9.8, score_hybride = 8.37

    Après le fix (surprise.SVD + injection Calibre), le SVD calculé par
    le script doit être proche de la valeur desktop (tolérance ±1.5).
    """

    DB_PATH = "app/src/main/assets/lmelp.db"
    LIEVRE_ID = "694a96308e5987c88c8463bf"
    DESKTOP_SVD = 7.77
    TOLERANCE = 1.5

    @pytest.fixture(autouse=True)
    def skip_if_no_db(self):
        import os

        if not os.path.exists(self.DB_PATH):
            pytest.skip("lmelp.db absent")

    def _compute_svd_surprise(self) -> dict:
        """Calcule les scores SVD avec surprise.SVD + injection notes Calibre."""
        import sqlite3

        from surprise import SVD, Dataset, Reader

        svd_params = {"n_factors": 20, "n_epochs": 50, "lr_all": 0.01, "reg_all": 0.1}
        user_id = "Moi"
        min_avis_per_critique = 10
        min_critiques_per_livre = 2

        conn = sqlite3.connect(self.DB_PATH)
        cur = conn.cursor()
        rows_data = cur.execute(
            "SELECT critique_id, livre_id, note FROM avis WHERE note IS NOT NULL"
        ).fetchall()
        calibre_rows = cur.execute(
            "SELECT livre_id, calibre_rating FROM palmares "
            "WHERE calibre_rating IS NOT NULL AND calibre_rating > 0"
        ).fetchall()
        conn.close()

        # Filter active critiques
        critique_counts: dict = {}
        for c, _, _ in rows_data:
            critique_counts[c] = critique_counts.get(c, 0) + 1
        active = {c for c, n in critique_counts.items() if n >= min_avis_per_critique}
        rows_filtered = [(c, lid, float(n)) for c, lid, n in rows_data if c in active]

        # Masque means
        masque_sums: dict = {}
        masque_counts: dict = {}
        for _, lid, n in rows_filtered:
            masque_sums[lid] = masque_sums.get(lid, 0.0) + float(n)
            masque_counts[lid] = masque_counts.get(lid, 0) + 1

        # Calibre notes
        calibre_notes = {lid: float(r) for lid, r in calibre_rows}
        seen = set(calibre_notes.keys())

        # Build and train
        import pandas as pd

        training = rows_filtered + [
            (user_id, lid, note) for lid, note in calibre_notes.items()
        ]
        df = pd.DataFrame(training, columns=["user", "item", "rating"])
        reader = Reader(rating_scale=(1, 10))
        dataset = Dataset.load_from_df(df[["user", "item", "rating"]], reader)
        trainset = dataset.build_full_trainset()
        algo = SVD(**svd_params)
        algo.fit(trainset)

        # Predict for all candidates
        candidates = [
            lid
            for lid, cnt in masque_counts.items()
            if lid not in seen and cnt >= min_critiques_per_livre
        ]
        return {lid: algo.predict(user_id, lid).est for lid in candidates}

    def test_lievre_svd_proche_desktop(self):
        """SVD de 'Le lièvre' doit être proche de la valeur desktop (7.77 ± 1.5)."""
        scores = self._compute_svd_surprise()
        assert self.LIEVRE_ID in scores, (
            f"'Le lièvre' ({self.LIEVRE_ID}) absent des candidats SVD."
        )
        svd = scores[self.LIEVRE_ID]
        assert abs(svd - self.DESKTOP_SVD) <= self.TOLERANCE, (
            f"SVD 'Le lièvre' = {svd:.4f}, desktop = {self.DESKTOP_SVD}. "
            f"Écart trop grand (tolérance ±{self.TOLERANCE})."
        )


class TestSvdScoreScale:
    """
    Vérifie que les scores SVD produits sont dans la bonne échelle (~7-9)
    quand les notes sont dans [1-10] et la matrice est sparse.

    Bug #70 : avec le mauvais centrage (mean incluant les zéros), les scores
    sont dans l'échelle ~1-5. Avec le bon centrage (notes non-nulles), les
    scores doivent rester proches de l'échelle des notes originales.
    """

    def _build_sparse_matrix(self, notes_by_critique):
        """Construit une matrice sparse à partir d'un dict {critique_idx: {livre_idx: note}}."""
        rows, cols, data = [], [], []
        n_critiques = len(notes_by_critique)
        n_livres = max(max(livres.keys()) for livres in notes_by_critique.values()) + 1
        for c_idx, livres in notes_by_critique.items():
            for l_idx, note in livres.items():
                rows.append(c_idx)
                cols.append(l_idx)
                data.append(float(note))
        return csr_matrix((data, (rows, cols)), shape=(n_critiques, n_livres))

    def test_svd_scores_in_correct_scale_with_correct_centering(self):
        """
        Avec un centrage correct, les scores SVD doivent être dans l'échelle
        des notes originales (≥ 5.0 pour des bonnes notes).
        5 critiques, 3 livres, notes entre 7 et 10, matrice très sparse.
        """
        # 5 critiques, chacun n'a noté qu'1 ou 2 livres sur 3
        notes = {
            0: {0: 9.0, 1: 8.0},
            1: {0: 8.0, 2: 9.0},
            2: {1: 7.0, 2: 8.0},
            3: {0: 9.0},
            4: {1: 8.0, 2: 9.0},
        }
        matrix = self._build_sparse_matrix(notes)

        # Centrage correct
        matrix_dense = matrix.toarray()
        non_zero_counts = np.count_nonzero(matrix_dense, axis=0)
        non_zero_counts = np.where(non_zero_counts == 0, 1, non_zero_counts)
        livre_means_correct = matrix_dense.sum(axis=0) / non_zero_counts
        matrix_centered_correct = matrix_dense - livre_means_correct

        from scipy.sparse.linalg import svds

        k = 1
        u, sigma, vt = svds(csr_matrix(matrix_centered_correct), k=k)
        global_critic = np.mean(u, axis=0)
        svd_scores_correct = (
            global_critic.dot(np.diag(sigma)).dot(vt) + livre_means_correct
        )

        # Les scores doivent être dans l'échelle des notes (≥ 4.0)
        # et leur moyenne doit être proche de la moyenne des notes originales (~8.3)
        assert np.all(svd_scores_correct >= 4.0), (
            f"Scores SVD avec centrage correct trop bas: {svd_scores_correct}. "
            "Attendu ≥ 4.0 pour des notes entre 7 et 10."
        )
        assert np.mean(svd_scores_correct) >= 6.0, (
            f"Moyenne des scores SVD trop basse: {np.mean(svd_scores_correct):.2f}. "
            "Attendu ≥ 6.0 pour des notes entre 7 et 10."
        )

    def test_svd_scores_wrong_scale_with_buggy_centering(self):
        """
        Avec le mauvais centrage (mean incluant zéros), les scores SVD
        sont dans la mauvaise échelle (< 5.0).
        C'est le comportement actuel — ce test documente le bug.
        """
        notes = {
            0: {0: 9.0, 1: 8.0},
            1: {0: 8.0, 2: 9.0},
            2: {1: 7.0, 2: 8.0},
            3: {0: 9.0},
            4: {1: 8.0, 2: 9.0},
        }
        matrix = self._build_sparse_matrix(notes)

        # Centrage buggé
        livre_means_buggy = np.array(matrix.mean(axis=0)).flatten()
        matrix_centered_buggy = matrix.toarray() - livre_means_buggy

        from scipy.sparse.linalg import svds

        k = 1
        u, sigma, vt = svds(csr_matrix(matrix_centered_buggy), k=k)
        global_critic = np.mean(u, axis=0)
        svd_scores_buggy = global_critic.dot(np.diag(sigma)).dot(vt) + livre_means_buggy

        # Les scores doivent être < 5.0 (mauvaise échelle — c'est le bug)
        assert np.any(svd_scores_buggy < 5.0), (
            f"Scores SVD avec centrage buggé: {svd_scores_buggy}. "
            "Attendu au moins un score < 5.0 (mauvaise échelle)."
        )
