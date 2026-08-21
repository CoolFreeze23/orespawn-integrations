# Cross-Mod Threads — 0.3.x design directive (user-approved 2026-08-17)

North star: fun, whole, discoverable — reward fantasies, not punishment. Anything whose
hook is "this hurts you" is reframed to "this empowers you". All 26 menu items kept,
re-aimed into five named threads. Verify every tweak against the pack jars before
building (same law as the original menu). Full menu detail: workflow wf_7c80cdb3-a11
journal (curated 26 of 49).

## THREAD 4 — "Big Game" (combat feel; SHIPS FIRST)
17. Big Bertha Better Combat — ~10 weapon_attributes JSONs (claymore/double_axe/rapier/
    battlestaff presets), full arsenal, ship first.
18. Mobzilla Plating — TF travellers vest modifier, 4 godzilla scales, as written.
19. Backpacks x3 (Mobzilla-hide / Kraken / Girlfriend) — art is its own small task;
    Girlfriend pack cross-links Thread 3's advancement tree.
20. Emperor's Chitin Band — Artifacts components, dropped by Emperor Scorpion.
21. Angel Insurance Policy — 3 tag files (detector anomaly, stealable rubies, Bertha
    hurts angels). Petty theft = story, not grief.
22. Decoy Ore Mines — WITH fairness tell: subtle shimmer particle + faint tick (verify
    SecurityCraft surface); defused/survived mines drop a "Suspicious Ore" trophy block.

## THREAD 1 — "It Was Always Uranium" (power)
1. GEIGER PROSPECTING (reframed from Geiger-Hot Cargo): NO player irradiation. HBM
   geiger = ore-dowsing rod, ticks faster near OreSpawn uranium/titanium/ruby veins.
2. Radioactive Enchanting Corner — as-is (Quanta shelf + Radioactive II infusion).
3. Uranium Collector Chain — gated by the reviewed EMC table (policy 3).
4. Uranium Arc Reactor — keep; tooltip/advancement: "Palladium nearly killed Tony.
   This is fine."
5. Bottled Uranium — cauldron fluid + faucets kept; DRINKING = "Uranium Rush" buff
   (glowing outline + night vision + speed, brief), not poison.
6. Uranium-belt A-10 ammo (Air Cavalry half) — thread endgame is literally a warthog.

## THREAD 2 — "The Royal Court" (boss economy; every item gets early/mid/post-boss gate)
7.  Witherite-Tempered RGS — gate post-Cataclysm-boss (anvil is earned).
8.  Boss-School Spell Scrolls — Godzilla=Fire, Kraken=Ice, King=Lightning, Queen=Holy,
    alien=Ender; drop rates tuned to be an economy, rare enough to trade.
9.  Royal Dragon Egg — long incubation; hatchling = living trophy.
10. Pre-bound Boss Soul Shards — bound at LOW TIER only; full kill-grind still applies.
11. Royal Scale Upgrade Orbs — same advancement branch as scrolls ("boss materials
    power magic" reads as one idea).
12. Royal Boss-Loot Uncrafting — the Court's sink; closes the economy loop.
13. Queen-to-Queen Tribute — as written + hidden advancement "Diplomacy".

## THREAD 3 — "Her Side of the Story" (Girlfriend arc; ships as one narrative)
14. Girlfriend's Diary — the hint book; pages reference the Tome and Familiar; Heart
    Locket hint + Prince flyover complaints as written.
15. Tome of the Girlfriend — as written; diary's later pages hint location.
16. Girlfriend Familiar — stretch build kept; jealousy debuff is the payoff; diary's
    final page teases it. NEW, VERIFY: "Date Night" gift chain — cross-mod flowers/
    foods unlock diary pages + small permanent buffs to her AI (connective quest).

## THREAD 5 — "The World Remembers" (atmosphere)
23. Rite of Gojira + Kraken rite — thunderstorm REQUIRED for Gojira (verify Enchanted
    condition support); Kraken rite wants rain + ocean biome.
24. Mobzilla Statue — full catalyst grind; ship uranium mob-killer catalyst (pure data)
    immediately; other-boss statues proposed as follow-up.
25. Crystalline World Transmutation — EMC-gated per policy 3.
26. OreSpawn Rail Works — reframed as world-building (industry acknowledges the ores).
    Also here: Air Cavalry's helicopter recipes fixing mcheli's missing survival
    recipes entirely — promote in changelog.

## POLICIES (apply to all 26)
1. Zero hard deps — ModList.isLoaded / recipe conditions; removed mod breaks nothing.
2. Every thread individually toggleable in config.
3. One reviewed EMC table for every OreSpawn item ProjectE touches — no default-EMC
   dupe surfaces.
4. Light-code integrations cite API class + version verified against, with a
   version-range note.
5. Player-path testing for gameplay contracts (mine tell, prospecting tick, shard
   binding, incubation, rite conditions); pure-JSON recipes verify via JEI eyeball.
6. Advancement tree per thread, pure JSON, doubling as documentation; changelog in the
   "Danger looked at raw uranium and said it's fine" voice.

## SEQUENCING
Thread 4 → 1 → 2 → 3 → 5. Each thread: verify → build → advancements → gated commit →
changelog section. Verify/cut verdicts on flagged items (mine tell support, rite
conditions, Date Night plumbing) presented before building them. Nothing cut — only
re-aimed.
