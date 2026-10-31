# One pick of the three part blocks: the King's 4, the Queen's 3, Mobzilla's 2 (of 9).
scoreboard players add #oi_parts cc_lucky 1
data modify storage lucky_blocks:storage count set value 1
execute store result score #oi_pick cc_lucky run random value 1..9
execute if score #oi_pick cc_lucky matches 1..4 run data modify storage lucky_blocks:storage value set value "orespawn:the_king_part_spawn_block"
execute if score #oi_pick cc_lucky matches 5..7 run data modify storage lucky_blocks:storage value set value "orespawn:the_queen_part_spawn_block"
execute if score #oi_pick cc_lucky matches 8..9 run data modify storage lucky_blocks:storage value set value "orespawn:godzilla_part_spawn_block"
function orespawn_integrations:lucky/item_spawn with storage lucky_blocks:storage
particle minecraft:totem_of_undying ~ ~-1 ~ 0.5 0.5 0.5 0.5 20 force @a[distance=..24]
