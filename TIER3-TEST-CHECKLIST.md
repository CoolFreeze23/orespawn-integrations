# Tier 3 Test Checklist — the progression update (+ bug fixes)

## Bug fixes from your last report
- [ ] **Scanning works as scan-to-swap now**: hold U on the girlfriend → unlocks (replaces your current shape). Same for kyuubi, anything. (Config: `unlockOverridesCurrentShape=true`)
- [ ] **Shrine waystones**: `/place structure orespawn_integrations:mobzilla_arena ~ ~ ~` → 5×5 shrine dais with waystone attached to the arena's **north edge**. (Old already-generated arenas won't retro-grow one; fresh chunks will. Kraken Lair intentionally has no shrine — it's underwater.)

## The progression tree (L → OreSpawn tab)
- [ ] **9 chapters, 109 advancements**: Surface Riches → Ant Farm Frontier → Mining Dimension → Dungeon Delving → The Menagerie → Crystal Ascension → Tower Challenges → Ultrabosses → Ultimate Craftsman
- [ ] `/give @s orespawn:ruby` → "first ruby" toast starts Surface Riches
- [ ] **Part-collection progress**: open the `chains` advancements (Bertha blade/guard/handle parts, ultimate arsenal, popcorn chain, zoo cage ladder) — hovering shows per-part checkmarks as you collect each ingredient
- [ ] Big Bertha completion = challenge frame + 500 XP + announce
- [ ] Fun set (18): try pizza bite (place pizza, eat a slice), duct-tape a damaged Big Bertha, ride the hoverboard, gift the girlfriend, cage a critter, lava-fish in the Mining dim…
- [ ] Dragon "taming" advancement fires on *riding* it (the port never calls tame() for dragons — verified quirk)

## Bespoke morph attacks (after scanning the mob, R fires it)
- [ ] Mothra → wing gust · Basilisk → petrifying gaze · Kraken (kill to absorb) → tentacle grab pull · Dragon → fire line
- [ ] Generic mobs still use their Tier-1/2 abilities; check the log stays clean on repeated use

## Heart Locket (new item!)
- [ ] JEI: "Heart Locket" — gold ingots + ruby + poppy recipe, pixel-art texture renders
- [ ] Equip in a charm slot (K): Regeneration I while a girlfriend/boyfriend is within 8 blocks; survives death everywhere
- [ ] Rare drop from girlfriend loot

## Gun affixes
- [ ] Ray gun / squid zooka roll Apotheosis affixes now (reforge one at a Reforging Table) — check the notes in JEI for which category they landed in

## JourneyMap structure icons
- [ ] Explore near a challenge tower / kyuubi dungeon / our arenas → icons appear on the map as chunks render
- [ ] If any icon spam or errors appear, tell me — the feature disables itself for the session on first failure by design

## Deferred (needs your input or a dedicated session)
Boss fight music (supply 3 oggs when ready) · Nightmare/Criminal/Jumpy Bug ports (best done in the port repo — a dedicated session) · Kraken Lair seabed shrine (say the word)
