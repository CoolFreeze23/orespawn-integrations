# OreSpawn Integrations 0.5.0 - "Brasil"

A sixth thread, aimed at the two Brazilian mods in the pack. Same rules as the rest:
zero hard dependencies, everything steps aside when a partner mod is missing, and the
whole thread has its own switch (`threads.brazil`, flip it, `/reload`, done).

---

## Thread 6: Brasil

Danger saw a capybara and asked why it was not already sitting on the porch.

- Brazilian critters become Domestication Innovation pets. Three
  `domesticationinnovation:taming` rules turn Brasil e Coisas' wild creatures into full
  pets: collars, enchantments, pet beds, the Wayward Lantern, the deed, the lot.
  - Capivara: a Corn Cob (OreSpawn's), a Manga or a Melon Slice, with a one in three
    chance.
  - Quero-quero: any seed a parrot would eat, with a one in three chance. It defends its
    owner, which anyone who has walked past one already knows.
  - Urutau: a Moth Scale, and only a Moth Scale (Luna Moths drop them, Mothra drops a
    pile). The chance is one in two, because you had to catch a moth first.
- There is nothing to add for the Vira-lata Caramelo, Pintinho, Ovinho or Amongus. They
  are tameable in their own mod already, and Domestication Innovation treats them like
  any wolf or cat once they are: collars, enchantments, beds and lanterns just work.
- Deliberately left wild: Cobra Coral, Lobisomem, Saci, Seu Zé, Mestre.
- Gated on Brasil e Coisas and Domestication Innovation being present, plus the
  `threads.brazil` toggle.

## Config
- New toggle `threads.brazil` (default on). The thread-count line in the log now says
  seven.

---

# 0.5.1 - two fixes

- The Prince flyover can no longer leave a Prince stuck in the sky. The glide puppet
  is now discarded before the shutdown save, and a puppet that still reaches disk
  (autosave mid-glide, then a crash) is refused when it loads back. Puppets carry the
  entity tag `orespawn_integrations.flyover_puppet`; an old leftover in an existing world
  goes away on its own the next time its chunk loads.
- The Royal Fire, Ice and Mana Upgrade Orb recipes load again. Their result wrote the
  item name as a JSON object where 1.21 expects a JSON string, so all three had failed
  to parse since the Royal Court thread shipped and were never craftable.
