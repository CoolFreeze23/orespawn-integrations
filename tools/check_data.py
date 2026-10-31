"""Static check of the mod's data: every JSON parses, every id resolves, every translate key has its lang line.

  python -I tools/check_data.py [--atlas <crazycraft-world-design-claude/atlas/data>] [--verbose]

What it reads:
  - every *.json under src/main/resources (parse check), and every data/*/function/**/*.mcfunction;
  - the pack's registry dump and tags (atlas/data/registry_dump.json, tags.json), its loot tables (loot_tables.json) and
    advancements (advancements.json): the ids that exist in CrazyCraft 5.0;
  - this mod's own ids: the items, blocks and entities its Java registers (the DeferredRegister names), and the tags,
    loot tables, advancements and functions under src/main/resources/data;
  - the ids the 0.10.0 contract fixes between groups that another group's branch adds (HANDOFF below).

What it resolves (an entry a tag marks "required": false, and every file whose neoforge:conditions name a mod the pack
does not load, are skipped):
  tags        item, block, entity_type, worldgen/biome, worldgen/structure values and #tag references
  loot tables item and loot_table entries, item tags
  loot modifiers   the added table and every neoforge:loot_table_id
  advancements     parent, icon, inventory_changed items, entity types, changed_dimension, reward function, recipe ids
  biome modifiers  biomes, entity types, spawner types
  Patchouli        entry icons, entry and page advancement locks, an i18n book's text keys
  mcfunctions      give, clear, loot, summon, function, advancement, execute if items and type= selectors
Translate keys in our namespace ({"translate": ...} in data and functions, and every text field of an i18n Patchouli
book) must be in a _lang_fragments/0.10.0/*.keys.json or already shipped in assets/orespawn_integrations/lang/en_us.json.

Exit 1 on any problem.
"""
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src" / "main" / "resources"
DATA = RES / "data"
JAVA = ROOT / "src" / "main" / "java"
FRAG = ROOT / "_lang_fragments" / "0.10.0"
MODS = Path(r"C:\Users\alvin\AppData\Roaming\PrismLauncher\instances\CrazyCraft 5.0\minecraft\mods")
# CustomNPCs reads its own lenient JSON (bare 1b, raw newlines): those files are its format, not Minecraft data.
LENIENT = ("orespawn_integrations/customnpcs/",)
SHIPPED_LANG = RES / "assets" / "orespawn_integrations" / "lang" / "en_us.json"
NS = "orespawn_integrations"

# Ids another group's branch adds, fixed by CONTRACT-0.10.0.md "Hand-offs between groups"; resolved here so a branch
# can be checked before the merge. Prefixes cover a family of ids.
HANDOFF = {
    # world (system-sightings, structure-chaos-antechamber): the Field Notebook's page locks and the Antechamber's node.
    "advancement": ["orespawn_integrations:avistamentos/", "orespawn_integrations:wonders/"],
}
TAG_KINDS = {"item": "items", "block": "blocks", "entity_type": "entity_types", "worldgen/biome": "biomes",
             "worldgen/structure": "structures"}
KEY_RE = re.compile(r'"translate"\s*:\s*"([^"]+)"')


