"""Writes the data group's 0.10.0 files (the First Hour's data lines that no code group owns) and their lang fragments.

  python -I tools/data/first_hour_data.py [--atlas <crazycraft-world-design-claude/atlas/data>]

Every file this script writes is listed in its part below, so a part can be rebuilt on its own and the output stays the
same run to run. The registry dump is read only for the generated tags (the Base decor tag's mod families and the
Unrecorded's spawn list); every other id is written out here by hand and checked by tools/check_data.py.

Parts (cards in bible/cards):
  twin      wonder-gemeo-da-arvore   the 23 log tables, their loot modifiers, the block tag, the pickup helper
  canopy    system-extravio          the canopy pool's rank-0 table, its tokens and their functions
  tree      system-advancement-tree  the eight tab roots, the tab-full helpers, the 0.10.0 nodes, the reward functions
  doors     system-portas-rasgadas   the witch huts' lamps and the two door advancements
  night     system-night-rebalance   the Unrecorded kept to the overworld and Chaos, Utopia quiet, the islands moored
  prey      item-lands-wildlife-goods the Land predators' new prey
  decor     item-builders-yard       the Base #manor_decor block tag
  guide     item-guia-de-campo       the Hoard Field Guide (Patchouli) and its staples
  notebook  item-caderneta-de-campo  the Field Notebook (Patchouli)
"""
import itertools
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src" / "main" / "resources"
DATA = RES / "data"
OI = DATA / "orespawn_integrations"
ASSETS = RES / "assets" / "orespawn_integrations"
FRAG = ROOT / "_lang_fragments" / "0.10.0"
NS = "orespawn_integrations"

# Lang lines gathered while writing: key -> (card id, SURFACE, where it shows, en_us, pt_br).
LANG = {}


def lang(key, card, surface, where, en, pt):
    LANG[key] = (card, surface, where, en, pt)
    return key


def tr(key):
    return {"translate": key}


def write(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    text = obj if isinstance(obj, str) else json.dumps(obj, indent=2, ensure_ascii=False) + "\n"
    path.write_text(text, encoding="utf-8", newline="\n")


def mod_loaded(*mods):
    return [{"type": "neoforge:mod_loaded", "modid": m} for m in mods]


def score_check(objective, lo=None, hi=None):
    rng = {}
    if lo is not None:
        rng["min"] = lo
    if hi is not None:
        rng["max"] = hi
    return {"condition": "minecraft:value_check",
            "value": {"type": "minecraft:score", "target": "this", "score": objective},
            "range": rng}


PLAYER_BREAKER = {"condition": "minecraft:entity_properties", "entity": "this",
                  "predicate": {"type": "minecraft:player"}}

# ----------------------------------------------------------------------------------------------------------------------
# twin: wonder-gemeo-da-arvore
# ----------------------------------------------------------------------------------------------------------------------

# The 23 logs of #orespawn_integrations:arvores_do_lote: the 19 of #minecraft:overworld_natural_logs (atlas/data/tags.json)
# plus the four Distant Lands trunks the card names. Every block table below is in atlas/data/loot_tables.json.
LOGS = [
    "minecraft:acacia_log", "minecraft:birch_log", "minecraft:oak_log", "minecraft:jungle_log", "minecraft:spruce_log",
    "minecraft:dark_oak_log", "minecraft:mangrove_log", "minecraft:cherry_log",
    "biomesoplenty:fir_log", "biomesoplenty:pine_log", "biomesoplenty:maple_log", "biomesoplenty:redwood_log",
    "biomesoplenty:mahogany_log", "biomesoplenty:jacaranda_log", "biomesoplenty:palm_log", "biomesoplenty:willow_log",
    "biomesoplenty:dead_log", "biomesoplenty:magic_log", "biomesoplenty:umbran_log",
    "distantlandsmc:jacaranda_log", "distantlandsmc:pau_brasil_log", "distantlandsmc:ipe_amarelo_log",
    "distantlandsmc:haru_log",
]
EGG = "brasilcoisas:ovinho_vivo_block"
CARD_TWIN = "wonder-gemeo-da-arvore"
CARD_COPA = "system-extravio"

LORE_1 = lang("orespawn_integrations.gemeo.lore.1", CARD_TWIN, "ITEM", "the first-log egg's lore, line 1",
              "Eggs, assorted, 1 lot. Misfiled: entered as a 'tree'.",
              "Ovos, sortidos, 1 lote. Extraviado: lançado como 'árvore'.")
LORE_2 = lang("orespawn_integrations.gemeo.lore.2", CARD_TWIN, "ITEM", "the first-log egg's lore, line 2",
              "Set it down and tap it. Then tap it again.",
              "Põe no chão e dá um toque. Depois, mais um.")


def gray(key):
    return {"translate": key, "color": "gray", "italic": False}


def egg_entry(log_id):
    return {
        "type": "minecraft:item",
        "name": EGG,
        "functions": [
            {"function": "minecraft:set_lore", "mode": "replace_all", "lore": [gray(LORE_1), gray(LORE_2)]},
            {"function": "minecraft:set_custom_data",
             "tag": '{"orespawn_integrations:lote":{log:"%s"}}' % log_id},
        ],
    }


def log_slug(log_id):
    ns, path = log_id.split(":")
    return f"{ns}/{path}"


def part_twin():
    write(OI / "tags" / "block" / "arvores_do_lote.json", {
        "replace": False,
        "values": [l if l.startswith("minecraft:") else {"id": l, "required": False} for l in LOGS],
    })
    for log_id in LOGS:
        slug = log_slug(log_id)
        ns, path = log_id.split(":")
        # The twin pool first, then the canopy; the score gates live on the pools, never on the modifier.
        write(OI / "loot_table" / "arvore" / f"{slug}.json", {
            "neoforge:conditions": mod_loaded("brasilcoisas"),
            "type": "minecraft:block",
            "pools": [
                {"rolls": 1, "bonus_rolls": 0,
                 "conditions": [PLAYER_BREAKER, score_check("cc_gemeo", hi=0)],
                 "entries": [egg_entry(log_id)]},
                {"rolls": 1, "bonus_rolls": 0,
                 "conditions": [PLAYER_BREAKER, score_check("cc_gemeo", lo=1), score_check("cc_copa", hi=2),
                                {"condition": "minecraft:random_chance", "chance": 1 / 24}],
                 "entries": [{"type": "minecraft:loot_table", "value": f"{NS}:copa/rank0"}]},
            ],
        })
        write(OI / "loot_modifiers" / "arvore" / f"{slug}.json", {
            "neoforge:conditions": mod_loaded("brasilcoisas"),
            "type": "neoforge:add_table",
            "conditions": [
                {"condition": "neoforge:loot_table_id", "loot_table_id": f"{ns}:blocks/{path}"},
                PLAYER_BREAKER,
            ],
            "table": f"{NS}:arvore/{slug}",
        })
    # The same egg for the First Fall crate's ITEM 5 (event-first-fall): it carries the lote key, so picking it up runs
    # the pickup helper and stops the logs dropping a second twin. The DNV will read the crate as where it was found.
    write(OI / "loot_table" / "gemeo" / "ovo_do_caixote.json", {
        "neoforge:conditions": mod_loaded("brasilcoisas"),
        "type": "minecraft:gift",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [egg_entry("ironchest:copper_chest")]}],
    })
    write(OI / "advancement" / "livro" / "lote_pickup.json", {
        "neoforge:conditions": mod_loaded("brasilcoisas"),
        "criteria": {"lote": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [
            {"items": EGG, "predicates": {"minecraft:custom_data": '{"orespawn_integrations:lote":{}}'}}]}}},
        "rewards": {"function": f"{NS}:wonder/lote_pickup"},
    })
    write(OI / "function" / "wonder" / "lote_pickup.mcfunction", "\n".join([
        "# The first lote egg picked up (wonder-gemeo-da-arvore): no log drops another twin from now on, and the log it",
        "# fell from is kept per player for the late registration's DNV (0.17.0). Run once per player by livro/lote_pickup.",
        "scoreboard objectives add cc_gemeo dummy",
        "scoreboard players set @s cc_gemeo 1",
        "playsound minecraft:entity.turtle.egg_crack player @a[distance=..16] ~ ~ ~ 1 1",
        "particle minecraft:poof ~ ~0.5 ~ 0.3 0.3 0.3 0.02 6",
        "data remove storage orespawn_integrations:tmp lote",
        "data modify storage orespawn_integrations:tmp lote.uuid set from entity @s UUID",
        'data modify storage orespawn_integrations:tmp lote.log set from entity @s Inventory[{components:{"minecraft:custom_data":{"orespawn_integrations:lote":{}}}}].components."minecraft:custom_data"."orespawn_integrations:lote".log',
        "data modify storage orespawn_integrations:players lote append from storage orespawn_integrations:tmp lote",
        "data remove storage orespawn_integrations:tmp lote",
        "",
    ]))
    write(OI / "function" / "wonder" / "grant_r1.mcfunction", "\n".join([
        "# R1, Why is nothing where it belongs? (system-advancement-tree): granted by Finders Keepers and by the Field Guide's",
        "# first opening; granting an advancement a player already has does nothing.",
        "advancement grant @s only orespawn_integrations:livro/r1",
        "",
    ]))


