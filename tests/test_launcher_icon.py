"""Tests TDD pour l'icône de lancement lmelp-mobile (issue #141).

L'icône doit se distinguer de celle de la PWA back-office « BO LMELP »
(fond rose, masque + plume + base de données) : fond bleu nuit de l'app,
smartphone à la place de la base de données, icône adaptive Android.
"""

import colorsys
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np
import pytest
from PIL import Image


sys.path.insert(0, str(Path(__file__).parent.parent / "scripts"))

import generate_launcher_icon as script


RES_DIR = Path(__file__).parent.parent / "app" / "src" / "main" / "res"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def _hue_deg(rgb: tuple[int, ...]) -> float:
    h, _, _ = colorsys.rgb_to_hsv(rgb[0] / 255, rgb[1] / 255, rgb[2] / 255)
    return h * 360


def _pixels(img: Image.Image) -> list[tuple[int, ...]]:
    return [tuple(px) for px in np.asarray(img.convert("RGBA")).reshape(-1, 4).tolist()]


def _is_close(
    rgb: tuple[int, ...], expected: tuple[int, int, int], tol: int = 12
) -> bool:
    return all(abs(a - b) <= tol for a, b in zip(rgb[:3], expected, strict=True))


class TestExtractForeground:
    def test_fond_vert_devient_transparent_et_motif_reste_opaque(self):
        img = Image.new("RGBA", (40, 40), (15, 174, 99, 255))
        # ombre / base de données vert foncé
        for x in range(30, 40):
            for y in range(30, 40):
                img.putpixel((x, y), (8, 96, 54, 255))
        # « masque » jaune
        for x in range(10, 20):
            for y in range(10, 20):
                img.putpixel((x, y), (249, 194, 68, 255))

        out = script.extract_foreground(img)

        assert out.mode == "RGBA"
        assert out.getpixel((2, 2))[3] == 0
        assert out.getpixel((35, 35))[3] == 0
        assert out.getpixel((15, 15)) == (249, 194, 68, 255)

    def test_liseres_rose_et_flou_vert_au_bord_sont_retires(self):
        img = Image.new("RGBA", (12, 5), (15, 174, 99, 255))
        for x in range(3, 9):
            for y in range(5):
                img.putpixel((x, y), (253, 253, 253, 255))  # plume blanche
        for y in range(5):
            img.putpixel((2, y), (174, 84, 110, 255))  # liseré rose
            img.putpixel((9, y), (150, 200, 175, 255))  # flou vert/blanc
            img.putpixel((10, y), (100, 160, 120, 255))

        out = script.extract_foreground(img)

        assert out.getpixel((2, 2))[3] == 0
        assert out.getpixel((9, 2))[3] == 0
        assert out.getpixel((5, 2))[3] == 255

    def test_yeux_bordeaux_enclos_dans_le_masque_conserves(self):
        img = Image.new("RGBA", (9, 9), (15, 174, 99, 255))
        for x in range(1, 8):
            for y in range(1, 8):
                img.putpixel((x, y), (249, 194, 68, 255))
        img.putpixel((4, 4), (120, 25, 60, 255))

        out = script.extract_foreground(img)

        assert out.getpixel((4, 4)) == (120, 25, 60, 255)

    def test_plume_blanche_et_grise_reste_opaque(self):
        img = Image.new("RGBA", (10, 10), (15, 174, 99, 255))
        img.putpixel((3, 3), (253, 253, 253, 255))
        img.putpixel((4, 3), (154, 162, 181, 255))

        out = script.extract_foreground(img)

        assert out.getpixel((3, 3))[3] == 255
        assert out.getpixel((4, 3))[3] == 255


class TestBuildBackground:
    def test_degrade_vertical_bleu_nuit(self):
        bg = script.build_background(108)

        assert bg.size == (108, 108)
        assert _is_close(bg.getpixel((54, 0)), script.NIGHT_BLUE, tol=3)
        assert _is_close(bg.getpixel((54, 107)), script.NIGHT_BLUE_END, tol=3)


