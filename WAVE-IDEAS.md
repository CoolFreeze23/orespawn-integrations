# Alive-World Mod Wave — installed manifest + OreSpawn integration idea menu (2026-08-17)

## Installed (24 jars, all native NeoForge)
- **yungs-api** `YungsApi-1.21.1-NeoForge-5.1.6.jar` v1.21.1-NeoForge-5.1.6 — deps: none listed
- **yungs-better-dungeons** `YungsBetterDungeons-1.21.1-NeoForge-5.1.4.jar` v1.21.1-NeoForge-5.1.4 — deps: yungs-api (required) — installed in this batch
- **yungs-better-mineshafts** `YungsBetterMineshafts-1.21.1-NeoForge-5.1.1.jar` v1.21.1-NeoForge-5.1.1 — deps: yungs-api (required) — installed in this batch
- **yungs-better-strongholds** `YungsBetterStrongholds-1.21.1-NeoForge-5.1.3.jar` v1.21.1-NeoForge-5.1.3 — deps: yungs-api (required) — installed in this batch
- **yungs-better-desert-temples** `YungsBetterDesertTemples-1.21.1-NeoForge-4.1.5.jar` v1.21.1-NeoForge-4.1.5 — deps: yungs-api (required) — installed in this batch
- **yungs-better-ocean-monuments** `YungsBetterOceanMonuments-1.21.1-NeoForge-4.1.2.jar` v1.21.1-NeoForge-4.1.2 — deps: yungs-api (required) — installed in this batch
- **yungs-better-nether-fortresses** `YungsBetterNetherFortresses-1.21.1-NeoForge-3.1.5.jar` v1.21.1-NeoForge-3.1.5 — deps: yungs-api (required) — installed in this batch
- **yungs-better-witch-huts** `YungsBetterWitchHuts-1.21.1-NeoForge-4.1.1.jar` v1.21.1-NeoForge-4.1.1 — deps: yungs-api (required) — installed in this batch
- **ct-overhaul-village** `[Neoforge]ctov-3.6.3.jar` v3.6.3 — deps: none declared
- **towns-and-towers** `t_and_t-fabric-neoforge-1.13.11.jar` v1.13.11 — deps: cristel-lib (missing -> downloaded); cristel-lib's own dep cloth-config already in pack (cloth-config-15.0.140-neoforge.jar)
- **structory** `Structory_26.2_v1.3.7.jar` v1.3.17 (file labeled 26.2_v1.3.7) — deps: none declared
- **structory-towers** `Structory_Towers_26.2_v1.0.17.jar` v1.0.17 — deps: none declared
- **incendium** `Incendium_1.21.x_v5.4.4.jar` v5.4.4 (alpha - only alphas exist for 1.21.1) — deps: none declared
- **dungeons-and-taverns** `dungeons-and-taverns-v4.4.4.jar` vv4.4.4+mod — deps: none declared (mod distribution, self-contained)
- **when-dungeons-arise-seven-seas** `DungeonsAriseSevenSeas-1.21.x-1.0.4-neoforge.jar` v1.0.4 — deps: base When Dungeons Arise already in pack (DungeonsArise-1.21.1-2.1.68-release.jar); no deps declared on Modrinth
- **cristel-lib** `cristellib-neoforge-1.21.1-3.1.7.jar` vneoforge-1.21.1-3.1.7 — deps: cloth-config already in pack
- **serene-seasons** `SereneSeasons-neoforge-1.21.1-10.1.0.3.jar` v10.1.0.3 — deps: glitchcore required [2.1.0.0,) -> satisfied by existing GlitchCore-neoforge-1.21.1-2.1.0.2.jar (not replaced); neoforge [21.1,) OK. Note: 10.1.0.3 is the newest 1.21.1 build and is beta-channel (no release-type builds exist for 1.21.1).
- **ambientsounds** `AmbientSounds_NEOFORGE_v6.3.8_mc1.21.1.jar` v6.3.8 — deps: creativecore required -> was missing, downloaded (see creativecore entry).
- **creativecore** `CreativeCore_NEOFORGE_v2.13.43_mc1.21.1.jar` v2.13.43 — deps: none (installed as required dependency of ambientsounds).
- **guard-villagers** `guardvillagers-2.4.10-1.21.1.jar` v2.4.10 — deps: no mod dependencies (only neoforge [20.4,) + minecraft [1.21.1,1.22)).
- **friends-and-foes** `friendsandfoes-neoforge-4.0.27+mc1.21.1.jar` vneoforge-4.0.27+mc1.21.1 — deps: resourcefullib required [3.0.12,) -> satisfied by existing resourcefullib-neoforge-1.21-3.0.12.jar; yacl optional -> already present (yet_another_config_lib_v3-3.6.6). NOTE: neoforge builds live under separate Modrinth project 'friends-and-foes-forge' (main 'friends-and-foes' project is fabric/quilt-only).
- **naturalist** `naturalist-2.0.3-neoforge-1.21.1.jar` v2.0.3+1.21.1-neoforge — deps: geckolib NOT required: Naturalist 2.0.3 declares no geckolib dependency in neoforge.mods.toml and the jar contains zero geckolib class references (2.x dropped GeckoLib), so existing geckolib-neoforge-1.21.1-4.9.2.jar is untouched and trivially compatible. Requires neoforge [21.1.234,) -> pack is on 21.1.248, OK. Optional lambdynlights_api not present, not needed.
- **doggy-talents-next** `DoggyTalentsNext-1.21.1-1.19.0.jar` v1.19.0 — deps: none required — Modrinth dependencies[] is empty for this version; mod is standalone
- **endrem** `endrem-neoforge-1.21.1-6.3.0.jar` v6.3.0 — deps: none required — Modrinth dependencies[] is empty for this version; mod is standalone
- **lithostitched** `lithostitched-1.7.13-neoforge-21.1.jar` — installed by verifier (ctov required dep missed by Modrinth metadata)

