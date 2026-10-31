"""Writes the world group's data for OI 0.10.0 (region names, sightings, the Antechamber, the Jade lines) and checks
every id against the pack's registry dump.

  python -I tools/world/world_data.py [<path to atlas/data>]

Writes, under src/main/resources/data/orespawn_integrations/:
  tags/worldgen/biome/region/<slug>.json      the fourteen region tags (system-region-names, the region cards)
  tags/entity_type/sightable.json             creatures a spyglass can sight (system-sightings)
  tags/block/sightable.json                   blocks that give a pin but no stamp
  tags/block/larder/<class>.json              the Larder's four egg classes, for the Jade line (item-the-larder)
  sightings/families.json                     the sighting families, read by the server handler
  advancement/avistamentos/*.json, advancement/wonders/the_antechamber.json
and _lang_fragments/0.10.0/world.{keys,en_us,pt_br}.json.

The default atlas path is the lore folder's; the script never writes there.
"""
import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src" / "main" / "resources" / "data" / "orespawn_integrations"
FRAG = ROOT / "_lang_fragments" / "0.10.0"
ATLAS = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(os.environ.get("CC_DESIGN_ATLAS", ""))
CARD = {"region": "system-region-names", "sight": "system-sightings", "ante": "structure-chaos-antechamber",
        "colony": "item-court-post-and-doors", "larder": "item-the-larder", "shelf9": "structure-hatching-ground"}

dump = json.loads((ATLAS / "registry_dump.json").read_text(encoding="utf-8"))
tags = json.loads((ATLAS / "tags.json").read_text(encoding="utf-8"))


def ids(reg):
    return {e if isinstance(e, str) else e["id"] for e in dump[reg]["entries"]}


BIOMES, STRUCTURES, ENTITIES, BLOCKS, ITEMS = (ids(r) for r in ("biomes", "structures", "entity_types", "blocks", "items"))
problems = []


def need(kind, pool, rid):
    if rid not in pool:
        problems.append(f"{kind} {rid} is not in the registry dump")
    return rid


def resolve(tag):
    out = set()
    for e in tags["worldgen/biome"].get(tag, []):
        e = e if isinstance(e, str) else e["id"]
        out |= resolve(e[1:]) if e.startswith("#") else {e}
    return out


def entry(rid):
    """A tag entry: vanilla and tag references plain, anything from another mod optional, so a missing mod never
    breaks the whole tag."""
    if rid.startswith("#") or rid.startswith("minecraft:"):
        return rid
    return {"id": rid, "required": False}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


# ---------------------------------------------------------------- region tags
# Contents from each region card's "Biomes, by tag"; the order lives in RegionTable.java (region-overworld's table).
wetlands = resolve("c:is_swamp")
yards_ids = ["biomesoplenty:wasteland", "biomesoplenty:wasteland_steppe", "biomesoplenty:volcanic_plains",
             "biomesoplenty:volcano"]
yards = resolve("c:is_desert") | resolve("c:is_badlands") | set(yards_ids)
# The Moth Heights: #c:is_mountain without #c:is_floral, as an explicit list (minus what the Wetlands and the Yards,
# read earlier, already hold: the BOP moor and volcano).
heights = sorted(resolve("c:is_mountain") - resolve("c:is_floral") - wetlands - yards)
REGIONS = {
    "old_ground": ["distantlandsmc:blooming_jacaranda_grove", "distantlandsmc:lavender_grove",
                   "distantlandsmc:pau_brazil_forest", "distantlandsmc:yellow_ipe_grove"],
    "usurpers_lawn": ["plantz:graveyard"],
    "wetlands": ["#c:is_swamp"],
    "selva": ["#c:is_jungle", "biomesoplenty:tropics"],
    "executioners_yards": ["#c:is_desert", "#c:is_badlands"] + yards_ids,
    "moth_heights": heights,
    "frost": ["#c:is_snowy", "biomesoplenty:tundra"],
    "dark_wood": ["minecraft:dark_forest", "biomesoplenty:ominous_woods", "biomesoplenty:dead_forest",
                  "biomesoplenty:old_growth_dead_forest", "biomesoplenty:spider_nest"],
    "pine_north": ["#c:is_taiga"],
    "sertao": ["#c:is_savanna"],
    "greenwood": ["#minecraft:is_forest", "minecraft:cherry_grove", "biomesoplenty:old_growth_woodland"],
    "abyssal_coast": ["#c:is_ocean", "#c:is_river", "#c:is_beach", "#c:is_stony_shores", "minecraft:mushroom_fields"],
    "larder": ["minecraft:dripstone_caves", "minecraft:lush_caves", "minecraft:deep_dark",
               "biomesoplenty:glowing_grotto"],
    "countryside": ["#c:is_plains", "minecraft:meadow"],
}
for slug, members in REGIONS.items():
    for m in members:
        if m.startswith("#"):
            if m[1:] not in tags["worldgen/biome"]:
                problems.append(f"biome tag {m} is not in tags.json")
        else:
            need("biome", BIOMES, m)
    write(DATA / "tags" / "worldgen" / "biome" / "region" / f"{slug}.json",
          {"replace": False, "values": [entry(m) for m in members]})

