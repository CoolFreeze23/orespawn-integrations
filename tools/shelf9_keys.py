"""Writes the shelf9 group's lang fragments for 0.10.0, beside each other in _lang_fragments/0.10.0/:
- shelf9.keys.json: every translate key the group's code and data use, as "<card id> | <SURFACE> | <where> | <EN, first words>";
- shelf9.en_us.json and shelf9.pt_br.json: the text, exactly as the cards write it (pt-BR written natively on the cards).

    python tools/shelf9_keys.py

Card placeholders ({n}, %thing%, ...) become Minecraft's positional %1$s, %2$s... in the order the code passes them.
Lines no card writes (marked "builder" in the where column) are written fresh in the spine's voices. The script checks
the spine's text limits: a SIGN line 15 characters, CHAT/ACTIONBAR/TITLE 90 (pt-BR 103), a placeholder counted at its
widest (a player name 12, a number 5).
"""
import json
import re
from pathlib import Path

SG = "structure-hatching-ground"
HS = "item-hatchling-shell"
LS = "item-ledger-scrap"
WO = "system-cronograma-de-obras"
S9 = "orespawn_integrations.shelf9."
CC = "orespawn_integrations.cc."

# key, card, surface, where, EN, pt-BR
ROWS = [
    ("item.orespawn_integrations.hatchling_shell", HS, "ITEM", "item name (line 1)", "Your Eggshell", "Sua Casca"),
    ("orespawn_integrations.hatchling_shell.lore", HS, "ITEM", "tooltip (line 2)",
     "Eggs, assorted, 1 lot. This was yours.", "Ovos, sortidos, 1 lote. Esta era a sua."),
    ("orespawn_integrations.hatchling_shell.lore_use", HS, "ITEM", "tooltip, second line (line 3)",
     "Use to open.", "Use para abrir."),
    ("orespawn_integrations.hatchling_shell.open", HS, "CHAT", "on opening (line 4)",
     "The shell gives up its contents. It was mostly packaging.",
     "A casca entrega o conteúdo. Era quase tudo embalagem."),
    ("orespawn_integrations.hatchling_shell.cap", HS, "CHAT", "the cap line, from function hatchling_shell/cap (line 5)",
     "The top of your shell is in your Wardrobe. Open your Wardrobe to put it on.",
     "A tampa da sua casca tá no seu Guarda-Roupa. Abre o Guarda-Roupa pra vestir."),
    ("item.orespawn_integrations.ledger_scrap", LS, "ITEM", "item name (line 3)",
     "Ledger Scrap", "Retalho do Livro-Razão"),
    ("orespawn_integrations.ledger_scrap.lore", LS, "ITEM", "item lore (line 4)",
     "...filed under: (torn)", "...lançado em: (rasgado)"),
    ("item.orespawn_integrations.guia_de_averbacao", LS, "ITEM", "item name (line 5)",
     "Correction Slip", "Guia de Averbação"),
    ("orespawn_integrations.guia_de_averbacao.lore_1", LS, "ITEM", "item lore, first line (line 6)",
     "Good for one correction. Checked: p.p. B., tomorrow.", "Vale uma emenda. Conferido: p.p. B., amanhã."),
    ("orespawn_integrations.guia_de_averbacao.lore_2", LS, "ITEM", "item lore, second line (line 7)",
     "Sealed in Sister Court wax.", "Lacrado em cera da Corte Irmã."),

    (S9 + "door.1", SG, "SIGN", "door sign row 1 (line 1)", "SHELF 9", "PRATELEIRA 9"),
    (S9 + "door.2", SG, "SIGN", "door sign row 2 (line 1)", "EGGS, ASSORTED", "OVOS SORTIDOS"),
    (S9 + "door.3", SG, "SIGN", "door sign row 3 (line 1)", "1 LOT", "1 LOTE"),
    (S9 + "batch.1", SG, "SIGN", "batch board row 1 (line 2)", "Part of the lot", "Parte do lote"),
    (S9 + "batch.2", SG, "SIGN", "batch board row 2 (line 2)", "misfiled as", "lançada como"),
    (S9 + "batch.3", SG, "SIGN", "batch board row 3 (line 2)", "\"tree\".", "\"árvore\"."),
    (S9 + "batch.4", SG, "SIGN", "batch board row 4 (line 2)", "Check trunks.", "Ver os troncos."),
    (S9 + "shelf10.1", SG, "SIGN", "Shelf 10 label row 1 (line 21)", "SHELF 10", "PRATELEIRA 10"),
    (S9 + "shelf10.2", SG, "SIGN", "Shelf 10 label row 2 (line 21)", "reserved.", "reservada."),
    (S9 + "shelf10.3", SG, "SIGN", "Shelf 10 label row 3 (line 21)", "Don't touch.", "Não mexer."),
    (S9 + "shelf10.4", SG, "SIGN", "Shelf 10 label row 4 (line 21)", "— B.", "— B."),
    (S9 + "waystone", SG, "TITLE", "waystone name (line 3)", "Shelf 9", "Prateleira 9"),
    (S9 + "register.overflow", SG, "SIGN", "Register overflow, back of the last sign; %1$s the count (line 5)",
     "…and %1$s more", "…e mais %1$s"),
    (S9 + "register.late_entry", SG, "ACTIONBAR", "late Register line; %1$s the line number (line 6)",
     "Egg No. %1$s, late entry.", "Ovo nº %1$s, lançado fora do prazo."),
    (S9 + "ways.heading", SG, "SIGN", "Ways row 1; %1$s arrow, %2$s direction word (line 7)", "%1$s %2$s", "%1$s %2$s"),
    (S9 + "ways.distance", SG, "SIGN", "Ways row 3; %1$s blocks, rounded to 10 (line 7)",
     "~%1$s blocks", "~%1$s blocos"),
    (S9 + "ways.dir.north", SG, "SIGN", "direction word (line 23)", "NORTH", "NORTE"),
    (S9 + "ways.dir.east", SG, "SIGN", "direction word (line 23)", "EAST", "LESTE"),
    (S9 + "ways.dir.south", SG, "SIGN", "direction word (line 23)", "SOUTH", "SUL"),
    (S9 + "ways.dir.west", SG, "SIGN", "direction word (line 23)", "WEST", "OESTE"),
]
WAYS = [
    ("village", 8, "a village", "People! I think", "uma vila", "Gente! Acho."),
    ("henge", 9, "standing stones", "Don't open. Do.", "pedras em pé", "Não abre. Abre."),
    ("tower", 10, "a tower", "Another 'king'.", "uma torre", "Mais um \"rei\"."),
    ("bar", 11, "a bar", "Credit? No.", "um boteco", "Fiado? Não."),
    ("hunt_lodge", 12, "the Hunt Lodge", "Bring a knife.", "o Pavilhão", "Traga uma faca."),
    ("nothing", 13, "…nothing", "Yet.", "…nada", "Ainda."),
]
for kind, line, thing_en, comment_en, thing_pt, comment_pt in WAYS:
    ROWS.append((S9 + f"ways.{kind}.thing", SG, "SIGN", f"Ways row 2 (line {line})", thing_en, thing_pt))
    ROWS.append((S9 + f"ways.{kind}.comment", SG, "SIGN", f"Ways row 4 (line {line})", comment_en, comment_pt))