## Skipped
- infernal-mobs: No 1.21.1 neoforge or fabric build exists. The Modrinth 'infernal-mobs' project is a Paper server plugin (loaders: [paper], up to 1.21 only), and a Modrinth search for mod-type projects matching 'infernal mobs' with versions:1.21.1 returned zero hits. Expected absent; the pack's Apotheosis elite-mob system covers the niche.

## STABILIZATION OUTCOME — FINAL (2026-08-17 late)
Fresh-world chunk gen crashed (`Parent chunk missing`). TRUE root cause (found after a
misleading bisect — the visible exception was downstream): **YungsApi 5.1.6's
BeardifierMixin NPEs** (`EnhancedBeardifierHelper.computeDensity` on null
`getEnhancedPieceIterator()`) when other Beardifier-mixing mods (marvel, irons_spellbooks)
are present; structure-dependent, so bisect verdicts were seed-noisy. FIX: removed
`BeardifierMixin` from `yungsapi.mixins.json` inside the YungsApi jar (original in
disabled-mods/*.orig — RE-PATCH on YungsApi updates). Cost: YUNG structures use vanilla
terrain blending (cosmetic). Lithium, lithostitched, and CTOV were exonerated and
RESTORED. Verified stable on two fresh seeds, 100s in-world each, full 186-jar roster.
Also fixed en route: Structory_Towers toml patch (missing modLoader), particle-rain
duplicate-modid (Fabric jar → disabled-mods/), ModernFix release_protochunks=false
override (left in place, harmless).

## Pack fix applied during verification
- Duplicate modid `particlerain` (particle-rain-3.0.5.jar Fabric vs Pretty Rain-1.21.1-NeoForge-1.1.4.jar) would hard-crash next launch; Fabric jar moved to disabled-mods/.

## Integration idea menu (52, proposals only — nothing built)
### [yungs-better-dungeons] Dungeon Delver's Cut  *(data)*
Every Better Dungeons chest can cough up rubies, amethyst gems, or a rare Miner's Dream — the pack's dungeon_delving advancement branch finally has YUNG-sized dungeons to feed it.
> neoforge:add_table GLM (same pattern as the addon's existing loot_modifiers/) targeting the 8 verified tables: betterdungeons:skeleton_dungeon/chests/common & middle, small_dungeon/chests/loot_piles, small_nether_dungeon/chests/common, zombie_dungeon/chests/common & special, spider_dungeon/chests/egg_room, zombie_dungeon/chests/tombstone.

### [yungs-better-dungeons] Tombstone Hauntings  *(light_code)*
Zombie-dungeon tombstone chests carry graveyard loot — and on Halloween the addon spawns a friendly orespawn:ghost when one opens. The graveyard structure's lore leaks into YUNG's crypts.
> Loot: GLM on betterdungeons:zombie_dungeon/chests/tombstone (verified). Halloween spawn: CelebrationHandler already gates on danger.orespawn.util.SeasonalDates.isHalloween(); add a chest-open (PlayerContainerEvent) check in the alive package.

### [yungs-better-dungeons] Egg Room Surprise  *(data)*
The spider dungeon's egg room occasionally hatches OreSpawn's own creepy-crawlies: a spit_bug or cave_fisher spawn egg in the chest. The nest was never just spiders.
> GLM on betterdungeons:spider_dungeon/chests/egg_room (verified) adding orespawn:spit_bug_spawn_egg / orespawn:cave_fisher_spawn_egg at low weight.

### [yungs-better-mineshafts] Mining Dim Mineshafts  *(data)*
Better Mineshafts thread through the OreSpawn Mining Dimension — abandoned rails winding past uranium and titanium veins is exactly what that dimension always wanted to look like.
> Append orespawn:mining_biome to the verified biome tags data/bettermineshafts/tags/worldgen/biome/has_structure/better_mineshaft_* (pick one variant, e.g. better_mineshaft_dripstone). Verify at build time that the structure set actually places in a non-overworld dimension (YUNG placement may filter by dimension).

### [yungs-better-mineshafts] Miner's Dream Cargo  *(data)*
Mineshaft chests carry titanium nuggets and, rarely, a Miner's Dream — the old miners were digging for Danger's ores all along.
> Better Mineshafts ships zero loot tables (verified) and reuses vanilla minecraft:chests/abandoned_mineshaft; one GLM on that id covers it. Note endrem also injects there (endrem:minecraft/chests/abandoned_mineshaft) — weights should respect the eye economy.

### [yungs-better-strongholds] Ender Knight Armoury  *(data)*
Stronghold armoury chests hold ender-pearl blocks and (rarely) an Ender Knight spawn egg; crypts get ghost_skelly remains. The stronghold garrison clearly answered to the Ender Knight.
> GLM on betterstrongholds:chests/armoury and betterstrongholds:chests/crypt (both verified in jar).

### [yungs-better-strongholds] Grand Library Marginalia  *(data)*
The grand library's chest shelves the addon's guide book and a Girlfriend's Diary page hint — Thread 3's paper trail runs through YUNG's best room.
> GLM on betterstrongholds:chests/grand_library (verified); items already exist in the addon (guide book, Thread 3 diary pages when built).

### [yungs-better-strongholds] Treasure Room Vault Shard  *(data)*
The stronghold treasure room can hold a Kraken or Mobzilla vault key at trace odds — the one non-boss source, priced as a lottery ticket.
> GLM on betterstrongholds:chests/treasure (verified) adding orespawn_integrations:kraken_vault_key / mobzilla_vault_key (existing addon items) at very low weight.

### [yungs-better-desert-temples] Pharaoh's Tiger's Eye  *(data)*
The pharaoh's hidden hoard glitters with tiger's eye ingots and tools — a desert-flavored OreSpawn metal in the temple that deserves it.
> GLM on betterdeserttemples:chests/tomb_pharaoh and chests/pharaoh_hidden (verified) adding tigers_eye_ingot and a low-weight tigers_eye tool/armor roll.

### [yungs-better-desert-temples] Scorpion Tomb Trap  *(light_code)*
Opening the pharaoh's tomb chest has a chance to wake an Emperor Scorpion — a mummy's curse with an OreSpawn accent, and Thread 4's Chitin Band gets a themed source.
> Chest-open handler in the addon (PlayerContainerEvent / loot-table-id check on betterdeserttemples:chests/tomb_pharaoh, verified) spawning orespawn:emperor_scorpion; fits Better Desert Temples' existing trap-room identity.

### [yungs-better-ocean-monuments] Kraken Trophy Chamber  *(data)*
The monument's side chamber chest holds kraken teeth, water dragon scales, and sea viper tongues — the Guardians were collecting trophies from the pack's sea bosses.
> GLM on betteroceanmonuments:chests/upper_side_chamber — the mod's only chest table (verified).

### [yungs-better-ocean-monuments] How the Guardians Slept  *(data)*
Same chest, rare kraken_repellent block: an environmental-storytelling wink at how a monument survives in Kraken waters.
> Second entry in the same GLM on betteroceanmonuments:chests/upper_side_chamber adding orespawn:kraken_repellent at low weight.

### [yungs-better-nether-fortresses] Relic of the Nether Lost  *(data)*
The fortress worship room enshrines OreSpawn's nether_lost item — a lore relic in the one room built for relics.
> GLM on betterfortresses:chests/worship (verified) with guaranteed-ish nether_lost plus flavor loot.

### [yungs-better-nether-fortresses] Uranium Beacon Cache  *(data)*
Beacon and obsidian vault chests hold uranium ingots — Thread 1 canon says the blaze-lit fortress reactor was always running on Danger's fuel.
> GLM on betterfortresses:chests/beacon and chests/obsidian (verified) adding ingot_uranium / uranium_nugget; crosslink the Thread 1 advancement tree.

### [yungs-better-witch-huts] Witch's Odd Ingredients  *(data)*
Witch hut chests brim with brewing oddities only this pack could supply: dead irukandji, dead stink bugs, moth seeds.
> GLM on betterwitchhuts:chests/hut_0 — the mod's only chest table (verified).

### [yungs-better-witch-huts] Witch Eye on the Shelf  *(data)*
A rare endrem:witch_eye in the hut chest — two new mods shaking hands, and a merciful alternate source for the eye witches already drop.
> Same GLM on betterwitchhuts:chests/hut_0 adding endrem:witch_eye at low weight (endrem item ids verified; endrem already has a witch GLM, so keep this rare).

### [ct-overhaul-village] Village Pantries, OreSpawn Produce  *(data)*
CTOV farm, bakery, and forager chests stock corn cobs, tomatoes, strawberries, radishes, and salad — villages that visibly farm the pack's crops.
> GLM on ctov:chests/village/village_farm, village_bakery, village_forager (all verified among ctov's 54 chest tables).

### [ct-overhaul-village] Smiths Who Know Titanium  *(data)*
The village smith's chest carries titanium nuggets and the odd ingot — CTOV blacksmiths participate in the pack's metal economy.
> GLM on ctov:chests/village/village_smith (verified).

### [ct-overhaul-village] Library Rumors  *(data)*
Village libraries shelve the addon's guide book and structure rumor notes pointing at OreSpawn landmarks — discoverability for the pack's own dungeons, delivered by villagers.
> GLM on ctov:chests/village/village_library (verified); optionally add exploration_map entries to orespawn structures via the addon's existing orespawn structure/biome tags.

### [towns-and-towers] Dig Up the Old World  *(data)*
T&T archaeology sites brush up trex teeth, worm teeth, and rock-crystal shards — the addon's archaeology story (it already injects vanilla suspicious sand) extends to Towns & Towers digs.
> GLM on kaisyn:archeology/forest_ruins_common and forest_ruins_rare (verified; T&T's loot lives under the kaisyn namespace). Addon precedent: data/minecraft/loot_table/archaeology already shipped.

### [towns-and-towers] Coastal Outposts Fear the Kraken  *(data)*
Beach and mediterranean outpost barrels hide kraken_repellent and a skate bow — seaside folk in this pack know exactly what's in the water.
> GLM on kaisyn:outpost/exclusives/outpost_beach_barrel and outpost_mediterranean_barrel (verified).

### [structory] Bandit Fences Move Rubies  *(data)*
Structory bandit camps fence stolen rubies — a data-only prologue to Thread 4's Angel Insurance 'stealable rubies' story.
> GLM on structory:outcast/generic/bandit and structory:outcast/bandit/desert (verified) adding orespawn:ruby.

### [structory] The Ruined Miners Knew  *(data)*
Miner-camp ruins hold uranium nuggets and a rare Miner's Dream — Thread 1's Geiger Prospecting fantasy foreshadowed in worldgen.
> GLM on structory:outcast/generic/miner (verified).

### [structory-towers] Wizard Tower Attic  *(data)*
The wizard tower's top floor can hold a thunder_staff — climbing the tallest Structory tower pays out the pack's most theatrical weapon.
> GLM on structory_towers:top/wizard_top (verified; the jar's 1-21-x overlay dirs don't matter since a GLM keys on the runtime loot_table_id).

### [structory-towers] Lighthouse Keeper's Rod  *(data)*
Lighthouse tops stash a sun_fish and, rarely, the Ultimate Fishing Rod — the keeper was fishing for more than cod.
> GLM on structory_towers:top/lighthouse_top (verified).

### [structory-towers] Tower-Top Eye Caches  *(data)*
Pillager and fortress tower tops rarely hold an End Remastered eye, turning Structory's skyline into waypoints on the End hunt.
> GLM on structory_towers:top/pillager_top and top/fortress_top (verified) adding endrem:old_eye at low weight.

### [incendium] It Was Always Uranium: The Reactor  *(data)*
Incendium literally ships a Nether reactor structure — its waste and treasure chests now leak OreSpawn uranium, and a Thread 1 advancement canonizes whose fuel it burns.
> GLM on incendium:reactor/waste, reactor/treasure, reactor/office_treasure (all verified in the jar's data/incendium/loot_table/reactor/) adding ingot_uranium, uranium_nugget, rare block_uranium.

### [incendium] Lab Findings  *(data)*
The Incendium lab's treasure holds OreSpawn acid and a researcher's note teasing the 'Uranium Rush' buff — the lab was studying Thread 1's bottled uranium.
> GLM on incendium:lab/treasure and lab/rare (verified) adding orespawn:acid plus a minecraft:written_book_content-composed lore book.

### [incendium] Honorary Court: Hovering Inferno  *(light_code)*
Downing Incendium's minibosses fires the addon's boss celebration in fire-palette colors — the Royal Court acknowledges the Nether's nobility.
> CelebrationHandler entity-id hook on death events for Incendium boss entities (entity loot dirs incendium:entity/ and hovering_inferno/ verified in jar); soft-dep via id string match, no classload.

### [dungeons-and-taverns] Tavern Menu Expansion  *(data)*
D&T taverns serve pizza, crabby patties, cooked bacon, and salad — the pack's kitchen canon on every tavern shelf.
> GLM on nova_structures:chests/tavern_quest and nova_structures:pots/pot_tavern (both verified in the jar).

### [dungeons-and-taverns] Mansion Secret: Royal Contraband  *(data)*
The illager mansion's secret room hides a queen_scale or a Thread 2 spell scroll — the illagers were smuggling Royal Court artifacts.
> GLM on minecraft:chests/illager_mansion/secret_room (verified — D&T ships its mansion tables under the minecraft namespace).

### [when-dungeons-arise-seven-seas] Kraken-Hunter Fleet  *(data)*
Every Seven Seas ship's treasure hold carries kraken teeth, sea viper tongues, and skate bows — the pirate fleet sails these waters to hunt the pack's Kraken.
> GLM on dungeons_arise_seven_seas:chests/unicorn_galleon/unicorn_galleon_treasure, corsair_corvette/corsair_corvette_treasure, pirate_junk/pirate_junk_treasure, small_yacht/small_yacht_treasure (all verified).

### [when-dungeons-arise-seven-seas] Pirates Know Better  *(data)*
Pirate junk barrels hide wall_kraken_repellent — the deckhands nail it to the mast, and observant players learn the mechanic from loot alone.
> GLM on dungeons_arise_seven_seas:chests/pirate_junk/pirate_junk_barrels (verified) adding orespawn:kraken_repellent / wall variant item.

### [when-dungeons-arise-seven-seas] Chart to the Water Dragon's Lair  *(data)*
Galleon captains carry a weathered chart to the Water Dragon Lair — an exploration map that stitches WDA's ocean into OreSpawn's sea-boss geography.
> GLM on the galleon/corvette treasure tables using the vanilla minecraft:exploration_map loot function with a structure tag on orespawn:water_dragon_lair (structure id verified in ORESPAWN-IDS; addon already ships orespawn worldgen tags, so adding the tag is one file).

### [serene-seasons] Crops on the Calendar  *(data)*
OreSpawn's farm becomes seasonal: corn/tomato/strawberry ripen in summer, radish/lettuce in spring, quinoa/rice in autumn — while the magic plants (experience, moth, firefly) ignore winter entirely.
> Append orespawn crop blocks (corn_0-3, tomato_0-3, lettuce_0-3, quinoa_0-3, strawberry_plant, radish_plant, rice_plant) and seed items to the verified tags sereneseasons:{spring,summer,autumn,winter,year_round}_crops (block+item variants all present in jar).

### [serene-seasons] Seasons Drive the Celebrations  *(light_code)*
CelebrationHandler graduates from real-world clock checks to in-game seasons: SeasonHelper decides the fireworks palette, and SeasonChangedEvent triggers a seasonal Prince flyover. The addon's SeasonalDates events become playable weather, not calendar trivia.
> sereneseasons.api.season.SeasonHelper + SeasonChangedEvent$Standard (both verified in jar) consumed behind the addon's ModList.isLoaded guard; SeasonalDates stays as fallback when Serene Seasons is absent.

### [serene-seasons] Dimension Season Policy  *(data)*
The Mining and Chaos dimensions don't do seasons; Utopia is eternal growing season. One tag file each and the pack's six dimensions feel deliberately climate-zoned.
> Append orespawn biomes to verified biome tags sereneseasons:infertile_biomes / blacklisted_biomes (mining_biome, chaos_biome) and tropical_biomes (utopia_plains); double-check Serene Seasons' whitelisted-dimensions server config for the non-overworld dims.

### [guard-villagers] Guards Join the Celebration  *(light_code)*
When a Royal boss falls near a village, the guards salute: shields raised, celebratory crossbow volleys timed to the addon's fireworks. Boss celebrations gain a crowd.
> CelebrationHandler proximity scan for guardvillagers:guard entities (entity + loot tables verified in jar), issuing shield-raise pose and firework-shot behavior via entity AI nudges; soft-dep by entity id.

### [guard-villagers] Standing Orders: OreSpawn Threats  *(config)*
Guards actually defend against the pack's village menaces — rats, stinkies, leaf monsters, aliens go on the guards' target list out of the box.
> Ship pack-level guardvillagers-common.toml with orespawn ids added to MobWhiteList (config field verified via GuardConfig$CommonConfig strings: 'Guards will additionally attack mobs ids put in this list').

### [guard-villagers] Royal Guard Requisition  *(data)*
Guards can spawn wearing amethyst or ruby armor — village defenders visibly equipped from the pack's own armory.
> GLM on guardvillagers:entities/guard_armor and entities/armor_sets/armor (both verified loot tables the mod rolls for guard equipment) adding orespawn armor pieces at modest weight.

### [friends-and-foes] Rascal's Real Treasure  *(data)*
Win the rascal's hide-and-seek and its good-reward pouch can contain rubies or a Miner's Dream — the mineshaft imp pays in the pack's currency.
> GLM on friendsandfoes:rewards/rascal_good_reward (verified table).

### [friends-and-foes] Iceologer's Ice Balls  *(data)*
Iceologers drop OreSpawn ice_ball ammo — the cloud-caller literally hands you its own attack to throw back.
> GLM on friendsandfoes:entities/iceologer (verified) adding orespawn:ice_ball.

### [friends-and-foes] Wildfire's Crown Burns Forever  *(data)*
The wildfire rare-drops an extreme_torch — a trophy light source with a story, from the boss whose crown never goes out.
> GLM on friendsandfoes:entities/wildfire (verified) adding orespawn:extreme_torch at low chance.

### [naturalist] One Food Chain  *(light_code)*
Naturalist predators learn the local menu: great whites chase gold_fish, lions stalk gazelles, snakes eat OreSpawn rats. Two ecosystems become one.
> NearestAttackableTargetGoal injection on EntityJoinLevelEvent for naturalist:great_white_shark, lion, snake vs orespawn prey entities (all entity ids verified both sides); same soft-dep pattern as the addon's walkers compat.

### [naturalist] Crab Is Crab  *(data)*
Naturalist crabs, Friends & Foes crabs, and OreSpawn crab meat all feed the crabby patty — one c: tag ends the pack's three-crab schism.
> New c:foods/raw_crab_meat item tag (addon already maintains data/c/tags/item/) spanning orespawn raw_crab_meat + naturalist/friendsandfoes crab drops (crab entities verified in both jars); switch the crabby_patty recipe input to the tag.

### [naturalist] The Menagerie Grows  *(data)*
Mammoth, komodo dragon, blobfish and friends join the addon's menagerie advancement wing — the zoo keeper's cage collection doubles overnight.
> New advancement JSONs in the existing orespawn_integrations:advancement/menagerie tree keyed on caging/encountering naturalist entities (46 entity loot tables verified for id list); uses the existing caged_mob/zoo_keeper items.

### [doggy-talents-next] Rice Is Rice  *(data)*
DTN and OreSpawn both grow rice; a shared tag makes either crop feed both mods' recipes instead of two incompatible rices sitting in one pack.
> c:crops/rice + c:foods/rice item tags spanning doggytalents rice (rice_crop/rice_mill verified in jar) and orespawn rice/rice_seed; recipe overrides in the addon swap literal items for the tag.

### [doggy-talents-next] Victory Backflips  *(light_code)*
When the boss-kill fireworks start, every nearby dog does a backflip. That's it. That's the feature the pack will be remembered for.
> DTN ships a backflip dog animation (assets/doggytalents/doggytalents/dog_animations/backflip.json verified); CelebrationHandler triggers it on nearby doggytalents dogs via DTN's animation-trigger API — verify the exact API entrypoint/packet before building.

### [doggy-talents-next] Big Game Hound  *(light_code)*
A hidden advancement for having a Guard Dog-talent companion survive a Royal boss kill at your side: 'Who's a Good Boy? Certified.'
> GuardDogTalent verified as a DTN talent class; addon custom criterion fired from the existing boss-kill handling, checking a nearby doggytalents:dog with the talent (talent lookup via DTN API, soft-dep).

### [endrem] The Royal Eye  *(config)*
A pack-exclusive End Remastered eye found only in the addon's Kraken/Mobzilla boss vaults — the eye hunt's final chapter runs through OreSpawn's endgame, exactly where this pack's heart is.
> End Remastered 6.x loads custom eyes from JSON files in its config eye folder (JsonEye class verified: fields rarity, loot_to_inject_id, loot_tables_id; auto-registers endrem:<name> items). Ship a royal_eye JSON whose loot_tables_id points at the addon's vault loot tables, plus item texture/model under assets/endrem (addon already ships foreign-namespace assets).

### [endrem] Vortex Sees the End  *(data)*
OreSpawn's vortex_eye finally earns its name: a recipe transmutes it into a Magical Eye, gated behind killing the Vortex. Danger's weirdest drop becomes a legitimate step to the End.
> Plain crafting recipe JSON in the endrem recipe namespace pattern (data/endrem/recipe/witch_eye.json et al verified as ordinary recipes) taking orespawn:vortex_eye and outputting endrem:magical_eye, with recipe unlock via a Vortex-kill advancement.

### [endrem] Ender Castle Joins the Hunt  *(data)*
OreSpawn's Ender Castle and Ender Knight Dungeon chests can hold a Lost or Cursed Eye — the pack's own End structures become canonical stops on the End Remastered pilgrimage.
> GLM (neoforge:add_table, same mechanism endrem itself uses per its verified loot_modifiers) targeting orespawn:chests/ender_castle and orespawn:chests/ender_knight_dungeon adding endrem:lost_eye / cursed_eye at low weight.

### [ambientsounds] Voices of Six Dimensions  *(data)*
Each OreSpawn dimension gets a soundscape: dripping-shaft ambience with faint geiger ticks in Mining, glassy chimes in Crystal, birdsong Utopia, droning Chaos. The dimensions stop sounding like silent overworld clones.
> AmbientSounds resolves per-dimension engine JSONs — assets/ambientsounds/basic/dimensions/<name>.json, with third-party examples (twilightforest.json, betweenlands.json) verified in the jar; ship orespawn dimension entries in the addon's assets following that schema.

### [ambientsounds] Crystal Plains Shimmer  *(data)*
Biome-level shimmer layers for crystal_plains and utopia_plains so crossing a biome line audibly changes the world.
> Same AmbientSounds engine format at biome granularity (the basic pack's dimension files carry biome selectors); verify the exact biome-matching selector syntax against the CreativeCore/AmbientSounds engine docs before authoring.
