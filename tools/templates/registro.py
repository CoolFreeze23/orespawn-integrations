"""Builds the Registry's three structure templates (npc-odetes, OI 0.10.0) with nbtlib, DataVersion 3955.

registro/balcao             the Registry desk alone, for the hub (/place template ...; the Odete comes by
                            /noppes clone spawn odete_registro 9 ~1 ~ ~). 3 wide, 2 high, 1 deep, no jigsaw.
registro/balcao_esconderijo the same desk as the wrapper's jigsaw child: its middle cell is the jigsaw the Cartorio's
                            inner jigsaw attaches to (final state: the desk), and its right drawer is Barto's jigsaw
                            (pool registro/esconderijo, empty until 0.17.0; final state: the same drawer).
registro/cartorio           the Village Registry, 7 wide x 9 deep x 5 high: the sealed-files cabinet (keypad chest)
                            beside a level-4 keycard reader, the provisional throne (brasilcoisas:cadeira_brahma)
                            under its TRONO sign, the queue bench, the trophy system over the desk (modded projectiles
                            on), the four-line CARTORIO sign over the door, and a marker where the desk Odete stands
                            (our code spawns the odete_registro clone there and removes the marker).

Every block id is checked against the pack's registry dump. Signs carry translate keys only.
Run from the repo root: python tools/templates/registro.py [path/to/registry_dump.json]
"""
import json
import os
import sys

import nbtlib
from nbtlib import Byte, Compound, Double, Float, Int, List, String

DATA_VERSION = 3955
OUT = os.path.join("src", "main", "resources", "data", "orespawn_integrations", "structure", "registro")
DUMP = os.path.join(os.environ.get("CC_DESIGN_ATLAS", ""), "registry_dump.json")  # the design atlas's data folder

SIGN_CARTORIO = ["orespawn_integrations.odete.placa.cartorio.%d" % i for i in range(1, 5)]
SIGN_TRONO = ["orespawn_integrations.odete.placa.trono.%d" % i for i in range(1, 5)]


class Template:
    def __init__(self, size):
        self.size = size
        self.palette = []
        self.index = {}
        self.blocks = []
        self.entities = []

    def state(self, name, props=None):
        key = (name, tuple(sorted((props or {}).items())))
        if key not in self.index:
            entry = Compound({"Name": String(name)})
            if props:
                entry["Properties"] = Compound({k: String(v) for k, v in props.items()})
            self.index[key] = len(self.palette)
            self.palette.append(entry)
        return self.index[key]

    def set(self, pos, name, props=None, nbt=None):
        x, y, z = pos
        assert 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2], (pos, name)
        self.blocks = [b for b in self.blocks if tuple(int(v) for v in b["pos"]) != tuple(pos)]
        block = Compound({"pos": List[Int]([Int(v) for v in pos]), "state": Int(self.state(name, props))})
        if nbt is not None:
            block["nbt"] = nbt
        self.blocks.append(block)

    def marker(self, pos, tag):
        x, y, z = pos
        self.entities.append(Compound({
            "pos": List[Double]([Double(x + 0.5), Double(y), Double(z + 0.5)]),
            "blockPos": List[Int]([Int(x), Int(y), Int(z)]),
            # Facing south (yaw 0), toward the door; the template's rotation turns it with the building.
            "nbt": Compound({"id": String("minecraft:marker"), "Tags": List[String]([String(tag)]),
                             "Rotation": List[Float]([Float(0.0), Float(0.0)])}),
        }))

    def ids(self):
        return {str(p["Name"]) for p in self.palette}

    def save(self, path):
        root = Compound({
            "DataVersion": Int(DATA_VERSION),
            "size": List[Int]([Int(v) for v in self.size]),
            "palette": List[Compound](self.palette),
            "blocks": List[Compound](self.blocks),
            "entities": List[Compound](self.entities),
        })
        os.makedirs(os.path.dirname(path), exist_ok=True)
        nbtlib.File(root, gzipped=True).save(path)
        return os.path.getsize(path)


def jigsaw(name, target, pool, final_state):
    return Compound({
        "id": String("minecraft:jigsaw"),
        "name": String(name),
        "target": String(target),
        "pool": String(pool),
        "final_state": String(final_state),
        "joint": String("aligned"),
        "placement_priority": Int(0),
        "selection_priority": Int(0),
    })


def sign_text(keys):
    return Compound({
        "messages": List[String]([String(json.dumps({"translate": k})) for k in keys]),
        "color": String("black"),
        "has_glowing_text": Byte(0),
    })


def sign_nbt(block_entity, keys):
    return Compound({
        "id": String(block_entity),
        "front_text": sign_text(keys),
        "back_text": sign_text(keys),
        "is_waxed": Byte(1),
    })


DESK_LEFT = ("storagedrawers:oak_full_drawers_2", {"facing": "south"})
DESK_MID = ("refurbished_furniture:oak_desk", {"facing": "south", "left": "false", "right": "false"})
DESK_RIGHT = ("storagedrawers:oak_full_drawers_1", {"facing": "south"})


def state_string(name, props):
    return name + "[" + ",".join("%s=%s" % kv for kv in props.items()) + "]"


