"""Draws the Interregnum Gazette's 16x16 item texture (item-diario-oficial) in the vanilla style: a folded newspaper,
hard pixels, a dark outline, one light source from the top left. Columns are grey rules and the masthead a solid bar;
no lettering anywhere. Our own art (plantz:newspaper's is English-only).

    python tools/textures/diario_oficial.py
"""
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/orespawn_integrations/textures/item"

P = {
    "o": (52, 48, 44, 255),     # outline
    "d": (186, 180, 164, 255),  # paper shade (the fold)
    "m": (222, 218, 204, 255),  # paper
    "l": (244, 242, 232, 255),  # paper light
    "b": (40, 40, 46, 255),     # masthead bar
    "r": (130, 128, 124, 255),  # column rule
    "g": (46, 120, 70, 255),    # the court's green stripe
}

ROWS = [
    "................",
    "..oooooooooooo..",
    "..olllllllllldo.",
    "..obbbbbbbbbbdo.",
    "..oggggggggggdo.",
    "..olllllllllldo.",
    "..olrrrr.rrrldo.",
    "..omllll.llmmdo.",
    "..olrrrr.rrrldo.",
    "..omllll.llmmdo.",
    "..olrrrr.rrrldo.",
    "..ommmmm.mmmmdo.",
    "..olrrrr.rrrmdo.",
    "..ommmmmmmmmmdo.",
    "..oddddddddddoo.",
    "...oooooooooooo.",
]


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ROWS):
        assert len(row) == 16, (y, row)
        for x, ch in enumerate(row):
            if ch == ".":
                if 3 <= x <= 12 and 2 <= y <= 13:
                    img.putpixel((x, y), P["m"])  # the gutter between columns is paper
                continue
            img.putpixel((x, y), P[ch])
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / "diario_oficial.png")
    print("diario_oficial.png written")


if __name__ == "__main__":
    main()
