# OreSpawn Integrations 0.3.0 — "Cross-Mod Threads"

Twenty-six ideas, five threads, zero hard dependencies. Every feature below quietly
steps aside if its partner mod goes missing, and every thread has its own on/off
switch in the config (`threads.*`, flip it, `/reload`, done). One documented
exception to that rule lives in Thread 4 — see the carve-out note.

---

## Thread 4 — Big Game (combat feel)

Danger looked at a ten-foot sword that swings like a butter knife and said we can
fix that. Then he kept going.

- **Big Bertha learns Better Combat.** The whole iconic arsenal gets real
  move-sets via Better Combat preset files — Bertha sweeps as a claymore, the
  Queen Battle Axe swings like a double axe, the Thunder Staff does battlestaff
  combos between lightning bolts, the Fairy Sword fences like a rapier. Pure data,
  read by Better Combat's own loader. Weapon-to-preset mapping: *(final table in
  build notes)*.
  - **Carve-out note:** a few Thread 4 data sets ship **ungated** by the
    `threads.big_game` config toggle, each for a good reason and all inert when
    their partner mod is absent: these `weapon_attributes` files (Better Combat
    reads them with its own resource loader, which never sees NeoForge
    conditions — user-approved exception), the Weeping Angels / Artifacts /
    tool-tier **tag merges** (NeoForge 21.1's TagLoader ignores conditions
    entirely), and the **block self-drop loot tables** for mines, trophies and
    backpacks (deliberate: if you toggle the thread off mid-world, already-placed
    blocks must still drop themselves — we don't punish).
- **Mobzilla Plating.** Four Godzilla scales onto a Twilight Forest Traveller's
  Vest. The boss took the hit so you don't have to. Repeatedly. That's the whole
  idea of armor.
- **Boss-hide backpacks, three of them.** Mobzilla-Hide, Kraken, and Girlfriend
  skins for Traveler's Backpack. The Girlfriend pack winks at Thread 3's story
  arc. Yes, the Kraken one has sucker prints. No, they don't do anything. They're
  suckers.
- **Emperor's Chitin Band.** The Emperor Scorpion now drops a wearable band with
  Artifacts-powered venom abilities. Danger looked at concentrated scorpion venom
  strapped to a wrist and said it's fine. It is, actually — it fights for you now.
- **Angel Insurance Policy.** Three tag files stitch Weeping Angels into OreSpawn:
  the Timey-Wimey Detector pings OreSpawn anomalies, angels can pocket loose
  rubies (petty theft is a story, not grief), and Big Bertha is on the shortlist
  of things allowed to hurt an angel. Don't blink. She doesn't.
- **Decoy Ore Mines.** Some ore veins were never ore — SecurityCraft mines wearing
  an OreSpawn costume. Every one of them carries a fairness tell (a subtle shimmer
  and a faint tick), the blast is tuned for drama over craters, and defusing or
  surviving one pays out a **Suspicious Ore** trophy block. The vein disagreed;
  you won anyway.

The whole thread is documented in-game as the **Big Game** advancement tab —
eight nodes, one per fantasy, readable as release notes with a progress bar.

---

## Thread 1 — It Was Always Uranium (power)

Danger looked at raw uranium and said it's fine. The pack's power mods looked at
Danger's uranium and started taking notes. Nothing in this thread hurts you —
that's not a disclaimer, that's the design document.

- **Geiger Prospecting.** Carry NTM's geiger counter and it moonlights as an
  ore-dowsing rod: a lazy click near buried rubies, a steady chatter over
  titanium, the full panic song standing on an OreSpawn uranium vein. Your dose
  stays at zero the entire time — right-click the counter and watch it swear to
  that. We drive HBM's telemetry channel, never its radiation; detection and
  harm are separate wires, and we only touch the one that can't bite.
- **Radioactive Enchanting Corner.** Blocks of uranium now shelve like
  bookcases and read nearly pure Quanta — the enchanting table gets volatile,
  you get options. Feed an uranium ingot to a maxed-out Apothic altar and it
  infuses into a **Radioactive II** book: strikes poison the target. Perfectly
  safe for the wielder. Notably less so for everyone else.
- **Uranium Collector Chain.** ProjectE prices Danger's fuel on the reviewed
  table — an uranium ingot condenses at nine diamonds, and the nugget sits at
  exact aeternalis parity, so the collector chain trades fuel for fuel one to
  one. An exchange, never a printer; the dupe audit says so in writing.
- **Uranium Arc Reactor.** Marvel's reactor line learns what Danger buried:
  the **Dirty Arc Reactor**, an arc reactor core rebuilt around an OreSpawn
  uranium slug. Palladium nearly killed Tony. This is fine.
- **Bottled Uranium.** A cauldron fluid that makes the cauldron literally glow,
  bottled by right-click, and yes, you can drink it. The **Uranium Rush**:
  glowing outline, night vision, a burst of speed. Not poison. Never poison.
  Deliberately EMC-less and recipe-less — the condenser doesn't get to brew it,
  you get it from the glowing pot like a person of culture.
- **A-10 Uranium Belts.** The thread's endgame is literally a warthog. An
  additive MCHeli content pack (`mcheli/orespawn_uranium/`) adds the DU-belt
  A-10 and its GAU-8 DU cannon, resupplied with uranium ingots. MCHeli matches
  ammo by registry path and ignores the namespace, so Danger's uranium and
  HBM's uranium both feed the belt — the warthog doesn't check the label.
  Stock aircraft untouched (the port is first-source-wins by design).

Documented in-game as the **It Was Always Uranium** advancement tab — six
nodes from first ping to warthog. The warthog one stays hidden: content packs
can't grant advancements, so consider it a standing invitation.
  - **Carve-out note:** two Thread 1 pieces ship **ungated** by the
    `threads.uranium` toggle, each for a structural reason and both inert when
    their partner mod is absent: the **MCHeli content pack** is game-dir
    instance content read by MCHeli's own loader, which never sees NeoForge
    conditions (additive ids only, stock files untouched), and the Amendments
    **can_glow tag merge** for the cauldron glow (NeoForge 21.1's TagLoader
    ignores conditions entirely). Bottled Uranium's missing EMC is an omission,
    not a file — nothing to gate.

---

## Thread 2 — The Royal Court (boss economy)

Every royal drop is currency now. The Court mints five kinds of it — scrolls,
scales, shards, orbs, and one very good egg — and everything is gated the way
a court should be: earn the audience first, then the treasury opens.

- **Witherite-Tempered Royal Guardian Sword.** The Mechanical Fusion Anvil
  takes six witherite ingots to build, and witherite drops from exactly one
  creature: The Harbinger. The anvil is earned. Temper the RGS there and it
  comes back harder — 749 to 861 damage, a quicker swing, fireproof, and
  renamed to match its new attitude. Your enchantments and your custom name
  survive the forge; the Court upgrades, it never confiscates.
- **Boss-School Spell Scrolls.** Each royal teaches one school — Godzilla
  breathes Fire, the Kraken deals Ice, the King throws Lightning, the Queen
  keeps Holy, and the alien brood hoards Ender. Drop rates are tuned as an
  economy, not a faucet: rare enough to trade, common enough to chase, with
  Looting sweetening the odds and the King and Queen carrying a rare
  max-level jackpot. The guaranteed-max scroll is the Court's trade currency.
- **Royal Dragon Egg.** Press eight queen scales around an egg, then incubate
  it through a full day of warming in the Aether's incubator. The Prince
  hatches and imprints on the first face he sees — a living trophy that
  follows you home. Long incubation, longer loyalty.
- **Pre-Bound Boss Soul Shards.** Royal bosses can drop soul shards already
  bound to them — at tier one, 64 kills of the 1024 signed off as a welcome
  gift. The remaining 960 are still your problem. The grind stays honest;
  only the introduction is free.
- **Royal Scale Upgrade Orbs.** Boss materials power magic — royal scales
  press into upgrade orbs keyed to the same five boss schools, riding Iron's
  Spellbooks' own orb system as pure data. Scrolls and orbs share one
  advancement branch because they're one idea wearing two hats.
- **Royal Boss-Loot Uncrafting.** The Court's sink: Twilight Forest's
  Uncrafting Table buys back royal gear for queen scales — the sword alone
  refunds six. Surplus loot melts down, scales flow back into scrolls, orbs,
  and eggs, and the economy loop closes. Nothing royal is ever a dead end.
- **Queen-to-Queen Tribute.** Bring queen scales to the Bumblezone's Bee
  Queen and she pays in royal jelly and honey crystal shards. Two monarchies,
  one trade table, zero stings. There's a hidden advancement in it for the
  first ambassador.

Documented in-game as **The Royal Court** advancement tab — eight nodes with
an early/mid/post-boss spine, from your first scroll to the witherite temper.
  - **Carve-out note:** one Thread 2 file ships **ungated** by the
    `threads.royal_court` toggle: the **Bee Queen trade JSON** is read by the
    Bumblezone's own trade loader, which never sees NeoForge conditions — it
    is inert when the Bumblezone is absent, and it only ever *adds* a trade.

---

## Thread 3 — Her Side of the Story (Girlfriend arc)

She has been in this pack since the beginning, and in all that time nobody asked
her opinion of it. This thread is her side, told in her own handwriting, one
earned page at a time. We are not going to quote the diary in the release notes.
She'd know.

- **Girlfriend's Diary.** Three Bibliocraft volumes in her own hand — earned,
  never found. What's written in them stays between the two of you, but expect
  strong opinions about a certain Prince's flyovers, a pointed hint about the
  Heart Locket, and — late in the second volume — directions worth following.
- **Date Night.** Eight gifts from across the pack say what poppies can't:
  Biomes O' Plenty roses, lavender, and pink daffodils, an Aether white flower,
  an Ars Nouveau sourceberry, dessert off her own mod's menu, and one very
  serious teddy bear. Every gift she accepts marks a diary milestone and leaves
  a small **permanent** buff on *her* — more hearts, a quicker stride, thicker
  armor. On her, never on you: you're courting her, not farming her. And no,
  handing her a teddy bear does not equip it as a weapon. We checked. She was
  going to.
  - Poppies and dandelions still mean exactly what they've always meant to her —
    those are hers and we didn't touch them. The heart box of chocolates already
    belongs to OreSpawn Delight's kitchen; we notice the gesture, we don't
    intercept it.
- **The finale.** A Decocraft rose-gold engagement ring closes the chain, opens
  the last volume, and changes the question from "do you like these" to
  "come with me."
- **Tome of the Girlfriend.** Out in the world where the rumors always said it
  was. The diary's later pages narrow the search; what happens when you find it
  is between you and the diary.
- **Girlfriend Familiar.** Finish the chain and she learns Ars Nouveau's oldest
  trick: coming along. The familiar unlock arrives with the ring — and one
  north-star guarantee in writing: the binding ritual only ever answers to a
  *wild* girlfriend. A tamed companion can never be consumed by it. We checked
  that twice, on purpose.

The whole arc documents itself in-game as the **Her Side of the Story**
advancement chain — one node per milestone, and the later nodes keep her
secrets until you've earned them.

---

## Thread 5 — The World Remembers (atmosphere)

Four features, one sentence: OreSpawn happened, and the rest of the world has
started acting like it. Witches summon its kings, alchemists paint in its
colors, industry rolls its metals, and somebody finally built the statue.

- **Rite of Gojira & Rite of the Kraken.** Enchanted's circle magic learns two
  names it should be afraid of. The weather is not flavor text — it is a
  **requirement the rite itself checks**: Gojira's circle only answers under a
  genuine thunderstorm, and the Kraken wants rain overhead and open ocean
  underfoot. Read the sky wrong and the circle hands every ingredient straight
  back with a drumroll — a failed rite costs you nothing but the walk home.
  Chalk, scales, storm. Then run.
- **Mobzilla Statue.** The full catalyst grind, ending in the pack's worst day
  rendered in stone — and the statue works for a living like every other statue
  on the table. It also brought a gift for the whole garden: a single OreSpawn
  **uranium ingot** on the statue table is now a straight alternate route to the
  mob-killer upgrade for *any* statue. Danger looked at a statue armed with raw
  uranium and said it's fine. The statue agrees. Other royals in stone: on the
  follow-up list.
- **Crystalline World Transmutation.** The Philosopher's Stone picks up local
  color — stone, soil, and timber transmute into Crystal Dimension blocks at a
  touch. It costs stone charge like any other transmutation and answers to the
  reviewed EMC table like everything else in this addon; the condenser gains
  nothing it didn't already have.
- **OreSpawn Rail Works.** Railcraft's crusher now accepts all five silk-touched
  OreSpawn ores, with the math done in public: ruby crushes generous, salt
  crushes cheap and cheerful, and titanium and uranium carry the **lowest
  multipliers in the shop** — deliberately under Railcraft's own diamond rate,
  because "rarer than diamond" stays true even inside a rock crusher. Uranium
  ore leaves sulfur dust behind, because industry always leaves something
  behind. The rolling machine answers back: titanium and obsidian dust roll
  into sixteen reinforced rails, and three titanium ingots stretch into sixteen
  lengths of rebar — double what steel manages either way, because the best
  rail metal in the pack should act like it.
- **The MCHeli fix.** Promoted from footnote to feature, because it is one: the
  aircraft files have carried `AddRecipe` lines since day one, and the port
  parses them faithfully into a field **nothing ever reads** — dead data, no
  recipes, never was. This addon ships real survival crafting recipes for the
  hangar: the AH-64 Apache Longbow, the EC665 Tiger, the A-10 the uranium
  crowd has been waiting for, the B-2A Spirit, the MH-53E Sea Dragon — and one
  Merkava Mk4, because somebody in the motor pool got jealous. First time in
  this pack's life a survival player can craft any of them. That's not a
  tweak, that's a repair.

Documented in-game as **The World Remembers** advancement branch — five nodes,
thunderstorm to takeoff.
  - **Carve-out note:** a few Thread 5 files ship **ungated** by the
    `threads.world_remembers` toggle, each for the usual structural reason:
    the Mobzilla statue's **block self-drop loot table** (deliberate, same law
    as always — flip the thread off mid-world and an already-placed statue
    still drops itself; we don't punish), and the two **statues tag joins**
    (`statues:statues/upgradeable` + `lootable`, optional entries — NeoForge
    21.1's TagLoader ignores conditions entirely, and both are inert without
    the statue).

---

## 0.3.1 - The World Wakes Up

Twenty-four new mods moved into the pack overnight — YUNG rebuilt every dungeon,
the seasons started turning, and the ocean grew a pirate problem. This wave
stitches all of it to OreSpawn. Same house rules as ever: zero hard dependencies,
every file steps aside if its partner mod is missing, and the whole wave hangs off
one switch (`threads.alive_world`, flip it, `/reload`, done). Reward, never
punish. Exceptions to the gating rule are listed in the carve-out note at the
bottom, each with its reason.

- **YUNG's suite (Better Dungeons, Mineshafts, Strongholds, Desert Temples,
  Ocean Monuments, Nether Fortresses, Witch Huts).** Seven structure mods, one
  through-line: YUNG builds the rooms, Danger stocks them. Dungeon chests cough up
  rubies, amethyst, and the rare Miner's Dream; zombie-dungeon tombstones carry
  graveyard loot (and on Halloween, a friendly ghost); the spider dungeon's egg
  room was never just spiders. Mineshafts thread titanium through their rails and
  wind through the Mining Dimension. Stronghold armouries answered to the Ender
  Knight, the grand library shelves our guide book, and the treasure room sells
  vault-key lottery tickets. The pharaoh hoarded tiger's eye — and opening his
  tomb can wake an Emperor Scorpion, because mummy's curses localize. Ocean
  monuments display trophies from the pack's sea bosses (and the kraken repellent
  that kept the Guardians sleeping). Fortress vaults run on uranium and enshrine
  the Nether Lost. Witch huts stock ingredients no vanilla witch could source.
