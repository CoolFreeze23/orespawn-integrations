"""Generates docs/effort.csv from the cards' effort clauses (system-cronograma-de-obras, section 1).

    python tools/gen_effort_csv.py <cards dir> [out.csv]

Reads the last "effort" clause of every live card's `cost` field (cards with status `cut` are left out) and writes one
row per card and release: card, tier, project, release, letters, points. A card with two parts in one release (for
example `S@OI-0.31.0 + S@OI-0.31.0`) gets one row with both letters and their sum. Letters: D 0, S 1, M 2, L 4.
Clauses with no release (`D (shipped)`, `D (rides on ...)`) carry no points and make no row. The build's
checkEffortTally task reads the table; regenerate it whenever a card's clause changes.
"""
import csv
import re
import sys
from collections import OrderedDict
from pathlib import Path

POINTS = {"D": 0, "S": 1, "M": 2, "L": 4}
PART = re.compile(r"\b([DSML])@([A-Z]+)-(v?\d[\w.\-]*?\d)(?=[\s,;)+]|$)")
FIELDS = ["card", "tier", "project", "release", "letters", "points"]


def frontmatter(text):
    match = re.match(r"---\r?\n(.*?)\r?\n---", text, re.S)
    fields = {}
    if match:
        for line in match.group(1).splitlines():
            key, sep, value = line.partition(":")
            if sep:
                fields[key.strip()] = value.strip().strip('"')
    return fields


def effort_clause(cost):
    """The text after the last 'effort' in the cost field (the clause is the field's last)."""
    i = cost.rfind("effort")
    return cost[i + len("effort"):].lstrip(": ") if i >= 0 else ""


def rows_for(card_id, tier, clause):
    parts = OrderedDict()
    for letter, project, release in PART.findall(clause):
        key = (project, release)
        parts.setdefault(key, []).append(letter)
    for (project, release), letters in parts.items():
        yield {
            "card": card_id,
            "tier": tier,
            "project": project,
            "release": release,
            "letters": "+".join(letters),
            "points": sum(POINTS[l] for l in letters),
        }


def version_key(release):
    nums = re.findall(r"\d+", release)
    return [int(n) for n in nums]


def main(cards_dir, out):
    rows = []
    for path in sorted(Path(cards_dir).glob("*.md")):
        fields = frontmatter(path.read_text(encoding="utf-8"))
        if not fields or fields.get("status") == "cut":
            continue
        card_id = fields.get("id", path.stem)
        rows.extend(rows_for(card_id, fields.get("tier", ""), effort_clause(fields.get("cost", ""))))
    rows.sort(key=lambda r: (r["project"], version_key(r["release"]), r["card"]))
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8", newline="\n") as f:
        writer = csv.DictWriter(f, fieldnames=FIELDS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    print("wrote", out, len(rows), "rows")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    default_out = Path(__file__).resolve().parents[1] / "docs/effort.csv"
    main(sys.argv[1], Path(sys.argv[2]) if len(sys.argv) > 2 else default_out)
