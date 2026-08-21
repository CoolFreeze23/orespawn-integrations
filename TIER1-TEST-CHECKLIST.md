# Tier 1 In-Game Test Checklist — orespawn_integrations 0.1.0

Run in a **throwaway creative test world** (not your real one). Anything that fails: note it down — the file territory system means fixes are surgical.

## Loot (2 min)
- [ ] `/loot give @s loot orespawn_integrations:inject/gems_minor` ×10 — mostly gems/nuggets, sometimes nothing
- [ ] `/loot give @s loot orespawn_integrations:inject/boss_trophy` ×20 — always boss materials; ultimate gear ~1/3; big_bertha/royal_guardian_sword rare (~1.6%)
- [ ] `/loot give @s loot minecraft:chests/simple_dungeon` ×5 — vanilla loot + occasional OreSpawn gems
- [ ] `/loot give @s loot twilightforest:chests/tower_library` — OreSpawn items mixed in
- [ ] Reverse: `/loot give @s loot orespawn:chests/crystal_chest` — occasional Aether/Apotheosis items

## Tags + EMC (2 min)
- [ ] Hold `orespawn:ingot_titanium`: `/execute if items entity @s weapon.mainhand #c:ingots/titanium run say OK` (repeat with Marvel's titanium ingot — both should pass)
- [ ] JEI search `#c:ores/ruby`, `#c:storage_blocks/uranium` — orespawn blocks listed
- [ ] ProjectE: ruby EMC = 2,048 · uranium ingot 4,096 · titanium 8,192 · amethyst_gem 2,048 (shard stays 32) · big_bertha/royal sword: **no EMC**

## Walkers morphs (5 min) — R = ability, G = transform, U = scan
- [ ] `/summon orespawn:ruby_bird` → scan (U) → morph (G) → fly; press R → receive a ruby
- [ ] `/summon orespawn:scorpion` → morph → climb a wall; R → poison splash
- [ ] `/summon orespawn:dragon` → morph → stand in fire (immune); R → large fireball
- [ ] `/summon orespawn:trex` → morph → sheep flee from you
- [ ] `/summon orespawn:godzilla` → scanning must FAIL (ultraboss blacklist) — same for kraken, the_king, the_queen, kyuubi

## Apotheosis (5 min)
- [ ] Smith netherite_sword → tigers_eye (4× tigers_eye_ingot) → ultimate (4× uranium ingot); affixes/gems survive both steps
- [ ] Salvaging Table eats ultimate gear → gem dust + rubies
- [ ] `block_uranium` around an enchanting table raises Quanta
- [ ] World tier: summit now additionally needs an OreSpawn dungeon-boss kill; pinnacle needs godzilla/kraken/queen/king
- [ ] JEI: "Heart of Godzilla" + "Eye of the Kraken" gems exist

## Structures (3 min)
- [ ] `/locate structure orespawn_integrations:mobzilla_arena` (badlands/desert) → tp: Mobzilla on the pad, `PersistenceRequired:1b`, waystone shrine beside it (auto-named, dungeon origin)
- [ ] `/locate structure orespawn_integrations:kraken_lair` (deep ocean) → flooded ruin + kraken
- [ ] `/locate biome biomesoplenty:mystic_grove` → fairies/butterflies present; ominous_woods at night → terrors among vanilla mobs (minority)

## Chaos + progression (3 min)
- [ ] `/pandora <tab>` lists our 5 effects; open a few boxes
- [ ] Break ~20 ly lucky blocks — expect ~5 OreSpawn outcomes (if ZERO in 30+: the pack-ordering fix moves to Tier 2)
- [ ] Advancements screen (L): "OreSpawn" tab with Big Bertha icon; `/give @s orespawn:ruby` fires "Shiny!"
- [ ] `/execute in orespawn:mining run tp @s ~ 100 ~` fires the dimension advancement
- [ ] Craft book + ruby → **OreSpawn Guide**; bestiary pages render live models; boss pages show spawner-block recipes
- [ ] Curios GUI: 3 charm slots; basilisk_scale/kraken_repellent/tigers_eye_ingot equip; a stick doesn't; equipping fires "Monster Memento"