- **ChoiceTheorem's Overhauled Village.** Village pantries grow OreSpawn produce,
  the smith keeps titanium in the back, and the library spreads rumors about
  where the pack's own landmarks are. Villagers gossip; we just wrote it down.
- **Towns & Towers.** Archaeology digs brush up trex teeth and rock crystal, and
  coastal outposts keep kraken repellent by the door. Seaside folk know what's in
  the water.
- **Structory & Structory: Towers.** Bandit camps fence stolen rubies, ruined
  miner camps prove Thread 1 right, the wizard's attic hides a thunder staff, the
  lighthouse keeper was fishing for more than cod, and tower tops hold End
  Remastered eyes — the skyline is now a waypoint network for the End hunt.
- **Incendium.** The Nether reactor runs on Danger's uranium — waste and treasure
  chests leak it, and the lab was bottling it. Downing Incendium's minibosses
  earns a fire-palette salute from the boss celebration. The Royal Court
  acknowledges the Nether's nobility.
- **Dungeons & Taverns.** Taverns serve pizza, crabby patties, and salad off the
  pack's own menu, and the illager mansion's secret room smuggles Royal Court
  contraband. The illagers were fencing queen scales. Of course they were.
- **When Dungeons Arise: Seven Seas.** The pirate fleet sails these waters to
  hunt our Kraken — treasure holds carry kraken teeth, sea viper tongues, and
  skate bows; galleon captains chart courses to the Water Dragon's lair; and the
  deckhands stash wall-mounted kraken repellent in the barrels, because pirates
  learn fast or sink.
