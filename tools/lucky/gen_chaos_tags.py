"""Writes the chaos group's tags (OI 0.10.0) from the cards' lists and the OreSpawn jar in libs/.

  python -I tools/lucky/gen_chaos_tags.py

- #orespawn_integrations:ovos_da_despensa: every spawn egg a larder block makes with a water bucket, read from OreSpawn's
  own "<egg>_from_<block>_spawn_block" recipes (the 105 larder outputs; the 11 Crystal Treasury ores are not larder
  blocks).
- the royal seal's 17 eggs and the Cuca's 18 royal egg blocks (system-material-ladder, event-cucas-lullaby);
- the 46 heavyweights (system-lancamentos-em-duplicidade), merged into Apothic Spawners' blacklist, and no_copies;
- the HBM exemptions and the royal diet (item-royal-diet); the statues no-spawner list (npc-mobzilla).
Every entry from another mod is written with required: false, so a missing mod never drops a tag.
"""
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src/main/resources/data"


def jar(prefix: str) -> Path:
    found = sorted((ROOT / "libs").glob(prefix + "*.jar"))
    if not found:
        raise SystemExit(f"no {prefix}*.jar in libs/")
    return found[-1]


def write(rel: str, values, plain=()):
    vals = [v for v in plain] + [{"id": v, "required": False} for v in values]
    p = DATA / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps({"replace": False, "values": vals}, indent=2) + "\n", encoding="utf-8", newline="\n")
    return len(vals)


def larder_outputs():
    out = set()
    with zipfile.ZipFile(jar("orespawn-")) as z:
        for name in z.namelist():
            if not re.match(r"data/orespawn/recipe/.+_spawn_egg_from_.+\.json$", name):
                continue
            recipe = json.loads(z.read(name))
            inputs = [i.get("item") for i in recipe["ingredients"] if i.get("item") != "minecraft:water_bucket"]
            if len(inputs) == 1 and not inputs[0].split(":")[1].startswith("ore_"):
                out.add(recipe["result"]["id"])
    return sorted(out)


ROYAL_SEAL = [
    "orespawn:the_king_spawn_egg", "orespawn:the_queen_spawn_egg", "orespawn:godzilla_spawn_egg",
    "orespawn:kraken_spawn_egg", "orespawn:mothra_spawn_egg", "orespawn:pitch_black_spawn_egg",
    "orespawn:kyuubi_spawn_egg", "orespawn:hammerhead_spawn_egg", "orespawn:giant_robot_spawn_egg",
    "orespawn:emperor_scorpion_spawn_egg", "orespawn:basilisk_spawn_egg", "orespawn:alien_boss_spawn_egg",
    "orespawn:cater_killer_spawn_egg", "orespawn:leon_spawn_egg", "orespawn:water_dragon_spawn_egg",
    "minecraft:wither_spawn_egg", "minecraft:ender_dragon_spawn_egg",
]
ROYAL_EGGS = [
    "orespawn:the_king_part_spawn_block", "orespawn:the_king_spawn_block", "orespawn:the_queen_part_spawn_block",
    "orespawn:the_queen_spawn_block", "orespawn:godzilla_part_spawn_block", "orespawn:godzilla_spawn_block",
    "orespawn:hammerhead_spawn_block", "orespawn:hercules_beetle_spawn_block", "orespawn:kraken_spawn_block",
    "orespawn:kyuubi_spawn_block", "orespawn:leon_spawn_block", "orespawn:mothra_spawn_block",
    "orespawn:nastysaurus_spawn_block", "orespawn:pitch_black_spawn_block", "orespawn:basilisk_spawn_block",
    "orespawn:cater_killer_spawn_block", "orespawn:emperor_scorpion_spawn_block", "orespawn:water_dragon_spawn_block",
]
HEAVYWEIGHTS = [
    "orespawn:emperor_scorpion", "orespawn:basilisk", "orespawn:cater_killer", "orespawn:pitch_black", "orespawn:leon",
    "orespawn:hammerhead", "orespawn:giant_robot", "orespawn:spider_robot", "orespawn:the_prince_adult",
    "orespawn:hercules_beetle", "orespawn:nastysaurus",
    "brazil_legends:mapinguari", "brazil_legends:mapinguary", "brazil_legends:headless_mule", "brazil_legends:capelobo",
    "brazil_legends:cuca",
    "born_in_chaos_v1:lord_pumpkinhead", "born_in_chaos_v1:krampus", "born_in_chaos_v1:lifestealer",
    "born_in_chaos_v1:missioner", "born_in_chaos_v1:supreme_bonescaller", "born_in_chaos_v1:sir_pumpkinhead",
    "born_in_chaos_v1:dire_hound_leader", "born_in_chaos_v1:mother_spider",
    "mutantmonsters:mutant_enderman", "mutantmonsters:mutant_zombie", "mutantmonsters:mutant_skeleton",
    "mutantmonsters:mutant_creeper",
    "cataclysm:ender_golem", "cataclysm:amethyst_crab", "cataclysm:kobolediator", "cataclysm:aptrgangr",
    "cataclysm:wadjet", "cataclysm:clawdian", "cataclysm:the_prowler",
    "plantz:gargantuar", "plantz:super_brainz", "plantz:robo_zombie", "plantz:pirate_captain", "plantz:zombie_yeti",
    "marvel:red_skull", "marvel:winter_soldier", "marvel:baron_zemo", "friendsandfoes:wildfire", "pandorasbox:giant",
    "irons_spellbooks:ice_spider",
]
HBM_EXEMPT = ["minecraft:green_wool", "minecraft:white_wool", "minecraft:gold_ingot", "minecraft:blaze_powder",
              "minecraft:gunpowder", "minecraft:pumpkin_pie", "minecraft:tnt", "minecraft:coal"]