# ---------------------------------------------------------------- sighting families
def match(patterns):
    out = []
    for p in patterns:
        rx = re.compile("^" + re.escape(p).replace(r"\*", ".*") + "$")
        hits = sorted(s for s in STRUCTURES if rx.match(s))
        if not hits:
            problems.append(f"structure pattern {p} matches nothing")
        out += hits
    return sorted(set(out))


# (number, slug, EN name, PT name, kind, targets, icon item)
# Names are system-sightings' EN/PT line 3, exactly as written. kind: structure / entity / block / special / hub /
# none (no target ships in 0.10.0: the page stays locked).
FAMILIES = [
    (1, "village", "a village", "uma vila", "structure",
     match(["minecraft:village_*", "ctov:*/village_*", "towns_and_towers:village_*"]), "minecraft:bell"),
    (2, "pandora_henge", "a Pandora henge", "um círculo de Pandora", "structure", ["pandorasbox:henge"], "minecraft:chest"),
    (3, "pandora_shrine", "a Pandora shrine", "um santuário de Pandora", "structure", ["pandorasbox:shrine_overworld"],
     "minecraft:ender_chest"),
    (4, "pretenders_tower", "a Pretender's tower", "uma torre de Pretendente", "structure",
     match(["apotheosis:tower_*"]), "minecraft:stone_bricks"),
    (5, "hoard_nest", "a Hoard nest", "um ninho do Tesouro", "structure", ["inventorypets:tree_top"], "minecraft:oak_leaves"),
    (6, "sky_nest", "a sky nest", "um ninho do céu", "structure", ["inventorypets:sky_dungeon"], "minecraft:feather"),
    (7, "tavern", "a tavern", "uma taverna", "structure", match(["nova_structures:tavern_*"]), "minecraft:barrel"),
    (8, "firewatch_tower", "a firewatch tower", "uma torre de vigia do fogo", "structure",
     match(["nova_structures:firewatch_tower_*"]), "minecraft:campfire"),
    (9, "watchtower", "a watchtower", "uma atalaia", "structure",
     ["structory_towers:wizard_tower", "structory_towers:engineer_tower", "structory_towers:taiga_outpost"],
     "minecraft:spyglass"),
    (10, "lighthouse", "the lighthouse", "o farol", "structure", ["structory_towers:lighthouse"], "minecraft:lantern"),
    (11, "hunter_tent", "a Hunter tent", "uma barraca de caçador", "structure",
     match(["monster_hunter_villager:hunters_tent_*"]), "minecraft:white_wool"),
    (12, "collectors_post", "a Collectors' post", "um posto dos Coletores", "structure",
     match(["minecraft:pillager_outpost", "ctov:pillager_outpost_*", "towns_and_towers:pillager_outpost_*",
            "towns_and_towers:exclusives/pillager_outpost_*"]), "minecraft:crossbow"),
    (13, "unrecorded_tower", "an Unrecorded tower", "uma torre Sem-Registro", "structure",
     match(["born_in_chaos_v1:dark_tower_*", "born_in_chaos_v1:observation_tower_*"]), "minecraft:wither_skeleton_skull"),
    (14, "cuca_hut", "the Cuca's hut", "o casebre da Cuca", "structure", ["brazil_legends:cuca_hut"], "minecraft:cauldron"),
    (15, "lost_cathedral", "the Lost Cathedral", "a Catedral Perdida", "structure", ["brazil_legends:lost_cathedral"],
     "minecraft:bell"),
    (16, "orespawn_oddity", "an OreSpawn oddity", "uma esquisitice do OreSpawn", "structure",
     ["orespawn:haunted_house", "orespawn:mantis_nest", "orespawn:rubber_ducky_pond", "orespawn:bouncy_castle",
      "orespawn:small_beehive", "orespawn:igloo"], "minecraft:jack_o_lantern"),
    (17, "girlfriend_island", "Girlfriend Island", "a Ilha da Namorada", "structure", ["orespawn:girlfriend_island"],
     "minecraft:poppy"),
    (18, "mobzilla_arena", "the Mobzilla Arena", "a Arena do Mobzilla", "structure",
     ["orespawn_integrations:mobzilla_arena"], "minecraft:iron_bars"),
    (19, "umvuthana_grove", "the Umvuthana grove", "o bosque dos Umvuthana", "structure", ["mowziesmobs:umvuthana_grove"],
     "minecraft:acacia_sapling"),
    (20, "mountain_tower", "the mountain tower", "a torre da montanha", "structure", ["irons_spellbooks:mountain_tower"],
     "minecraft:enchanted_book"),
    (21, "hydra_outpost", "a Hydra outpost", "um posto da Hidra", "structure",
     ["marvel:hydra_outpost_classic", "marvel:hydra_outpost_mountain", "marvel:hydra_outpost_winter"], "minecraft:tnt"),
    (22, "bandit_towers", "the bandit towers", "as torres dos bandidos", "structure", ["dungeons_arise:bandit_towers"],
     "minecraft:iron_sword"),
    (23, "ruins", "ruins", "ruínas", "structure", ["nova_structures:wild_ruin", "structory:ruin_grassy"],
     "minecraft:mossy_cobblestone"),
    (24, "boteco", "the boteco", "o boteco", "hub", ["boteco"], "minecraft:glass_bottle"),
    (25, "hunt_lodge", "the Hunt Lodge", "o Pavilhão de Caça", "hub", ["hunt_lodge"], "minecraft:bow"),
    (26, "hatching_ground", "the Hatching Ground", "o Ninho", "hub", ["hatching_ground"], "minecraft:egg"),
    (27, "prince", "the Prince", "o Príncipe", "entity",
     ["orespawn:the_prince", "orespawn:the_prince_teen", "orespawn:the_prince_adult"], "minecraft:golden_helmet"),
    (28, "girlfriend", "the Girlfriend", "a Namorada", "entity", ["orespawn:girlfriend"], "minecraft:rose_bush"),
    (29, "mestre", "the Mestre", "o Mestre", "entity", ["brasilcoisas:mestre_ensinador"], "minecraft:book"),
    (30, "saci", "a Saci Pererê", "o Saci-Pererê", "entity", ["brazil_legends:saci_perere"], "minecraft:red_wool"),
    (31, "court_beast", "a Court Beast", "uma Fera da Corte", "entity",
     ["orespawn:emperor_scorpion", "orespawn:basilisk", "orespawn:alien_boss", "orespawn:cater_killer"],
     "minecraft:golden_sword"),
    (32, "mapinguari", "the Mapinguari", "o Mapinguari", "entity", ["brazil_legends:mapinguari"], "minecraft:leather"),
    (33, "caramelo", "Caramelo", "o Caramelo", "entity", ["brasilcoisas:cachorro_caramelo"], "minecraft:bone"),
    (34, "sky_beast", "a sky beast", "um bicho do céu", "entity",
     ["orespawn:cloud_shark", "orespawn:leonopteryx", "orespawn:cephadrome"], "minecraft:phantom_membrane"),
    (35, "mobzilla", "Mobzilla", "o Mobzilla", "entity", ["orespawn:godzilla"], "minecraft:dragon_head"),
    (36, "kraken", "the Kraken", "a Kraken", "entity", ["orespawn:kraken"], "minecraft:ink_sac"),
    (37, "door_cloud", "the door cloud", "a nuvem da porta", "special", ["orespawn:butterfly"], "minecraft:glow_ink_sac"),
    (38, "hoard_fall", "a Hoard Fall", "uma Queda do Tesouro", "special", ["minecraft:falling_block"],
     "minecraft:magma_block"),
    # The urutau has no spawn placement until 0.10.1, so its page stays locked in 0.10.0 (system-sightings).
    (39, "mae_da_lua", "the mãe-da-lua", "a mãe-da-lua", "none", [], "minecraft:ender_eye"),
    (40, "mula_trail", "the Mula's fire trail", "o rastro de fogo da Mula", "none", [], "minecraft:blaze_powder"),
    (41, "red_islands", "red islands over the void", "ilhas vermelhas sobre o vazio", "special", ["orespawn:chaos"],
     "minecraft:obsidian"),
    (43, "bad_egg", "a Bad Egg", "um Ovo Gorado", "block", ["hbmsntm:crashed_bomb", "hbmsntm:mine_ap"],
     "minecraft:turtle_egg"),
    (46, "oil_spill", "an oil spill", "um vazamento", "block", ["hbmsntm:oil_spill"], "minecraft:black_dye"),
    (49, "three_butterflies", "three butterflies", "três borboletas", "special",
     ["orespawn:butterfly", "naturalist:butterfly", "distantlandsmc:butterfly"], "minecraft:pink_petals"),
]
for n, slug, en, pt, kind, targets, icon in FAMILIES:
    need("item", ITEMS, icon)
    pool = {"structure": STRUCTURES, "entity": ENTITIES, "block": BLOCKS}.get(kind)
    for t in targets:
        if pool is not None:
            need(kind, pool, t)
        elif kind == "special" and ":" in t and t not in ("orespawn:chaos",):
            need("entity", ENTITIES, t)
