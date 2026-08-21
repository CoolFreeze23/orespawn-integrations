# Tier 2 In-Game Test Checklist — orespawn_integrations 0.1.0 (code pass)

Boot-verified: all 5 wired compat modules active, zero init failures. Keybind reminders for this pack: **R** = morph ability · **G** = transform · **U** = scan · **K** = Curios screen.

## Walkers (the fun one)
- [ ] `/summon orespawn:girlfriend` → scan (U) → morph (G) → press R: she fires her real ranged attack along your crosshair (2s cooldown, bow icon)
- [ ] R aimed at sky and at a far wall — no crash either way (failures log once, never break the game)
- [ ] `/reload` then R again — ability still fires (reload-safe registration)
- [ ] Morph as any OreSpawn hostile → normal hostiles ignore you, but `/summon orespawn:kyuubi` or `the_king` still attacks (bosses see through disguises)
- [ ] **Kill `orespawn:kraken` as a player** → chat: "You have absorbed the shape of Kraken!" → G morphs you into it — even though U-scanning a live Kraken still refuses (blacklist intact)
- [ ] Kill same boss twice → message only fires the first time

## Jade
- [ ] Look at a T-Rex: red "Hostile" + drop hint line · apple_cow: green "Passive" · the_king/godzilla: gold "Boss" · vanilla cow: no extra lines
- [ ] Hit a Kraken in survival → "Vengeful — this Kraken is hunting you!" appears; switch to creative → it clears
- [ ] Look at a placed king_spawner (quick, it fuses) → "Summons: The King" + fuse warning; godzilla_spawn_block → "Only one may awaken per world"
- [ ] Jade settings → orespawn_integrations section shows 5 toggles

## Curios (K to open)
- [ ] All five equip into Charm slots: kraken_repellent, godzilla_scale (+2 hearts, +1 toughness), basilisk_scale (+2 armor), water_dragon_scale (water breathing), tigers_eye_ingot (+0.5 dmg, +1 luck)
- [ ] Two tigers_eye in both slots → bonuses stack
- [ ] Wearing kraken_repellent → a summoned Kraken never targets you; remove it → it does
- [ ] keepInventory OFF: die in `orespawn:mining` wearing all five → all still equipped on respawn; die in overworld → they drop normally

## JEI
- [ ] Search "mobzilla" → scale + armor surface via aliases; hover uranium_nugget → info page explains the 1-ore-1-nugget economy
- [ ] Dimension ant blocks/spawn blocks/ray_gun/royal gear all have info pages
- [ ] Crystal Workbench + Crystal Furnace appear as recipe catalysts (JEI sidebar on crafting/smelting recipes)

## Maps
- [ ] `/locate structure orespawn_integrations:mobzilla_arena` → tp → shrine waystone is named **"Mobzilla Arena Waystone"** (not a random name); kraken_lair → "Kraken Lair Waystone"
- [ ] Activate it → JourneyMap waypoint appears boss-red; village waystones stay default color
- [ ] Survival-break the shrine waystone → refused (dungeon origin)

## Guide + Quests
- [ ] Join with a FRESH player (or wipe your flag) → chat notice + red "OreSpawn Guide" book in inventory; relog → no duplicate; die+relog → still no duplicate
- [ ] NEW world → log: "installed 4 premade OreSpawn quest/dialog file(s)"; `/noppes` → Quest tab → "OreSpawn" category: Hunt: Mobzilla · Miner's Fortune · Tea with the Queen
- [ ] NPC wand → add dialog "The Monster Hunter" to an NPC → talking starts the Mobzilla quest → kill godzilla → completion + reward
- [ ] Restart same world → no reinstall (marker guard)

## Deferred to Tier 3 (by design)
Boss fight music + jukebox discs (needs real audio tracks — you'd supply/commission 3 oggs), girlfriend charm, bespoke morph attacks (Mothra breath etc.), the Apotheosis gun category, JourneyMap structure auto-icons, Nightmare/Criminal/Jumpy Bug.
