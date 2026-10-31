"""Writes our six OreSpawn biome overrides: the port's own biome file with only our effects laid over it.

  python -I tools/biomes/gen_biome_overrides.py [<orespawn jar>]

Minecraft can't patch a biome, only replace the whole file, so each override is a full copy. A full copy goes stale
the moment the port changes its biome (new caves, ores, plants or spawns would be hidden behind our old copy). So the
copies are never edited by hand. This reads each biome from the port jar (default: the newest orespawn-*.jar in libs/),
replaces its "effects" block's keys with ours from tools/biomes/effects.json, and writes the result to
data/orespawn/worldgen/biome/. EffectsOverrideTest checks that the shipped files equal what this script writes.
"""
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src" / "main" / "resources" / "data" / "orespawn" / "worldgen" / "biome"


def port_jar() -> Path:
    if len(sys.argv) > 1:
        return Path(sys.argv[1])
    jars = sorted((ROOT / "libs").glob("orespawn-1.21.1-*.jar"))
    if not jars:
        raise SystemExit("no OreSpawn jar in libs/")
    return jars[-1]


def build(port: dict, effects: dict) -> dict:
    out = json.loads(json.dumps(port))
    merged = dict(out.get("effects", {}))
    merged.update(effects)
    out["effects"] = merged
    return out


def main() -> int:
    ours = json.loads((ROOT / "tools" / "biomes" / "effects.json").read_text(encoding="utf-8"))["effects"]
    jar = port_jar()
    with zipfile.ZipFile(jar) as z:
        for name, effects in ours.items():
            port = json.loads(z.read(f"data/orespawn/worldgen/biome/{name}.json"))
            text = json.dumps(build(port, effects), indent=2, ensure_ascii=False) + "\n"
            (OUT / f"{name}.json").write_text(text, encoding="utf-8")
            print(f"{name}: the port's biome from {jar.name}, with our {len(effects)} effects")
    return 0


if __name__ == "__main__":
    sys.exit(main())