if len([f for f in FAMILIES if f[0] <= 40]) != 40:
    problems.append("the core families are not forty")

write(DATA / "sightings" / "families.json", {
    "_comment": "Read by world/sightings/SightingFamilies. Written by tools/world/world_data.py; edit there.",
    "families": [{"n": n, "slug": slug, "kind": kind, "targets": targets} for n, slug, en, pt, kind, targets, icon in
                 FAMILIES]})

SIGHTABLE_ENTITIES = sorted({t for f in FAMILIES if f[4] == "entity" for t in f[5]}
                            | {"orespawn:butterfly", "naturalist:butterfly", "distantlandsmc:butterfly"})
write(DATA / "tags" / "entity_type" / "sightable.json",
      {"replace": False, "values": [entry(need("entity", ENTITIES, e)) for e in SIGHTABLE_ENTITIES]})
# The kite (orespawn_integrations:snagged_kite) joins with its block in 0.16.0.
SIGHTABLE_BLOCKS = ["orespawn:ant_block", "orespawn:red_ant_block", "orespawn:rainbow_ant_block",
                    "orespawn:unstable_ant_block", "orespawn:termite_block", "simpleluckyblock:lucky_block",
                    "pandorasbox:pandoras_box"]
write(DATA / "tags" / "block" / "sightable.json",
      {"replace": False, "values": [entry(need("block", BLOCKS, b)) for b in SIGHTABLE_BLOCKS]})

