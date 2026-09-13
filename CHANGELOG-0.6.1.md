# OreSpawn Integrations 0.6.1

Neo Origins' resource bars now speak the player's language.

## Fixed
- **HUD resource-bar labels** ("Energy", "Essence", "Stamina", "Mana", "Vitality", "Qi" and
  this mod's "Tide", "Heat", "Fox-fire", "Sparkle", "Mischief", "Witchcraft", plus the Slime origin's "Moisture") were drawn as
  the raw string from the power JSON and stayed English in every language. A client mixin
  now looks each label up as `orespawn_integrations.hud_label.<label>` and substitutes the
  translation when the current language has one; languages without the key keep the
  original text. Portuguese ships all thirteen.

## Notes
- Client-side only; the server jar is unchanged in behaviour.
- The pack's pt-BR resource pack now also carries a full `neoorigins` translation
  (2,296 keys), which together with this fix makes Neo Origins fully Portuguese.
