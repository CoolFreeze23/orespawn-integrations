"""Builds Seu Ze's boteco (structure-boteco-do-seu-ze, OI 0.10.1) with nbtlib, DataVersion 3955.

boteco/bar   the start piece, 24 x 12 x 24 (x, y, z), the road side to the south (+z):
             - the yard (z 0-5): string lights on fence posts, a small mango tree (jungle log and leaves), the regulars'
               overflow bench, grass the processor wears to coarse dirt;
             - the back room, os Fundos (x 1-8, z 7-13): one door, a malisisdoorsreborn:red_curtain in front of a
               secretroomsmod:secret_door (open to anyone until 0.14.0's check), a chest with the Hat Launcher
               (chests/boteco/fundos), the Doggy Talents piano, an empty item frame by the back door for the tuft key;
             - the kitchen (x 10-22, z 7-13): a refurbished_furniture:black_grill, the feijoada pot, the filtro de barro,
               the pantry barrel (chests/boteco/despensa, Lootr gives each player their own roll), the chimney with a
               campfire for smoke, the saloon-door hatch to the bar;
             - the bar (z 14-21): purple and yellow walls, the counter east-west with the jukebox, Seu Ze behind it
               (brasilcoisas:seu_ze, tag cc_boteco_counter, PersistenceRequired), the truco table (mesa_da_skol and four
               cadeira_skol) over the jigsaw to orespawn_integrations:esconderijo/boteco (Barto's hiding place, an
               empty pool until 0.17.0; final state the floor), the regulars' table by the window, the album display
               case by the door, the PF chalkboard;
             - the front (z 22-23): the rolling door (malisisdoorsreborn:oak_garage_door), the porch with Skol tables,
               the counter sign (FIADO SO / AMANHA, four translate keys) and the roadside waystone.

Ticking block entities: the jukebox (while playing) and the campfire. No spawner. Every block id and the two entities
are checked against the pack's registry dump; signs carry translate keys only. The later pieces each ship as their
own small template for /cc boteco upgrade from the slice that adds them; 0.10.1 has none.
Run from the repo root: python tools/templates/boteco.py [path/to/registry_dump.json]
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from registro import Template, jigsaw, sign_nbt, state_string  # noqa: E402

from nbtlib import Byte, Compound, Double, Float, Int, List, String  # noqa: E402

OUT = os.path.join("src", "main", "resources", "data", "orespawn_integrations", "structure", "boteco")
# The pack's registry dump: the first argument, else registry_dump.json in the design atlas named by CC_DESIGN_ATLAS.
DUMP = os.path.join(os.environ.get("CC_DESIGN_ATLAS", "."), "registry_dump.json")
W, H, D = 24, 12, 24

SIGN_COUNTER = ["orespawn_integrations.boteco.placa.balcao.%d" % i for i in range(1, 5)]
ENTITIES = ["brasilcoisas:seu_ze", "doggytalents:upright_piano_brown"]


def entity(t, pos, nbt, yaw=0.0):
    x, y, z = pos
    nbt["Rotation"] = List[Float]([Float(yaw), Float(0.0)])
    t.entities.append(Compound({
        "pos": List[Double]([Double(x + 0.5), Double(y), Double(z + 0.5)]),
        "blockPos": List[Int]([Int(x), Int(y), Int(z)]),
        "nbt": nbt,
    }))


def container(block_entity, table):
    return Compound({"id": String(block_entity), "LootTable": String(table)})


def door(t, x, y, z, name, facing, extra=None):
    props = {"facing": facing, "half": "lower"}
    props.update(extra or {})
    t.set((x, y, z), name, props)
    up = dict(props)
    up["half"] = "upper"
    t.set((x, y + 1, z), name, up)


def build():
    t = Template((W, H, D))
    # Ground: the yard's grass, a dirt path to the road, and the building's tiled floor.
    for x in range(W):
        for z in range(D):
            t.set((x, 0, z), "minecraft:grass_block", {"snowy": "false"})
    for z in range(22, 24):
        for x in range(9, 15):
            t.set((x, 0, z), "minecraft:dirt_path")

    # The building shell: x 1-22, z 7-21, walls y 1-4, a spruce slab roof at y 5.
    x0, x1, z0, z1 = 1, 22, 7, 21
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            floor = "minecraft:white_terracotta" if (x + z) % 2 == 0 else "minecraft:yellow_terracotta"
            t.set((x, 0, z), floor)
            t.set((x, 5, z), "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
            edge = x in (x0, x1) or z in (z0, z1)
            for y in range(1, 5):
                if edge:
                    corner = x in (x0, x1) and z in (z0, z1)
                    if corner:
                        t.set((x, y, z), "minecraft:stripped_oak_log", {"axis": "y"})
                    else:
                        wall = "minecraft:purple_terracotta" if y <= 2 else "minecraft:yellow_terracotta"
                        t.set((x, y, z), wall)
                else:
                    t.set((x, y, z), "minecraft:air")
    # Inner walls: the back wall of the bar (z 14) and the wall between the back room and the kitchen (x 9).
    for x in range(x0 + 1, x1):
        for y in range(1, 5):
            t.set((x, y, 14), "minecraft:oak_planks")
    for z in range(z0 + 1, 14):
        for y in range(1, 5):
            t.set((9, y, z), "minecraft:oak_planks")
    # Windows.
    for x in (4, 17):
        t.set((x, 2, z1), "minecraft:glass_pane", {"east": "true", "west": "true", "north": "false", "south": "false",
                                                    "waterlogged": "false"})
    for z in (17, 19):
        t.set((x0, 2, z), "minecraft:glass_pane", {"east": "false", "west": "false", "north": "true", "south": "true",
                                                   "waterlogged": "false"})

    # --- the back room, os Fundos (x 2-8, z 8-13) ---
    door(t, 5, 1, 14, "secretroomsmod:secret_door", "north", {"hinge": "left", "open": "false"})
    door(t, 5, 1, 15, "malisisdoorsreborn:red_curtain", "north")
    t.set((2, 1, 8), "minecraft:chest", {"facing": "south", "type": "single", "waterlogged": "false"},
          container("minecraft:chest", "orespawn_integrations:chests/boteco/fundos"))
    entity(t, (7, 1, 9), Compound({"id": String("doggytalents:upright_piano_brown")}), 180.0)
    # The back door to the yard, and the empty frame by it for the tuft key (0.14.0).
    door(t, 4, 1, 7, "minecraft:oak_door", "south", {"hinge": "left", "open": "false", "powered": "false"})
    t.entities.append(Compound({
        "pos": List[Double]([Double(5.5), Double(2.5), Double(8.03)]),
        "blockPos": List[Int]([Int(5), Int(2), Int(8)]),
        "nbt": Compound({"id": String("minecraft:item_frame"), "Facing": Byte(3), "TileX": Int(5), "TileY": Int(2),
                         "TileZ": Int(8), "Tags": List[String]([String("cc_boteco_tufo")])}),
    }))
    t.set((3, 4, 11), "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})

    # --- the kitchen (x 10-21, z 8-13) ---
    t.set((11, 1, 8), "refurbished_furniture:black_grill")
    t.set((13, 1, 8), "braziliandelight:feijoada_pot")
    t.set((15, 1, 8), "brasilcoisas:filtro_de_barro", {"facing": "south"})
    t.set((20, 1, 8), "minecraft:barrel", {"facing": "up", "open": "false"},
          container("minecraft:barrel", "orespawn_integrations:chests/boteco/despensa"))
    t.set((17, 1, 8), "minecraft:smoker", {"facing": "south", "lit": "false"})
    # The chimney over the grill: bricks up to y 8, a campfire at its foot sends the smoke.
    for y in range(1, 9):
        if y >= 5:
            t.set((11, y, 7), "minecraft:bricks")
    t.set((11, 5, 8), "minecraft:campfire", {"facing": "south", "lit": "true", "signal_fire": "false",
                                              "waterlogged": "false"})
    door(t, 15, 1, 14, "malisisdoorsreborn:saloon_door", "south", {"hinge": "left"})
    t.set((18, 4, 11), "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})

    # --- the bar (x 2-21, z 15-20) ---
    for x in range(4, 19):
        if x == 15:
            continue  # the hatch's way through
        t.set((x, 1, 16), "minecraft:spruce_planks")
        t.set((x, 2, 16), "minecraft:spruce_slab", {"type": "bottom", "waterlogged": "false"})
    t.set((19, 1, 16), "minecraft:jukebox", {"has_record": "false"})
    entity(t, (10, 1, 15), Compound({
        "id": String("brasilcoisas:seu_ze"),
        "Tags": List[String]([String("cc_boteco_counter")]),
        "PersistenceRequired": Byte(1),
    }), 0.0)
    # The truco table, Barto's jigsaw under it (an empty pool until 0.17.0; it resolves to the floor).
    t.set((8, 1, 19), "brasilcoisas:mesa_da_skol", {"facing": "north"})
    t.set((8, 0, 19), "minecraft:jigsaw", {"orientation": "down_south"},
          jigsaw("minecraft:empty", "orespawn_integrations:esconderijo", "orespawn_integrations:esconderijo/boteco",
                 "minecraft:white_terracotta"))
    for (x, z, f) in ((8, 18, "south"), (8, 20, "north"), (7, 19, "east"), (9, 19, "west")):
        t.set((x, 1, z), "brasilcoisas:cadeira_skol", {"facing": f})
    # The regulars' table by the window (their clones come in 0.15.0).
    t.set((15, 1, 19), "brasilcoisas:mesa_da_skol", {"facing": "north"})
    t.set((14, 1, 19), "brasilcoisas:cadeira_skol", {"facing": "east"})
    t.set((16, 1, 19), "brasilcoisas:cadeira_skol", {"facing": "west"})
    # The album case by the door (0.11.0) and the PF chalkboard on the wall (0.16.0's dish).
    t.set((20, 1, 20), "bibliocraft:green_oak_display_case", {"facing": "north", "waterlogged": "false"})
    t.set((2, 2, 20), "decocraft:chalkboard")
    t.set((2, 1, 18), "decocraft:casino_stool_red")
    for x in (6, 12, 18):
        t.set((x, 4, 18), "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})

    # --- the front: the rolling door, the porch, the counter sign, the waystone ---
    for x in (11, 12):
        t.set((x, 1, z1), "malisisdoorsreborn:oak_garage_door",
              {"facing": "south", "open": "true", "powered": "false", "top": "false"})
        t.set((x, 2, z1), "malisisdoorsreborn:oak_garage_door",
              {"facing": "south", "open": "true", "powered": "false", "top": "true"})
    t.set((14, 2, 22), "minecraft:oak_wall_sign", {"facing": "south", "waterlogged": "false"},
          sign_nbt("minecraft:sign", SIGN_COUNTER))
    for x in (4, 18):
        t.set((x, 1, 22), "brasilcoisas:mesa_da_skol", {"facing": "north"})
        t.set((x - 1, 1, 22), "brasilcoisas:cadeira_skol", {"facing": "east"})
        t.set((x + 1, 1, 22), "brasilcoisas:cadeira_skol", {"facing": "west"})
    door(t, 21, 1, 23, "waystones:waystone", "south")

    # --- the yard: string lights on fence posts, the mango tree, the bench ---
    for x in (2, 7, 12, 17, 21):
        for y in (1, 2, 3):
            t.set((x, y, 1), "minecraft:oak_fence", {"east": "false", "west": "false", "north": "false",
                                                     "south": "false", "waterlogged": "false"})
        t.set((x, 4, 1), "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    for y in range(1, 5):
        t.set((15, y, 3), "minecraft:jungle_log", {"axis": "y"})
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if abs(dx) + abs(dz) <= 3 and (dx, dz) != (0, 0):
                t.set((15 + dx, 5, 3 + dz), "minecraft:jungle_leaves",
                      {"distance": "1", "persistent": "true", "waterlogged": "false"})
    t.set((15, 5, 3), "minecraft:jungle_leaves", {"distance": "1", "persistent": "true", "waterlogged": "false"})
    t.set((15, 6, 3), "minecraft:jungle_leaves", {"distance": "1", "persistent": "true", "waterlogged": "false"})
    for x in (5, 6):
        t.set((x, 1, 4), "minecraft:oak_stairs", {"facing": "north", "half": "bottom", "shape": "straight",
                                                  "waterlogged": "false"})
    # The porch's bricks, which the processor wears.
    for x in range(2, 22):
        if not (9 <= x <= 14):
            t.set((x, 0, 22), "minecraft:bricks")
    return t


def check(t, dump_path):
    if not os.path.exists(dump_path):
        sys.exit("registry dump not found; ids NOT checked: " + dump_path)
    dump = json.load(open(dump_path, encoding="utf-8"))
    blocks = {e["id"] for e in dump["blocks"]["entries"]}
    entities = {e["id"] for e in dump["entity_types"]["entries"]}
    missing = sorted(t.ids() - blocks)
    missing += [e for e in ENTITIES + ["minecraft:item_frame"] if e not in entities]
    if missing:
        sys.exit("unknown ids: %s" % missing)
    print("all %d block ids and %d entity ids found in the registry dump" % (len(t.ids()), len(ENTITIES) + 1))


def main():
    dump = sys.argv[1] if len(sys.argv) > 1 else DUMP
    t = build()
    check(t, dump)
    size = t.save(os.path.join(OUT, "bar.nbt"))
    assert size < 100 * 1024, size
    assert t.size[0] <= 48 and t.size[2] <= 48
    print("boteco/bar", t.size, size, "bytes,", len(t.blocks), "blocks,", len(t.entities), "entities")


if __name__ == "__main__":
    main()
