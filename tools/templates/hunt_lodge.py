"""Builds the Hunt Lodge (structure-hunt-lodge) as a structure template:
src/main/resources/data/orespawn_integrations/structure/hunt_lodge/lodge.nbt

    python -I tools/templates/hunt_lodge.py [path/to/registry_dump.json]

Rebuildable and deterministic. 40 x 16 x 30 (x, y, z); local y 0 is the ground (the jigsaw start is projected to
the surface, terrain_adaptation beard_thin). North is z 0.

- The Lodge, 27 x 13 on two floors (x 2-28, z 3-15): the front hall with Captain Iracema's Monday board (a
  monster_hunter_villager:hunters_table with her marker beside it), the pantry barrel (loot table
  orespawn_integrations:chests/hunt_lodge_pantry), the holding cell with its jail door and sign, the stairs (a
  ladder) to the upper floor's four bunks.
- The back wall (z 3, inside face z 4): seven orespawn_integrations:wanted_poster blocks, rows 1-7 left to right,
  each over a wall sign the Wall of the Wanted's writer rewrites (it finds them as signs under a poster); they start
  as "WANTED: ROW n / Felled by: / nobody. Yet." in translate keys. A lectern below, the Kill Register's from 0.19.0.
- The waystone by the door (origin dungeon), the yard (20 x 12) in front, three hunters' tents to the east, each
  with a bed and a Hunter's Workbench, and the kennel run (8 x 6) with its sign.
- Entities: Iracema's marker (minecraft:marker tagged cc_iracema: the code spawns her clone there), two hunters
  (villagers with the monster_hunter_villager:monster_hunter profession), two hounds and a page in training
  (orespawn:cannon_fodder), all PersistenceRequired. No spawner.

The 0.14.0 named placements (the evidence locker, the Wall's frames, the kennel's paving) and 0.17.0's plank are
later add-ons with their own templates. Every block id is checked against the pack's registry dump (OI's own poster
block is ours, so it is exempt).
"""
import json
import os
import sys
from pathlib import Path

import nbtlib
from nbtlib import Byte, Compound, Double, Int, List, String

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/data/orespawn_integrations/structure/hunt_lodge/lodge.nbt"
# The pack's registry dump: the first argument, else registry_dump.json in the design atlas named by CC_DESIGN_ATLAS.
DUMP = Path(os.environ.get("CC_DESIGN_ATLAS", ".")) / "registry_dump.json"
DATA_VERSION = 3955
SX, SY, SZ = 40, 16, 30
OURS = {"orespawn_integrations:wanted_poster"}
KEY = "orespawn_integrations.hunt."
LODGE = "orespawn_integrations.hunt_lodge."

blocks = {}
entities = []


def put(x, y, z, name, props=None, nbt=None):
    assert 0 <= x < SX and 0 <= y < SY and 0 <= z < SZ, (x, y, z)
    blocks[(x, y, z)] = (name, props or {}, nbt)


def fill(x0, y0, z0, x1, y1, z1, name, props=None):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                put(x, y, z, name, props)


def text(*keys):
    msgs = [json.dumps({"translate": k}) if k else '""' for k in keys]
    msgs += ['""'] * (4 - len(msgs))
    return Compound({"messages": List[String]([String(m) for m in msgs]), "color": String("black"),
                     "has_glowing_text": Byte(0)})


def wall_sign(x, y, z, facing, lines, waxed=True):
    put(x, y, z, "minecraft:spruce_wall_sign", {"facing": facing, "waterlogged": "false"}, Compound({
        "id": String("minecraft:sign"), "front_text": text(*lines), "back_text": text(),
        "is_waxed": Byte(1 if waxed else 0)}))


def entity(x, y, z, nbt):
    nbt["Pos"] = List[Double]([Double(x + 0.5), Double(y), Double(z + 0.5)])
    entities.append(Compound({
        "pos": List[Double]([Double(x + 0.5), Double(y), Double(z + 0.5)]),
        "blockPos": List[Int]([Int(x), Int(y), Int(z)]),
        "nbt": nbt}))


