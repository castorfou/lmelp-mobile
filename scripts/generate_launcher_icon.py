"""Génère l'icône de lancement Android de lmelp-mobile (issue #141).

L'icône reprend le masque et la plume de l'icône historique lmelp, mais :
- sur le fond bleu nuit du bandeau d'accueil de l'app (#12192C → #1E2D4A),
- avec un smartphone à la place de la base de données,
pour se distinguer de la PWA back-office « BO LMELP » (fond rose + base de données).

Source : ``scripts/icon/source_lmelp_green.png``, copie de
``frontend/public/gimp_favicon/favicon.png`` du repo castorfou/back-office-lmelp
(icône lmelp verte, 1327×1328). Son fond vert uni se détoure facilement.

Produit, sous ``app/src/main/res`` :
- ``mipmap-*dpi/ic_launcher.png`` et ``ic_launcher_round.png`` (icônes legacy) ;
- ``mipmap-*dpi/ic_launcher_foreground.png`` et ``ic_launcher_monochrome.png``
  (calques de l'icône adaptive) ;
- ``drawable/ic_launcher_background.xml`` (dégradé bleu nuit) ;
- ``mipmap-anydpi/ic_launcher.xml`` et ``ic_launcher_round.xml``.

Usage :
    python scripts/generate_launcher_icon.py
"""

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage  # type: ignore[import-untyped]


ROOT = Path(__file__).resolve().parent.parent
SOURCE = Path(__file__).resolve().parent / "icon" / "source_lmelp_green.png"
RES_DIR = ROOT / "app" / "src" / "main" / "res"

# Couleurs du bandeau hero de l'accueil (LmelpNightBlue / LmelpNightBlueEnd, Theme.kt)
NIGHT_BLUE = (0x12, 0x19, 0x2C)
NIGHT_BLUE_END = (0x1E, 0x2D, 0x4A)
# Smartphone : cadre clair pour ressortir sur le bleu nuit, écran bleu Émissions
PHONE_FRAME_COLOR = (0xE3, 0xE8, 0xF0)
SCREEN_COLOR = (0x15, 0x65, 0xC0)
SCREEN_LINE_COLOR = (0x90, 0xCA, 0xF9)

# Taille de l'icône legacy (48 dp) par densité ; les calques adaptive font 108 dp
LEGACY_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE_DP = 108
# Diamètre du disque toujours visible, quel que soit le masque du launcher
SAFE_ZONE_DP = 66
# Portion du calque 108 dp visible une fois l'icône affichée
VIEWPORT_DP = 72

# Pixels « verts » (fond + ombre/base de données) : G dépasse max(R, B)
_GREEN_OPAQUE_BELOW = 5
_GREEN_TRANSPARENT_ABOVE = 35
# Liseré rose/magenta (R et B nettement au-dessus de G), pelé depuis les bords
_MAGENTA_MARGIN = 10
_PEEL_ITERATIONS = 25
# Monochrome : les détails plus sombres que ce seuil de luminance sont évidés
_MONOCHROME_LUMA_MIN = 110

# Smartphone, dans le repère de l'image source (1327×1328) : en bas à droite,
# derrière la plume, là où se trouvait la base de données
_PHONE_BOX = (730, 560, 1150, 1240)
_WORK_SIZE = 1080