# ---------------------------------------------------------------- the Larder's classes (item-the-larder's table)
LORE = ATLAS.parent.parent / "bible" / "cards" / "item-the-larder.md"
classes = {"farm": [], "menagerie": [], "quarry": [], "royal": []}
for line in LORE.read_text(encoding="utf-8").splitlines():
    if not line.startswith("| `"):
        continue
    cells = [c.strip() for c in line.strip().strip("|").split("|")]
    m = re.match(r"`([a-z_]+:[a-z0-9_]+)`", cells[0])
    t = re.match(r"L·([a-z]+)", cells[5]) if m and len(cells) > 5 else None
    if m and t:
        classes[t.group(1)].append(need("block", BLOCKS, m.group(1)))
for cls, members in classes.items():
    write(DATA / "tags" / "block" / "larder" / f"{cls}.json",
          {"replace": False, "values": [entry(b) for b in sorted(members)]})
if sum(len(v) for v in classes.values()) != 119:
    problems.append("the Larder table no longer has 119 egg blocks")

# ---------------------------------------------------------------- advancements
# Parented to the tab root of What the Ledger Says (orespawn_integrations:wonder/1, system-advancement-tree), where
# Land Ho! and Wonder 4 sit.
LEDGER_ROOT = "orespawn_integrations:wonder/1"