ROYAL_DIET = ["orespawn:ingot_uranium", "orespawn:uranium_nugget", "orespawn:ingot_titanium", "orespawn:titanium_nugget",
              "orespawn:ruby", "orespawn:amethyst_gem", "orespawn:tigers_eye_ingot"]


def main():
    assert len(ROYAL_SEAL) == 17 and len(ROYAL_EGGS) == 18 and len(HEAVYWEIGHTS) == 46 and len(set(HEAVYWEIGHTS)) == 46
    eggs = larder_outputs()
    counts = {
        "ovos_da_despensa": write("orespawn_integrations/tags/item/ovos_da_despensa.json", eggs),
        "royal_seal_eggs": write("orespawn_integrations/tags/item/royal_seal_eggs.json", ROYAL_SEAL),
        "royal_eggs": write("orespawn_integrations/tags/item/royal_eggs.json", ROYAL_EGGS),
        "hbm_hazard_exempt": write("orespawn_integrations/tags/item/hbm_hazard_exempt.json", [], HBM_EXEMPT),
        "royal_titanium": write("orespawn_integrations/tags/item/royal_titanium.json", ["orespawn:ingot_titanium"]),
        "royal_diet": write("orespawn_integrations/tags/item/royal_diet.json", ROYAL_DIET),
        "statues/no_spawner": write("orespawn_integrations/tags/item/statues/no_spawner.json",
                                    ["orespawn_integrations:mobzilla_statue", "statues:warden_statue",
                                     "statues:elder_guardian_statue"]),
        "heavyweights": write("orespawn_integrations/tags/entity_type/heavyweights.json", HEAVYWEIGHTS),
        "apothic blacklist": write("apothic_spawners/tags/entity_type/blacklisted_from_spawners.json", HEAVYWEIGHTS),
        "jar_blacklist": write("ars_nouveau/tags/entity_type/jar_blacklist.json", [],
                               ["minecraft:ender_dragon", "minecraft:wither"]),
        "no_copies": write("orespawn_integrations/tags/entity_type/no_copies.json",
                           ["#c:bosses", "#orespawn_integrations:heavyweights", "orespawn:godzilla", "orespawn:kraken",
                            "orespawn:the_king", "orespawn:the_queen", "orespawn:mothra", "orespawn:kyuubi",
                            "orespawn:alien_boss"],
                           ["minecraft:wither", "minecraft:ender_dragon", "minecraft:warden",
                            "minecraft:elder_guardian"]),
    }
    if len(eggs) != 105:
        raise SystemExit(f"expected 105 larder outputs, found {len(eggs)}")
    for k, v in counts.items():
        print(f"{k}: {v}")


if __name__ == "__main__":
    main()
