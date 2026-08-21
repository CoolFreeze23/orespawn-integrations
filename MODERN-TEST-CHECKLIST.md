# orespawn-integrations 0.2.0 — "Modern CrazyCraft" Test Checklist

Boot + world-load verification comes first (biome overrides are world-load-blocking if broken — I test that before handing over). Then work through these in game:

## Boss vaults & keys
- [ ] Kill **Mobzilla** → drops a **Mobzilla Vault Key** (100%). Kill the **Kraken** → **Kraken Vault Key**.
- [ ] Mobzilla Arena now has an **ominous (blue-flame) vault** on the north edge of the magma centerpiece — hologram cycles loot when you get close. Right-click with the key: loot ejects per player (heavy core ~1 in 3, Dragon's Breath books, uranium/titanium blocks). NOTE: only NEWLY generated arenas have the vault — explore fresh chunks.
- [ ] Kraken Lair vault opens with its key → **guaranteed Kraken smithing template** + sea loot + Kraken's Grip books.
- [ ] Using the same key twice on the same vault = rejection sound, nothing (per-player, one reward each).

## Trial chambers (vanilla structure, new chunks only)
- [ ] Find a trial chamber in fresh chunks — some chambers now spawn **Ender Knights / Mantises** (melee), **Scorpions / Lurking Terrors** (small), **Spit Bugs** (ranged) from trial spawners. Roughly 1 in 3 chambers rolls an OreSpawn set.
- [ ] Trial/ominous keys still drop as vanilla. Reward chests + spawner rewards occasionally include uranium/titanium nuggets + rubies.

## Enchantments
- [ ] Enchanting table (Apotheosis one included) can roll **Radioactive** (poison on hit, III) and **Kraken's Grip** (slow on hit, II) on swords — both also apply to the **Mace**.
- [ ] **Dragon's Breath** (ignite, II) never appears in the table — vault books/anvil only.

## Armor trims
- [ ] Smithing table: any vanilla trim template + armor + **ruby / uranium ingot / titanium ingot / queen scale** = colored trim (crimson / acid green / steel blue / orchid). Item sprite shows no overlay (expected) — the worn armor shows it.
- [ ] The **Kraken pattern** template from the vault applies its tentacle-swirl trim with any material; duplicate it with 7 diamonds + prismarine.

## Paintings & archaeology
- [ ] Creative painting item / random placement includes the 6 new ones (The King, Mobzilla Rises, Terror of the Deep, First Date, Flight of the Prince, Crystal Wastes).
- [ ] Brush the **suspicious gravel** patches on the arena floors (4 per arena) → teeth/scales/nuggets/prismarine finds. Trail ruins digs can now surface uranium/titanium/ruby.

## Alive world
- [ ] Kill any OreSpawn boss near a village → chat message, the village **bell rings**, **fireworks** launch over it, hostiles stand down for ~60s (Halloween = orange/purple fireworks).
- [ ] Rare **meteor** on overworld nights: warning flames overhead, impact crater with **uranium + titanium ore**, 3 aliens crawl out. Being within 64 blocks grants **Close Encounter**.
- [ ] Rare daytime **Prince flyover** — white dragon glides across the sky and vanishes. Within ~96 blocks grants **Royal Escort**.
- [ ] Dec 31/Jan 1 nights: firework volleys over village bells.

## Dimension ambience (enter each; MUSIC NEEDS A FRESH WORLD LOAD, not /reload)
- [ ] Crystal dim: drifting end-rod motes + Frozen Peaks music · Mining dim: Dripstone Caves music · Danger/Chaos dim: ash fall + Soul Sand Valley music/moans · Utopia: spore blossoms + Meadow music · Village dim: Forest music · Island dim: Sparse Jungle music.

## Advancements & quests
- [ ] New **Modern Wonders** branch (9): enter a trial chamber → get/use both vault keys → Radioactive gear → template → Kraken trim craft + the two hidden event ones.
- [ ] Quest line continues 703 → **704 The Trials → 705 Vault Heist → 706 Enchanter of Legend**. ⚠ Existing worlds: delete `<world>/customnpcs/orespawn_integrations_quests.installed` so the installer adds 704-706; the old 703 won't auto-chain to 704 in an already-installed world (fresh worlds are fully wired).

**Verdicts wanted:** key/template/trim/painting art (regen scripts exist for all), meteor/flyover rarity feel, vault loot generosity.