def advancement(path, icon, frame, hidden, recipes=None):
    adv = {
        "parent": LEDGER_ROOT,
        "criteria": {"granted": {"trigger": "minecraft:impossible"}},
        "requirements": [["granted"]],
        "display": {
            "icon": {"count": 1, "id": icon},
            "title": {"translate": f"advancements.orespawn_integrations.{path.replace('/', '.')}.title"},
            "description": {"translate": f"advancements.orespawn_integrations.{path.replace('/', '.')}.description"},
            "frame": frame, "show_toast": True, "announce_to_chat": False, "hidden": hidden},
        "sends_telemetry_event": False}
    if recipes:
        adv["rewards"] = {"recipes": recipes}
    write(DATA / "advancement" / f"{path}.json", adv)


for n, slug, en, pt, kind, targets, icon in FAMILIES:
    # The first stamp writes the vanilla spyglass into the recipe book (system-sightings, "A second spyglass").
    advancement(f"avistamentos/{slug}", icon, "task", True, ["minecraft:spyglass"])
advancement("avistamentos/terra_a_vista", "minecraft:spyglass", "goal", False)
advancement("wonders/the_antechamber", "minecraft:obsidian", "challenge", False)

# ---------------------------------------------------------------- lang fragments
keys, en_us, pt_br = {}, {}, {}


def line(key, card, surface, where, en, pt):
    keys[key] = f"{CARD[card]} | {surface} | {where}"
    en_us[key] = en
    pt_br[key] = pt


K = "orespawn_integrations."
# Region names: system-region-names EN/PT lines 1-14 (title / subtitle).
REGION_TEXT = [
    (1, "The Old Ground", "the Land before the House", "O Chão Velho", "a Terra de antes da Casa"),
    (2, "The Countryside", "villages, gossip and market day", "O Interior", "vila, fofoca e feira"),
    (3, "The Greenwood", "the Land's own woods, full of the King's leftovers", "A Mata",
     "mata da Terra mesmo, cheia de sobra do Rei"),
    (4, "The Dark Wood", "the Hoard's night shift", "A Mata Fechada", "o turno da noite do Tesouro"),
    (5, "The Wetlands", "the Cuca's country. Don't sing.", "O Brejo", "terra da Cuca. Não cante."),
    (6, "The Selva", "hot, loud, and the Saci's", "A Selva", "calor, barulho e Saci"),
    (7, "The Sertão", "World 21's sun never left", "O Sertão", "o sol do Mundo 21 ficou de vez"),
    (8, "The Executioner's Yards", "Mobzilla practises here", "Os Pátios do Carrasco", "aqui o Mobzilla treina"),
    (9, "The Moth Heights", "the Queen's moths roost here", "As Alturas das Mariposas",
     "aqui as mariposas da Rainha pousam"),
    (10, "The Frost", "the King's freezer. Snow is Hoard.", "A Geada", "o freezer do Rei. Neve é Tesouro."),
    (11, "The Pine North", "small vermin, big hunters", "O Pinheiral", "bicho miúdo, caçador graúdo"),
    (12, "The Abyssal Coast", "the sea wants a crown", "A Costa do Abismo", "o mar tá de olho numa coroa"),
    (13, "The Usurper's Lawn", "a Province of Zomboss, says Zomboss", "O Gramado do Usurpador",
     "Província de Zomboss, segundo o próprio Zomboss"),
    (14, "The Larder", "where the eggs fell into the stone", "A Despensa", "onde os ovos caíram na pedra"),
]
# Map-pin names for the #sightable blocks: pins are literal text, so every pinned thing needs a name of ours in both
# languages (another mod's own name would be English for a pt-BR player). Names follow the pack's renames and the
# glossary (the lucky block is the Sealed Casket / Arca Lacrada); the rest keep the pack's shipped pt-BR names.
PIN_NAMES = [
    ("orespawn", "ant_block", "Ant Block", "Bloco de Formigas"),
    ("orespawn", "red_ant_block", "Red Ant Block", "Bloco de Formigas Vermelhas"),
    ("orespawn", "rainbow_ant_block", "Rainbow Ant Block", "Bloco de Formigas Arco-íris"),
    ("orespawn", "unstable_ant_block", "Unstable Ant Block", "Bloco de Formigas Instáveis"),
    ("orespawn", "termite_block", "Termite Block", "Bloco de Cupins"),
    ("simpleluckyblock", "lucky_block", "Sealed Casket", "Arca Lacrada"),
    ("pandorasbox", "pandoras_box", "Pandora's Box", "Caixa de Pandora"),
]
for ns, path, en, pt in PIN_NAMES:
    line(f"{K}avistamento.pin.{ns}.{path}", "sight", "ITEM", f"map pin name for {ns}:{path}", en, pt)