ROWS += [
    (CC + "refused_unloaded", WO, "CHAT", "operators, roadmap 4.7; %1$s thing",
     "%1$s: part of the footprint isn't loaded. Stand on the spot and try again.",
     "%1$s: parte da área não está carregada. Fique no local e tente de novo."),
    (CC + "refused_close", WO, "CHAT", "operators, roadmap 4.7; %1$s thing, %2$s blocks",
     "%1$s: too close to a bed, chest or Parish Stone (%2$s blocks).",
     "%1$s: perto demais de uma cama, baú ou Marco da Freguesia (%2$s blocos)."),
    (CC + "placed", WO, "CHAT", "operators, roadmap 4.7; %1$s thing, %2$s %3$s %4$s x y z",
     "%1$s placed at %2$s %3$s %4$s and added to the hub list.",
     "%1$s colocado em %2$s %3$s %4$s e incluído na lista do hub."),
    (CC + "upgraded", WO, "CHAT", "operators, roadmap 4.7; %1$s thing, %2$s pieces, %3$s version",
     "%1$s: %2$s new pieces placed for version %3$s.",
     "%1$s: %2$s peças novas colocadas para a versão %3$s."),
    (CC + "warn_spawn", WO, "CHAT", "operators, roadmap 4.7",
     "This moves world spawn and may pause the server for a few seconds.",
     "Isto muda o spawn do mundo e pode travar o servidor por alguns segundos."),
    (CC + "nothing", SG, "CHAT", "operators, roadmap 4.7 (line 22); %1$s thing",
     "Nothing to place for %1$s in this version.", "Nada a colocar para %1$s nesta versão."),
    (CC + "refused_twice", WO, "CHAT", "operators, builder (a second place of a one-off); %1$s thing, %2$s %3$s %4$s its x y z",
     "%1$s is already placed at %2$s %3$s %4$s. One is enough.",
     "%1$s já está colocado em %2$s %3$s %4$s. Um basta."),
    (CC + "hub_full", WO, "CHAT", "operators, builder (the hub list is full); %1$s thing, %2$s the cap",
     "%1$s: the hub list is full (%2$s places). Nothing was placed.",
     "%1$s: a lista do hub está cheia (%2$s lugares). Nada foi colocado."),
    (CC + "failed", SG, "CHAT", "operators, builder (the template is missing); %1$s thing",
     "%1$s: the template is missing from this jar. Nothing was placed.",
     "%1$s: o molde não está neste jar. Nada foi colocado."),
    (CC + "hub_list", WO, "CHAT", "operators, builder (/cc hub_places header); %1$s count, %2$s cap",
     "Hub list: %1$s of %2$s places.", "Lista do hub: %1$s de %2$s lugares."),
    (CC + "hub_entry", WO, "CHAT", "operators, builder (/cc hub_places row); %1$s thing, %2$s-%4$s x y z, %5$s dimension",
     "%1$s at %2$s %3$s %4$s (%5$s)", "%1$s em %2$s %3$s %4$s (%5$s)"),
]