# ----------------------------------------------------------------------------------------------------------------------
# canopy: system-extravio (0.10.0 rows only: rank 0, at most 3 finds a game day, no Mimic, no Leaf Monster, no creature)
# ----------------------------------------------------------------------------------------------------------------------

TOKEN = "minecraft:paper"
# Row -> weight in copa/rank0. The creature (30), stray (5) and parcel (3) rows ship later, so their weight goes to the
# casket as the card says: 18 + 30 + 5 + 3 = 56.
COPA_ROWS = [("arca", 56), ("chapeu", 14), ("gaveta", 12), ("retalho", 9), ("presente", 6), ("parte", 3)]
COPA_LINES = {
    "arca": ("A casket, stuck in the canopy since the night it rained junk.",
             "Uma arca enganchada na copa desde a Noite da Tralha."),
    "chapeu": ("A hat fell out of the tree, straight into your Wardrobe. It wasn't yours. It is now.",
               "Caiu um chapéu do galho, direto pro seu Guarda-Roupa. Não era seu. Agora é."),
    "gaveta": ("That doesn't grow on trees. It isn't even from this world.",
               "Isso não é de árvore. Isso não é nem deste mundo."),
    "retalho": ("A scrap of the Ledger, tangled in the leaves.",
                "Um retalho do Livro-Razão, enroscado na folhagem."),
    "presente": ("The Queen's mail, caught on a branch. Refused, of course.",
                 "Correspondência da Rainha, presa num galho. Recusada, claro."),
    "parte": ("A piece of King fell out. About a ninth.",
              "Caiu um pedaço de Rei. Um nono, mais ou menos."),
}
COPA_WHERE = {"arca": "casket", "chapeu": "hat", "gaveta": "Wrong Drawer item", "retalho": "Ledger Scrap",
              "presente": "Refused Gift", "parte": "royal egg part"}
DRAWER = ["decocraft:crt_tv", "decocraft:globe", "decocraft:jukebox", "refurbished_furniture:dark_toaster",
          "bibliocraft:typewriter", "farmersrespite:black_tea", "culturaldelights:avocado_toast",
          "cookscollection:lemonade"]
# One royal egg part at the casket jackpot's 4/3/2 (wonder-ninth-of-a-king).
PARTS = [("orespawn:the_king_part_spawn_block", 4), ("orespawn:the_queen_part_spawn_block", 3),
         ("orespawn:godzilla_part_spawn_block", 2)]


def token_predicate(row):
    return 'minecraft:paper[minecraft:custom_data~{"orespawn_integrations:copa":{row:"%s"}}]' % row


def part_canopy():
    token_name = lang("orespawn_integrations.extravio.copa.token", CARD_COPA, "ITEM",
                      "the canopy find's wrapper, seen for a tick on pickup (fresh)",
                      "Misfiled", "Extraviado")
    entries = []
    for row, weight in COPA_ROWS:
        entries.append({
            "type": "minecraft:item", "name": TOKEN, "weight": weight,
            "functions": [
                {"function": "minecraft:set_custom_data", "tag": '{"orespawn_integrations:copa":{row:"%s"}}' % row},
                {"function": "minecraft:set_name", "target": "item_name", "name": tr(token_name)},
            ],
        })
    write(OI / "loot_table" / "copa" / "rank0.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": entries}],
    })
    write(OI / "loot_table" / "copa" / "gaveta.json", {
        "type": "minecraft:gift",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": i, "weight": 1} for i in DRAWER]}],
    })
    write(OI / "loot_table" / "copa" / "parte.json", {
        "type": "minecraft:gift",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "entries": [{"type": "minecraft:item", "name": i, "weight": w} for i, w in PARTS]}],
    })
    write(OI / "advancement" / "extravio" / "copa.json", {
        "criteria": {"token": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [
            {"items": TOKEN, "predicates": {"minecraft:custom_data": '{"orespawn_integrations:copa":{}}'}}]}}},
        "rewards": {"function": f"{NS}:extravio/copa/recebido"},
    })
    lines = [
        "# A canopy find reached its breaker's inventory (system-extravio): the token turns into what it stands for. The",
        "# advancement is re-armed at once, so every later token fires it again. One token is handled a pass; a stack of",
        "# two runs this again until none is left.",
        "advancement revoke @s only orespawn_integrations:extravio/copa",
        "scoreboard objectives add cc_copa dummy",
    ]
    for row, _ in COPA_ROWS:
        lines.append(f"execute if items entity @s container.* {token_predicate(row)} run return run function "
                     f"orespawn_integrations:extravio/copa/{row}")
    lines.append("")
    write(OI / "function" / "extravio" / "copa" / "recebido.mcfunction", "\n".join(lines))
    for row, _ in COPA_ROWS:
        key = lang(f"orespawn_integrations.extravio.copa.{row}", CARD_COPA, "ACTIONBAR",
                   f"a canopy find, the {COPA_WHERE[row]} row", *COPA_LINES[row])
        write(OI / "function" / "extravio" / "copa" / f"{row}.mcfunction", "\n".join([
            f"# The canopy's {COPA_WHERE[row]} row. The day's cap is checked again here, so tokens left lying about can",
            "# never make a fourth find: one over the cap is only taken away.",
            f"clear @s {token_predicate(row)} 1",
            "execute if score @s cc_copa matches 3.. run return run function orespawn_integrations:extravio/copa/recebido",
            "scoreboard players add @s cc_copa 1",
            f"function orespawn_integrations:extravio/copa/dar/{row}",
            "function orespawn_integrations:extravio/copa/recebido",
            "",
        ]))
        give = {
            "arca": ["loot give @s loot lucky_blocks:lucky_block"],
            "chapeu": ["hats give @s random"],
            "gaveta": ["loot give @s loot orespawn_integrations:copa/gaveta"],
            "presente": ["give @s pandorasbox:pandoras_box 1"],
            "parte": ["loot give @s loot orespawn_integrations:copa/parte"],
            "retalho": [
                "# A canopy scrap counts as a kite scrap (cap 10, item-ledger-scrap); past the cap, or with scraps off,",
                "# the find is a casket instead.",
                "execute unless score #retalhos cc_cfg matches 1 run return run function orespawn_integrations:extravio/copa/dar/arca",
                "execute if score @s cc_scrap_kite matches 10.. run return run function orespawn_integrations:extravio/copa/dar/arca",
                "function orespawn_integrations:retalho/kite",
            ],
        }[row]
        write(OI / "function" / "extravio" / "copa" / "dar" / f"{row}.mcfunction", "\n".join(
            [f"# What the {COPA_WHERE[row]} row gives, with its rustle and its line."] + give + [
                "playsound minecraft:block.azalea_leaves.break player @s ~ ~ ~ 1 1",
                "title @s actionbar " + json.dumps(tr(key)),
                "",
            ]))


# ----------------------------------------------------------------------------------------------------------------------
# tree: system-advancement-tree
# ----------------------------------------------------------------------------------------------------------------------

CARD_TREE = "system-advancement-tree"


def adv_keys(path, card, title, desc):
    base = "advancements.orespawn_integrations." + path.replace("/", ".")
    t = lang(base + ".title", card, "ADV", f"advancement {path}, title", *title)
    d = lang(base + ".description", card, "ADV", f"advancement {path}, description", *desc)
    return t, d