for n, et, es, pt_t, ps in REGION_TEXT:
    line(f"{K}region.l{n}.title", "region", "TITLE", f"region L{n} title", et, pt_t)
    line(f"{K}region.l{n}.subtitle", "region", "TITLE", f"region L{n} subtitle", es, ps)
TIERS = [("solid ground (1)", "terra firme (1)"), ("quiet, for now (2)", "tranquilo, por enquanto (2)"),
         ("it gets rough here (3)", "aqui o bicho pega (3)"), ("it gets nasty here (4)", "aqui o bicho pega feio (4)"),
         ("it gets rough and stays rough (5)", "o bicho pega e não solta (5)"),
         ("this is the end of the line (6)", "aqui é o fim da picada (6)"),
         ("from here down, you're on your own (7)", "daqui pra baixo, é por sua conta (7)")]
for i, (e, p) in enumerate(TIERS, 1):
    line(f"{K}tier.{i}", "region", "TITLE", f"tier line {i}, the subtitle under a tiered dimension", e, p)
# Dimensions: lines 16-27. A tiered dimension's subtitle is its tier line; a side realm's is its own line.
DIMS = [
    ("orespawn.utopia", "The Garden Palace", "O Palácio-Jardim", None, None),
    ("orespawn.mining", "The Royal Mines", "As Minas Reais", None, None),
    ("orespawn.village", "The Vassal Kingdom", "O Reino Vassalo", None, None),
    ("orespawn.crystal", "The Crystal Treasury", "A Tesouraria de Cristal", None, None),
    ("orespawn.islands", "The Proving Grounds", "O Campo de Provas", None, None),
    ("minecraft.the_end", "The Founder's Tomb", "O Túmulo do Fundador", None, None),
    ("orespawn.chaos", "The Oubliette", "A Masmorra do Esquecimento", None, None),
    ("minecraft.the_nether", "The Furnace Province", "A Província da Fornalha", "the Royal Kitchen's ovens",
     "os fornos da Cozinha Real"),
    ("twilightforest.twilight_forest", "The Dusk Court", "A Corte do Crepúsculo", "a supper nobody finished",
     "a ceia que ficou na mesa"),
    ("aether.the_aether", "The Royal Hatchery", "A Chocadeira Real", "where the royal eggs keep warm",
     "onde os ovos reais ficam quentinhos"),
    ("the_bumblezone.the_bumblezone", "The Sister Court", "A Corte Irmã", "the Queen's dowry, still buzzing",
     "o dote da Rainha, zumbindo até hoje"),
    ("vortexmod.tardisdim", "The Royal Clock", "O Relógio Real", "it only goes where your name has been",
     "só anda por onde o seu nome andou"),
    ("vortexmod.vortexdim", "The Royal Clock", "O Relógio Real", "it only goes where your name has been",
     "só anda por onde o seu nome andou"),
    ("vortexmod.skarodim", "The Royal Clock", "O Relógio Real", "it only goes where your name has been",
     "só anda por onde o seu nome andou"),
    ("irons_spellbooks.pocket_dimension", "The pockets", "Os bolsos", "the Land, dreaming small",
     "a Terra sonhando miudinho"),
    ("ars_nouveau.jar", "The pockets", "Os bolsos", "the Land, dreaming small", "a Terra sonhando miudinho"),
]
for path, et, pt_t, es, ps in DIMS:
    line(f"{K}dimension.{path}.title", "region", "TITLE", f"dimension title, {path.replace('.', ':')}", et, pt_t)
    if es:
        line(f"{K}dimension.{path}.subtitle", "region", "TITLE", f"dimension subtitle, {path.replace('.', ':')}", es, ps)
for slug, et, es, pt_t, ps in [("raso", "The Shallows", "upper islands, small fry", "O Raso", "ilha de cima, peixe miúdo"),
                               ("meio", "The Middle", "the Big-Uns come down to drink", "O Meio",
                                "os Grandões descem pra beber"),
                               ("bercario", "The Nursery", "the Unrecorded are born here", "O Berçário",
                                "aqui nascem os Sem-Registro")]:
    line(f"{K}depth.{slug}.title", "region", "TITLE", f"Chaos depth title, {slug}", et, pt_t)
    line(f"{K}depth.{slug}.subtitle", "region", "TITLE", f"Chaos depth subtitle, {slug}", es, ps)