def extract_foreground(img: Image.Image) -> Image.Image:
    """Détoure le masque et la plume : les pixels verts deviennent transparents.

    L'alpha décroît progressivement avec la « verdeur » (G - max(R, B)) pour
    garder des bords anti-aliasés, et le vert résiduel des bords est retiré.
    Seul le plus grand ensemble connexe (masque + plume) est conservé, ce qui
    élimine les liserés parasites de la base de données d'origine.
    """
    rgba = np.asarray(img.convert("RGBA")).astype(np.int32)
    r, g, b, a = rgba[..., 0], rgba[..., 1], rgba[..., 2], rgba[..., 3]
    greenness = g - np.maximum(r, b)

    span = _GREEN_TRANSPARENT_ABOVE - _GREEN_OPAQUE_BELOW
    keep = np.clip((_GREEN_TRANSPARENT_ABOVE - greenness) / span, 0.0, 1.0)
    alpha = (a * keep).round().astype(np.int32)

    # Pelage : les pixels teintés (vert résiduel d'un flou, liseré rose hérité
    # de l'icône BO) qui touchent la transparence sont retirés de proche en
    # proche. Les yeux et la bouche, enclos dans le masque jaune, sont épargnés.
    magenta = np.minimum(r, b) - g > _MAGENTA_MARGIN
    tinted = (greenness > 0) | magenta
    for _ in range(_PEEL_ITERATIONS):
        touching = ndimage.binary_dilation(alpha == 0) & (alpha > 0) & tinted
        if not touching.any():
            break
        alpha[touching] = 0

    labels, count = ndimage.label(alpha > 0)
    if count > 1:
        sizes = ndimage.sum_labels(
            np.ones_like(labels), labels, index=range(1, count + 1)
        )
        alpha[labels != int(np.argmax(sizes)) + 1] = 0

    out = rgba.copy()
    out[..., 1] = np.where(greenness > 0, np.maximum(r, b), g)
    out[..., 3] = alpha
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def draw_smartphone(
    size: tuple[int, int], box: tuple[int, int, int, int]
) -> Image.Image:
    """Dessine un smartphone (cadre clair, écran bleu avec lignes de texte) dans ``box``."""
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0

    draw.rounded_rectangle(box, radius=int(w * 0.16), fill=PHONE_FRAME_COLOR + (255,))
    border = int(w * 0.08)
    top, bottom = int(h * 0.11), int(h * 0.13)
    screen = (x0 + border, y0 + top, x1 - border, y1 - bottom)
    draw.rounded_rectangle(screen, radius=int(w * 0.04), fill=SCREEN_COLOR + (255,))

    # haut-parleur et bouton
    cx = (x0 + x1) // 2
    speaker_w, speaker_h = int(w * 0.24), max(2, int(h * 0.018))
    sy = y0 + top // 2
    draw.rounded_rectangle(
        (cx - speaker_w // 2, sy - speaker_h, cx + speaker_w // 2, sy + speaker_h),
        radius=speaker_h,
        fill=SCREEN_COLOR + (255,),
    )
    button_r = int(bottom * 0.28)
    by = y1 - bottom // 2
    draw.ellipse(
        (cx - button_r, by - button_r, cx + button_r, by + button_r),
        fill=SCREEN_COLOR + (255,),
    )

    # lignes de texte évoquant une page de lecture
    sx0, sy0, sx1, sy1 = screen
    line_h = max(2, int(h * 0.025))
    margin = int((sx1 - sx0) * 0.14)
    for i in range(6):
        ly = sy0 + int((sy1 - sy0) * (0.14 + i * 0.13))
        lx1 = sx1 - margin - (int((sx1 - sx0) * 0.25) if i % 3 == 2 else 0)
        draw.rounded_rectangle(
            (sx0 + margin, ly, lx1, ly + line_h),
            radius=line_h // 2,
            fill=SCREEN_LINE_COLOR + (255,),
        )
    return layer


def _compose_motif() -> Image.Image:
    """Masque et plume détourés par-dessus le smartphone, dans le repère source."""
    motif = extract_foreground(Image.open(SOURCE))
    phone = draw_smartphone(motif.size, _PHONE_BOX)
    return Image.alpha_composite(phone, motif)


def build_foreground(size: int) -> Image.Image:
    """Calque avant de l'icône adaptive, centré et contenu dans la safe zone."""
    motif = _compose_motif()
    alpha = np.asarray(motif)[..., 3]
    ys, xs = np.nonzero(alpha)
    cx, cy = (xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2
    radius = float(np.sqrt((xs - cx) ** 2 + (ys - cy) ** 2).max())

    work = _WORK_SIZE
    scale = (work * SAFE_ZONE_DP / ADAPTIVE_DP / 2) / radius
    scaled = motif.resize(
        (round(motif.width * scale), round(motif.height * scale)),
        Image.Resampling.LANCZOS,
    )
    canvas = Image.new("RGBA", (work, work), (0, 0, 0, 0))
    canvas.alpha_composite(
        scaled, (round(work / 2 - cx * scale), round(work / 2 - cy * scale))
    )
    return canvas.resize((size, size), Image.Resampling.LANCZOS)


def build_background(size: int) -> Image.Image:
    """Dégradé vertical bleu nuit, identique à ``drawable/ic_launcher_background.xml``."""
    t = np.linspace(0.0, 1.0, size)[:, None]
    start, end = np.array(NIGHT_BLUE), np.array(NIGHT_BLUE_END)
    rows = (start + (end - start) * t).round().astype(np.uint8)
    rgb = np.repeat(rows[:, None, :], size, axis=1)
    alpha = np.full((size, size, 1), 255, dtype=np.uint8)
    return Image.fromarray(np.concatenate([rgb, alpha], axis=2), "RGBA")


def build_monochrome(foreground: Image.Image) -> Image.Image:
    """Calque monochrome (icônes thémées Android 13+) : seul l'alpha compte.

    Les détails sombres (yeux, bouche, écran du téléphone) sont évidés pour
    que le motif reste lisible une fois teinté d'une seule couleur.
    """
    rgba = np.asarray(foreground.convert("RGBA")).astype(np.float64)
    luma = 0.299 * rgba[..., 0] + 0.587 * rgba[..., 1] + 0.114 * rgba[..., 2]
    alpha = np.where(luma >= _MONOCHROME_LUMA_MIN, rgba[..., 3], 0)
    out = np.zeros_like(rgba)
    out[..., :3] = 255
    out[..., 3] = alpha
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def build_legacy_icon(size: int, round_shape: bool = False) -> Image.Image:
    """Icône legacy : partie visible (72/108) de l'adaptive, en carré arrondi ou disque."""
    full = round(size * ADAPTIVE_DP / VIEWPORT_DP)
    layered = Image.alpha_composite(build_background(full), build_foreground(full))
    offset = (full - size) // 2
    icon = layered.crop((offset, offset, offset + size, offset + size))

    shape = Image.new("L", (size * 4, size * 4), 0)
    draw = ImageDraw.Draw(shape)
    if round_shape:
        draw.ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    else:
        draw.rounded_rectangle(
            (0, 0, size * 4 - 1, size * 4 - 1), radius=size * 4 // 6, fill=255
        )
    shape = shape.resize((size, size), Image.Resampling.LANCZOS)

    alpha = np.minimum(np.asarray(icon)[..., 3], np.asarray(shape))
    icon.putalpha(Image.fromarray(alpha.astype(np.uint8), "L"))
    return icon


BACKGROUND_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- Généré par scripts/generate_launcher_icon.py (issue #141) : ne pas éditer à la main -->
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient
        android:angle="270"
        android:startColor="#FF{start}"
        android:endColor="#FF{end}" />
</shape>
"""

ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- Généré par scripts/generate_launcher_icon.py (issue #141) : ne pas éditer à la main -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"""


def _hex(rgb: tuple[int, int, int]) -> str:
    return "".join(f"{c:02X}" for c in rgb)


def main() -> None:
    for density, size in LEGACY_SIZES.items():
        folder = RES_DIR / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        build_legacy_icon(size).save(folder / "ic_launcher.png", optimize=True)
        build_legacy_icon(size, round_shape=True).save(
            folder / "ic_launcher_round.png", optimize=True
        )
        foreground = build_foreground(size * ADAPTIVE_DP // 48)
        foreground.save(folder / "ic_launcher_foreground.png", optimize=True)
        build_monochrome(foreground).save(
            folder / "ic_launcher_monochrome.png", optimize=True
        )
        print(f"✓ mipmap-{density}")

    drawable = RES_DIR / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    (drawable / "ic_launcher_background.xml").write_text(
        BACKGROUND_XML.format(start=_hex(NIGHT_BLUE), end=_hex(NIGHT_BLUE_END)),
        encoding="utf-8",
    )
    anydpi = RES_DIR / "mipmap-anydpi"
    anydpi.mkdir(parents=True, exist_ok=True)
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        (anydpi / name).write_text(ADAPTIVE_XML, encoding="utf-8")
    print("✓ icône adaptive (mipmap-anydpi + drawable/ic_launcher_background.xml)")


if __name__ == "__main__":
    main()