class Ids:
    def __init__(self, atlas: Path):
        dump = json.loads((atlas / "registry_dump.json").read_text(encoding="utf-8"))
        self.loaded = {m["id"] for m in dump["loaded_mods"]}
        self.reg = {kind: {e["id"] for e in dump[kind]["entries"]}
                    for kind in ("items", "blocks", "entity_types", "biomes", "structures", "dimensions")}
        self.reg["dimensions"] |= {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"}
        tags = json.loads((atlas / "tags.json").read_text(encoding="utf-8"))
        self.tags = {kind: set(tags.get(kind, {})) for kind in TAG_KINDS}
        self.loot = {e["id"] for e in json.loads((atlas / "loot_tables.json").read_text(encoding="utf-8"))}
        adv = json.loads((atlas / "advancements.json").read_text(encoding="utf-8"))
        self.adv = {a["id"] for a in (adv if isinstance(adv, list) else adv.get("advancements", adv.values()))
                    if isinstance(a, dict) and "id" in a}
        self.functions = set()
        self.recipes = {r["id"] for r in json.loads((atlas / "recipes.json").read_text(encoding="utf-8"))}
        self._jar_functions()
        self._own()

    def _jar_functions(self):
        """Functions the pack's mod jars ship (a datapack mod such as ly's Lucky Blocks calls its own)."""
        if not MODS.is_dir():
            return
        rx = re.compile(r"^data/([^/]+)/functions?/(.+)\.mcfunction$")
        for jar in MODS.glob("*.jar"):
            try:
                with zipfile.ZipFile(jar) as z:
                    for n in z.namelist():
                        m = rx.match(n)
                        if m:
                            self.functions.add(f"{m.group(1)}:{m.group(2)}")
            except (zipfile.BadZipFile, OSError):
                continue

    def _own(self):
        for f in DATA.glob("*/**/*"):
            if not f.is_file():
                continue
            rel = f.relative_to(DATA).as_posix()
            ns, rest = rel.split("/", 1)
            for kind in TAG_KINDS:
                pre = f"tags/{kind}/"
                if rest.startswith(pre) and rest.endswith(".json"):
                    self.tags[kind].add(f"{ns}:{rest[len(pre):-5]}")
            for folder, store in (("loot_table/", self.loot), ("advancement/", self.adv), ("recipe/", self.recipes)):
                if rest.startswith(folder) and rest.endswith(".json"):
                    store.add(f"{ns}:{rest[len(folder):-5]}")
            if rest.startswith("function/") and rest.endswith(".mcfunction"):
                self.functions.add(f"{ns}:{rest[len('function/'):-len('.mcfunction')]}")
            if rest.startswith("tags/function/") and rest.endswith(".json"):
                self.functions.add(f"#{ns}:{rest[len('tags/function/'):-5]}")
        # Our Java registrations: DeferredRegister names (register("name", ...), registerItem("name", ...)).
        names = set()
        for f in JAVA.rglob("*.java"):
            for m in re.finditer(r'\.register\w*\(\s*"([a-z0-9_/]+)"', f.read_text(encoding="utf-8")):
                names.add(f"{NS}:{m.group(1)}")
        for kind in ("items", "blocks", "entity_types"):
            self.reg[kind] |= names

    def handoff(self, kind, rid):
        return any(rid == h or (h.endswith("/") and rid.startswith(h)) for h in HANDOFF.get(kind, []))

    def has(self, kind, rid):
        """kind: items, blocks, entity_types, biomes, structures, dimensions, loot_table, advancement, function."""
        if rid.startswith("#"):
            tag_kind = {"items": "item", "blocks": "block", "entity_types": "entity_type", "biomes": "worldgen/biome",
                        "structures": "worldgen/structure"}.get(kind)
            if kind == "function":
                return rid in self.functions
            return tag_kind is not None and rid[1:] in self.tags[tag_kind]
        rid = rid if ":" in rid else "minecraft:" + rid
        if kind == "loot_table":
            return rid in self.loot or self.handoff(kind, rid)
        if kind == "advancement":
            return rid in self.adv or self.handoff(kind, rid)
        if kind == "function":
            return rid in self.functions
        if kind == "recipe":
            return rid in self.recipes or rid.startswith("minecraft:")
        return rid in self.reg[kind]


class Checker:
    def __init__(self, ids: Ids):
        self.ids = ids
        self.problems = []
        self.keys_used = {}

    def bad(self, where, msg):
        self.problems.append(f"{where}: {msg}")

    def need(self, where, kind, rid):
        if isinstance(rid, str) and not self.ids.has(kind, rid):
            self.bad(where, f"unknown {kind} {rid}")

    def skipped(self, obj):
        """True when the file's neoforge:conditions name a mod the pack does not load."""
        conds = obj.get("neoforge:conditions", []) if isinstance(obj, dict) else []
        for c in conds:
            if c.get("type") == "neoforge:mod_loaded" and c.get("modid") not in self.ids.loaded:
                return True
        return False

    def key(self, where, k):
        if k.startswith(NS + ".") or k.startswith(f"advancements.{NS}.") or k.startswith(f"item.{NS}.") \
                or k.startswith(f"block.{NS}.") or k.startswith(f"book.{NS}."):
            self.keys_used.setdefault(k, where)

    def keys_in(self, where, text):
        for m in KEY_RE.finditer(text):
            self.key(where, m.group(1))

    # --- data kinds -----------------------------------------------------------------------------------------------
    def tag(self, where, kind, obj):
        reg = TAG_KINDS.get(kind)
        for v in obj.get("values", []):
            rid, required = (v, True) if isinstance(v, str) else (v.get("id"), v.get("required", True))
            if not required or reg is None:
                continue
            self.need(where, reg, rid)

    def loot_entries(self, where, node):
        if isinstance(node, dict):
            t = node.get("type", "")
            if t in ("minecraft:item", "item") and "name" in node:
                self.need(where, "items", node["name"])
            elif t in ("minecraft:loot_table", "loot_table") and isinstance(node.get("value"), str):
                self.need(where, "loot_table", node["value"])
            elif t in ("minecraft:tag", "tag") and "name" in node:
                self.need(where, "items", "#" + node["name"])
            for v in node.values():
                self.loot_entries(where, v)
        elif isinstance(node, list):
            for v in node:
                self.loot_entries(where, v)

    def predicates(self, where, node):
        """Entity type and item ids inside advancement criteria and loot conditions."""
        if isinstance(node, dict):
            for k, v in node.items():
                if k == "items" and (isinstance(v, (str, list))):
                    for i in ([v] if isinstance(v, str) else v):
                        if isinstance(i, str):
                            self.need(where, "items", i)
                if k in ("predicate", "entity") and isinstance(v, dict) and isinstance(v.get("type"), str):
                    self.need(where, "entity_types", v["type"])
                self.predicates(where, v)
        elif isinstance(node, list):
            for v in node:
                self.predicates(where, v)

    def advancement(self, where, obj):
        if "parent" in obj:
            self.need(where, "advancement", obj["parent"])
        icon = obj.get("display", {}).get("icon", {})
        if "id" in icon:
            self.need(where, "items", icon["id"])
        for crit in obj.get("criteria", {}).values():
            cond = crit.get("conditions", {})
            self.predicates(where, cond)
            if crit.get("trigger") == "minecraft:changed_dimension":
                for side in ("from", "to"):
                    if side in cond:
                        self.need(where, "dimensions", cond[side])
            if crit.get("trigger") == "minecraft:recipe_crafted" and "recipe_id" in cond:
                self.need(where, "recipe", cond["recipe_id"])
        fn = obj.get("rewards", {}).get("function")
        if fn:
            self.need(where, "function", fn)
        self.keys_in(where, json.dumps(obj))

    def loot_modifier(self, where, obj):
        if "table" in obj:
            self.need(where, "loot_table", obj["table"])
        for c in obj.get("conditions", []):
            if c.get("condition") == "neoforge:loot_table_id":
                self.need(where, "loot_table", c["loot_table_id"])
        self.predicates(where, obj.get("conditions", []))

    def holder(self, where, kind, node):
        if isinstance(node, str):
            self.need(where, kind, node)
        elif isinstance(node, list):
            for v in node:
                self.holder(where, kind, v)
        elif isinstance(node, dict):
            if node.get("type") in ("neoforge:or", "neoforge:and"):
                self.holder(where, kind, node.get("values", []))
            elif node.get("type") == "neoforge:not":
                self.holder(where, kind, node.get("value"))

    def biome_modifier(self, where, obj):
        t = obj.get("type", "")
        if t in ("neoforge:add_spawns", "neoforge:remove_spawns", "neoforge:add_features", "neoforge:remove_features",
                 "neoforge:add_spawn_costs", "neoforge:remove_spawn_costs"):
            if "biomes" in obj:
                self.holder(where, "biomes", obj["biomes"])
            if "entity_types" in obj:
                self.holder(where, "entity_types", obj["entity_types"])
            spawners = obj.get("spawners", [])
            for s in ([spawners] if isinstance(spawners, dict) else spawners):
                self.need(where, "entity_types", s.get("type"))

    def patchouli_entry(self, where, obj, i18n):
        if "icon" in obj and isinstance(obj["icon"], str) and ".png" not in obj["icon"]:
            self.need(where, "items", obj["icon"].split("{")[0].split("[")[0])
        for node in [obj] + list(obj.get("pages", [])):
            if node.get("advancement"):
                self.need(where, "advancement", node["advancement"])
        if i18n:
            for field in ("name", "description", "landing_text", "subtitle"):
                if isinstance(obj.get(field), str):
                    self.key(where, obj[field])
            for page in obj.get("pages", []):
                for field in ("title", "text"):
                    if isinstance(page.get(field), str):
                        self.key(where, page[field])

    # --- functions ------------------------------------------------------------------------------------------------
    CMD = [
        (re.compile(r"\bgive @\w(?:\[[^\]]*\])? ([a-z0-9_.-]+:[a-z0-9_/.-]+)"), "items"),
        (re.compile(r"\bclear @\w(?:\[[^\]]*\])? ([a-z0-9_.-]+:[a-z0-9_/.-]+)"), "items"),
        (re.compile(r"\bif items entity @\w \S+ ([a-z0-9_.-]+:[a-z0-9_/.-]+)"), "items"),
        (re.compile(r"\bloot (?:give|spawn|insert|replace) .*? loot ([a-z0-9_.-]+:[a-z0-9_/.-]+)"), "loot_table"),
        (re.compile(r"\bsummon ([a-z0-9_.-]+:[a-z0-9_/.-]+)"), "entity_types"),
        (re.compile(r"type=!?([a-z0-9_.-]+:[a-z0-9_/.-]+)"), "entity_types"),
        (re.compile(r"\bfunction (#?[a-z0-9_.-]+:[a-z0-9_/.-]+)"), "function"),
        (re.compile(r"\badvancement (?:grant|revoke) @\w(?:\[[^\]]*\])? (?:only|from|through|until) "
                    r"([a-z0-9_.-]+:[a-z0-9_/.-]+)"), "advancement"),
    ]

    def mcfunction(self, where, text):
        for line in text.splitlines():
            line = line.strip()
            if not line or line.startswith("#") or line.startswith("$"):
                continue
            for rx, kind in self.CMD:
                for m in rx.finditer(line):
                    self.need(where, kind, m.group(1))
            self.keys_in(where, line)

    # --- walk -----------------------------------------------------------------------------------------------------
    def run(self):
        i18n_books = set()
        for book in DATA.glob("*/patchouli_books/*/book.json"):
            try:
                obj = json.loads(book.read_text(encoding="utf-8"))
            except ValueError:
                continue
            if obj.get("i18n"):
                i18n_books.add(book.parent.name)
                self.patchouli_entry(str(book.relative_to(ROOT)), obj, True)
        for f in sorted(RES.rglob("*.json")):
            where = f.relative_to(ROOT).as_posix()
            rel = f.relative_to(RES).as_posix()
            if rel.startswith(LENIENT):
                continue
            try:
                obj = json.loads(f.read_text(encoding="utf-8"))
            except ValueError as e:
                self.bad(where, f"does not parse: {e}")
                continue
            if rel.startswith("assets/") and "/patchouli_books/" in rel:
                parts = rel.split("/")
                book = parts[3]
                if parts[-2] in ("entries", "categories") or "entries" in parts or "categories" in parts:
                    self.patchouli_entry(where, obj, book in i18n_books)
                continue
            if not rel.startswith("data/") or not isinstance(obj, dict) or self.skipped(obj):
                continue
            ns, rest = rel[len("data/"):].split("/", 1)
            if rest.startswith("tags/"):
                for kind in TAG_KINDS:
                    if rest.startswith(f"tags/{kind}/"):
                        self.tag(where, kind, obj)
            elif rest.startswith("loot_table/"):
                self.loot_entries(where, obj.get("pools", []))
                self.predicates(where, obj.get("pools", []))
                self.keys_in(where, json.dumps(obj))
            elif rest.startswith("advancement/"):
                self.advancement(where, obj)
            elif rest.startswith("loot_modifiers/") and ns != "neoforge":
                self.loot_modifier(where, obj)
            elif ns == "neoforge" and rest == "loot_modifiers/global_loot_modifiers.json":
                for e in obj.get("entries", []):
                    e_ns, e_path = e.split(":", 1)
                    if not (DATA / e_ns / "loot_modifiers" / f"{e_path}.json").exists():
                        self.bad(where, f"lists a loot modifier with no file: {e}")
            elif rest.startswith("neoforge/biome_modifier/"):
                self.biome_modifier(where, obj)
            else:
                self.keys_in(where, json.dumps(obj))
        for f in sorted(DATA.rglob("*.mcfunction")):
            self.mcfunction(f.relative_to(ROOT).as_posix(), f.read_text(encoding="utf-8"))
        known = set(json.loads(SHIPPED_LANG.read_text(encoding="utf-8")))
        for kf in FRAG.glob("*.keys.json"):
            known |= set(json.loads(kf.read_text(encoding="utf-8")))
        for k, where in sorted(self.keys_used.items()):
            if k not in known:
                self.bad(where, f"translate key {k} is in no _lang_fragments/0.10.0/*.keys.json")


def main(argv):
    atlas = Path(r"C:\Homework\Sessions\Japan\crazycraft-world-design-claude\atlas\data")
    if "--atlas" in argv:
        atlas = Path(argv[argv.index("--atlas") + 1])
    checker = Checker(Ids(atlas))
    checker.run()
    for p in checker.problems:
        print(p)
    print(f"check_data: {len(checker.keys_used)} translate keys, {len(checker.problems)} problems")
    return 1 if checker.problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
