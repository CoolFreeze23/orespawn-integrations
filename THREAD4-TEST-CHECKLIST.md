# Thread 4 "Big Game" — Test Checklist (0.3.0)

New config: `config/orespawn_integrations-common.toml` — five thread toggles (only
`big_game` has content so far). Flip + `/reload` to watch recipes/advancements vanish.
Carve-outs that ignore the toggle (by design): Better Combat move-sets, tag merges,
block self-drops.

## Combat feel (the 30-second test)
- [ ] Hold **Big Bertha** → she sweeps like a claymore with bonus reach. Royal Guardian
      Sword = claymore too; Queen's Battle Axe = double-axe combos; Fairy Sword fences
      like a rapier; Thunder Staff gets battlestaff melee combos (right-click bolt
      unchanged); Nightmare Sword = katana flow. Ten weapons total.

## Decoy Ore Mines (SecurityCraft)
- [ ] Craft: any of ruby/titanium/tigers-eye/uranium **ore + SecurityCraft mine**
      (shapeless). Place one — it looks like the real ore.
- [ ] Watch closely: a **subtle shimmer particle** gives it away (~1–2/s). Stepping on
      it is harmless — it detonates when MINED (tuned blast, not crater-grade).
- [ ] **Defuse with wire cutters** → it converts to a **Suspicious Ore** trophy block
      (decoration + the "It Was Never Ore" advancement when you pick it up).

## Backpacks (Traveler's Backpack)
- [ ] Craft **Mobzilla-Hide** (godzilla scales ring), **Kraken** (kraken teeth +
      sea-monster scales), **Girlfriend** (pink + heart theme; second recipe exists)
      around a standard backpack. Full TB feature set: tanks, sleeping bag, curios slot.
- [ ] Wear each — check the strap art on your back (black/purple plates, teal suckers,
      pink heart clasp).

## Emperor's Chitin Band (Artifacts)
- [ ] Kill Emperor Scorpions → ~8% drop (fallback craft: 6 emperor scorpion scales +
      2 gold + spider eye). Equips in the Accessories **hands** slot.
- [ ] Punch something — **Poison II on hit** (100%, short cooldown), venom particles.

## Mobzilla Plating (Twilight Forest)
- [ ] 4 godzilla scales N/E/S/W around a **Traveller's Vest** in the crafting grid →
      vest gains +4 armor & knockback resistance (tooltip lines; grindstone removes).

## Angel Insurance (Weeping Angels)
- [ ] Timey-Wimey Detector now ticks near OreSpawn's teleporters (Ender Knight, Ender
      Reaper, Vortex, Fairy).
- [ ] Angels can pickpocket rubies/titanium ingots (they're carrying YOUR loot now).
- [ ] Big Bertha / Royal Guardian Sword can finally damage angels.

## Advancements — "Big Game" tab
- [ ] Root + 6-7: sweeping ambition (hold Bertha), plating, any backpack, chitin band,
      kill an angel, **It Was Never Ore** (own a Suspicious Ore trophy), minesweeper
      (defuse a decoy with wire cutters).

**Also in this jar:** compiled against your new **orespawn 2.0.0-beta.1** (your port AI's
Princess fix is in — companion_royalty removed; wild royal spawns gone).
**Verdicts wanted:** move-set feel per weapon, mine shimmer visibility (too subtle / too obvious?),
backpack art, blast strength, poison numbers.