def build():
    # ground: the yard's path and the lodge's footprint cleared
    fill(0, 1, 0, SX - 1, SY - 1, SZ - 1, "minecraft:air")
    fill(0, 0, 0, SX - 1, 0, SZ - 1, "minecraft:grass_block", {"snowy": "false"})
    fill(4, 0, 17, 23, 0, 28, "minecraft:dirt_path")
    fill(12, 0, 16, 16, 0, 16, "minecraft:dirt_path")

    # the Lodge: 27 x 13, ground floor y 1-5, upper floor y 6 (floor) to y 10, roof y 11
    x0, x1, z0, z1 = 2, 28, 3, 15
    fill(x0, 0, z0, x1, 0, z1, "minecraft:spruce_planks")
    for y in range(1, 11):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                put(x, y, z, "minecraft:spruce_planks")
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                put(x, y, z, "minecraft:spruce_planks")
    for x in (x0, x1):
        for z in (z0, z1):
            fill(x, 1, z, x, 10, z, "minecraft:stripped_spruce_log", {"axis": "y"})
    fill(x0, 5, z0, x1, 5, z0, "minecraft:stripped_spruce_log", {"axis": "x"})
    fill(x0, 5, z1, x1, 5, z1, "minecraft:stripped_spruce_log", {"axis": "x"})
    fill(x0 + 1, 6, z0 + 1, x1 - 1, 6, z1 - 1, "minecraft:spruce_planks")
    fill(x0, 11, z0, x1, 11, z1, "minecraft:dark_oak_planks")
    fill(x0 + 1, 12, z0 + 3, x1 - 1, 12, z1 - 3, "minecraft:dark_oak_planks")
    # windows, front and sides
    for x in (5, 9, 19, 23):
        put(x, 3, z1, "minecraft:glass_pane", {"north": "false", "south": "false", "east": "true", "west": "true",
                                                "waterlogged": "false"})
        put(x, 8, z1, "minecraft:glass_pane", {"north": "false", "south": "false", "east": "true", "west": "true",
                                                "waterlogged": "false"})
    for z in (7, 11):
        for x in (x0, x1):
            put(x, 3, z, "minecraft:glass_pane", {"north": "true", "south": "true", "east": "false", "west": "false",
                                                   "waterlogged": "false"})
    # the double door
    for x, hinge in ((14, "left"), (15, "right")):
        put(x, 1, z1, "minecraft:spruce_door", {"facing": "north", "half": "lower", "hinge": hinge, "open": "false",
                                                "powered": "false"})
        put(x, 2, z1, "minecraft:spruce_door", {"facing": "north", "half": "upper", "hinge": hinge, "open": "false",
                                                "powered": "false"})
    # light
    for x in (7, 14, 21):
        put(x, 5, 9, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
        put(x, 10, 9, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})

    # the back wall: seven posters over seven signs, rows 1-7 west to east
    for row, x in enumerate((5, 8, 11, 14, 17, 20, 23), start=1):
        put(x, 3, z0 + 1, "orespawn_integrations:wanted_poster", {"facing": "south", "row": str(row)})
        wall_sign(x, 2, z0 + 1, "south", (KEY + f"sign.row.{row}", KEY + "sign.felled_by_colon", KEY + "sign.nobody_yet"),
                  waxed=True)
    put(14, 1, 6, "minecraft:lectern", {"facing": "south", "has_book": "false", "powered": "false"})

    # the front hall: the Monday board, Iracema's spot, the pantry
    put(8, 1, 12, "monster_hunter_villager:hunters_table", {"facing": "south"})
    entity(8, 1, 13, Compound({"id": String("minecraft:marker"), "Tags": List[String]([String("cc_iracema")]),
                               "Rotation": List[nbtlib.Float]([nbtlib.Float(0), nbtlib.Float(0)])}))
    put(4, 1, 13, "minecraft:barrel", {"facing": "up", "open": "false"}, Compound({
        "id": String("minecraft:barrel"), "LootTable": String("orespawn_integrations:chests/hunt_lodge_pantry")}))

    # the holding cell, east end: iron bars and the jail door, its sign beside
    for z in range(10, 15):
        put(23, 1, z, "minecraft:iron_bars", {"north": "true", "south": "true", "east": "false", "west": "false",
                                              "waterlogged": "false"})
        put(23, 2, z, "minecraft:iron_bars", {"north": "true", "south": "true", "east": "false", "west": "false",
                                              "waterlogged": "false"})
    for x in range(24, 28):
        put(x, 1, 10, "minecraft:iron_bars", {"north": "false", "south": "false", "east": "true", "west": "true",
                                              "waterlogged": "false"})
        put(x, 2, 10, "minecraft:iron_bars", {"north": "false", "south": "false", "east": "true", "west": "true",
                                              "waterlogged": "false"})
    put(23, 1, 12, "malisisdoorsreborn:jail_door", {"facing": "west", "half": "lower", "hinge": "left",
                                                     "open": "false", "powered": "false"})
    put(23, 2, 12, "malisisdoorsreborn:jail_door", {"facing": "west", "half": "upper", "hinge": "left",
                                                     "open": "false", "powered": "false"})
    put(22, 3, 11, "minecraft:spruce_planks")
    wall_sign(21, 3, 11, "west", (LODGE + "sign.cell.1", LODGE + "sign.cell.2", LODGE + "sign.cell.3",
                                  LODGE + "sign.cell.4"))

    # the stairs: a ladder up the back wall's east end, and its hole in the upper floor
    for y in range(1, 7):
        put(27, y, 4, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})
    put(27, 6, 4, "minecraft:ladder", {"facing": "south", "waterlogged": "false"})
    # the upper floor's four bunks and Iracema's map table
    for x in (4, 7, 10, 13):
        put(x, 7, 13, "minecraft:red_bed", {"facing": "north", "part": "foot", "occupied": "false"})
        put(x, 7, 12, "minecraft:red_bed", {"facing": "north", "part": "head", "occupied": "false"})
    put(22, 7, 6, "minecraft:cartography_table")

    # the waystone by the door, outside
    put(11, 1, 17, "waystones:waystone", {"facing": "south", "half": "lower", "waterlogged": "false",
                                          "origin": "dungeon"})
    put(11, 2, 17, "waystones:waystone", {"facing": "south", "half": "upper", "waterlogged": "false",
                                          "origin": "dungeon"})

    # the yard's banner pole
    fill(20, 1, 26, 20, 4, 26, "minecraft:spruce_fence", {"north": "false", "south": "false", "east": "false",
                                                           "west": "false", "waterlogged": "false"})
    put(20, 5, 26, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})

    # three hunters' tents to the east, wool A-frames with a bed and a Hunter's Workbench each
    for tz, wool in ((3, "minecraft:brown_wool"), (10, "minecraft:white_wool"), (17, "minecraft:brown_wool")):
        for z in range(tz, tz + 5):
            put(31, 1, z, wool)
            put(37, 1, z, wool)
            put(32, 2, z, wool)
            put(36, 2, z, wool)
            put(33, 3, z, wool)
            put(35, 3, z, wool)
            put(34, 4, z, wool)
        put(32, 1, tz + 1, "minecraft:red_bed", {"facing": "south", "part": "head", "occupied": "false"})
        put(32, 1, tz + 2, "minecraft:red_bed", {"facing": "south", "part": "foot", "occupied": "false"})
        put(36, 1, tz + 1, "monster_hunter_villager:hunters_table", {"facing": "west"})

    # the kennel run, 8 x 6
    kx0, kx1, kz0, kz1 = 26, 33, 23, 28
    fence = {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}
    for x in range(kx0, kx1 + 1):
        for z in (kz0, kz1):
            put(x, 1, z, "minecraft:oak_fence", fence)
    for z in range(kz0, kz1 + 1):
        for x in (kx0, kx1):
            put(x, 1, z, "minecraft:oak_fence", fence)
    put(kx0, 1, 25, "minecraft:oak_fence_gate", {"facing": "west", "in_wall": "false", "open": "false",
                                                  "powered": "false"})
    put(31, 1, 27, "minecraft:hay_block", {"axis": "y"})
    put(25, 1, 22, "minecraft:spruce_planks")
    wall_sign(25, 1, 21, "north", (LODGE + "sign.kennel.1", LODGE + "sign.kennel.2", LODGE + "sign.kennel.3",
                                   LODGE + "sign.kennel.4"))

    # the inhabitants
    for x, z in ((6, 21), (16, 22)):
        entity(x, 1, z, Compound({
            "id": String("minecraft:villager"), "PersistenceRequired": Byte(1),
            "VillagerData": Compound({"profession": String("monster_hunter_villager:monster_hunter"),
                                      "level": Int(1), "type": String("minecraft:plains")})}))
    for x, z in ((28, 25), (30, 26)):
        entity(x, 1, z, Compound({"id": String("minecraft:wolf"), "PersistenceRequired": Byte(1)}))
    entity(32, 1, 25, Compound({"id": String("orespawn:cannon_fodder"), "PersistenceRequired": Byte(1)}))


