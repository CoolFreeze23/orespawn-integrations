# OreSpawn Integrations 0.7.0

Hats Renewed hats now sit on every OreSpawn creature's head.

## Fixed
- Hat placement on OreSpawn mobs: Hats Renewed finds a GeckoLib mob's head by guessing from a short list of
  bone names, assumes that bone's pivot sits at the bottom of the head, and sizes the hat by the mob's hitbox width.
  OreSpawn's rigs were converted from the 1.7.10 models: their pivots sit at the top, the middle or a corner of the
  head, twenty-odd species name the head differently or have no head at all, and hitbox width says nothing about
  head size. So hats floated, sank, sat off to one side or came out several times too big. This mod now draws the hat
  itself on every OreSpawn rig, on a head bone chosen per species, at the true top-centre of the head (measured from
  the rig's own cubes at rest), standing upright, following the head's animation, and sized to the head's own width.
  Hats Renewed's per-entity placement files, and its in-game placement editor, still apply on top.

## Added
- A head-anchor table for all 116 OreSpawn species (104 rigs) at
  `assets/orespawn_integrations/hats/anchors.json`. Species with a split head or a ridge on the skull list the extra
  bones; species with a built-in hat (Ostrich, Chipmunk, Lizard, Velocity Raptor) wear the new hat on top of it.
  A pack can retarget single species through `config/orespawn_integrations/hat_anchors.json` without a rebuild.
- A config toggle, `[compat] hats_on_rigs` (default on), to fall back to Hats Renewed's own placement.
- A table test: every rig in the OreSpawn jar has an anchor, and every bone an anchor names exists in its rig.

## Notes
- Client-side only; the server jar is unchanged in behaviour and needs no config.
- Things that are not creatures (the Coin, the T-shirt, the two islands, the Vortex, the Crystal orbs, the Elevator,
  the Rotator, the Purple Power) no longer get a hat at all.
- Species still drawn by their classic renderer (the Queen, the Ant Robot and Spider Robot, the boss heads, the cows)
  are unchanged: Hats Renewed's own vanilla-model placement applies to them.
