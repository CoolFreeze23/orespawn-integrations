# Captain Iracema's "And the King?" option (npc-iracema, R4): her line once per player, as chat, and R4 once.
execute unless entity @s[advancements={orespawn_integrations:livro/r4=true}] run tellraw @s {"translate":"orespawn_integrations.iracema.speaker","with":[{"translate":"orespawn_integrations.iracema.r4"}]}
advancement grant @s only orespawn_integrations:livro/r4
