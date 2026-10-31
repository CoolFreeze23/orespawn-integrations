"""Writes Captain Iracema's CustomNPCs files for the 0.10.1 install (npc-iracema, system-boss-ladder):

    python -I tools/customnpcs_hunt.py

into src/main/resources/orespawn_integrations/customnpcs_0101/:
- dialogs/Caca/730-732.json and 741-743.json: her greeting, the Monday board, Work for the Hunt and the three
  quest offers (each available only while its quest is unfinished, so CustomNPCs hides
  a dialog option to it once done);
- quests/OreSpawn/701-703.json, quests/Modern Wonders/704-706.json and dialogs/OreSpawn/701.json: the shipped files
  with every text field turned into one whole-field lang key (CustomNPCs resolves those on the client), the kill
  objectives' targets as keys too (OI's kill bridge counts the kill), and dialog 701 hidden once 701 is done;
- dialogs/Registro/710.json: the Registry desk with one more option, the Captain's board.
The shipped originals stay where they are (QuestCompat's and the Registry's resources); the installer replaces a
world's copy only while it still matches them, so an operator's edit is kept.

The files follow the shipped dialogs' format (SNBT-flavoured JSON read by CustomNPCs' NBTJsonUtil).
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/resources/orespawn_integrations/customnpcs"
OUT = ROOT / "src/main/resources/orespawn_integrations/customnpcs_0101"
K = "orespawn_integrations.iracema."
Q = "orespawn_integrations.quest."

MAIL = """{
        "Subject": "",
        "MailQuest": -1,
        "BeenRead": 0b,
        "MailItems": [
        ],
        "Sender": "",
        "Message": {
        },
        "Time": 0L,
        "TimePast": 0L
    }"""


def option(slot, kind, title, dialog=-1, command=""):
    return """        {
            "Option": {
                "OptionType": %d,
                "Title": "%s",
                "Dialog": %d,
                "DialogCommand": "%s",
                "DialogColor": 14737632
            },
            "OptionSlot": %d
        }""" % (kind, title, dialog, command.replace('"', '\\"'), slot)


def dialog(did, title, text, options, command="", quest=-1, quest_avail=0, quest_id=-1):
    return """{
    "AvailabilityScoreboardObjective": "",
    "AvailabilityDialog3Id": -1,
    "AvailabilityFaction2Stance": 0,
    "AvailabilityDialog4": 0,
    "AvailabilityQuest2": 0,
    "AvailabilityFaction": 0,
    "DecreaseFaction2Points": 0b,
    "AvailabilityQuest3Id": -1,
    "AvailabilityDialog2Id": -1,
    "AvailabilityQuest": %d,
    "OptionFaction2Points": 100,
    "AvailabilityMinPlayerLevel": 0,
    "OptionFaction1Points": 100,
    "AvailabilityQuest4Id": -1,
    "AvailabilityQuest2Id": -1,
    "DecreaseFaction1Points": 0b,
    "DialogQuest": %d,
    "DialogDisableEsc": 0b,
    "AvailabilityDialog3": 0,
    "DialogMail": %s,
    "DialogTitle": "%s",
    "DialogCommand": "%s",
    "AvailabilityDayTime": 0,
    "AvailabilityScoreboard2Type": 1,
    "AvailabilityScoreboard2Objective": "",
    "AvailabilityDialog": 0,
    "AvailabilityDialog4Id": -1,
    "AvailabilityQuestId": %d,
    "OptionFactions2": -1,
    "AvailabilityScoreboardType": 1,
    "AvailabilityDialogId": -1,
    "AvailabilityFactionId": -1,
    "Options": [
%s
    ],
    "DialogText": "%s",
    "AvailabilityDialog2": 0,
    "AvailabilityQuest3": 0,
    "AvailabilityQuest4": 0,
    "DialogShowWheel": 0b,
    "AvailabilityFactionStance": 0,
    "DialogHideNPC": 0b,
    "AvailabilityFaction2": 0,
    "OptionFactions1": -1,
    "AvailabilityScoreboardValue": 1,
    "AvailabilityFaction2Id": -1,
    "DialogId": %d,
    "AvailabilityScoreboard2Value": 1,
    "ModRev": 18
}
""" % (quest_avail, quest, MAIL, title, command.replace('"', '\\"'), quest_id, ",\n".join(options), text, did)


QUIT = 0
GOTO = 1
COMMAND = 4
BEFORE = 2  # EnumAvailabilityQuest.Before: the quest is not finished


def write(rel, content):
    p = OUT / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline="\n")


def field_replace(s, name, value):
    """Replaces a string field's whole value (text may span lines) with value."""
    tag = '"%s": "' % name
    i = s.find(tag)
    assert i >= 0, name
    j = i + len(tag)
    k = j
    while s[k] != '"':
        k += 2 if s[k] == chr(92) else 1
    return s[:j] + value + s[k:]


