# OreSpawn Integrations 0.3.0 - "Cross-Mod Threads"

Twenty-six ideas, five threads, zero hard dependencies. Every feature below quietly
steps aside if its partner mod goes missing, and every thread has its own on/off
switch in the config (`threads.*`, flip it, `/reload`, done). One documented
exception to that rule lives in Thread 4; see the carve-out note.

---

## Thread 4: Big Game (combat feel)

Danger looked at a ten-foot sword that swings like a butter knife and said we can
fix that. Then he kept going.

- Big Bertha learns Better Combat. The big OreSpawn weapons get real move-sets
  through Better Combat preset files: Bertha sweeps as a claymore, the Queen
  Battle Axe swings like a double axe, the Thunder Staff does battlestaff combos
  between lightning bolts, and the Fairy Sword fences like a rapier. The presets
  are pure data, read by Better Combat's own loader. The full weapon-to-preset
  mapping is in `data/orespawn/weapon_attributes/`.
  - Carve-out note: a few Thread 4 data sets ship ungated by the
    `threads.big_game` config toggle. Each has a reason, and all are inert when
    their partner mod is absent. The `weapon_attributes` files are a deliberate
    exception because Better Combat reads them with its own resource loader,
    which never sees NeoForge conditions. The Weeping Angels, Artifacts and
    tool-tier tag merges are ungated because NeoForge 21.1's TagLoader ignores
    conditions entirely. The block self-drop loot tables for mines, trophies and
    backpacks are ungated on purpose: if you toggle the thread off mid-world,
    already-placed blocks must still drop themselves. We don't punish.
- Mobzilla Plating puts four Godzilla scales onto a Twilight Forest Traveller's
  Vest. The boss took the hit, repeatedly, so you don't have to. That's the whole
  idea of armor.
- Three boss-hide backpacks: Mobzilla-Hide, Kraken and Girlfriend skins for
  Traveler's Backpack. The Girlfriend pack winks at Thread 3's story arc. Yes, the
  Kraken one has sucker prints. No, they don't do anything. They're suckers.
- The Emperor Scorpion now drops the Emperor's Chitin Band, a wearable band with
  Artifacts-powered venom abilities. Danger looked at concentrated scorpion venom
  strapped to a wrist and said it's fine. It is, actually, because it fights for
  you now.
- Angel Insurance Policy: three tag files stitch Weeping Angels into OreSpawn.
  The Timey-Wimey Detector pings OreSpawn anomalies, angels can pocket loose
  rubies (petty theft is a story, not grief), and Big Bertha is on the shortlist
  of things allowed to hurt an angel. Don't blink. She doesn't.
- Decoy Ore Mines: some ore veins were never ore. They are SecurityCraft mines
  wearing an OreSpawn costume. Every one of them carries a fairness tell (a subtle
  shimmer and a faint tick), the blast is tuned for drama over craters, and
  defusing or surviving one pays out a **Suspicious Ore** trophy block. The vein
  disagreed; you won anyway.

The whole thread is documented in-game as the **Big Game** advancement tab: eight
nodes, one per fantasy, readable as release notes with a progress bar.

---

## Thread 1: It Was Always Uranium (power)

Danger looked at raw uranium and said it's fine. The pack's power mods looked at
Danger's uranium and started taking notes. Nothing in this thread hurts you, and
that is on purpose.

- Geiger Prospecting: carry NTM's geiger counter and it moonlights as an
  ore-dowsing rod. You get a lazy click near buried rubies, a steady chatter over
  titanium, and the full panic song standing on an OreSpawn uranium vein. Your
  dose stays at zero the entire time; right-click the counter and watch it swear
  to that. We drive HBM's telemetry channel, never its radiation. Detection and
  harm are separate wires, and we only touch the one that can't bite.
- Radioactive Enchanting Corner: blocks of uranium now shelve like bookcases and
  read nearly pure Quanta, so the enchanting table gets volatile and you get
  options. Feed a uranium ingot to a maxed-out Apothic altar and it infuses into
  a **Radioactive II** book: strikes poison the target. It is perfectly safe for
  the wielder and notably less so for everyone else.
