"""Checks the lang fragments of a release and merges them into the shipped lang files.

  python -I tools/lang_fragments.py <release>            check only (exit 1 on any problem)
  python -I tools/lang_fragments.py <release> --merge    check, then write assets/.../lang/en_us.json and pt_br.json

Each system group keeps three files in _lang_fragments/<release>/:
  <group>.keys.json   {"<key>": "<card id> | <SURFACE> | <where it shows>"}
  <group>.en_us.json  {"<key>": "<English>"}
  <group>.pt_br.json  {"<key>": "<Portuguese>"}

The checks are the spine's text limits (bible/00-spine.md, "Text limits"):
  SIGN       one sign line, at most 15 characters (a {player} placeholder counts as 12)
  HANGING    one hanging-sign line, at most 10
  CHAT, TITLE, ACTIONBAR   at most 90 characters, pt-BR at most 103
  BOOK, GUIDE              a letter or Field Guide page at most 120 words, a Patchouli entry (GUIDE) at most 200
  BUBBLE     at most 2 sentences;  OPTION  at most 6 words;  ADV (descriptions)  1 sentence
plus: every key has a value in both languages, no value is blank, no pt-BR value equals the English unless the
English is a name or number the glossary keeps, no key is defined twice with different text, and the merge never
changes a key that is already shipped.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LANG = ROOT / "src" / "main" / "resources" / "assets" / "orespawn_integrations" / "lang"
PLAYER = re.compile(r"%(\d+\$)?s|\{player\}|%player%")
LIMITS = {"SIGN": 15, "HANGING": 10}
LINE_LIMIT = {"CHAT": (90, 103), "TITLE": (90, 103), "ACTIONBAR": (90, 103)}
WORDS = {"BOOK": 120, "GUIDE": 200, "OPTION": 6}
SENTENCES = {"BUBBLE": 2, "ADV": 1}


def visible(text: str) -> str:
    """The text as a player sees it: formatting codes out, a player-name placeholder as 12 characters."""
    return PLAYER.sub("x" * 12, re.sub("§.", "", text))


def sentences(text: str) -> int:
    return len([s for s in re.split(r"(?<=[.!?])\s+", text.strip()) if s])


def check_value(key: str, surface: str, lang: str, text: str) -> list:
    problems = []
    seen = visible(text)
    if surface in LIMITS:
        for line in seen.split("\n"):
            if len(line) > LIMITS[surface]:
                problems.append(f"{lang} {key}: {surface} line has {len(line)} characters (limit {LIMITS[surface]}): {line!r}")
    if surface in LINE_LIMIT:
        limit = LINE_LIMIT[surface][1 if lang == "pt_br" else 0]
        if len(seen) > limit:
            problems.append(f"{lang} {key}: {surface} has {len(seen)} characters (limit {limit})")
    if surface in WORDS and len(seen.split()) > WORDS[surface]:
        problems.append(f"{lang} {key}: {surface} has {len(seen.split())} words (limit {WORDS[surface]})")
    if surface in SENTENCES and not key.endswith(".title") and sentences(seen) > SENTENCES[surface]:
        problems.append(f"{lang} {key}: {surface} has {sentences(seen)} sentences (limit {SENTENCES[surface]})")
    return problems


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    release, merge = sys.argv[1], "--merge" in sys.argv
    folder = ROOT / "_lang_fragments" / release
    problems, values = [], {"en_us": {}, "pt_br": {}}
    for keys_file in sorted(folder.glob("*.keys.json")):
        group = keys_file.name[: -len(".keys.json")]
        notes = json.loads(keys_file.read_text(encoding="utf-8"))
        langs = {}
        for lang in ("en_us", "pt_br"):
            f = folder / f"{group}.{lang}.json"
            langs[lang] = json.loads(f.read_text(encoding="utf-8")) if f.exists() else {}
        for key, note in notes.items():
            parts = [p.strip() for p in str(note).split("|")]
            surface = parts[1].upper() if len(parts) > 1 else ""
            for lang in ("en_us", "pt_br"):
                text = langs[lang].get(key)
                if text is None or not str(text).strip():
                    problems.append(f"{group}: {lang} has no text for {key}")
                    continue
                prior = values[lang].get(key)
                if prior is not None and prior != text:
                    problems.append(f"{group}: {key} is defined twice with different {lang} text")
                values[lang][key] = text
                problems += [f"{group}: {p}" for p in check_value(key, surface, lang, text)]
            en, pt = langs["en_us"].get(key), langs["pt_br"].get(key)
            if en and pt and en == pt and re.search(r"[a-z]{4,}", en) and surface not in ("SIGN", "HANGING"):
                problems.append(f"{group}: {key} has the same text in both languages: {en!r}")
        for lang in ("en_us", "pt_br"):
            for extra in sorted(set(langs[lang]) - set(notes)):
                problems.append(f"{group}: {lang} has {extra}, which its keys file doesn't list")
    shipped = {lang: json.loads((LANG / f"{lang}.json").read_text(encoding="utf-8")) for lang in ("en_us", "pt_br")}
    for lang in ("en_us", "pt_br"):
        for key, text in values[lang].items():
            if key in shipped[lang] and shipped[lang][key] != text:
                problems.append(f"{key} would change a shipped {lang} line; give the new line a new key")
    for p in problems:
        print(p)
    print(f"{release}: {len(values['en_us'])} keys, {len(problems)} problems")
    if problems:
        return 1
    if merge:
        for lang in ("en_us", "pt_br"):
            merged = dict(shipped[lang])
            merged.update(values[lang])
            (LANG / f"{lang}.json").write_text(json.dumps(merged, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print("merged into en_us.json and pt_br.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