- **Serene Seasons.** OreSpawn's farm joins the calendar — corn ripens in summer,
  radishes in spring, quinoa in autumn, and the magic plants ignore winter out of
  principle. The celebration system graduates from wall-clock holidays to in-game
  seasons, with a seasonal Prince flyover when they turn. The Mining and Chaos
  dimensions don't do seasons; Utopia is eternal growing season. Climate-zoned,
  deliberately.
- **AmbientSounds.** Six dimensions, six soundscapes — geiger-tick drips in
  Mining, glassy chimes in Crystal, birdsong in Utopia, a drone in Chaos you'll
  learn to stop noticing. The dimensions stop sounding like silent overworld
  clones.
- **Guard Villagers.** Guards put the pack's village menaces on their target list
  out of the box, can spawn wearing ruby and amethyst plate from the pack's own
  armory, and when a Royal boss falls near a village they raise shields and fire
  a crossbow volley in time with the fireworks. Boss kills now draw a crowd.
- **Friends & Foes.** Win the rascal's game and it pays in rubies, iceologers
  hand you their own ice balls to throw back, and the wildfire rare-drops an
  extreme torch — the crown that never goes out, mounted on your wall.
- **Naturalist.** Two ecosystems become one: great whites chase gold fish, lions
  stalk gazelles, snakes eat our rats, and one `c:` crab tag finally ends the
  pack's three-crab schism. The **Menagerie** advancement wing doubles overnight —
  seven Naturalist showpieces (Mammoth to Capybara) with a capstone for caging
  them all. The zoo keeper is thrilled.
