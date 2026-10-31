"""Draws the Freguesia's 16x16 textures in the vanilla style (hard pixels, one light source from the top left) with our
own palette, and writes them into the mod's assets.

    python tools/textures/freguesia.py

marco_da_freguesia (block side): a dressed boundary stone, chisel marks and a moss foot, no lettering.
marco_da_freguesia_top (block top): the stone's weathered crown.
estaca_de_lote (block): a split wooden stake with a band of red survey paint.
item/marco_da_freguesia, item/estaca_de_lote: the inventory icons of both.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/orespawn_integrations/textures"

STONE = {
    "o": (62, 60, 58, 255),     # outline, deep grey
    "d": (112, 108, 102, 255),  # stone shade
    "m": (148, 144, 136, 255),  # stone mid
    "l": (182, 178, 168, 255),  # stone light
    "c": (92, 88, 84, 255),     # chisel mark
    "g": (78, 110, 52, 255),    # moss
    "h": (108, 140, 70, 255),   # moss light
}
WOOD = {
    "o": (58, 40, 24, 255),
    "d": (112, 80, 48, 255),
    "m": (146, 108, 66, 255),
    "l": (178, 138, 90, 255),
    "r": (170, 44, 38, 255),    # survey paint
    "p": (210, 78, 66, 255),    # paint light
}

ART = {
    "block/marco_da_freguesia": (STONE, [
        "dmmmmlmmmmmmlmmd",
        "mllmmmmmcmmmmmlm",
        "mlmmmcmmmmmmmmmm",
        "mmmmmmmmmmlmmmcm",
        "mmcmmmmmmmmmmmmm",
        "mmmmmlmmmcmmmmmd",
        "mlmmmmmmmmmmlmmm",
        "mmmmcmmmmmmmmmmm",
        "mmmmmmmmlmmmcmmd",
        "dmmmmmmmmmmmmmmm",
        "mmlmmmcmmmmmmmmd",
        "mmmmmmmmmmmlmmmm",
        "dmmmmmmmcmmmmmmd",
        "mhmmmmmmmmmmmhmd",
        "ghgmmdmmmmdmmghg",
        "gggggdgggdggggdg",
    ]),
    "block/marco_da_freguesia_top": (STONE, [
        "dddddddddddddddd",
        "dmmmmmmmmmmmmmmd",
        "dmllmmmmmmmmlmmd",
        "dmlmmmmcmmmmmmmd",
        "dmmmmmmmmmmmmmmd",
        "dmmmmlmmmmmmcmmd",
        "dmmcmmmmmmmmmmmd",
        "dmmmmmmmmlmmmmmd",
        "dmmmmmmmmmmmmmmd",
        "dmlmmmcmmmmmmlmd",
        "dmmmmmmmmmmmmmmd",
        "dmmmmmmmmmcmmmmd",
        "dmmlmmmmmmmmmmmd",
        "dmmmmmmmlmmmmmmd",
        "dmmmmmmmmmmmmmmd",
        "dddddddddddddddd",
    ]),
    "block/estaca_de_lote": (WOOD, [
        "......lmmd......",
        "......lmmd......",
        "......lmmd......",
        "......pppr......",
        "......prrr......",
        "......lmmd......",
        "......lmmd......",
        "......lmdd......",
        "......lmmd......",
        "......lmmd......",
        "......lmdd......",
        "......lmmd......",
        "......lmmd......",
        "......lmmd......",
        "......lmmd......",
        "......lmmd......",
    ]),
    "item/marco_da_freguesia": (STONE, [
        "................",
        "......oooo......",
        ".....ollmmo.....",
        ".....olmmmo.....",
        ".....omcmmo.....",
        ".....olmmdo.....",
        ".....ommmdo.....",
        ".....olmcdo.....",
        ".....ommmdo.....",
        ".....olmmdo.....",
        ".....omcmdo.....",
        ".....ommmdo.....",
        ".....ohmmdo.....",
        "....oghgggdo....",
        "....oooooooo....",
        "................",
    ]),
    "item/estaca_de_lote": (WOOD, [
        "................",
        "...........oo...",
        "..........olmo..",
        ".........olmdo..",
        "........oprro...",
        ".......opprro...",
        "......olmdo.....",
        ".....olmdo......",
        "....olmdo.......",
        "...olmdo........",
        "..olmdo.........",
        "..oldo..........",
        "..odo...........",
        "..oo............",
        "................",
        "................",
    ]),
}


def draw(palette, rows):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, row
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), palette[ch])
    return img


def main():
    for name, (palette, rows) in ART.items():
        out = ROOT / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        draw(palette, rows).save(out)
        print("wrote", out.relative_to(ROOT.parents[3]))


if __name__ == "__main__":
    main()