def advancement(path, card, icon, title, desc, criteria, frame="task", parent=None, background=None, hidden=False,
                xp=None, function=None, mods=(), announce=False, requirements=None):
    t, d = adv_keys(path, card, title, desc)
    display = {"icon": {"id": icon}, "title": tr(t), "description": tr(d), "frame": frame, "show_toast": True,
               "announce_to_chat": announce, "hidden": hidden}
    if background:
        display["background"] = background
    obj = {}
    if mods:
        obj["neoforge:conditions"] = mod_loaded(*mods)
    if parent:
        obj["parent"] = f"{NS}:{parent}"
    obj["display"] = display
    obj["criteria"] = criteria
    if requirements:
        obj["requirements"] = requirements
    rewards = {}
    if xp:
        rewards["experience"] = xp
    if function:
        rewards["function"] = function
    if rewards:
        obj["rewards"] = rewards
    write(OI / "advancement" / f"{path}.json", obj)


IMPOSSIBLE = {"granted": {"trigger": "minecraft:impossible"}}


def hidden_helper(path, parent=None, criteria=None, function=None, mods=()):
    obj = {}
    if mods:
        obj["neoforge:conditions"] = mod_loaded(*mods)
    if parent:
        obj["parent"] = f"{NS}:{parent}"
    obj["criteria"] = criteria or IMPOSSIBLE
    if function:
        obj["rewards"] = {"function": function}
    write(OI / "advancement" / f"{path}.json", obj)


def tex(name):
    return f"minecraft:textures/block/{name}.png"


def part_tree():
    # The eight roots, exactly as the card's roots table fixes them (id, background, trigger, reward). Two root
    # descriptions on the card are two sentences; the spine allows one, so their two halves are joined with a colon or
    # a semicolon and nothing else changes.
    advancement("wonder/2", CARD_TREE, "brasilcoisas:cadeira_brahma",
                ("The Royal Summons", "A Convocação Real"),
                ("A royal dragon dropped a letter at your feet, and it knew what you were.",
                 "Um dragão real largou uma carta no seu pé, e sabia muito bem o que você era."),
                IMPOSSIBLE, frame="challenge", background=tex("gilded_blackstone"), xp=10, mods=("brasilcoisas",),
                announce=True)
    advancement("wonder/1", CARD_TWIN, "orespawn_integrations:ledger_scrap",
                ("Finders Keepers", "Achado Não É Roubado"),
                ("Filed under 'tree': finders keepers, and it found you.",
                 "Lançado como 'árvore': achado não é roubado, e quem achou foi ele."),
                {"tamed": {"trigger": "minecraft:tame_animal", "conditions": {"entity": [
                    {"condition": "minecraft:entity_properties", "entity": "this",
                     "predicate": {"type": "brasilcoisas:ovinho_vivo"}}]}}},
                frame="challenge", background=tex("bookshelf"), xp=10, function=f"{NS}:wonder/grant_r1",
                mods=("brasilcoisas",), announce=True)
    advancement("week/root", CARD_TREE, "braziliandelight:cheese_bread",
                ("Nothing Happens on Tuesday", "Terça Não Acontece Nada"),
                ("Wake to your first day name; the Land keeps a schedule.",
                 "Acorde com o nome do seu primeiro dia: a Terra tem horário pra tudo."),
                IMPOSSIBLE, background=tex("hay_block_side"), xp=5, mods=("braziliandelight",))
    advancement("hunt/root", CARD_TREE, "monster_hunter_villager:hunters_table",
                ("On the Board", "Tá no Mural"),
                ("Take your first Monday bounty: place, target, price.",
                 "Pegue sua primeira caçada de segunda: lugar, alvo, preço."),
                IMPOSSIBLE, frame="goal", background=tex("spruce_planks"), xp=10, mods=("monster_hunter_villager",))
    # The Parish Stone (the card's icon) is registered with item-marco-da-freguesia in 0.10.1; until then the root shows
    # the bed it is about.
    advancement("parish/root", CARD_TREE, "minecraft:red_bed",
                ("Home Is Where the Bed Is", "Onde Eu Deito É Casa"),
                ("Sleep in a bed that will have you back.", "Durma numa cama que aceita você de volta."),
                {"slept": {"trigger": "minecraft:slept_in_bed"}}, background=tex("bricks"), xp=5)
    advancement("property/root", CARD_TREE, "orespawn:elevator",
                ("Signed For", "Assinou a Cautela"),
                ("Sign out your first piece of Treasury property; it is still theirs.",
                 "Retire seu primeiro patrimônio da Tesouraria; continua sendo deles, viu?"),
                IMPOSSIBLE, background=tex("iron_block"), xp=10, mods=("orespawn",))
    advancement("causos/root", CARD_TREE, "brazil_legends:cachimbo",
                ("Made to Order", "Sob Encomenda"),
                ("Open your first commissioned tale; the Land tells it bigger.",
                 "Abra seu primeiro causo de encomenda; a Terra conta maior do que foi."),
                IMPOSSIBLE, frame="goal", background=tex("dark_oak_planks"), xp=5, mods=("brazil_legends",))
    advancement("secrets/root", CARD_TREE, "minecraft:spyglass",
                ("Between You and the Land", "Cá Entre Nós e a Terra"),
                ("Find something the Land never told anyone.", "Ache uma coisa que a Terra nunca contou pra ninguém."),
                IMPOSSIBLE, frame="challenge", background=tex("mossy_cobblestone"), hidden=True, xp=5)

    # One hidden tab-full helper per tab, on the impossible trigger: the AdvancementEarnEvent handler grants it when
    # every node of its tab is done or switched off; the epithet it pays reaches the deed log in 0.10.1.
    for folder, root in [("court", "wonder/2"), ("livro", "wonder/1"), ("week", "week/root"), ("hunt", "hunt/root"),
                         ("parish", "parish/root"), ("property", "property/root"), ("causos", "causos/root"),
                         ("secrets", "secrets/root")]:
        hidden_helper(f"{folder}/tab_full", parent=root)

    # The reveals granted in 0.10.0 (tab 2, tasks, hidden until granted): R1 (Finders Keepers, the Guide, any Odete),
    # R2 (the Field Odete), R4 (the Summons' use), R5 (the First Fall crate), R6 (the Antechamber's ledge), R21 (the
    # first copy at a desk). Ids are the card's livro/r<n>.
    reveals = {
        1: (("Why is nothing where it belongs?", "Por que nada tá no lugar?"),
            ("The Ledger is torn lengthwise.", "O Livro-Razão rasgou de comprido.")),
        2: (("What am I?", "O que eu sou?"),
            ("A Hatchling, logged as eggs, assorted, 1 lot.", "Um Filhote, lançado como ovos, sortidos, 1 lote.")),
        4: (("Where are the King and Queen?", "Cadê o Rei e a Rainha?"),
            ("Asleep in their royal trees since the Spill.", "Dormindo nas árvores reais desde o Derramamento.")),
        5: (("Why do things fall from the sky?", "Por que cai coisa do céu?"),
            ("Utopia's treasury cracked, and the Hoard is still falling.",
             "O tesouro de Utopia rachou, e o Tesouro ainda tá caindo.")),
        6: (("Why are the nights full of horrors?", "Por que a noite é cheia de assombração?"),
            ("The Unrecorded climb the moth roads from the oubliette.",
             "Os Sem-Registro sobem do Caos pelas estradas das mariposas.")),
        21: (("Where did the Treasurer go?", "Pra onde foi o Tesoureiro?"),
             ("One day ahead, with the Royal Clock: every come-back-tomorrow is his address.",
              "Um dia na frente, com o Relógio Real: todo volte-amanhã é o endereço dele.")),
    }
    for n, (title, desc) in reveals.items():
        advancement(f"livro/r{n}", CARD_TREE, "orespawn_integrations:ledger_scrap", title, desc, IMPOSSIBLE,
                    parent="wonder/1", hidden=True, xp=5)

    # Nodes of 0.10.0 that other cards promised and no code group writes.
    advancement("livro/keep_the_change", "event-first-fall", "orespawn:coin_spawn_egg",
                ("Keep the Change", "Fica com o Troco"),
                ("Punch a coin out of the sky's loose change.", "Soque uma moeda do troco que caiu do céu."),
                IMPOSSIBLE, parent="wonder/1", xp=10, mods=("orespawn",))
    advancement("wonder/5", "wonder-nascido-vivo", "orespawn_integrations:guia_de_averbacao",
                ("Born Alive", "Nascido(a) Vivo(a)"),
                ("The whole village hid from you because you were an egg, and the midwife put your twin on your birth form.",
                 "A vila inteira correu pra dentro porque você era um ovo, e a parteira ainda botou o seu gêmeo na sua Declaração de Nascido Vivo."),
                {"slip": {"trigger": "minecraft:inventory_changed",
                          "conditions": {"items": [{"items": "orespawn_integrations:guia_de_averbacao"}]}}},
                frame="challenge", parent="wonder/1", xp=10, announce=True)
    advancement("wonder/17", "wonder-ant-post", "orespawn:red_ant_spawn_egg",
                ("The Ant Post", "O Correio das Formigas"),
                ("Shake hands with an ant and wake up in the King's mines.",
                 "Aperta a mão de uma formiga e acorda nas minas do Rei."),
                {"mines": {"trigger": "minecraft:changed_dimension", "conditions": {"to": "orespawn:mining"}}},
                frame="challenge", parent="wonder/2", xp=10, mods=("orespawn",), announce=True)
    # Perda Total is granted by a dismount check on orespawn:elevator (mob-patrimonio-que-anda); the node is here so the
    # handler has something to grant.
    advancement("property/perda_total", "mob-patrimonio-que-anda", "orespawn:elevator",
                ("Total Loss", "Perda Total"),
                ("Crash the Treasury's Hoverboard into anything.", "Bata o Hoverboard da Tesouraria em qualquer coisa."),
                IMPOSSIBLE, parent="property/root", xp=5, mods=("orespawn",))

    # The reward functions every later node pays from (rule 2). Reais wait for the rate (0.10.3).
    for n in (5, 10, 25, 50):
        write(OI / "function" / "advancement" / "reward" / f"xp{n}.mcfunction",
              f"# Rule 2's {n} XP reward (system-advancement-tree).\nxp add @s {n} points\n")
    write(OI / "function" / "advancement" / "reward" / "scrap.mcfunction", "\n".join([
        "# Rule 2's one Ledger Scrap, for a node that opens a road (at most one per branch, system-advancement-tree).",
        "execute unless score #retalhos cc_cfg matches 1 run return fail",
        "give @s orespawn_integrations:ledger_scrap 1",
        "",
    ]))


