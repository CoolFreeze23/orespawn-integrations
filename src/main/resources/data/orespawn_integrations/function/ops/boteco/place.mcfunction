# /cc boteco place (structure-boteco-do-seu-ze, OI 0.10.1): Seu Ze's boteco at the operator's feet, any biome. /cc has
# already refused an unloaded footprint, a second boteco and a spot within 64 blocks of a bed, chest or Parish Stone,
# and it appends the boteco to the hub list (orespawn_integrations:hub_places) after this function, so every reader of
# "the nearest boteco" finds it before any locate. A placed structure records no start of its own.
data modify storage orespawn_integrations:cc_result placed set value 0b
execute store success storage orespawn_integrations:cc_result placed byte 1 run place structure orespawn_integrations:boteco ~ ~ ~
execute if data storage orespawn_integrations:cc_result {placed:0b} run return fail
# The counter Seu Ze comes with the template, tagged cc_boteco_counter; say so if he is missing.
execute unless entity @e[type=brasilcoisas:seu_ze,tag=cc_boteco_counter,distance=..48] run tellraw @s {"translate":"orespawn_integrations.boteco.ops.no_keeper","color":"yellow"}
execute if entity @e[type=brasilcoisas:seu_ze,tag=cc_boteco_counter,distance=..48] run tellraw @s {"translate":"orespawn_integrations.boteco.ops.keeper"}