- Uranium Collector Chain: ProjectE prices Danger's fuel on the reviewed table. A
  uranium ingot condenses at nine diamonds, and the nugget sits at exact
  aeternalis parity, so the collector chain trades fuel for fuel one to one. The
  chain is an exchange, never a printer, and it has been checked for dupes.
- Uranium Arc Reactor: Marvel's reactor line learns what Danger buried. The
  **Dirty Arc Reactor** is an arc reactor core rebuilt around an OreSpawn uranium
  slug. Palladium nearly killed Tony. This is fine.
- Bottled Uranium is a cauldron fluid that makes the cauldron literally glow. You
  bottle it by right-click, and yes, you can drink it. The **Uranium Rush** gives
  you a glowing outline, night vision and a burst of speed. It is never poison,
  and it is deliberately EMC-less and recipe-less: the condenser doesn't get to
  brew it, you get it from the glowing pot like a person of culture.
- A-10 Uranium Belts: the thread's endgame is literally a warthog. An additive
  MCHeli content pack (`mcheli/orespawn_uranium/`) adds the DU-belt A-10 and its
  GAU-8 DU cannon, resupplied with uranium ingots. MCHeli matches ammo by registry
  path and ignores the namespace, so Danger's uranium and HBM's uranium both feed
  the belt; the warthog doesn't check the label. Stock aircraft are untouched (the
  port is first-source-wins by design).

The thread is documented in-game as the **It Was Always Uranium** advancement
tab, with six nodes from first ping to warthog. The warthog one stays hidden:
content packs can't grant advancements, so consider it a standing invitation.
  - Carve-out note: two Thread 1 pieces ship ungated by the `threads.uranium`
    toggle. Each has a structural reason, and both are inert when their partner
    mod is absent. The MCHeli content pack is game-dir instance content read by
    MCHeli's own loader, which never sees NeoForge conditions (additive ids only,
    stock files untouched). The Amendments can_glow tag merge for the cauldron
    glow is ungated because NeoForge 21.1's TagLoader ignores conditions entirely.
    Bottled Uranium's missing EMC is an omission, not a file, so there is nothing
    to gate.

---

## Thread 2: The Royal Court (boss economy)

Every royal drop is currency now. The Court mints five kinds of it (scrolls,
scales, shards, orbs, and one very good egg), and everything is gated the way a
court should be: earn the audience first, then the treasury opens.

- Witherite-Tempered Royal Guardian Sword: the Mechanical Fusion Anvil takes six
  witherite ingots to build, and witherite drops from exactly one creature, The
  Harbinger. The anvil is earned. Temper the RGS there and it comes back harder:
  749 to 861 damage, a quicker swing, fireproof, and renamed to match its new
  attitude. Your enchantments and your custom name survive the forge; the Court
  upgrades, it never confiscates.
- Boss-School Spell Scrolls: each royal teaches one school. Godzilla breathes
  Fire, the Kraken deals Ice, the King throws Lightning, the Queen keeps Holy, and
  the alien brood hoards Ender. Drop rates are tuned as an economy, not a faucet:
  rare enough to trade, common enough to chase, with Looting sweetening the odds
  and the King and Queen carrying a rare max-level jackpot. The guaranteed-max
  scroll is the Court's trade currency.
- Royal Dragon Egg: press eight queen scales around an egg, then incubate it
  through a full day of warming in the Aether's incubator. The Prince hatches and
  imprints on the first face he sees, and he follows you home as a living trophy.
- Pre-Bound Boss Soul Shards: royal bosses can drop soul shards already bound to
  them. They start at tier one, so 64 of the 1024 kills come as a welcome gift.
  The remaining 960 are still your problem. The grind stays honest; only the
  introduction is free.
- Royal Scale Upgrade Orbs: boss materials power magic. Royal scales press into
  upgrade orbs keyed to the same five boss schools, riding Iron's Spellbooks' own
  orb system as pure data. Scrolls and orbs share one advancement branch because
  they're one idea wearing two hats.
- Royal Boss-Loot Uncrafting is the Court's sink. Twilight Forest's Uncrafting
  Table buys back royal gear for queen scales, and the sword alone refunds six.
  Surplus loot melts down, scales flow back into scrolls, orbs and eggs, and the
  economy loop closes. Nothing royal is ever a dead end.
