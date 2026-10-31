"""Draws the First Hour's three 16x16 item textures in the vanilla style (hard pixels, a dark outline, one light
source from the top left) with our own palette, and writes them into the mod's assets.

    python tools/textures/first_hour_items.py

hatchling_shell: a cracked eggshell, the top broken off. ledger_scrap: a torn strip of ledger paper with ruled lines.
guia_de_averbacao: a small stamped form with a red wax blob. No lettering anywhere.
"""
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/orespawn_integrations/textures/item"

_ = None
PALETTES = {
    "hatchling_shell": {
        "o": (74, 58, 44, 255),     # outline, warm brown
        "d": (176, 160, 132, 255),  # shell shade
        "m": (222, 210, 184, 255),  # shell mid
        "l": (246, 240, 224, 255),  # shell light
        "s": (196, 104, 62, 255),   # speckle
        "c": (104, 84, 64, 255),    # crack
        "i": (140, 120, 96, 255),   # inside of the shell
    },
    "ledger_scrap": {
        "o": (70, 54, 38, 255),
        "d": (196, 176, 136, 255),
        "m": (226, 210, 170, 255),
        "l": (244, 234, 206, 255),
        "r": (128, 150, 176, 255),  # ruled line, faded blue
        "x": (168, 64, 52, 255),    # the red margin line
    },
    "guia_de_averbacao": {
        "o": (58, 52, 46, 255),
        "d": (196, 192, 178, 255),
        "m": (226, 224, 212, 255),
        "l": (248, 248, 240, 255),
        "r": (110, 110, 120, 255),  # form rule
        "w": (150, 30, 36, 255),    # wax
        "h": (206, 70, 66, 255),    # wax highlight
        "k": (92, 16, 22, 255),     # wax shade
        "b": (70, 96, 150, 255),    # blue stamp ring
    },
}

ART = {
    "hatchling_shell": [
        "................",
        "................",
        "................",
        ".....o..o.......",
        "....olo.olo..o..",
        "...olmlooml.olo.",
        "..olmiiiiiimlmo.",
        "..olmiiiiiiimdo.",
        ".olmlmmmmmmmmdo.",
        ".olllmsmmcmmmdo.",
        ".olllmmmcmmsmdo.",
        ".olmlmmmmcmmddo.",
        "..olmmsmcmmddo..",
        "..oommmmmmddoo..",
        "....oooooooo....",
        "................",
    ],
    "ledger_scrap": [
        "................",
        "................",
        "..o.o..o........",
        ".olololloo.o....",
        ".oxllllllllolo..",
        ".oxrrrrrrrrrllo.",
        ".oxlllllllllllo.",
        "..oxmmmmmmmmmmo.",
        "..oxrrrrrrrrrmo.",
        "..oxmmmmmmmmmmo.",
        ".oxmmmmmmmmmddo.",
        ".oxrrrrrrrrrddo.",
        ".odddddddddddo..",
        "..oo.o.oo.oo....",
        "................",
        "................",
    ],
    "guia_de_averbacao": [
        "................",
        "..oooooooooooo..",
        "..ollllllllllo..",
        "..olrrrrrrrllo..",
        "..ollllllllllo..",
        "..olrrrrrlbbbo..",
        "..olllllllblbo..",
        "..olrrrrrlbbbo..",
        "..ommmmmmmmmmo..",
        "..omrrrrmmwwmo..",
        "..ommmmmmwhwwo..",
        "..omrrrmmwwwko..",
        "..ommmmmmmkkmo..",
        "..oddddddddddo..",
        "..oooooooooooo..",
        "................",
    ],
}


def draw(name):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    pal = PALETTES[name]
    for y, row in enumerate(ART[name]):
        assert len(row) == 16, (name, y, len(row))
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), pal[ch])
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / f"{name}.png")


if __name__ == "__main__":
    for item in ART:
        draw(item)
        print("wrote", OUT / f"{item}.png")
