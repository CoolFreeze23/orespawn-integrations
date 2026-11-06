"""Draws the seven Wanted posters of the Hunt Lodge's back wall (system-boss-ladder), 64x64 each, and Captain
Iracema's skin (npc-iracema), 64x64 in the player-skin layout. Our own art, drawn in code; no lettering.

    python -I tools/textures/wanted_posters.py

Each poster is aged parchment with a dark frame, two nail heads and the row's most famous face, painted flat in a
few colours like the vanilla paintings: 1 the Saci (red cap, one eye-glint grin), 2 the Basilisk (green serpent
head), 3 the Mapinguari (one eye, shaggy brown, a mouth in the belly), 4 the Snow Queen (pale face, ice crown),
5 the Kraken (purple eye among tentacles), 6 the goat (Craig), 7 the King (golden dragon with a crown).
Deterministic: a fixed seed for the parchment grain.
"""
import random
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
BLOCK = ROOT / "src/main/resources/assets/orespawn_integrations/textures/block"
NPC = ROOT / "src/main/resources/assets/orespawn_integrations/textures/entity/npc"
S = 64


def parchment(seed):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    for y in range(S):
        for x in range(S):
            base = (214, 190, 140)
            n = rnd.randint(-14, 10)
            edge = min(x, y, S - 1 - x, S - 1 - y)
            burn = max(0, 6 - edge) * 9
            px[x, y] = (max(0, base[0] + n - burn), max(0, base[1] + n - burn), max(0, base[2] + n - burn - 6), 255)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, S - 1, S - 1], outline=(70, 46, 26, 255))
    d.rectangle([1, 1, S - 2, S - 2], outline=(110, 76, 44, 255))
    for nx in (5, S - 6):
        d.rectangle([nx - 1, 3, nx + 1, 5], fill=(60, 60, 66, 255))
        d.point((nx, 4), fill=(150, 150, 160, 255))
    # the reward band at the bottom: plain stripes, no letters
    d.rectangle([10, 52, 53, 54], fill=(120, 40, 30, 255))
    d.rectangle([18, 57, 45, 58], fill=(90, 62, 36, 255))
    return img, d


def saci(d):
    d.ellipse([20, 18, 44, 44], fill=(92, 52, 30, 255))
    d.polygon([(20, 22), (32, 6), (46, 20)], fill=(196, 30, 30, 255))
    d.rectangle([20, 20, 46, 23], fill=(160, 20, 20, 255))
    d.rectangle([26, 28, 29, 31], fill=(250, 240, 220, 255))
    d.rectangle([35, 28, 38, 31], fill=(250, 240, 220, 255))
    d.point((28, 30), fill=(20, 10, 10, 255))
    d.point((37, 30), fill=(20, 10, 10, 255))
    d.arc([25, 32, 40, 40], 10, 170, fill=(250, 240, 220, 255), width=2)
    d.rectangle([40, 36, 48, 38], fill=(120, 90, 60, 255))  # the pipe
    d.rectangle([47, 32, 49, 37], fill=(120, 90, 60, 255))


def basilisk(d):
    d.polygon([(14, 40), (24, 18), (44, 16), (52, 30), (46, 44), (26, 46)], fill=(46, 112, 52, 255))
    d.polygon([(24, 18), (30, 10), (34, 18)], fill=(34, 84, 40, 255))
    d.polygon([(36, 17), (42, 9), (44, 18)], fill=(34, 84, 40, 255))
    d.ellipse([30, 24, 37, 31], fill=(240, 210, 40, 255))
    d.rectangle([33, 25, 34, 30], fill=(20, 20, 10, 255))
    d.line([(26, 40), (46, 38)], fill=(20, 50, 24, 255), width=2)
    for x in range(16, 46, 6):
        d.point((x, 44), fill=(220, 220, 200, 255))


def mapinguari(d):
    d.ellipse([16, 12, 48, 50], fill=(104, 70, 40, 255))
    for i in range(14):
        x = 16 + i * 2 + (i % 2)
        d.line([(x, 12 + (i % 3)), (x - 2, 6 + (i % 4))], fill=(84, 56, 30, 255), width=1)
    d.ellipse([27, 18, 37, 26], fill=(240, 236, 210, 255))
    d.ellipse([30, 19, 34, 25], fill=(170, 30, 20, 255))
    d.rectangle([24, 36, 40, 42], fill=(60, 20, 18, 255))
    for x in range(25, 40, 3):
        d.polygon([(x, 36), (x + 1, 39), (x + 2, 36)], fill=(236, 230, 200, 255))


def snow_queen(d):
    d.ellipse([20, 16, 44, 46], fill=(222, 232, 244, 255))
    d.polygon([(18, 18), (22, 6), (27, 15), (32, 4), (37, 15), (42, 6), (46, 18)], fill=(150, 206, 240, 255))
    d.rectangle([26, 27, 29, 29], fill=(60, 110, 180, 255))
    d.rectangle([35, 27, 38, 29], fill=(60, 110, 180, 255))
    d.line([(28, 38), (36, 38)], fill=(120, 140, 170, 255), width=1)
    d.polygon([(20, 30), (12, 50), (24, 46)], fill=(190, 214, 236, 255))
    d.polygon([(44, 30), (52, 50), (40, 46)], fill=(190, 214, 236, 255))


def kraken(d):
    for i, x in enumerate(range(12, 54, 8)):
        d.arc([x - 6, 30, x + 6, 52], 180 if i % 2 else 0, 360 if i % 2 else 180, fill=(110, 40, 120, 255), width=3)
    d.ellipse([18, 10, 46, 38], fill=(130, 50, 140, 255))
    d.ellipse([26, 18, 38, 28], fill=(240, 220, 120, 255))
    d.rectangle([31, 18, 33, 28], fill=(30, 10, 30, 255))
    for x in (22, 42):
        d.ellipse([x - 2, 30, x + 2, 34], fill=(200, 140, 210, 255))