- Queen-to-Queen Tribute: bring queen scales to the Bumblezone's Bee Queen and
  she pays in royal jelly and honey crystal shards. There's a hidden advancement
  in it for the first ambassador.

The thread is documented in-game as **The Royal Court** advancement tab: eight
nodes along an early, mid and post-boss spine, from your first scroll to the
witherite temper.
  - Carve-out note: one Thread 2 file ships ungated by the `threads.royal_court`
    toggle. The Bee Queen trade JSON is read by the Bumblezone's own trade loader,
    which never sees NeoForge conditions. It is inert when the Bumblezone is
    absent, and it only ever *adds* a trade.

---

## Thread 3: Her Side of the Story (Girlfriend arc)

She has been in this pack since the beginning, and in all that time nobody asked
her opinion of it. This thread is her side, told in her own handwriting, one
earned page at a time. We are not going to quote the diary in the release notes.
She'd know.

- Girlfriend's Diary: three Bibliocraft volumes in her own hand, earned and never
  found. What's written in them stays between the two of you, but expect strong
  opinions about a certain Prince's flyovers, a pointed hint about the Heart
  Locket and, late in the second volume, directions worth following.
- Date Night: eight gifts from across the pack say what poppies can't. There are
  Biomes O' Plenty roses, lavender and pink daffodils, an Aether white flower, an
  Ars Nouveau sourceberry, dessert off her own mod's menu, and one very serious
  teddy bear. Every gift she accepts marks a diary milestone and leaves a small
  permanent buff on *her*: more hearts, a quicker stride, thicker armor. The buffs
  go on her, never on you, because you're courting her, not farming her. And no,
  handing her a teddy bear does not equip it as a weapon. We checked. She was
  going to.
  - Poppies and dandelions still mean exactly what they've always meant to her.
    Those are hers and we didn't touch them. The heart box of chocolates already
    belongs to OreSpawn Delight's kitchen; we notice the gesture, we don't
    intercept it.
- The finale: a Decocraft rose-gold engagement ring closes the chain, opens the
  last volume, and changes the question from "do you like these" to "come with
  me."
- Tome of the Girlfriend: it is out in the world where the rumors always said it
  was. The diary's later pages narrow the search; what happens when you find it
  is between you and the diary.
- Girlfriend Familiar: finish the chain and she learns Ars Nouveau's oldest
  trick, coming along. The familiar unlock arrives with the ring, and so does one
  guarantee in writing: the binding ritual only ever answers to a *wild*
  girlfriend. A tamed companion can never be consumed by it. We checked that
  twice, on purpose.

The whole arc documents itself in-game as the **Her Side of the Story**
advancement chain: one node per milestone, and the later nodes keep her secrets
until you've earned them.

---

## Thread 5: The World Remembers (atmosphere)

The idea behind this thread: OreSpawn happened, and the rest of the world has
started acting like it. Witches summon its kings, alchemists paint in its colors,
industry rolls its metals, and somebody finally built the statue.

- Rite of Gojira and Rite of the Kraken: Enchanted's circle magic learns two
  names it should be afraid of. The rite itself checks the weather: Gojira's
  circle only answers under a genuine thunderstorm, and the Kraken wants rain
  overhead and open ocean underfoot. Read the sky wrong and the circle hands
  every ingredient straight back with a drumroll, so a failed rite costs you
  nothing but the walk home. Bring chalk and scales, wait for the storm, then run.
- Mobzilla Statue: the full catalyst grind ends in the pack's worst day rendered
  in stone, and the statue works for a living like every other statue on the
  table. It also brought a gift for the whole garden: a single OreSpawn uranium
  ingot on the statue table is now a straight alternate route to the mob-killer
  upgrade for *any* statue. Danger looked at a statue armed with raw uranium and
  said it's fine. The statue agrees. Other royals in stone are on the follow-up
  list.
- Crystalline World Transmutation: the Philosopher's Stone picks up local color.
  Stone, soil and timber transmute into Crystal Dimension blocks at a touch. It
  costs stone charge like any other transmutation and answers to the reviewed
  EMC table like everything else in this addon; the condenser gains nothing it
  didn't already have.