# ----------------------------------------------------------------------------------------------------------------------
# doors: system-portas-rasgadas
# ----------------------------------------------------------------------------------------------------------------------

CARD_DOORS = "system-portas-rasgadas"


def part_doors():
    write(OI / "loot_table" / "portas" / "witch_hut_lamps.json", {
        "neoforge:conditions": mod_loaded("betterwitchhuts"),
        "type": "minecraft:chest",
        "pools": [{"rolls": 1, "bonus_rolls": 0,
                   "conditions": [{"condition": "minecraft:random_chance", "chance": 0.5}],
                   "entries": [{"type": "minecraft:item", "name": "minecraft:glowstone", "functions": [
                       {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2},
                        "add": False}]}]}],
    })
    write(OI / "loot_modifiers" / "witch_hut_lamps.json", {
        "neoforge:conditions": mod_loaded("betterwitchhuts"),
        "type": "neoforge:add_table",
        "conditions": [{"condition": "neoforge:loot_table_id", "loot_table_id": "betterwitchhuts:chests/hut_0"}],
        "table": f"{NS}:portas/witch_hut_lamps",
    })
    # Both card descriptions are two sentences; joined with a semicolon for the spine's one-sentence rule.
    advancement("gambiarra/luz_de_bruxa", CARD_DOORS, "minecraft:glowstone",
                ("Witch Light", "Luz de Bruxa"),
                ("Four dust make a block, and the dust is usually a witch's; the clouds take it for a frame.",
                 "Quatro pós fazem uma pedra, e o pó quase sempre é de bruxa; as nuvens aceitam de moldura."),
                {"crafted": {"trigger": "minecraft:recipe_crafted", "conditions": {"recipe_id": "minecraft:glowstone"}}},
                frame="challenge", parent="wonder/1", xp=10)
    advancement("gambiarra/enxoval_da_rainha", CARD_DOORS, "minecraft:bee_nest",
                ("The Queen's Trousseau", "O Enxoval da Rainha"),
                ("Got yourself a hive; now throw a pearl at it and the Queen's sister will answer.",
                 "Arrumou uma colmeia; agora joga uma pérola nela, que a irmã da Rainha atende."),
                {"hive": {"trigger": "minecraft:inventory_changed",
                          "conditions": {"items": [{"items": ["minecraft:bee_nest", "minecraft:beehive"]}]}}},
                frame="challenge", parent="wonder/1", xp=10)


# ----------------------------------------------------------------------------------------------------------------------
# night: system-night-rebalance (and mob-patrimonio-que-anda's island lines)
# ----------------------------------------------------------------------------------------------------------------------

BUMBLEZONE = ["the_bumblezone:bumbling_beepartments", "the_bumblezone:crystal_canyon", "the_bumblezone:floral_meadow",
              "the_bumblezone:hive_pillar", "the_bumblezone:hive_wall", "the_bumblezone:howling_constructs",
              "the_bumblezone:pollinated_fields", "the_bumblezone:pollinated_pillar", "the_bumblezone:sugar_water_floor"]


def unrecorded_types(atlas: Path):
    """The entity of every Born in Chaos add-spawns biome modifier (atlas/data/spawns.json): the Unrecorded's list."""
    spawns = json.loads((atlas / "spawns.json").read_text(encoding="utf-8"))["spawns"]["biome_modifier_spawns"]
    types = set()
    for m in spawns:
        if m.get("source", "").startswith("born_in_chaos") and m.get("type") == "neoforge:add_spawns":
            for s in m.get("spawners", []):
                types.add(s.get("entity") or s.get("type"))
    return sorted(types)


def remove_spawns(name, biomes, entities, mods):
    write(OI / "neoforge" / "biome_modifier" / "night" / f"{name}.json", {
        "neoforge:conditions": mod_loaded(*mods),
        "type": "neoforge:remove_spawns",
        "biomes": biomes,
        "entity_types": entities,
    })


def part_night(atlas: Path):
    write(OI / "tags" / "entity_type" / "unrecorded.json", {
        "replace": False,
        "values": [{"id": t, "required": False} for t in unrecorded_types(atlas)],
    })
    remove_spawns("unrecorded_out_of_realms",
                  {"type": "neoforge:or",
                   "values": ["#minecraft:is_nether", "#twilightforest:in_twilight_forest", BUMBLEZONE]},
                  "#orespawn_integrations:unrecorded", ("born_in_chaos_v1", "twilightforest", "the_bumblezone"))
    remove_spawns("unrecorded_out_of_orespawn",
                  ["orespawn:mining_biome", "orespawn:village_biome", "orespawn:crystal_plains",
                   "orespawn:island_biome", "orespawn:utopia_plains"],
                  "#orespawn_integrations:unrecorded", ("born_in_chaos_v1", "orespawn"))
    remove_spawns("utopia_no_monsters", "orespawn:utopia_plains",
                  ["minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:spider",
                   "minecraft:enderman", "minecraft:witch", "minecraft:slime"], ("orespawn",))
    remove_spawns("mestre_overworld_only",
                  {"type": "neoforge:or", "values": ["#twilightforest:in_twilight_forest", BUMBLEZONE]},
                  "brasilcoisas:mestre_ensinador", ("brasilcoisas", "twilightforest", "the_bumblezone"))
    remove_spawns("islands_moored", "orespawn:island_biome", ["orespawn:island", "orespawn:island_too"],
                  ("orespawn",))
    stale = DATA / "orespawn" / "worldgen" / "biome" / "utopia_plains.json"
    if stale.exists():
        stale.unlink()
    done = lang("orespawn_integrations.ops.encalhar_ilhas.done", "system-night-rebalance", "CHAT",
                "operator, after ops/encalhar_ilhas; %s = islands removed (fresh, roadmap 4.7)",
                "Islands beached: %s. Their blocks stay where they are.",
                "Ilhas encalhadas: %s. Os blocos ficam onde estão.")
    write(OI / "function" / "ops" / "encalhar_ilhas.mcfunction", "\n".join([
        "# Operator only, run by hand and never scheduled (system-night-rebalance, mob-patrimonio-que-anda): removes every",
        "# loaded OreSpawn island entity and leaves the island's blocks where they are.",
        "execute store result storage orespawn_integrations:tmp ilhas int 1 if entity @e[type=orespawn:island]",
        "execute store result storage orespawn_integrations:tmp ilhas_too int 1 if entity @e[type=orespawn:island_too]",
        "kill @e[type=orespawn:island]",
        "kill @e[type=orespawn:island_too]",
        "tellraw @s " + json.dumps({"translate": done, "with": [
            {"nbt": "ilhas", "storage": "orespawn_integrations:tmp"}]}, ensure_ascii=False),
        "tellraw @s " + json.dumps({"translate": done, "with": [
            {"nbt": "ilhas_too", "storage": "orespawn_integrations:tmp"}]}, ensure_ascii=False),
        "",
    ]))