line(f"{K}region.first_visit", "region", "ACTIONBAR", "first visit to a region, with its scrap (line 30)",
     "First visit on file. Scrap enclosed.", "Primeira visita registrada. Retalho anexo.")

# Sightings: line 2 (the stamp), line 3 (family names), lines 4-5, line 14 (Land Ho!).
line(f"{K}avistamento.sighted", "sight", "ACTIONBAR",
     "a sighting; %1$s the family's name, %2$s the distance, a number (line 2)",
     "Sighted: %1$s · %2$s blocks", "Avistado: %1$s · %2$s blocos")
for n, slug, en, pt, kind, targets, icon in FAMILIES:
    line(f"{K}avistamento.family.{slug}", "sight", "ACTIONBAR",
         f"family {n}'s name, in the stamp and its map pin (line 3)", en, pt)
    title_en, title_pt = ("Red Islands over the Void", "Ilhas Vermelhas sobre o Vazio") if slug == "red_islands" \
        else (f"Sighted: {en}", f"Avistado: {pt}")
    line(f"advancements.orespawn_integrations.avistamentos.{slug}.title", "sight", "ADV",
         f"hidden advancement title, family {n} (the Notebook page's lock)", title_en, title_pt)
    line(f"advancements.orespawn_integrations.avistamentos.{slug}.description", "sight", "ADV",
         f"hidden advancement description, family {n}", "Stamped in the Field Notebook from far away.",
         "Carimbado na Caderneta de Campo, de longe.")
line(f"{K}avistamento.bad_egg", "sight", "ACTIONBAR", "a Bad Egg stamped, instead of the sighting line (line 4)",
     "Dud. Not on record. Don't kick it.", "Bomba falhada. Não consta. Não chute.")
line(f"{K}avistamento.oil_spill", "sight", "ACTIONBAR", "an oil spill stamped, instead of the sighting line (line 5)",
     "Spill: not on record.", "Vazamento: não consta.")
line("advancements.orespawn_integrations.avistamentos.terra_a_vista.title", "sight", "ADV",
     "advancement at the fortieth stamp (line 14)", "Land Ho!", "Terra à Vista!")
line("advancements.orespawn_integrations.avistamentos.terra_a_vista.description", "sight", "ADV",
     "advancement at the fortieth stamp (line 14)", "Forty stamps in the Field Notebook.",
     "Quarenta carimbos na Caderneta de Campo.")

# The Antechamber: lines 1-2, 4, 6, 8, 9, 13, 14 and the Jade door lines of "The player experience".
for i, (e, p) in enumerate([("You're early.", "Chegou cedo."), ("Go home.", "Volta pra casa."),
                            ("The moths", "As mariposas"), ("will wait.", "esperam.")], 1):
    line(f"{K}antechamber.sign1.{i}", "ante", "SIGN", f"ledge sign 1, row {i} (line 1)", e, p)
for i, (e, p) in enumerate([("The Oubliette:", "A Masmorra:"), ("crowned only.", "pra quem coroou")], 1):
    line(f"{K}antechamber.sign2.{i}", "ante", "SIGN", f"ledge sign 2, row {i} (line 2)", e, p)
for i, (e, p) in enumerate([("NO FISHING", "PROIBIDO PESCAR"), ("(unless", "(a não ser"),
                            ("you want to)", "que queira)")], 1):
    line(f"{K}antechamber.sign3.{i}", "ante", "SIGN", f"ledge sign 3, the edge, row {i} (line 4)", e, p)
line(f"{K}antechamber.catch", "ante", "ACTIONBAR", "the first fall caught (line 6)",
     "The moths caught you. Next time you're on your own.",
     "As mariposas te pegaram. Da próxima vez, é por sua conta.")
line(f"{K}antechamber.perch", "ante", "ACTIONBAR", "the void perch at Squire (line 8)",
     "The moths let nobody fall into the void. Yet.", "As mariposas não deixam ninguém cair no vazio. Por enquanto.")
