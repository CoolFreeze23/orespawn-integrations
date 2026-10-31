"""Writes _lang_fragments/0.10.0/shelf9.keys.json: every translate key the shelf9 group's code and data use, with
the card, where it shows and the card's EN line (first words). Values are written later from the cards' final text.

    python tools/shelf9_keys.py
"""
import json
from pathlib import Path

SG = "structure-hatching-ground"
HS = "item-hatchling-shell"
LS = "item-ledger-scrap"
WO = "system-cronograma-de-obras"
P = "orespawn_integrations.shelf9."

KEYS = {
    "item.orespawn_integrations.hatchling_shell": f"{HS} | item name | Your Eggshell",
    "orespawn_integrations.hatchling_shell.lore": f"{HS} | tooltip line 1 | Eggs, assorted, 1 lot. This was yours.",
    "orespawn_integrations.hatchling_shell.lore_use": f"{HS} | tooltip line 2 | Use to open.",
    "orespawn_integrations.hatchling_shell.open": f"{HS} | chat, on opening | The shell gives up its contents...",
    "orespawn_integrations.hatchling_shell.cap": f"{HS} | chat, the cap line (%s = the Wardrobe key) | The top of your shell is in your Wardrobe...",
    "item.orespawn_integrations.ledger_scrap": f"{LS} | item name | Ledger Scrap",
    "orespawn_integrations.ledger_scrap.lore": f"{LS} | item lore | ...filed under: (torn)",
    "item.orespawn_integrations.guia_de_averbacao": f"{LS} | item name | Correction Slip",
    "orespawn_integrations.guia_de_averbacao.lore_1": f"{LS} | item lore line 1 | Good for one correction. Checked: p.p. B., tomorrow.",
    "orespawn_integrations.guia_de_averbacao.lore_2": f"{LS} | item lore line 2 | Sealed in Sister Court wax.",
    P + "door.1": f"{SG} | SIGN | door sign, left half (line 1) | SHELF 9 ·",
    P + "door.2": f"{SG} | SIGN | door sign, right half (line 1) | EGGS, ASSORTED · 1 LOT",
    P + "batch.1": f"{SG} | SIGN | batch board row 1 (line 2) | Part of the lot",
    P + "batch.2": f"{SG} | SIGN | batch board row 2 (line 2) | misfiled: entered",
    P + "batch.3": f"{SG} | SIGN | batch board row 3 (line 2) | as \"tree\". Check",
    P + "batch.4": f"{SG} | SIGN | batch board row 4 (line 2) | the trunks.",
    P + "shelf10.1": f"{SG} | SIGN | Shelf 10 label row 1 (line 21) | SHELF 10",
    P + "shelf10.2": f"{SG} | SIGN | Shelf 10 label row 2 (line 21) | reserved.",
    P + "shelf10.3": f"{SG} | SIGN | Shelf 10 label row 3 (line 21) | Don't touch.",
    P + "shelf10.4": f"{SG} | SIGN | Shelf 10 label row 4 (line 21) | — B.",
    P + "waystone": f"{SG} | waystone name (line 3) | Shelf 9",
    P + "register.line": f"{SG} | SIGN | Register row, %1$s player %2$s origin %3$s day (line 4) | {{player}} · {{origin}} · day {{day}}",
    P + "register.origin_pending": f"{SG} | SIGN | Register row's origin before one is picked (no card line; builder's addition) | (to be labelled)",
    P + "register.origin_none": f"{SG} | SIGN | Register row's origin without Neo Origins (no card line; builder's addition) | assorted",
    P + "register.overflow": f"{SG} | SIGN | Register overflow, back of the last sign (line 5) | …and {{n}} more",
    P + "register.late_entry": f"{SG} | actionbar, late Register line (line 6) | Egg No. {{n}}, late entry.",
    P + "ways.heading": f"{SG} | SIGN | Ways row 1, %1$s arrow %2$s direction (line 7) | {{arrow}} {{DIRECTION}}",
    P + "ways.distance": f"{SG} | SIGN | Ways row 3 (line 7) | ~{{distance}} blocks",
    P + "ways.dir.north": f"{SG} | SIGN | direction word (line 23) | NORTH",
    P + "ways.dir.east": f"{SG} | SIGN | direction word (line 23) | EAST",
    P + "ways.dir.south": f"{SG} | SIGN | direction word (line 23) | SOUTH",
    P + "ways.dir.west": f"{SG} | SIGN | direction word (line 23) | WEST",
}
for kind, line, thing, comment in [
    ("village", 8, "a village", "People! I think."),
    ("henge", 9, "standing stones", "There's a box. Don't open it. (Open it.)"),
    ("tower", 10, "a tower", "Some guy who thinks he's king. Another one."),
    ("bar", 11, "a bar", "Credit? No."),
    ("hunt_lodge", 12, "the Hunt Lodge", "Bring a knife."),
    ("nothing", 13, "…nothing", "Yet."),
]:
    KEYS[P + f"ways.{kind}.thing"] = f"{SG} | SIGN | Ways row 2 (line {line}) | {thing}"
    KEYS[P + f"ways.{kind}.comment"] = f"{SG} | SIGN | Ways row 4 (line {line}) | {comment}"

CC = "orespawn_integrations.cc."
KEYS.update({
    CC + "refused_unloaded": f"{WO} | operator chat, %1$s thing (Works Schedule line 4) | %thing%: part of the footprint isn't loaded...",
    CC + "refused_close": f"{WO} | operator chat, %1$s thing %2$s blocks (Works Schedule line 5) | %thing%: too close to a bed, chest or Parish Stone...",
    CC + "placed": f"{WO} | operator chat, %1$s thing %2$s-%4$s x y z (Works Schedule line 6) | %thing% placed at %x% %y% %z% and added to the hub list.",
    CC + "upgraded": f"{WO} | operator chat, %1$s thing %2$s pieces %3$s version (Works Schedule line 7) | %thing%: %n% new pieces placed for version %version%.",
    CC + "warn_spawn": f"{WO} | operator chat (Works Schedule line 8) | This moves world spawn and may pause the server...",
    CC + "nothing": f"{SG} | operator chat, %1$s thing (line 22) | Nothing to place for {{thing}} in this version.",
    CC + "refused_twice": f"{WO} | operator chat, %1$s thing %2$s-%4$s the first one's x y z (no card line; Implementation: a second place refuses with its position) | (builder's line) already placed at x y z",
    CC + "hub_full": f"{WO} | operator chat, %1$s thing %2$s the cap (no card line; the hub list holds at most 32) | (builder's line) the hub list is full",
    CC + "failed": f"{SG} | operator chat, %1$s thing (no card line; the template is missing) | (builder's line) could not be placed",
    CC + "hub_list": f"{WO} | operator chat, /cc hub_places header, %1$s count %2$s cap (no card line) | (builder's line) hub list: n of 32",
    CC + "hub_entry": f"{WO} | operator chat, /cc hub_places row, thing x y z dim (no card line) | (builder's line) thing at x y z (dim)",
})

OUT = Path(__file__).resolve().parents[1] / "_lang_fragments/0.10.0/shelf9.keys.json"


def main(extra=None):
    keys = dict(KEYS)
    keys.update(extra or {})
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(keys, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print("wrote", OUT, len(keys), "keys")


if __name__ == "__main__":
    main()