def part_prey():
    tags = DATA / "naturalist" / "tags" / "entity_type"
    prey = {
        "lion_hostiles": ["orespawn:scorpion"],
        "tiger_hostiles": ["orespawn:stink_bug", "orespawn:mantis"],
        "alligator_hostiles": ["orespawn:frog"],
        "bear_hostiles": ["orespawn:beaver"],
    }
    for name, ids in prey.items():
        write(tags / f"{name}.json", {"replace": False, "values": [{"id": i, "required": False} for i in ids]})


def part_decor(atlas: Path):
    dump = json.loads((atlas / "registry_dump.json").read_text(encoding="utf-8"))
    blocks = sorted(b["id"] for b in dump["blocks"]["entries"])
    colours = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
               "purple", "blue", "brown", "green", "red", "black"]
    vanilla = ["#minecraft:wool_carpets", "minecraft:moss_carpet", "#minecraft:banners", "#minecraft:candles",
               "#minecraft:flower_pots", "minecraft:lantern", "minecraft:soul_lantern", "#minecraft:all_signs"]
    vanilla += [f"minecraft:{c}_stained_glass" for c in colours]
    vanilla += [f"minecraft:{c}_stained_glass_pane" for c in colours]
    vanilla += [f"minecraft:{c}_glazed_terracotta" for c in colours]
    vanilla += ["minecraft:chain", "minecraft:decorated_pot", "minecraft:bookshelf", "minecraft:chiseled_bookshelf"]
    mods = ("bibliocraft", "refurbished_furniture", "framedblocks", "malisisdoorsreborn", "rechiseled")
    modded = [{"id": b, "required": False} for b in blocks if b.split(":")[0] in mods]
    write(OI / "tags" / "block" / "manor_decor.json", {"replace": False, "values": vanilla + modded})


# ----------------------------------------------------------------------------------------------------------------------
# guide: item-guia-de-campo (Patchouli, i18n: every text field is a lang key)
# ----------------------------------------------------------------------------------------------------------------------

CARD_GUIA = "item-guia-de-campo"
PAGE_CHARS = 240  # about what one Patchouli text page holds at the default scale


def sentences(text):
    return [s for s in re.split(r"(?<=[.!?])\s+", text.strip()) if s]


def chunks(text, n):
    """Splits text into n pages at sentence ends, keeping the longest page as short as the sentences allow."""
    parts = sentences(text)
    if n == 1:
        return [text]
    if len(parts) < n:
        return None
    best = None
    for cuts in itertools.combinations(range(1, len(parts)), n - 1):
        bounds = (0,) + cuts + (len(parts),)
        pages = [" ".join(parts[bounds[i]:bounds[i + 1]]) for i in range(n)]
        if best is None or max(map(len, pages)) < max(map(len, best)):
            best = pages
    return best


def split_lang(base, card, surface, where, en, pt):
    """One lang key per Patchouli page: the card's line split at sentence ends, the same number of pages per language,
    as few pages as keep every page under PAGE_CHARS (or as many as the sentences allow)."""
    n = 1
    while True:
        a, b = chunks(en, n + 1), chunks(pt, n + 1)
        if max(map(len, chunks(en, n) + chunks(pt, n))) <= PAGE_CHARS or not (a and b):
            break
        n += 1
    a, b = chunks(en, n), chunks(pt, n)
    if n == 1:
        return [lang(base, card, surface, where, en, pt)]
    return [lang(f"{base}.{i + 1}", card, surface, f"{where} ({i + 1} of {n})", a[i], b[i]) for i in range(n)]


def text_pages(keys, title=None, advancement=None):
    pages = []
    for i, k in enumerate(keys):
        p = {"type": "patchouli:text", "text": k}
        if i == 0 and title:
            p["title"] = title
        if advancement:
            p["advancement"] = advancement
        pages.append(p)
    return pages


def book(book_id, name, landing, subtitle=None, model="patchouli:book_brown",
         texture="patchouli:textures/gui/book_brown.png"):
    obj = {"name": name, "landing_text": landing, "i18n": True, "use_resource_pack": True, "show_progress": False,
           "book_texture": texture, "model": model, "creative_tab": "tools_and_utilities"}
    if subtitle:
        obj["subtitle"] = subtitle
    write(OI / "patchouli_books" / book_id / "book.json", obj)


def entry(book_id, cat, name, icon, pages, sortnum, advancement=None):
    obj = {"name": name, "category": f"{NS}:{cat}", "icon": icon, "sortnum": sortnum, "pages": pages}
    if advancement:
        obj["advancement"] = advancement
        obj["secret"] = True
    return obj