def desk(hidden):
    """The desk, 3x2x1, facing south (toward the counter's public side)."""
    t = Template((3, 2, 1))
    t.set((0, 0, 0), *DESK_LEFT)
    if hidden:
        t.set((1, 0, 0), "minecraft:jigsaw", {"orientation": "south_up"},
              jigsaw("orespawn_integrations:balcao", "minecraft:empty", "minecraft:empty", state_string(*DESK_MID)))
        t.set((2, 0, 0), "minecraft:jigsaw", {"orientation": "north_up"},
              jigsaw("minecraft:empty", "orespawn_integrations:esconderijo", "orespawn_integrations:registro/esconderijo",
                     state_string(*DESK_RIGHT)))
    else:
        t.set((1, 0, 0), *DESK_MID)
        t.set((2, 0, 0), *DESK_RIGHT)
    t.set((0, 1, 0), "refurbished_furniture:brown_lamp", {"powered": "false"})
    t.set((1, 1, 0), "bibliocraft:typewriter", {"facing": "south", "paper": "0", "waterlogged": "false"})
    t.set((2, 1, 0), "bibliocraft:oak_bookcase", {"facing": "south", "waterlogged": "false"})
    return t


def cartorio():
    w, h, d = 7, 5, 9
    t = Template((w, h, d))
    for x in range(w):
        for z in range(d):
            t.set((x, 0, z), "minecraft:spruce_planks")
            t.set((x, 4, z), "minecraft:dark_oak_planks")
            edge = x in (0, w - 1) or z in (0, d - 1)
            corner = x in (0, w - 1) and z in (0, d - 1)
            for y in (1, 2, 3):
                if corner:
                    t.set((x, y, z), "minecraft:stripped_oak_log", {"axis": "y"})
                elif edge:
                    t.set((x, y, z), "minecraft:white_terracotta")
                else:
                    t.set((x, y, z), "minecraft:air")
    for z in (3, 5):
        t.set((0, 2, z), "minecraft:glass_pane", {"east": "false", "west": "false", "north": "true", "south": "true"})
        t.set((w - 1, 2, z), "minecraft:glass_pane", {"east": "false", "west": "false", "north": "true", "south": "true"})
    # The door, its street jigsaw under the threshold (CTOV houses use minecraft:building_entrance), the sign above.
    t.set((3, 1, 8), "minecraft:oak_door", {"facing": "north", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
    t.set((3, 2, 8), "minecraft:oak_door", {"facing": "north", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
    t.set((3, 0, 8), "minecraft:jigsaw", {"orientation": "south_up"},
          jigsaw("minecraft:building_entrance", "minecraft:empty", "minecraft:empty", "minecraft:spruce_planks"))
    t.set((3, 3, 8), "minecraft:oak_hanging_sign", {"rotation": "0", "attached": "false", "waterlogged": "false"},
          sign_nbt("minecraft:hanging_sign", SIGN_CARTORIO))
    # C: the sealed-files cabinet; R: the level-4 keycard reader beside it.
    t.set((1, 1, 1), "securitycraft:keypad_chest", {"facing": "south"})
    t.set((1, 1, 2), "securitycraft:keycard_reader", {"facing": "east", "powered": "false"},
          Compound({"id": String("securitycraft:keycard_reader"),
                    "acceptedLevels": Compound({"lvl%d" % i: Byte(1 if i == 4 else 0) for i in range(1, 6)}),
                    "requiresExactKeycard": Byte(1)}))
    # D: the desk's cell. The inner jigsaw at O faces the desk row and pulls registro/balcao (the hidden variant).
    t.set((3, 1, 2), "minecraft:jigsaw", {"orientation": "north_up"},
          jigsaw("minecraft:empty", "orespawn_integrations:balcao", "orespawn_integrations:registro/balcao", "minecraft:air"))
    t.marker((3, 1, 2), "cc_registro_odete")
    # T: the provisional throne, its sign on the wall above and behind it.
    t.set((5, 1, 2), "brasilcoisas:cadeira_brahma", {"facing": "south"})
    t.set((5, 2, 1), "minecraft:oak_wall_sign", {"facing": "south", "waterlogged": "false"},
          sign_nbt("minecraft:sign", SIGN_TRONO))
    # B: the queue bench.
    t.set((1, 1, 6), "minecraft:oak_stairs", {"facing": "west", "half": "bottom", "shape": "straight", "waterlogged": "false"})
    t.set((1, 1, 5), "minecraft:oak_stairs", {"facing": "west", "half": "bottom", "shape": "straight", "waterlogged": "false"})
    # S: the trophy system over the desk, modded projectiles on (key "14").
    t.set((3, 3, 1), "securitycraft:trophy_system", None,
          Compound({"id": String("securitycraft:trophy_system"), "projectiles": Compound({"14": Byte(1)})}))
    t.set((3, 3, 5), "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    return t


def check(templates, dump_path):
    if not os.path.exists(dump_path):
        print("registry dump not found; ids NOT checked:", dump_path)
        return
    dump = json.load(open(dump_path, encoding="utf-8"))
    blocks = {e["id"] for e in dump["blocks"]["entries"]}
    missing = sorted({i for t in templates for i in t.ids()} - blocks)
    if missing:
        sys.exit("unknown block ids: %s" % missing)
    print("all block ids found in the registry dump")


def main():
    dump = sys.argv[1] if len(sys.argv) > 1 else DUMP
    built = {"balcao": desk(False), "balcao_esconderijo": desk(True), "cartorio": cartorio()}
    check(built.values(), dump)
    for name, t in built.items():
        size = t.save(os.path.join(OUT, name + ".nbt"))
        assert size < 100 * 1024
        print(name, t.size, size, "bytes")


if __name__ == "__main__":
    main()