- **Doggy Talents Next.** Shared rice tags end the two-incompatible-rices
  situation, and when the boss-kill fireworks start, every nearby dog does a
  backflip. That's it. That's the feature this pack will be remembered for.
  Guard-dog companions who survive a Royal boss fight at your side earn
  *Who's a Good Boy? Certified.*
- **End Remastered.** The eye hunt runs through OreSpawn's endgame: a
  pack-exclusive **Royal Eye** waits in the Kraken and Mobzilla boss vaults, the
  vortex eye finally earns its name via transmutation into a Magical Eye, and the
  Ender Castle joins the pilgrimage with Lost and Cursed eyes in its chests.
- **Lootr.** Not one of ours, but worth saying out loud: every chest injection in
  this wave lands on the loot *table*, not the chest — so inside Lootr's
  per-player instanced containers, everyone rolls their own. Nobody loots your
  loot.
- **The World Wakes Up advancement tab.** A new `alive_world` branch documents
  the wave in-game: pull a Miner's Dream out of somebody else's dungeon, pocket
  your first End Remastered eye, learn kraken repellent from the people who nail
  it to the mast, and witness the Prince take wing as the season turns.
  - **Carve-out note:** a few of this wave's data sets ship **ungated** by the
    `threads.alive_world` toggle, each for a good reason and all inert when their
    partner mod is absent: the **tag merges** (Serene Seasons crop/biome tags,
    the `c:` crab and rice tags, structure-biome tags — NeoForge 21.1's TagLoader
    ignores conditions entirely), any **block self-drop loot** (already-placed
    blocks must keep dropping themselves if you flip the thread off mid-world —
    we don't punish), and the **partner-loader-scanned files** (AmbientSounds
    engine soundscapes, End Remastered's config-folder eye JSON, the Guard
    Villagers config — their own loaders read these and never see NeoForge
    conditions).