def check_ids(path):
    if not path.exists():
        print("registry dump not found, ids not checked:", path)
        return
    dump = json.loads(path.read_text(encoding="utf-8"))
    known = {e["id"] for e in dump["blocks"]["entries"]} | OURS
    missing = sorted({name for name, _, _ in blocks.values()} - known)
    if missing:
        raise SystemExit("unknown block ids: %s" % missing)
    ents = {e["id"] for e in dump["entity_types"]["entries"]}
    bad = sorted({str(e["nbt"]["id"]) for e in entities} - ents)
    if bad:
        raise SystemExit("unknown entity ids: %s" % bad)
    print("all", len({n for n, _, _ in blocks.values()}), "block ids and", len(entities), "entities checked")


def write():
    palette, index, entries = [], {}, []
    for pos in sorted(blocks, key=lambda p: (p[1], p[2], p[0])):
        name, props, nbt = blocks[pos]
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            state = Compound({"Name": String(name)})
            if props:
                state["Properties"] = Compound({k: String(v) for k, v in sorted(props.items())})
            palette.append(state)
        entry = Compound({"pos": List[Int]([Int(pos[0]), Int(pos[1]), Int(pos[2])]), "state": Int(index[key])})
        if nbt is not None:
            entry["nbt"] = nbt
        entries.append(entry)
    root = Compound({
        "DataVersion": Int(DATA_VERSION),
        "size": List[Int]([Int(SX), Int(SY), Int(SZ)]),
        "palette": List[Compound](palette),
        "blocks": List[Compound](entries),
        "entities": List[Compound](entities),
    })
    OUT.parent.mkdir(parents=True, exist_ok=True)
    nbtlib.File(root, gzipped=True).save(OUT)
    size = OUT.stat().st_size
    assert size < 100_000, size
    print("wrote", OUT, size, "bytes,", len(entries), "blocks,", len(palette), "states,", len(entities), "entities")


if __name__ == "__main__":
    build()
    check_ids(Path(sys.argv[1]) if len(sys.argv) > 1 else DUMP)
    write()