LIMITS = {"SIGN": (15, 15), "CHAT": (90, 103), "ACTIONBAR": (90, 103), "TITLE": (90, 103)}
WIDTH = {"ways.heading": 7}  # an arrow, a space and the longest direction word, NORTE/OESTE


def width(key, text):
    """The text's length with each placeholder counted at its widest (a name or thing 12, a number 5)."""
    for suffix, w in WIDTH.items():
        if key.endswith(suffix):
            return w
    return len(re.sub(r"%\d+\$s", "x" * 5, text)) if "%" in text else len(text)


def main():
    out = Path(__file__).resolve().parents[1] / "_lang_fragments/0.10.0"
    out.mkdir(parents=True, exist_ok=True)
    keys, en, pt = {}, {}, {}
    problems = []
    for key, card, surface, where, text_en, text_pt in ROWS:
        keys[key] = f"{card} | {surface} | {where} | {text_en[:40]}"
        en[key] = text_en
        pt[key] = text_pt
        if surface in LIMITS:
            lim_en, lim_pt = LIMITS[surface]
            for lang, text, lim in (("en", text_en, lim_en), ("pt", text_pt, lim_pt)):
                if width(key, text) > lim:
                    problems.append(f"{key} ({lang}): {width(key, text)} > {lim}")
    for name, data in (("shelf9.keys.json", keys), ("shelf9.en_us.json", en), ("shelf9.pt_br.json", pt)):
        (out / name).write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print("wrote", len(keys), "keys to", out)
    if problems:
        raise SystemExit("over the spine's text limits:\n  " + "\n  ".join(problems))


if __name__ == "__main__":
    main()
