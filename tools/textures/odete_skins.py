"""Draws the two Odete CustomNPCs skins (64x64, the player-skin layout) for OI 0.10.0.

odete_registro: the desk clerk - grey bun, reading glasses, beige cardigan over a white blouse, the lanyard and its
blank tag (no lettering: art carries no words), a charcoal skirt, tan stockings, brown shoes.
odete_campo: the same clerk in the field - a straw hat on the hat layer, galoshes, the cardigan's sleeves rolled.

Run from the repo root: python tools/textures/odete_skins.py
"""
import os
import random

from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "orespawn_integrations", "textures", "entity", "npc")

SKIN = (224, 182, 150)
SKIN_D = (200, 158, 128)
HAIR = (150, 146, 140)
HAIR_D = (118, 114, 110)
CARDIGAN = (205, 186, 150)
CARDIGAN_D = (178, 160, 126)
BLOUSE = (238, 236, 228)
LANYARD = (170, 40, 40)
TAG = (245, 245, 240)
SKIRT = (64, 62, 66)
STOCKING = (196, 160, 128)
SHOE = (92, 60, 40)
GLASS = (40, 36, 34)
EYE = (60, 80, 110)
MOUTH = (170, 96, 90)
STRAW = (222, 196, 112)
STRAW_D = (188, 160, 82)
BAND = (120, 70, 40)
GALOSH = (60, 110, 60)
GALOSH_D = (44, 86, 44)


def noisy(img, box, base, dark, seed, amount=0.18):
    """Fills a rectangle with a base colour speckled with its darker shade, vanilla-style."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        for y in range(y0, y1):
            img.putpixel((x, y), dark if rnd.random() < amount else base)


# Each part: (top, bottom, right, front, left, back) rectangles in the 64x64 layout.
def part(u, v, w, h, d):
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


HEAD = part(0, 0, 8, 8, 8)
HAT = part(32, 0, 8, 8, 8)
BODY = part(16, 16, 8, 12, 4)
R_ARM = part(40, 16, 4, 12, 4)
R_LEG = part(0, 16, 4, 12, 4)
L_LEG = part(16, 48, 4, 12, 4)
L_ARM = part(32, 48, 4, 12, 4)


def fill_part(img, p, base, dark, seed):
    for i, box in enumerate(p.values()):
        noisy(img, box, base, dark, seed + i)


def rows(img, p, y_from, y_to, base, dark, seed):
    """Recolours rows y_from..y_to (0 = top of the side faces) on every side face of a limb or the body."""
    for i, key in enumerate(("right", "front", "left", "back")):
        x0, y0, x1, _ = p[key]
        noisy(img, (x0, y0 + y_from, x1, y0 + y_to), base, dark, seed + i)


def desk_skin(field=False):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    # Head: skin, hair over the top, back and upper sides, the bun on the back.
    fill_part(img, HEAD, SKIN, SKIN_D, 1)
    noisy(img, HEAD["top"], HAIR, HAIR_D, 2, 0.3)
    noisy(img, HEAD["back"], HAIR, HAIR_D, 3, 0.3)
    for key in ("right", "left"):
        x0, y0, x1, _ = HEAD[key]
        noisy(img, (x0, y0, x1, y0 + 3), HAIR, HAIR_D, 4, 0.3)
    fx, fy = HEAD["front"][0], HEAD["front"][1]
    noisy(img, (fx, fy, fx + 8, fy + 2), HAIR, HAIR_D, 5, 0.3)
    # Reading glasses: two frames and the bridge; eyes inside.
    for gx in (1, 4):
        for dx in range(3):
            img.putpixel((fx + gx + dx, fy + 3), GLASS)
            img.putpixel((fx + gx + dx, fy + 5), GLASS)
        img.putpixel((fx + gx, fy + 4), GLASS)
        img.putpixel((fx + gx + 2, fy + 4), GLASS)
        img.putpixel((fx + gx + 1, fy + 4), EYE)
    img.putpixel((fx + 0, fy + 3), GLASS)
    img.putpixel((fx + 7, fy + 3), GLASS)
    for dx in (3, 4):
        img.putpixel((fx + dx, fy + 6), SKIN_D)
    for dx in (3, 4):
        img.putpixel((fx + dx, fy + 7), MOUTH)
    # The bun, on the hat layer's back.
    bx, by = HAT["back"][0], HAT["back"][1]
    noisy(img, (bx + 2, by + 1, bx + 6, by + 4), HAIR_D, HAIR, 6, 0.4)

    # Body: cardigan, white blouse down the front, the lanyard and its blank tag.
    fill_part(img, BODY, CARDIGAN, CARDIGAN_D, 10)
    x0, y0 = BODY["front"][0], BODY["front"][1]
    noisy(img, (x0 + 3, y0, x0 + 5, y0 + 12), BLOUSE, BLOUSE, 11)
    for y in range(0, 5):
        img.putpixel((x0 + 2, y0 + y), LANYARD)
        img.putpixel((x0 + 5, y0 + y), LANYARD)
    for x in range(2, 6):
        for y in range(5, 8):
            img.putpixel((x0 + x, y0 + y), TAG)
    # Arms: cardigan sleeves, hands at the bottom (rolled sleeves in the field).
    for arm, seed in ((R_ARM, 20), (L_ARM, 30)):
        fill_part(img, arm, CARDIGAN, CARDIGAN_D, seed)
        rows(img, arm, 8 if field else 10, 12, SKIN, SKIN_D, seed + 5)
        noisy(img, arm["bottom"], SKIN, SKIN_D, seed + 6)
    # Legs: skirt to the knee, stockings, shoes or galoshes.
    for leg, seed in ((R_LEG, 40), (L_LEG, 50)):
        fill_part(img, leg, STOCKING, STOCKING, seed)
        rows(img, leg, 0, 6, SKIRT, SKIRT, seed + 1)
        noisy(img, leg["top"], SKIRT, SKIRT, seed + 2)
        if field:
            rows(img, leg, 8, 12, GALOSH, GALOSH_D, seed + 3)
            noisy(img, leg["bottom"], GALOSH_D, GALOSH_D, seed + 4)
        else:
            rows(img, leg, 11, 12, SHOE, SHOE, seed + 3)
            noisy(img, leg["bottom"], SHOE, SHOE, seed + 4)

    if field:
        # A straw hat on the hat layer: the crown over the top and a brim band round the sides.
        noisy(img, HAT["top"], STRAW, STRAW_D, 60, 0.35)
        for key in ("right", "front", "left", "back"):
            hx, hy, hx1, _ = HAT[key]
            noisy(img, (hx, hy, hx1, hy + 2), STRAW, STRAW_D, 61, 0.35)
            for x in range(hx, hx1):
                img.putpixel((x, hy + 2), BAND)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    desk_skin(False).save(os.path.join(OUT, "odete_registro.png"))
    desk_skin(True).save(os.path.join(OUT, "odete_campo.png"))
    print("wrote", OUT)


if __name__ == "__main__":
    main()