line(f"{K}antechamber.ranked_arrival", "ante", "CHAT", "a ranked first arrival in Chaos, sign 1 in chat (line 9)",
     "A sign on the wind: \"You're early. Go home. The moths will wait.\"",
     "Uma placa no vento: \"Chegou cedo. Volta pra casa. As mariposas esperam.\"")
line(f"{K}antechamber.jade.door", "ante", "ITEM", "Jade over an OreSpawn butterfly or luna moth, below Squire",
     "door: Antechamber", "porta: Antessala")
line(f"{K}antechamber.jade.door_ranked", "ante", "ITEM", "Jade over an OreSpawn butterfly or luna moth, from Squire",
     "door: open Chaos (7) · sneaking: your ledge", "porta: Caos aberto (7) · agachado: sua beirada")
line("advancements.orespawn_integrations.wonders.the_antechamber.title", "ante", "ADV",
     "Wonder 4, the first ledge landing (line 13)", "The Antechamber", "A Antessala do Caos")
line("advancements.orespawn_integrations.wonders.the_antechamber.description", "ante", "ADV",
     "Wonder 4, the first ledge landing (line 14)",
     "Follow a butterfly to a blood-red sky and read the sign telling you to go home.",
     "Siga uma borboleta até um céu vermelho-sangue e leia a placa mandando você voltar pra casa.")

# Jade: the colonies (item-court-post-and-doors lines 2-4, 10).
line(f"{K}colony.black_ant", "colony", "ITEM", "Jade over orespawn:ant_block (line 2)",
     "Black Ant colony: road to the Garden Palace, a post province.",
     "Colônia de Formiga Preta: estrada pro Palácio-Jardim, província do correio.")
line(f"{K}colony.termite", "colony", "ITEM", "Jade over orespawn:termite_block (line 3)",
     "Termite colony: road to Crystal. Nothing in your pockets.",
     "Colônia de Cupim: estrada pra Tesouraria de Cristal. Nada no bolso.")
line(f"{K}colony.pattern", "colony", "ITEM", "Jade over the other colonies; %1$s the colony, %2$s the province (line 4)",
     "%1$s colony: road to %2$s, a post province.", "Colônia de %1$s: estrada para %2$s, província do correio.")
for slug, ce, cp, pe, pp in [("red_ant", "Red Ant", "Formiga Vermelha", "the Royal Mines", "as Minas Reais"),
                             ("rainbow_ant", "Rainbow Ant", "Formiga Arco-Íris", "the Vassal Kingdom", "o Reino Vassalo"),
                             ("unstable_ant", "Unstable Ant", "Formiga Instável", "the Proving Grounds",
                              "o Campo de Provas")]:
    line(f"{K}colony.name.{slug}", "colony", "ITEM", f"colony name for the pattern, {slug} (line 10)", ce, cp)
    line(f"{K}colony.province.{slug}", "colony", "ITEM", f"province name for the pattern, {slug} (line 10)", pe, pp)

# Jade: the Larder (item-the-larder line 5). The four class names have no card line; written fresh from the Field
# Guide's Larder page (line 3: farm eggs, zoo eggs, royal eggs).
line(f"{K}larder.jade", "larder", "ITEM", "Jade over a dried egg block; %1$s the class (line 5)",
     "Larder: %1$s.", "Despensa: %1$s.")
for cls, e, p in [("farm", "farm egg", "ovo de granja"), ("menagerie", "zoo egg", "ovo de zoológico"),
                  ("quarry", "quarry egg", "ovo de caça"), ("royal", "royal egg, sealed", "ovo real, selado")]:
    line(f"{K}larder.class.{cls}", "larder", "ITEM", f"the Larder class name, L·{cls}", e, p)

# Jade: a Register line's origin and day (structure-hatching-ground line 4's note).
line(f"{K}shelf9.register.jade", "shelf9", "ITEM",
     "Jade over a Hatch Register sign, one per row; %1$s the player name, %2$s the origin, %3$s the day",
     "%1$s · %2$s · day %3$s", "%1$s · %2$s · dia %3$s")

FRAG.mkdir(parents=True, exist_ok=True)
for name, obj in (("keys", keys), ("en_us", en_us), ("pt_br", pt_br)):
    write(FRAG / f"world.{name}.json", obj)

for p in problems:
    print(p)
print(f"world data: {len(FAMILIES)} families, {len(keys)} keys, {len(problems)} problems")
sys.exit(1 if problems else 0)