def part_guide():
    g = "orespawn_integrations.guia"
    title = lang(f"{g}.title", CARD_GUIA, "ITEM", "the book's name (line 2)",
                 "The Hoard Field Guide", "Guia de Campo do Tesouro")
    cover = lang(f"{g}.cover", CARD_GUIA, "BOOK", "the cover (lines 3 and 4)",
                 "New pages are stapled in as needed. The management.$(br2)The guide says what it is. The notebook says where you saw it.",
                 "Páginas novas são grampeadas conforme a necessidade. A Gerência.$(br2)O guia diz o que a coisa é. A caderneta diz onde você viu.")
    book("guia_de_campo", title, cover)
    staple = lambda s: f"{NS}:guia/{s}"
    for s in ("p01", "p03", "p07", "p08", "dusk", "close"):
        hidden_helper(f"guia/{s}")
    # Opening the Guide grants R1 (Patchouli's open_book trigger).
    hidden_helper("guia/aberto", criteria={"opened": {"trigger": "patchouli:open_book",
                                                      "conditions": {"book": f"{NS}:guia_de_campo"}}},
                  function=f"{NS}:wonder/grant_r1", mods=("patchouli",))
    names = [("What is all this", "O que é tudo isso"), ("Your batch", "Seu lote"), ("The Wardrobe", "O Guarda-Roupa"),
             ("Doors", "Portas"), ("The Hoard's furniture", "A mobília do Tesouro")]
    nk = [lang(f"{g}.p{i + 1}.name", CARD_GUIA, "BOOK", f"page {i + 1}'s title (line 24)", *names[i]) for i in range(5)]
    base = ASSETS / "patchouli_books" / "guia_de_campo" / "en_us"
    write(base / "categories" / "guia.json", {"name": title, "description": cover, "icon": "patchouli:guide_book",
                                              "sortnum": 0})
    p1 = split_lang(f"{g}.p1.text", CARD_GUIA, "BOOK", "page 1 (line 7)",
                    "The Ledger is torn: nothing is where it was filed. Here, eggs grow on trees, animals wear other people's hats and boxes fall from the sky. This guide says what each thing is, and whose.",
                    "O Livro-Razão rasgou: nada tá onde foi lançado. Aqui, ovo dá em árvore, bicho usa chapéu dos outros e caixa cai do céu. Este guia diz o que cada coisa é, e de quem.")
    p1f = lang(f"{g}.p1.foot", CARD_GUIA, "BOOK", "page 1's foot line (line 8)",
               "Lost? Find the smoke, or climb the highest thing you can see.",
               "Sem rumo? Procure a fumaça, ou suba na coisa mais alta que estiver à vista.")
    p2 = split_lang(f"{g}.p2.text", CARD_GUIA, "BOOK", "page 2 (line 9)",
                    "Eggs, assorted, 1 lot: you weren't the only egg on Shelf 9. The rest of the batch fell wherever the tear filed it: in the Nest's straw, in the stone, in the trees. A batch egg that hatches and follows you is family. Crouch and tap it to pocket it; safer at night. If it dies it turns back into an egg, with a chick for change. Chicks tame with Guaraná.",
                    "Ovos, sortidos, 1 lote: você não era o único ovo da Prateleira 9. O resto do lote caiu onde o rasgo lançou: na palha do Ninho, na pedra, nas árvores. Ovo do lote que choca e segue você é família. Agache e toque nele pra guardar no bolso; de noite é mais seguro. Se morrer, vira ovo de novo, com um pintinho de troco. Pintinho se doma com Guaraná.")
    p2f = lang(f"{g}.p2.foot", CARD_GUIA, "BOOK", "page 2's foot line, stapled at P07 (line 10)",
               "An egg in the stone? Add water: a bucket and a dried egg on the bench make the spawn egg. Three of your siblings are in the Nest's straw.",
               "Ovo na pedra? É só pôr água: um balde e um ovo ressecado na bancada dão o ovo gerador. Três irmãos seus estão na palha do Ninho.")
    p3 = split_lang(f"{g}.p3.text", CARD_GUIA, "BOOK", "page 3 (line 11)",
                    "The Spill night began as a masked ball. The Royal Wardrobe burst, and to this day one creature in three wears a hat that isn't theirs. Field Correction: touch the creature with a Ledger Scrap in hand and the hat goes back to the Wardrobe, which is now you. Open your Wardrobe to wear it. Works on cows, zombies, anything you dare. The rest of what's out of place, two uraniums, two titaniums, a bench corrects with a scrap.",
                    "O Derramamento começou como baile de máscaras. O Guarda-Roupa Real estourou, e até hoje uma criatura em cada três usa um chapéu que não é dela. Emenda de Campo: toque a criatura com um Retalho do Livro-Razão na mão e o chapéu volta pro Guarda-Roupa, que agora é você. Abre o Guarda-Roupa pra vestir. Funciona em vaca, em zumbi, no que você tiver coragem. O resto do que está fora do lugar, dois urânios, dois titânios, a bancada averba com um retalho.")
    # Page 4's bracketed Squire margin is its own page, stapled at Squire (Apotheosis Frontier until ranks open).
    p4 = split_lang(f"{g}.p4.text", CARD_GUIA, "BOOK", "page 4 (line 12, without the Squire margin)",
                    "Doors. An ant, empty hand: the provinces. A butterfly: the pit (too early, go home). A diamond in a flower-ringed pond: the Dusk Court. A glowstone frame and a bucket of water: the clouds; the crate's four are a start, and witches leave the dust, four dust a block. An ender pearl thrown at a beehive or a bee nest: the Queen's sister answers. A hive takes six oak planks and three honeycombs. Obsidian and fire: the Furnace.",
                    "Portas. Uma formiga, mão vazia: as províncias. Uma borboleta: a Masmorra do Esquecimento (cedo demais, volte pra casa). Um diamante num lago cercado de flores: a Corte do Crepúsculo. Uma moldura de pedra-luminosa e um balde de água: as nuvens; as quatro do caixote são um começo, e as bruxas deixam o pó, quatro de pó de pedra-luminosa por bloco. Uma pérola de ender jogada numa colmeia ou numa colmeia artificial: a irmã da Rainha atende. A colmeia artificial leva seis tábuas de carvalho e três favos de mel. Obsidiana e fogo: a Fornalha.")
    p4s = lang(f"{g}.p4.margin.squire", CARD_GUIA, "BOOK", "page 4's margin, stapled at Squire (line 12)",
               "Now the butterfly takes you to real Chaos. Tier 7. Sneak-use it for your old ledge.",
               "Agora a borboleta leva pro Caos de verdade. Nível 7. Agacha e usa pra voltar pra sua beirada.")
    margins = [
        ("No coal? Fireflies come off in your hand.", "Sem carvão? Vaga-lume sai na mão."),
        ("Don't put it on an Eye of Ender block. Seriously.", "Só não põe em bloco de Olho do Ender. Tô falando sério."),
        ("And hide it from the Aliens: they eat them.", "E esconde dos Alienígenas, que eles comem."),
        ("A parrot doing a creeper? Could be a creeper. Could be the parrot. When in doubt, run.",
         "Papagaio imitando creeper? Pode ser creeper. Pode ser o papagaio. Na dúvida, corre."),
    ]
    # Seu Zé's four margins share one page, stapled at the first dusk (lines 13-16, one under the other).
    mk = [lang(f"{g}.p4.margins", CARD_GUIA, "BOOK", "page 4's margins, stapled at the first dusk (lines 13-16)",
               "$(br2)".join(m[0] for m in margins), "$(br2)".join(m[1] for m in margins))]
    p5 = split_lang(f"{g}.p5.text", CARD_GUIA, "BOOK", "page 5 (line 17)",
                    "The Hoard spilled the furniture of ninety-four worlds, and nobody assembled it. A chisel (iron and a stick): any stone in another pattern, for free. The framing saw: any shape, wearing any block's face. The DecoBench (three planks, five logs, one iron): three thousand ornaments, paid in dye. Bibliocraft and Refurbished: shelves, tables, a stove that really cooks. A Parish Stone by the door, and the Land moves in nearby.",
                    "O Tesouro derramou a mobília de noventa e quatro mundos, e ninguém montou. Um cinzel (ferro e um graveto): qualquer pedra em outro padrão, de graça. A Serra Emoldurada: qualquer formato, com a cara de qualquer bloco. A DecoBench (três tábuas, cinco troncos, um ferro): três mil enfeites, pagos em corante. Bibliocraft e Refurbished: estantes, mesas, um fogão que cozinha de verdade. Um Marco da Freguesia na porta, e a Terra vem morar por perto.")
    entries = {
        "p1": entry("guia_de_campo", "guia", nk[0], "minecraft:paper",
                    text_pages(p1, nk[0]) + text_pages([p1f]), 1),
        "p2": entry("guia_de_campo", "guia", nk[1], EGG,
                    text_pages(p2, nk[1]) + text_pages([p2f], advancement=staple("p07")), 2),
        "p3": entry("guia_de_campo", "guia", nk[2], "orespawn_integrations:ledger_scrap",
                    text_pages(p3, nk[2]), 3, advancement=staple("p03")),
        "p4": entry("guia_de_campo", "guia", nk[3], "minecraft:glowstone",
                    text_pages(p4, nk[3]) + text_pages([p4s], advancement="apotheosis:progression/frontier")
                    + [p for k in mk for p in text_pages([k], advancement=staple("dusk"))], 4,
                    advancement=staple("p08")),
        "p5": entry("guia_de_campo", "guia", nk[4], "rechiseled:chisel",
                    text_pages(p5, nk[4]), 5, advancement=staple("close")),
    }
    for name, obj in entries.items():
        write(base / "entries" / "guia" / f"{name}.json", obj)


# ----------------------------------------------------------------------------------------------------------------------
# notebook: item-caderneta-de-campo (Patchouli, i18n). Page texts 1-41 and 52-67 had no card line: written fresh in the
# Field Odete's notary voice, one fact and one dry remark, well under 60 words.
# ----------------------------------------------------------------------------------------------------------------------