class TestBuildForeground:
    def test_transparent_hors_safe_zone(self):
        fg = script.build_foreground(432)

        assert fg.size == (432, 432)
        # la safe zone d'une adaptive icon est le disque central de 66/108
        # du côté : les coins doivent rester vides
        for p in [(0, 0), (431, 0), (0, 431), (431, 431), (20, 216), (216, 20)]:
            assert fg.getpixel(p)[3] == 0, p

    def test_smartphone_present_en_bas_a_droite(self):
        fg = script.build_foreground(432)

        # l'écran du téléphone est de la couleur SCREEN_COLOR
        box = fg.crop((216, 216, 432, 432))
        pixels = [
            px
            for px in _pixels(box)
            if px[3] == 255 and _is_close(px, script.SCREEN_COLOR, tol=6)
        ]
        assert len(pixels) > 400

    def test_masque_present_en_haut_a_gauche(self):
        fg = script.build_foreground(432)

        box = fg.crop((100, 100, 216, 216))
        jaunes = [
            px for px in _pixels(box) if px[3] == 255 and 35 <= _hue_deg(px) <= 50
        ]
        assert len(jaunes) > 400


class TestBuildMonochrome:
    def test_details_sombres_evides(self):
        img = Image.new("RGBA", (4, 1), (0, 0, 0, 0))
        img.putpixel((0, 0), (249, 194, 68, 255))  # jaune du masque : conservé
        img.putpixel((1, 0), (120, 25, 60, 255))  # yeux bordeaux : évidés
        img.putpixel((2, 0), script.SCREEN_COLOR + (255,))  # écran : évidé

        mono = script.build_monochrome(img)

        assert mono.getpixel((0, 0))[3] == 255
        assert mono.getpixel((1, 0))[3] == 0
        assert mono.getpixel((2, 0))[3] == 0
        assert mono.getpixel((3, 0))[3] == 0


class TestBuildLegacyIcon:
    def test_carre_arrondi_coins_transparents_fond_bleu_nuit(self):
        icon = script.build_legacy_icon(192)

        assert icon.size == (192, 192)
        assert icon.getpixel((0, 0))[3] == 0
        assert _is_close(icon.getpixel((96, 4)), script.NIGHT_BLUE, tol=6)

    def test_rond_hors_disque_transparent(self):
        icon = script.build_legacy_icon(192, round_shape=True)

        assert icon.getpixel((10, 10))[3] == 0
        assert icon.getpixel((96, 96))[3] == 255


class TestRessourcesCommittees:
    """Non-régression sur les ressources Android effectivement embarquées."""

    @pytest.mark.parametrize(("density", "size"), list(script.LEGACY_SIZES.items()))
    def test_png_legacy_bleu_nuit_et_pas_rose(self, density, size):
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            path = RES_DIR / f"mipmap-{density}" / name
            img = Image.open(path).convert("RGBA")
            assert img.size == (size, size), path

            # pixel de fond, en haut au centre (hors motif)
            px = img.getpixel((size // 2, size // 8))
            assert px[3] == 255, path
            assert _is_close(px, script.NIGHT_BLUE, tol=20), (path, px)
            # l'ancienne icône (copie du BO) avait un fond rose
            assert not (px[0] > 180 and px[0] > px[2]), (path, px)

    @pytest.mark.parametrize(("density", "size"), list(script.LEGACY_SIZES.items()))
    def test_foreground_adaptive_present(self, density, size):
        path = RES_DIR / f"mipmap-{density}" / "ic_launcher_foreground.png"
        img = Image.open(path)
        assert img.size == (size * 108 // 48, size * 108 // 48)
        assert img.mode == "RGBA"

    @pytest.mark.parametrize("name", ["ic_launcher.xml", "ic_launcher_round.xml"])
    def test_adaptive_icon_xml(self, name):
        root = ET.parse(RES_DIR / "mipmap-anydpi" / name).getroot()

        assert root.tag == "adaptive-icon"
        refs = {child.tag: child.get(f"{ANDROID_NS}drawable") for child in root}
        assert refs["background"] == "@drawable/ic_launcher_background"
        assert refs["foreground"] == "@mipmap/ic_launcher_foreground"
        assert refs["monochrome"] == "@mipmap/ic_launcher_monochrome"

    @pytest.mark.parametrize(("density", "size"), list(script.LEGACY_SIZES.items()))
    def test_monochrome_present(self, density, size):
        path = RES_DIR / f"mipmap-{density}" / "ic_launcher_monochrome.png"
        img = Image.open(path)
        assert img.size == (size * 108 // 48, size * 108 // 48)

    def test_background_drawable_degrade_bleu_nuit(self):
        text = (RES_DIR / "drawable" / "ic_launcher_background.xml").read_text()

        assert "#FF12192C" in text
        assert "#FF1E2D4A" in text