- OreSpawn Rail Works: Railcraft's crusher now accepts all five silk-touched
  OreSpawn ores, with the math done in public. Ruby crushes generous, salt
  crushes cheap and cheerful, and titanium and uranium carry the lowest
  multipliers in the shop. They sit deliberately under Railcraft's own diamond
  rate, because "rarer than diamond" stays true even inside a rock crusher.
  Uranium ore leaves sulfur dust behind, because industry always leaves something
  behind. The rolling machine answers back: titanium and obsidian dust roll into
  sixteen reinforced rails, and three titanium ingots stretch into sixteen
  lengths of rebar. That is double what steel manages either way, because the
  best rail metal in the pack should act like it.
- The MCHeli fix: the aircraft files have carried `AddRecipe` lines since day
  one, and the port parses them faithfully into a field nothing ever reads. That
  was dead data, so there were never any recipes. This addon ships real survival
  crafting recipes for the hangar: the AH-64 Apache Longbow, the EC665 Tiger, the
  A-10 the uranium crowd has been waiting for, the B-2A Spirit, the MH-53E Sea
  Dragon, and one Merkava Mk4, because somebody in the motor pool got jealous.
  This is the first time in this pack's life a survival player can craft any of
  them.

The thread is documented in-game as **The World Remembers** advancement branch:
five nodes, from thunderstorm to takeoff.
  - Carve-out note: a few Thread 5 files ship ungated by the
    `threads.world_remembers` toggle, each for the usual structural reason. The
    Mobzilla statue's block self-drop loot table is ungated on purpose, as
    always: flip the thread off mid-world and an already-placed statue still
    drops itself, because we don't punish. The two statues tag joins
    (`statues:statues/upgradeable` and `lootable`, optional entries) are ungated
    because NeoForge 21.1's TagLoader ignores conditions entirely, and both are
    inert without the statue.

---

## 0.3.1 - The World Wakes Up

Twenty-four new mods moved into the pack overnight: YUNG rebuilt every dungeon,
the seasons started turning, and the ocean grew a pirate problem. This wave
stitches all of it to OreSpawn. Same house rules as ever: zero hard dependencies,
every file steps aside if its partner mod is missing, and the whole wave hangs off
one switch (`threads.alive_world`, flip it, `/reload`, done). Reward, never
punish. Exceptions to the gating rule are listed in the carve-out note at the
bottom, each with its reason.

- YUNG's suite (Better Dungeons, Mineshafts, Strongholds, Desert Temples, Ocean
  Monuments, Nether Fortresses, Witch Huts): seven structure mods where YUNG
  builds the rooms and Danger stocks them. Dungeon chests cough up rubies,
  amethyst, and the rare Miner's Dream; zombie-dungeon tombstones carry graveyard
  loot (and on Halloween, a friendly ghost); the spider dungeon's egg room was
  never just spiders. Mineshafts thread titanium through their rails and wind
  through the Mining Dimension. Stronghold armouries answered to the Ender
  Knight, the grand library shelves our guide book, and the treasure room sells
  vault-key lottery tickets. The pharaoh hoarded tiger's eye, and opening his
  tomb can wake an Emperor Scorpion, because mummy's curses localize. Ocean
  monuments display trophies from the pack's sea bosses (and the kraken repellent
  that kept the Guardians sleeping). Fortress vaults run on uranium and enshrine
  the Nether Lost. Witch huts stock ingredients no vanilla witch could source.
- ChoiceTheorem's Overhauled Village: village pantries grow OreSpawn produce, the
  smith keeps titanium in the back, and the library spreads rumors about where
  the pack's own landmarks are. Villagers gossip; we just wrote it down.
- Towns & Towers: archaeology digs brush up trex teeth and rock crystal, and
  coastal outposts keep kraken repellent by the door. Seaside folk know what's in
  the water.
- In Structory & Structory: Towers, bandit camps fence stolen rubies, ruined miner
  camps prove Thread 1 right, the wizard's attic hides a thunder staff, the
  lighthouse keeper was fishing for more than cod, and tower tops hold End
  Remastered eyes. The skyline is now a waypoint network for the End hunt.