def main():
    tag_greet = "execute as @dp run tag @s add cc_iracema_ola"
    tag_board = "execute as @dp run tag @s add cc_quadro"
    write("dialogs/Caca/730.json", dialog(730, K + "dialog.greeting.title", K + "dialog.greeting.text", [
        option(0, GOTO, K + "option.board", 731),
        option(1, GOTO, K + "option.work", 732),
        option(2, COMMAND, K + "option.king", command="execute as @dp at @s run function orespawn_integrations:hunt/o_rei"),
        option(3, GOTO, Q + "701.title", 701),
        option(4, QUIT, K + "option.leave"),
    ], command=tag_greet))
    write("dialogs/Caca/731.json", dialog(731, K + "dialog.board.title", K + "dialog.board.text", [
        option(0, GOTO, K + "option.work", 732),
        option(1, QUIT, K + "option.leave"),
    ], command=tag_board))
    write("dialogs/Caca/732.json", dialog(732, K + "dialog.work.title", K + "dialog.work.text", [
        option(0, GOTO, Q + "701.title", 741),
        option(1, GOTO, Q + "702.title", 742),
        option(2, GOTO, Q + "703.title", 743),
        option(3, QUIT, K + "option.leave"),
    ]))
    for qid in (701, 702, 703):
        write(f"dialogs/Caca/{qid + 40}.json", dialog(qid + 40, Q + f"{qid}.title", Q + f"{qid}.offer", [
            option(0, QUIT, Q + "701.option.1"),
        ], quest=qid, quest_avail=BEFORE, quest_id=qid))

    # the shipped quests and dialog 701, re-keyed
    for rel in ["quests/OreSpawn/701.json", "quests/OreSpawn/702.json", "quests/OreSpawn/703.json",
                "quests/Modern Wonders/704.json", "quests/Modern Wonders/705.json", "quests/Modern Wonders/706.json"]:
        qid = re.search(r"(\d+)\.json$", rel).group(1)
        s = (SRC / rel).read_text(encoding="utf-8")
        for f, k in (("Title", "title"), ("Text", "text"), ("CompleteText", "complete")):
            s = field_replace(s, f, f"{Q}{qid}.{k}")
        if qid in ("701", "703"):
            target = {"701": "orespawn:godzilla", "703": "orespawn:the_queen"}[qid]
            assert target in s
            s = s.replace('"Slot": "%s"' % target, '"Slot": "%s%s.alvo"' % (Q, qid))
        write(rel, s)
    s = (SRC / "dialogs/OreSpawn/701.json").read_text(encoding="utf-8")
    s = field_replace(s, "DialogTitle", Q + "701.dialog.title")
    s = field_replace(s, "DialogText", Q + "701.dialog.text")
    s = s.replace('"Title": "I\'ll see it done."', '"Title": "%s701.option.1"' % Q)
    s = s.replace('"Title": "That thing will eat me alive."', '"Title": "%s701.option.2"' % Q)
    s = s.replace('"AvailabilityQuest": 0,', '"AvailabilityQuest": %d,' % BEFORE, 1)
    s = s.replace('"AvailabilityQuestId": -1,', '"AvailabilityQuestId": 701,', 1)
    assert Q + "701.option.2" in s
    write("dialogs/OreSpawn/701.json", s)

    # the Registry desk with the Captain's board
    s = (SRC / "dialogs/Registro/710.json").read_text(encoding="utf-8")
    old = '''        {
            "Option": {
                "OptionType": 0,
                "Title": "orespawn_integrations.odete.opcao.sair",
                "Dialog": -1,
                "DialogCommand": "",
                "DialogColor": 14737632
            },
            "OptionSlot": 4
        }'''
    assert old in s
    new = option(4, COMMAND, K + "option.desk_board",
                 command="execute as @dp at @s run function orespawn_integrations:hunt/quadro_balcao") + ",\n" + \
        old.replace('"OptionSlot": 4', '"OptionSlot": 5')
    write("dialogs/Registro/710.json", s.replace(old, new))
    print("written to", OUT)


if __name__ == "__main__":
    main()