CARD_CAD = "item-caderneta-de-campo"
# (page, advancement slug under avistamentos/, EN title, pt-BR title, EN text, pt-BR text)
NOTEBOOK = [
    (1, "village", "a village", "uma vila",
     "Villagers, a bell and a well. Most villages keep a Registry desk somewhere, if you can find the clerk.",
     "Aldeão, sino e poço. Quase toda vila tem um balcão do Cartório em algum canto, se você achar a servidora."),
    (2, "pandora_henge", "a Pandora henge", "um círculo de Pandora",
     "Standing stones around a box nobody ordered. Opened, the Queen's mail answers. Not advised before dinner.",
     "Pedras em roda de uma caixa que ninguém pediu. Abriu, a correspondência da Rainha responde. Não recomendado antes da janta."),
    (3, "pandora_shrine", "a Pandora shrine", "um santuário de Pandora",
     "A smaller altar to the same box. Same contents, less ceremony. No signature required.",
     "Um altar menor pra mesma caixa. Mesmo conteúdo, menos cerimônia. Dispensa assinatura."),
    (4, "pretenders_tower", "a Pretender's tower", "uma torre de Pretendente",
     "A tower some claimant built to look royal. The deed is not on file. The loot, regrettably, is real.",
     "Torre que algum pretendente ergueu pra parecer real. A escritura não consta. O espólio, infelizmente, é de verdade."),
    (5, "hoard_nest", "a Hoard nest", "um ninho do Tesouro",
     "A treetop stuffed with fallen Hoard. Climb at your own risk; the Treasury takes no claims for falls.",
     "Copa de árvore entupida de Tesouro caído. Suba por sua conta; a Tesouraria não aceita reclamação de queda."),
    (6, "sky_nest", "a sky nest", "um ninho do céu",
     "The same nest on a higher shelf. Sighted from below, filed from below.",
     "O mesmo ninho, numa prateleira mais alta. Avistado de baixo, lançado de baixo."),
    (7, "tavern", "a tavern", "uma taverna",
     "Food, drink, a bed and gossip. It is not a boteco. Do not say so inside.",
     "Comida, bebida, cama e fofoca. Não é boteco. Não fala isso lá dentro."),
    (8, "firewatch_tower", "a firewatch tower", "uma torre de vigia do fogo",
     "A tall wooden post for watching smoke. From the top, more sightings. Efficient.",
     "Posto alto de madeira pra vigiar fumaça. Lá de cima, mais avistamentos. Eficiente."),
    (9, "watchtower", "a watchtower", "uma atalaia",
     "Wizards, engineers or outposts, all of them tall. Filed together to save paper.",
     "Mago, engenheiro ou posto avançado, tudo alto. Lançados juntos pra economizar papel."),
    (10, "lighthouse", "the lighthouse", "o farol",
     "One light on the coast, kept by nobody in particular. The ships are not on file either.",
     "Uma luz no litoral, cuidada por ninguém em especial. Navio também não consta."),
    (11, "hunter_tent", "a Hunter tent", "uma barraca de caçador",
     "Hunters camp here between bounties. Knock first; they are armed.",
     "Os caçadores acampam aqui entre uma caçada e outra. Bate antes; eles andam armados."),
    (12, "collectors_post", "a Collectors' post", "um posto dos Coletores",
     "The Collectors' outpost: tax for nobody, collected anyway. Keep your receipts.",
     "O posto dos Coletores: imposto pra ninguém, cobrado mesmo assim. Guarde os recibos."),
    (13, "unrecorded_tower", "an Unrecorded tower", "uma torre Sem-Registro",
     "Dark stone and no record at all. What lives here was never registered. Sight it from far.",
     "Pedra escura e registro nenhum. O que mora ali nunca foi lançado. Avista de longe."),
    (14, "cuca_hut", "the Cuca's hut", "o casebre da Cuca",
     "A cottage in the swamp that smells of syrup. The owner wants a royal egg. Do not bring one.",
     "Um casebre no brejo com cheiro de melado. A dona quer ovo real. Não leva nenhum."),
    (15, "lost_cathedral", "the Lost Cathedral", "a Catedral Perdida",
     "A church nobody built, on no map. Bells at the wrong hours. File it and walk on.",
     "Uma igreja que ninguém construiu, em mapa nenhum. Sino em hora errada. Lança e segue."),
    (16, "orespawn_oddity", "an OreSpawn oddity", "uma esquisitice do OreSpawn",
     "A haunted house, a duck pond, a bouncy castle: the Hoard's odd furniture, left out in the rain. One family.",
     "Casa mal-assombrada, lago de patinho, pula-pula: a mobília esquisita do Tesouro, largada no tempo. Uma família só."),
    (17, "girlfriend_island", "Girlfriend Island", "a Ilha da Namorada",
     "An island with a resident who writes letters. Visiting hours not posted.",
     "Uma ilha com uma moradora que escreve cartas. Horário de visita não informado."),
    (18, "mobzilla_arena", "the Mobzilla Arena", "a Arena do Mobzilla",
     "The Royal Executioner's yard. He is usually asleep. Keep it that way.",
     "O pátio do Carrasco Real. Ele costuma estar dormindo. Deixa assim."),
    (19, "umvuthana_grove", "the Umvuthana grove", "o bosque dos Umvuthana",
     "Masked folk around a bird-faced chief. They trade, for the right fee.",
     "Um povo mascarado em volta de um chefe com cara de pássaro. Negociam, pela taxa certa."),
    (20, "mountain_tower", "the mountain tower", "a torre da montanha",
     "A wizard's tower up a peak. The climb is the fee.",
     "Torre de mago no alto do pico. A subida é a taxa."),
    (21, "hydra_outpost", "a Hydra outpost", "um posto da Hidra",
     "People in green uniforms guarding crates. Not on our payroll. Sign nothing.",
     "Gente de farda verde vigiando caixote. Não é da nossa folha. Não assina nada."),
    (22, "bandit_towers", "the bandit towers", "as torres dos bandidos",
     "Bandits on stilts. They collect too, without a receipt.",
     "Bandido em palafita. Também cobram, só que sem nota."),
    (23, "ruins", "ruins", "ruínas",
     "The old walls of nobody's house. A good place to find what fell.",
     "Paredes velhas da casa de ninguém. Bom lugar pra achar o que caiu."),
    (24, "boteco", "the boteco", "o boteco",
     "Seu Zé's bar. Credit tomorrow, stories today.",
     "O bar do Seu Zé. Fiado só amanhã, causo hoje."),
    (25, "hunt_lodge", "the Hunt Lodge", "o Pavilhão de Caça",
     "Captain Iracema's lodge and the Monday board. Place, target, price.",
     "O pavilhão da Capitã Iracema e o mural de segunda. Lugar, alvo, preço."),
    (26, "hatching_ground", "the Hatching Ground", "o Ninho",
     "Shelf 9, where your lot was filed. First stamp, free of charge.",
     "A Prateleira 9, onde o seu lote foi lançado. Primeiro carimbo, sem custo."),
    (27, "prince", "the Prince", "o Príncipe",
     "A young royal dragon on his rounds. He writes. Write back.",
     "Um dragão real novinho fazendo a ronda. Ele escreve. Responde."),
    (28, "girlfriend", "the Girlfriend", "a Namorada",
     "The Girlfriend, on her side of the story. Be polite.",
     "A Namorada, no lado dela da história. Educação, viu."),
    (29, "mestre", "the Mestre", "o Mestre",
     "An old man who gives things away. The Treasury has questions.",
     "Um velhinho que dá as coisas. A Tesouraria tem perguntas."),
    (30, "saci", "a Saci Pererê", "o Saci-Pererê",
     "One leg, a red cap and a whirlwind. Count your spoons.",
     "Uma perna, gorro vermelho e redemoinho. Conta as colheres."),
    (31, "court_beast", "a Court Beast", "uma Fera da Corte",
     "A beast of the old court: scorpion, basilisk, alien or caterpillar. From far. Only from far.",
     "Uma fera da corte antiga: escorpião, basilisco, alienígena ou lagarta. De longe. Só de longe."),
    (32, "mapinguari", "the Mapinguari", "o Mapinguari",
     "A forest giant with one eye and a mouth in its belly. Sundays are his. Stay home.",
     "Gigante da mata, um olho só, boca na barriga. Domingo é dele. Fica em casa."),
    (33, "caramelo", "Caramelo", "o Caramelo",
     "The realm's caramel dog. The best citizen on file.",
     "O vira-lata caramelo do reino. O melhor cidadão que consta."),
    (34, "sky_beast", "a sky beast", "um bicho do céu",
     "Something big up there: shark, bird or cab. Look up, then file.",
     "Bicho grande lá em cima: tubarão, pássaro ou táxi. Olha pra cima e lança."),
    (35, "mobzilla", "Mobzilla", "o Mobzilla",
     "The Royal Executioner, out of his yard. Note the time. Leave.",
     "O Carrasco Real fora do pátio. Anota a hora. Vai embora."),
    (36, "kraken", "the Kraken", "a Kraken",
     "The Admiral of the sea. She remembers every squid. Keep to the shore.",
     "A Almirante do mar. Ela lembra de cada lula. Fica na beira."),
    (37, "door_cloud", "the door cloud", "a nuvem da porta",
     "Three butterflies together at dusk: the door down. Too early for you.",
     "Três borboletas juntas no fim da tarde: a porta pra baixo. Cedo demais pra você."),
    (38, "hoard_fall", "a Hoard Fall", "uma Queda do Tesouro",
     "Something gold coming down, whistling. Not a star. Mark where it lands.",
     "Um troço dourado descendo, assobiando. Não é estrela. Marca onde cai."),
    (39, "mae_da_lua", "the mãe-da-lua", "a mãe-da-lua",
     "A bird that looks like a branch, crying at dusk. The night starts when she does.",
     "Um pássaro que parece galho, chorando no fim da tarde. A noite começa quando ela começa."),
    (40, "mula_fire_trail", "the Mula's fire trail", "o rastro de fogo da Mula",
     "Fire across a field on a Thursday, and no animal in sight. File it from a distance.",
     "Fogo cortando o campo numa quinta, e bicho nenhum à vista. Lança de longe."),
    (41, "red_islands", "red islands over the void", "ilhas vermelhas sobre o vazio",
     "Red islands over the void, seen from the ledge. You're early. They will wait.",
     "Ilhas vermelhas sobre o vazio, vistas da beirada. Chegou cedo. Elas esperam."),
    (52, "prince_from_above", "the Prince on his flyover, from above", "O Príncipe no sobrevoo, visto de cima",
     "The Prince, seen from higher than the Prince. He noticed.",
     "O Príncipe, visto de mais alto que o Príncipe. Ele percebeu."),
    (53, "kings_tree", "the King's tree from 64 blocks", "A árvore do Rei, a 64 blocos",
     "The King's tree in Utopia, from sixty-four blocks. He is asleep inside. Do not knock.",
     "A árvore do Rei em Utopia, a sessenta e quatro blocos. Ele dorme lá dentro. Não bate."),
    (54, "cloud_gap", "the cloud gap", "a fresta na nuvem",
     "The overworld through a gap in the Aether's clouds. Everything you own, very small.",
     "O mundo de baixo por uma fresta nas nuvens do Aether. Tudo que é seu, bem pequenininho."),
    (55, "revoada", "the Revoada leaving the Mines", "A Revoada saindo das Minas",
     "Leonopteryx leaving the Mines in a flock. Count them; nobody else does.",
     "Leonopteryx saindo das Minas em bando. Conta; ninguém mais conta."),
    (56, "dinosaur", "a dinosaur", "Dinossauro",
     "A dinosaur, alive despite the records. Sighted at a distance, as advised.",
     "Dinossauro vivo, apesar dos registros. Avistado de longe, como recomendado."),
    (57, "host_robot", "a Host robot", "Robô da Hoste",
     "A robot of the Host, still on its rounds. Its paperwork expired long ago.",
     "Um robô da Hoste, ainda fazendo a ronda. A papelada dele venceu faz tempo."),
    (58, "sea_beast", "a sea beast", "Fera do mar",
     "Something large under the water. Whale or worse. The sea keeps its own file.",
     "Coisa grande debaixo d'água. Baleia ou pior. O mar tem o próprio arquivo."),
    (59, "capelobo_stare", "the Capelobo's Stare (night)", "O Olhar do Capelobo (noite)",
     "Two eyes in the dark, at night only. The Capelobo is looking back. Do not wave.",
     "Dois olhos no escuro, só de noite. É o Capelobo olhando de volta. Não acena."),
    (60, "capivara", "a capivara crossing water", "Capivara atravessando a água",
     "A capybara crossing water, exactly where it belongs.",
     "Capivara atravessando a água, bem no lugar dela."),
    (61, "lobisomem", "the Lobisomem", "O Lobisomem",
     "A wolf that walks on two legs on Fridays. Paperwork pending.",
     "Um lobo que anda em duas pernas nas sextas. Documentação pendente."),
    (62, "mutant", "a Mutant", "Grandão (Mutante)",
     "Something ordinary, much too big. The form has no box for it.",
     "Uma coisa comum, grande demais. O formulário não tem campo pra isso."),
    (63, "unrecorded_lord", "an Unrecorded lord", "Senhor dos Sem-Registro",
     "A lord of the Unrecorded, never written down. Now it is, by you.",
     "Um senhor dos Sem-Registro, nunca lançado. Agora consta, por sua conta."),
    (64, "big_game", "big game", "Caça grande",
     "Elephant, giraffe, rhino or hippo. The Land's own, and big about it.",
     "Elefante, girafa, rinoceronte ou hipopótamo. Da Terra mesmo, e grande ainda por cima."),
    (65, "dusk_court_lord", "a Dusk Court lord", "Senhor da Corte do Crepúsculo",
     "A lord of the Dusk Court: the naga, the lich or the hydra. Bow from a distance.",
     "Um senhor da Corte do Crepúsculo: a naga, o lich ou a hidra. Reverência de longe."),
    (66, "hatchery_beast", "a Hatchery beast", "Fera da Chocadeira",
     "A whale in the clouds or a bird that walks. Hatched upstairs, filed downstairs.",
     "Baleia nas nuvens ou pássaro que anda. Chocado lá em cima, lançado aqui embaixo."),
    (67, "beehemoth", "a Beehemoth", "Beehemoth",
     "A bee the size of a cart, in the Queen's sister's halls. Friendly. Mostly.",
     "Abelha do tamanho de uma carroça, nos salões da irmã da Rainha. Mansa. Quase sempre."),
]


