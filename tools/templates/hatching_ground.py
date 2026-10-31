"""Builds Shelf 9, the Hatching Ground (structure-hatching-ground), as a structure template:
src/main/resources/data/orespawn_integrations/structure/hatching_ground.nbt

    python tools/templates/hatching_ground.py [path/to/registry_dump.json]

Rebuildable and deterministic (fixed seed). 21 x 16 x 21 (x, y, z). Local y 3 is the ground: the placer puts the
template's origin three blocks below the chosen surface, so the shell's floor sits at ground level and the Shelf 10
crawlspace (y 0-2) lies under it. Positions left out of the template keep the world's own blocks (the terrain under
the ring); everything inside the footprint disc from y 4 up is written, air included, so trees and bumps are cleared.

What the code reads back from the template (StructureTemplate.filterBlocks), so the placer never hard-codes positions:
- signs carry an extra "cc_role" key in their block-entity data: register_0..7 (the Hatch Register, top-left first as
  read from inside), ways_n/e/s/w (the Four Ways), door_1/door_2, batch, shelf10; the sign itself ignores the key;
- the straw's three larder spots are orespawn:chicken_spawn_block placeholders, re-rolled at placement among the four
  farm larder blocks;
- the waystone is not in the template: the code places it at local (5, 4, 17).

Every block id is checked against the pack's registry dump when it is given (default: the design repo's atlas).
"""
import json
import math
import random
import sys
from pathlib import Path

import nbtlib
from nbtlib import Byte, Compound, Int, List, String

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/data/orespawn_integrations/structure/hatching_ground.nbt"
DUMP = Path("C:/Homework/Sessions/Japan/crazycraft-world-design-claude/atlas/data/registry_dump.json")
DATA_VERSION = 3955
SX, SY, SZ = 21, 16, 21
C = 10  # centre column
GROUND = 3  # the shell floor's y; outside, y 4 stands on the world's ground
KEY = "orespawn_integrations.shelf9."

CALCITE = ("minecraft:calcite", {})
CRACKED = ("rechiseled:bone_block_cracked", {})
AIR = ("minecraft:air", {})

blocks = {}  # (x, y, z) -> (name, props, nbt or None)


def put(x, y, z, state, nbt=None):
    assert 0 <= x < SX and 0 <= y < SY and 0 <= z < SZ, (x, y, z)
    blocks[(x, y, z)] = (state[0], state[1], nbt)


def dist(x, z):
    return math.hypot(x - C, z - C)


def text(*keys):
    msgs = [json.dumps({"translate": k}) if k else '""' for k in keys]
    msgs += ['""'] * (4 - len(msgs))
    return Compound({
        "messages": List[String]([String(m) for m in msgs]),
        "color": String("black"),
        "has_glowing_text": Byte(0),
    })


def sign_nbt(role, front=(), back=()):
    return Compound({
        "id": String("minecraft:sign"),
        "front_text": text(*front),
        "back_text": text(*back),
        "is_waxed": Byte(1),
        "cc_role": String(role),
    })


def wall_sign(x, y, z, facing, role, front=()):
    put(x, y, z, ("minecraft:spruce_wall_sign", {"facing": facing, "waterlogged": "false"}), sign_nbt(role, front))


def standing_sign(x, y, z, rotation, role):
    put(x, y, z, ("minecraft:spruce_sign", {"rotation": str(rotation), "waterlogged": "false"}), sign_nbt(role))


