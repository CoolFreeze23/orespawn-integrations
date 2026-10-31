"""Writes the rank-0 casket's custom-branch dispatcher from lullaby's Lucky Blocks jar.

  python -I tools/lucky/gen_hatchling_lists.py <path to ly-lucky-blocks-v1.3.3.jar>

The rank-0 custom branch (system-hoard-caskets, "OP enemies at rank 0") calls ly's own spawn functions by id, minus the
twelve OP enemies under spawn/op/enemy/, so it rerolls into the other 35 entries. This script reads ly's custom/random
function from the jar, keeps every entry that is not an OP enemy, in ly's order, and writes
data/orespawn_integrations/function/lucky/hatchling/custom.mcfunction. Nothing of ly's is copied but the function ids.
"""
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/data/orespawn_integrations/function/lucky/hatchling/custom.mcfunction"
SOURCE = "data/lucky_blocks/function/random/summon/custom/random.mcfunction"


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    with zipfile.ZipFile(sys.argv[1]) as jar:
        text = jar.read(SOURCE).decode("utf-8")
    ids = re.findall(r"run function (lucky_blocks:random/summon/custom/functions/spawn/[a-z0-9_/]+)", text)
    kept = [i for i in ids if "/op/enemy/" not in i]
    if len(ids) != 47 or len(kept) != 35:
        print(f"unexpected ly table: {len(ids)} entries, {len(kept)} kept (expected 47 and 35)")
        return 1
    lines = [
        "# The rank-0 custom branch (system-hoard-caskets): ly's 47 custom outcomes minus the twelve OP enemies, so a",
        "# Hatchling's casket rerolls into the other 35 (the god-roll gear, the elytra and the mace stay in). Written by",
        "# tools/lucky/gen_hatchling_lists.py from ly's custom/random; it calls ly's own spawn functions by id.",
        f"execute store result score #oi_pick cc_lucky run random value 1..{len(kept)}",
        "",
    ]
    for n, fid in enumerate(kept, 1):
        lines.append(f"execute if score #oi_pick cc_lucky matches {n} run return run function {fid}")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"wrote {OUT.relative_to(ROOT)} ({len(kept)} entries)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
