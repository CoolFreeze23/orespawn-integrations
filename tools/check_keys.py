"""Every translation key the mod's data and code name must exist in both languages, or a player reads a raw key.

  python -I tools/check_keys.py [--ptbr-pack CLIENT_ZIP]

Reads data and asset JSON, mcfunctions, Java string literals and Java joins of a key constant with a literal, in this class or
another (WhoComes.KEY + "cc.later"). A key built at run time from a variable can't be seen here, which is why the full-pack
script also watches chat and signs for raw keys. A key missing from the mod's pt_br.json counts as present when the pack's own
pt-BR resource pack holds it; give the pack's client zip (or the resource pack zip itself) to check that. Exit 1 on any missing key.
"""
import io
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LANG = ROOT / "src/main/resources/assets/orespawn_integrations/lang"
PREFIX = re.compile(r"^(advancements\.|item\.|block\.|entity\.|event\.|itemGroup\.|key\.|config\.|biome\.|structure\.|death\.attack\.)?orespawn_integrations\.")
LITERAL = re.compile(r'"((?:advancements\.|item\.|block\.|entity\.|event\.|itemGroup\.|key\.|config\.|biome\.|structure\.)?orespawn_integrations\.[a-zA-Z0-9_.-]*[a-zA-Z0-9_])"')
CONST = re.compile(r'(?:static final |final )?String (\w+)\s*=\s*"((?:advancements\.|item\.|block\.|entity\.|event\.)?orespawn_integrations\.[a-zA-Z0-9_.]*)"')
JOIN = re.compile(r'\b(?:(\w+)\.)?([A-Z][A-Z0-9_]*|[a-z]\w*)\s*\+\s*"([a-zA-Z0-9_.]+)"(?!\s*\+)')


def pack_lang(zip_path):
    with zipfile.ZipFile(zip_path) as z:
        inner = "assets/orespawn_integrations/lang/pt_br.json"
        if inner in z.namelist():
            return json.loads(z.read(inner).decode("utf-8"))
        name = next(n for n in z.namelist() if n.endswith("resourcepacks/CrazyCraft5-ptBR.zip"))
        with zipfile.ZipFile(io.BytesIO(z.read(name))) as rp:
            return json.loads(rp.read(inner).decode("utf-8"))


def main(argv):
    en = json.loads((LANG / "en_us.json").read_text(encoding="utf-8"))
    pt = json.loads((LANG / "pt_br.json").read_text(encoding="utf-8"))
    pack = pack_lang(argv[argv.index("--ptbr-pack") + 1]) if "--ptbr-pack" in argv else {}
    used = {}
    for base in ("src/main/resources/data", "src/main/resources/assets"):
        for p in (ROOT / base).rglob("*"):
            if p.suffix not in (".json", ".mcfunction") or "/lang/" in p.as_posix():
                continue
            text = p.read_text(encoding="utf-8", errors="replace")
            for m in re.finditer(r'"(?:translate|fallback_key|translatable|key)"\s*:\s*\\*"([a-zA-Z0-9_.]+)\\*"', text):
                if PREFIX.match(m.group(1)):
                    used.setdefault(m.group(1), p.name)
    sources = {p: p.read_text(encoding="utf-8", errors="replace") for p in (ROOT / "src/main/java").rglob("*.java")}
    by_class = {p.stem: dict(CONST.findall(t)) for p, t in sources.items()}
    for p, text in sources.items():
        own = by_class[p.stem]
        for m in LITERAL.finditer(text):
            k = m.group(1)
            if k not in own.values() and re.search(r"\.[a-z]", k):
                used.setdefault(k, p.name)
        for cls, name, tail in JOIN.findall(text):
            prefix = by_class.get(cls, {}).get(name) if cls else own.get(name)
            # A tail ending in a dot is joined to a variable next, so it's a family of keys, not a key.
            if prefix and prefix.endswith(".") and not tail.endswith("."):
                used.setdefault(prefix + tail, p.name)
    # A literal naming a family of keys isn't a key itself.
    prefixes = {k for k in used if k not in en and any(e.startswith(k + ".") for e in en)}
    missing_en = sorted(k for k in used if k not in en and k not in prefixes)
    missing_pt = sorted(k for k in used if k not in pt and k not in pack and k not in prefixes)
    print(f"check_keys: {len(used)} keys named; missing from en_us {len(missing_en)}; missing from pt_br"
          f"{' and the pack' if pack else ''} {len(missing_pt)}")
    for k in missing_en[:60]:
        print("  en_us:", k, "(" + used[k] + ")")
    for k in missing_pt[:60]:
        print("  pt_br:", k, "(" + used[k] + ")")
    return 1 if missing_en or missing_pt else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