def goat(d):
    d.polygon([(22, 18), (42, 18), (40, 42), (32, 50), (24, 42)], fill=(232, 228, 216, 255))
    d.arc([10, 4, 28, 26], 200, 330, fill=(120, 100, 80, 255), width=3)
    d.arc([36, 4, 54, 26], 210, 340, fill=(120, 100, 80, 255), width=3)
    d.rectangle([25, 26, 29, 28], fill=(200, 160, 40, 255))
    d.rectangle([35, 26, 39, 28], fill=(200, 160, 40, 255))
    d.line([(26, 27), (28, 27)], fill=(20, 20, 20, 255))
    d.line([(36, 27), (38, 27)], fill=(20, 20, 20, 255))
    d.polygon([(29, 42), (35, 42), (32, 52)], fill=(200, 196, 186, 255))  # the beard
    d.ellipse([29, 36, 35, 40], fill=(180, 150, 140, 255))


def king(d):
    d.polygon([(14, 46), (20, 22), (32, 14), (44, 22), (50, 46), (32, 50)], fill=(210, 170, 40, 255))
    d.polygon([(20, 18), (24, 6), (28, 14), (32, 4), (36, 14), (40, 6), (44, 18)], fill=(250, 214, 60, 255))
    for x in (24, 32, 40):
        d.point((x, 9), fill=(200, 30, 40, 255))
    d.rectangle([24, 28, 28, 31], fill=(200, 40, 30, 255))
    d.rectangle([36, 28, 40, 31], fill=(200, 40, 30, 255))
    d.polygon([(26, 40), (32, 46), (38, 40)], fill=(140, 100, 20, 255))
    d.line([(14, 30), (6, 22)], fill=(170, 130, 30, 255), width=2)
    d.line([(50, 30), (58, 22)], fill=(170, 130, 30, 255), width=2)


FACES = [saci, basilisk, mapinguari, snow_queen, kraken, goat, king]


def iracema_skin():
    """A woman in a scuffed leather hunting coat and a wide-brimmed hat, the Hunt's brass badge on the lapel."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    skin = (150, 104, 74, 255)
    hair = (36, 24, 18, 255)
    coat = (110, 72, 40, 255)
    coat_dark = (82, 52, 28, 255)
    shirt = (200, 186, 150, 255)
    trousers = (64, 60, 52, 255)
    boots = (48, 32, 20, 255)
    hat = (92, 70, 44, 255)
    # head (8x8 faces at 0,8 / 8,8 / 16,8 / 24,8; top 8,0; bottom 16,0)
    d.rectangle([0, 8, 31, 15], fill=skin)
    d.rectangle([8, 0, 23, 7], fill=hair)
    d.rectangle([0, 8, 7, 11], fill=hair)
    d.rectangle([16, 8, 31, 12], fill=hair)
    d.rectangle([8, 8, 15, 9], fill=hair)
    d.point((10, 12), fill=(30, 20, 14, 255))
    d.point((13, 12), fill=(30, 20, 14, 255))
    d.line([(10, 14), (13, 14)], fill=(110, 70, 50, 255))
    # hat layer (the overlay at 32,0 .. 63,15): a brim all round and a crown on top
    d.rectangle([40, 0, 55, 7], fill=hat)
    d.rectangle([32, 8, 63, 9], fill=hat)
    d.rectangle([40, 8, 47, 9], fill=(70, 52, 30, 255))
    # body (front 20,20 8x12; sides 16,20 / 28,20; back 32,20)
    d.rectangle([16, 20, 39, 31], fill=coat)
    d.rectangle([22, 20, 25, 31], fill=shirt)
    d.rectangle([20, 26, 27, 27], fill=coat_dark)  # the belt
    d.point((21, 22), fill=(220, 180, 60, 255))  # the brass badge
    d.line([(32, 20), (39, 31)], fill=(90, 60, 30, 255))  # the bow's string across the back
    # arms (right 44,20; left 36,52)
    d.rectangle([40, 20, 55, 31], fill=coat)
    d.rectangle([44, 30, 51, 31], fill=skin)
    d.rectangle([32, 52, 47, 63], fill=coat)
    d.rectangle([36, 62, 43, 63], fill=skin)
    # legs (right 4,20; left 20,52)
    d.rectangle([0, 20, 15, 31], fill=trousers)
    d.rectangle([0, 28, 15, 31], fill=boots)
    d.rectangle([16, 52, 31, 63], fill=trousers)
    d.rectangle([16, 60, 31, 63], fill=boots)
    # tops and bottoms of the limbs
    d.rectangle([4, 16, 11, 19], fill=trousers)
    d.rectangle([20, 16, 35, 19], fill=coat)
    d.rectangle([44, 16, 51, 19], fill=coat)
    d.rectangle([20, 48, 27, 51], fill=trousers)
    d.rectangle([36, 48, 43, 51], fill=coat)
    return img


def main():
    BLOCK.mkdir(parents=True, exist_ok=True)
    NPC.mkdir(parents=True, exist_ok=True)
    for row, face in enumerate(FACES, start=1):
        img, d = parchment(1000 + row)
        face(d)
        img.save(BLOCK / f"wanted_poster_{row}.png")
    iracema_skin().save(NPC / "iracema.png")
    print("7 posters and the Iracema skin written")


if __name__ == "__main__":
    main()