- Incendium: the Nether reactor runs on Danger's uranium. Waste and treasure
  chests leak it, and the lab was bottling it. Downing Incendium's minibosses
  earns a fire-palette salute from the boss celebration. The Royal Court
  acknowledges the Nether's nobility.
- Dungeons & Taverns: taverns serve pizza, crabby patties, and salad off the
  pack's own menu, and the illager mansion's secret room smuggles Royal Court
  contraband. The illagers were fencing queen scales. Of course they were.
- In When Dungeons Arise: Seven Seas, the pirate fleet sails these waters to hunt
  our Kraken. Treasure holds carry kraken teeth, sea viper tongues, and skate
  bows; galleon captains chart courses to the Water Dragon's lair; and the
  deckhands stash wall-mounted kraken repellent in the barrels, because pirates
  learn fast or sink.
- Serene Seasons: OreSpawn's farm joins the calendar. Corn ripens in summer,
  radishes in spring, quinoa in autumn, and the magic plants ignore winter out of
  principle. The celebration system graduates from wall-clock holidays to in-game
  seasons, with a seasonal Prince flyover when they turn. The Mining and Chaos
  dimensions don't do seasons; Utopia is eternal growing season. The climate
  zoning is deliberate.
- AmbientSounds gives six dimensions their own soundscapes: geiger-tick drips in
  Mining, glassy chimes in Crystal, birdsong in Utopia, and a drone in Chaos
  you'll learn to stop noticing. The dimensions stop sounding like silent
  overworld clones.
- Guard Villagers: guards put the pack's village menaces on their target list out
  of the box, can spawn wearing ruby and amethyst plate from the pack's own
  armory, and when a Royal boss falls near a village they raise shields and fire
  a crossbow volley in time with the fireworks. Boss kills now draw a crowd.
- Friends & Foes: win the rascal's game and it pays in rubies, iceologers hand
  you their own ice balls to throw back, and the wildfire rare-drops an extreme
  torch, the crown that never goes out, mounted on your wall.
- Naturalist: two ecosystems become one. Great whites chase gold fish, lions
  stalk gazelles, snakes eat our rats, and one `c:` crab tag finally ends the
  pack's three-crab schism. The **Menagerie** advancement wing doubles overnight
  with seven Naturalist showpieces (Mammoth to Capybara) and a capstone for
  caging them all. The zoo keeper is thrilled.
- Doggy Talents Next: shared rice tags end the two-incompatible-rices situation,
  and when the boss-kill fireworks start, every nearby dog does a backflip.
  That's it. That's the feature this pack will be remembered for. Guard-dog
  companions who survive a Royal boss fight at your side earn *Who's a Good Boy?
  Certified.*
- End Remastered: the eye hunt runs through OreSpawn's endgame. A pack-exclusive
  **Royal Eye** waits in the Kraken and Mobzilla boss vaults, the vortex eye
  finally earns its name via transmutation into a Magical Eye, and the Ender
  Castle joins the pilgrimage with Lost and Cursed eyes in its chests.
- Lootr is not one of ours, but it is worth saying out loud: every chest
  injection in this wave lands on the loot *table*, not the chest, so inside
  Lootr's per-player instanced containers, everyone rolls their own. Nobody loots
  your loot.
- The World Wakes Up advancement tab: a new `alive_world` branch documents the
  wave in-game. Pull a Miner's Dream out of somebody else's dungeon, pocket your
  first End Remastered eye, learn kraken repellent from the people who nail it to
  the mast, and witness the Prince take wing as the season turns.
  - Carve-out note: a few of this wave's data sets ship ungated by the
    `threads.alive_world` toggle, each for a good reason and all inert when their
    partner mod is absent. The tag merges (Serene Seasons crop and biome tags,
    the `c:` crab and rice tags, structure-biome tags) are ungated because
    NeoForge 21.1's TagLoader ignores conditions entirely. Block self-drop loot
    is ungated because already-placed blocks must keep dropping themselves if you
    flip the thread off mid-world; we don't punish. The partner-loader-scanned
    files (AmbientSounds engine soundscapes, End Remastered's config-folder eye
    JSON, the Guard Villagers config) are read by their own loaders, which never
    see NeoForge conditions.