def build():
    rng = random.Random(9)

    # Clear the footprint disc from the ground up (trees, bumps); later puts overwrite.
    for y in range(GROUND + 1, SY):
        for x in range(SX):
            for z in range(SZ):
                if dist(x, z) <= 10.5:
                    put(x, y, z, AIR)

    # Shelf 10, the crawlspace under the shell: interior 3 x 2 x 5 (x 5-7, y 1-2, z 11-15) in a cracked-bone box.
    # No calcite below y 5 except the shard ring, so the processor's wear rule touches only the ring.
    for x in range(4, 9):
        for z in range(10, 17):
            put(x, 0, z, CRACKED)
            for y in (1, 2):
                inner = 5 <= x <= 7 and 11 <= z <= 15
                put(x, y, z, AIR if inner else CRACKED)
    put(6, 1, 11, ("minecraft:hay_block", {"axis": "y"}))  # the empty nest
    put(7, 2, 14, ("minecraft:glow_lichen", {"up": "true", "down": "false", "north": "false", "south": "false",
                                             "east": "false", "west": "false", "waterlogged": "false"}))  # keeps it dark-free
    # The label hangs on a vanilla bone block: rechiseled's cracked bone reads as air where Rechiseled is missing, and
    # a wall sign with nothing behind it drops at placement. Bone, not calcite, under y 5 (the wear rule).
    put(6, 2, 10, ("minecraft:bone_block", {"axis": "y"}))
    wall_sign(6, 2, 11, "south", "shelf10", [KEY + "shelf10.%d" % i for i in range(1, 5)])

    # The shell floor (y 3): a disc of cracked bone; one cracked panel over the crawlspace is a ghost block.
    for x in range(SX):
        for z in range(SZ):
            if dist(x, z) <= 7.0:
                put(x, GROUND, z, CRACKED)
    ghost = Compound({
        "id": String("secretroomsmod:secret_tile_entity"),
        "secret_data": Compound({"blockstate": Compound({"Name": String("rechiseled:bone_block_cracked")})}),
    })
    put(6, GROUND, 14, ("secretroomsmod:ghost_block", {}), ghost)

    # The shell walls (y 4-14): an egg profile, broken low on the east side where its top half came off.
    radius = {4: 7.0, 5: 7.5, 6: 7.5, 7: 7.5, 8: 7.5, 9: 7.5, 10: 7.5, 11: 7.2, 12: 6.6, 13: 5.8, 14: 4.6}
    for y, r in radius.items():
        for x in range(SX):
            for z in range(SZ):
                d = dist(x, z)
                if not (r - 1.0 < d <= r):
                    continue
                if x - C >= 3 and y > 9 + rng.randint(0, 2):
                    continue  # the broken east side
                # The lowest course is cracked bone, so the processor's wear rule (calcite at y 4 only) hits the
                # shard ring and never the shell.
                if y == 4:
                    put(x, y, z, CRACKED)
                else:
                    put(x, y, z, CRACKED if rng.random() < 0.3 else CALCITE)

    # The door (south, 2 wide, 3 high) and its signs: the three-line label over it, the batch board beside it.
    for x in (9, 10):
        for y in (4, 5, 6):
            for z in (16, 17):
                put(x, y, z, AIR)
    # Each hangs on a calcite block of the wall, never a cracked one, for the same reason as the Shelf 10 label.
    put(10, 7, 17, CALCITE)
    put(11, 5, 17, CALCITE)
    wall_sign(10, 7, 18, "south", "door", [KEY + "door.%d" % i for i in range(1, 4)])
    wall_sign(11, 5, 18, "south", "batch", [KEY + "batch.%d" % i for i in range(1, 5)])

    # Inside: the 8 x 8 straw, four soul lanterns (light 10, no spawns), the three larder placeholders.
    for x in range(6, 14):
        for z in range(6, 14):
            put(x, 4, z, ("minecraft:hay_block", {"axis": "y"}))
    for x, z in ((6, 6), (13, 6), (6, 13), (13, 13)):
        put(x, 5, z, ("minecraft:soul_lantern", {"hanging": "false", "waterlogged": "false"}))
    for x in (8, 10, 12):
        put(x, 5, 7, ("orespawn:chicken_spawn_block", {}))

    # The Hatch Register: eight signs on the inside of the west wall, read from inside (facing west), top-left first.
    order = [(7, 11), (7, 10), (7, 9), (7, 8), (6, 11), (6, 10), (6, 9), (6, 8)]
    for i, (y, z) in enumerate(order):
        put(3, y, z, CALCITE)  # a flat backing for each sign
        wall_sign(4, y, z, "east", "register_%d" % i)

    # The steps up the inner wall to the lookout, then the 3 x 3 ledge on the west rim and the top spike.
    for k, y in enumerate(range(5, 14)):
        a = math.radians(158 + (13 - y) * 22)
        x = round(C + 5.6 * math.cos(a))
        z = round(C + 5.6 * math.sin(a))
        put(x, y, z, CALCITE)
        for h in (1, 2):
            if y + h < SY:
                put(x, y + h, z, AIR)
    for x in range(5, 8):
        for z in range(9, 12):
            put(x, 14, z, CALCITE)
    put(5, 15, 9, ("minecraft:bone_block", {"axis": "y"}))

    # The egg's breath: a signal campfire on a hay block in the broken crown.
    put(10, 14, 6, ("minecraft:hay_block", {"axis": "y"}))
    put(10, 15, 6, ("minecraft:campfire", {"lit": "true", "signal_fire": "true", "facing": "south",
                                           "waterlogged": "false"}))

    # Outside: the Four Ways' signs, the seat by the door, the Gallery's eight empty plinths, the shard ring.
    # The Four Ways' posts stand outside the floor, where y 3 is the world's own; each gets its own block of dirt (as the
    # placer props up the ring), so a post over a dip or a cave mouth does not drop at placement.
    for x, z, rotation, side in ((10, 1, 8, "n"), (19, 10, 12, "e"), (10, 19, 0, "s"), (1, 10, 4, "w")):
        put(x, GROUND, z, ("minecraft:dirt", {}))
        standing_sign(x, 4, z, rotation, "ways_" + side)
    put(13, 4, 18, ("bibliocraft:white_oak_seat", {}))
    plinths = [(0, 20), (2, 20), (4, 20), (6, 20), (0, 12), (0, 14), (0, 16), (0, 18)]
    for x, z in plinths:
        put(x, 4, z, ("minecraft:polished_diorite", {}))
    keep_clear = [(10, 1), (19, 10), (10, 19), (1, 10), (5, 17), (13, 18), (9, 18), (10, 18), (11, 18)]
    for i in range(24):
        a = math.radians(i * 15 + 7)
        x = round(C + 9 * math.cos(a))
        z = round(C + 9 * math.sin(a))
        if any(abs(x - kx) + abs(z - kz) <= 2 for kx, kz in keep_clear) or (x, z) in plinths:
            continue
        put(x, 4, z, CALCITE)
        if x - C >= 3 and rng.random() < 0.5:
            put(x, 5, z, CALCITE)  # bigger pieces where the top half landed
    return blocks


def check_ids(path):
    if not path.exists():
        print("registry dump not found, ids not checked:", path)
        return
    dump = json.loads(path.read_text(encoding="utf-8"))
    known = {e["id"] for e in dump["blocks"]["entries"]}
    missing = sorted({name for name, _, _ in blocks.values()} - known)
    if missing:
        raise SystemExit("unknown block ids: %s" % missing)
    print("all", len({n for n, _, _ in blocks.values()}), "block ids found in the registry dump")


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
        "entities": List[Compound]([]),
    })
    OUT.parent.mkdir(parents=True, exist_ok=True)
    nbtlib.File(root, gzipped=True).save(OUT)
    size = OUT.stat().st_size
    assert size < 100_000, size
    roles = sorted(str(b[2]["cc_role"]) for b in blocks.values() if b[2] is not None and "cc_role" in b[2])
    print("wrote", OUT, size, "bytes,", len(entries), "blocks,", len(palette), "states; signs:", roles)


if __name__ == "__main__":
    build()
    check_ids(Path(sys.argv[1]) if len(sys.argv) > 1 else DUMP)
    write()