def cap(s):
    return s[:1].upper() + s[1:]


def part_notebook():
    c = "orespawn_integrations.caderneta"
    title = lang(f"{c}.title", CARD_CAD, "ITEM", "the book's name (line 13)", "Field Notebook", "Caderneta de Campo")
    tip = lang(f"{c}.subtitle", CARD_CAD, "ITEM", "the book's tooltip (line 13)",
               "Hold a spyglass on the far thing. It counts.", "Aponta a luneta pra coisa lá longe. Vale carimbo.")
    opening = lang(f"{c}.open", CARD_CAD, "GUIDE", "the open page (line 2)",
                   "Raise the spyglass. Hold it on anything far away. The Treasury will take it from there.",
                   "Levanta a luneta. Segura em qualquer coisa longe. A Tesouraria cuida do resto.")
    book("caderneta_de_campo", title, opening, subtitle=tip, model="patchouli:book_green",
         texture="patchouli:textures/gui/book_green.png")
    base = ASSETS / "patchouli_books" / "caderneta_de_campo" / "en_us"
    write(base / "categories" / "paginas.json", {"name": title, "description": opening, "icon": "minecraft:spyglass",
                                                 "sortnum": 0})
    for page, slug, en_t, pt_t, en, pt in NOTEBOOK:
        nk = lang(f"{c}.p{page:02d}.name", CARD_CAD, "GUIDE", f"page {page}'s title (fresh from the family's name)",
                  cap(en_t), cap(pt_t))
        tk = lang(f"{c}.p{page:02d}.text", CARD_CAD, "GUIDE", f"page {page} (fresh, the Field Odete's notary voice)",
                  en, pt)
        write(base / "entries" / "paginas" / f"p{page:02d}.json",
              entry("caderneta_de_campo", "paginas", nk, "minecraft:spyglass", text_pages([tk], nk), page,
                    advancement=f"{NS}:avistamentos/{slug}"))


# ----------------------------------------------------------------------------------------------------------------------

def register_modifiers():
    """NeoForge only runs the loot modifiers listed in neoforge:loot_modifiers/global_loot_modifiers; ours go last."""
    path = DATA / "neoforge" / "loot_modifiers" / "global_loot_modifiers.json"
    glm = json.loads(path.read_text(encoding="utf-8"))
    ours = [f"{NS}:arvore/{log_slug(l)}" for l in LOGS] + [f"{NS}:witch_hut_lamps"]
    glm["entries"] = [e for e in glm["entries"] if e not in ours] + ours
    write(path, glm)


def write_fragments():
    keys, en, pt = {}, {}, {}
    for key in sorted(LANG):
        card, surface, where, e, p = LANG[key]
        keys[key] = f"{card} | {surface} | {where}"
        en[key], pt[key] = e, p
    write(FRAG / "data.keys.json", keys)
    write(FRAG / "data.en_us.json", en)
    write(FRAG / "data.pt_br.json", pt)


def main(argv):
    atlas = Path(r"C:\Homework\Sessions\Japan\crazycraft-world-design-claude\atlas\data")
    if "--atlas" in argv:
        atlas = Path(argv[argv.index("--atlas") + 1])
    part_twin()
    part_canopy()
    part_tree()
    part_doors()
    part_night(atlas)
    part_prey()
    part_decor(atlas)
    part_guide()
    part_notebook()
    register_modifiers()
    write_fragments()
    print(f"wrote the data group's files; {len(LANG)} lang keys")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
