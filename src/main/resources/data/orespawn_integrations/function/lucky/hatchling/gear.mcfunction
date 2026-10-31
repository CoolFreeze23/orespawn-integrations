# Rank 0-1 gear: the Loaner Bertha 2 in 9, else one of the early OreSpawn chest tables. Not generic_dungeon: its loot can
# hold a Wither or Ender Dragon spawn egg, which the chaos floor keeps out of rank 0 and 1 (mini_dungeon takes its slot).
execute store result score #oi_pick cc_lucky run random value 1..9
execute if score #oi_pick cc_lucky matches 1..2 run return run function orespawn_integrations:lucky/hatchling/loaner
execute if score #oi_pick cc_lucky matches 3 run data modify storage lucky_blocks:storage value set value "orespawn:chests/mini_dungeon"
execute if score #oi_pick cc_lucky matches 4 run data modify storage lucky_blocks:storage value set value "orespawn:chests/mini_dungeon"
execute if score #oi_pick cc_lucky matches 5 run data modify storage lucky_blocks:storage value set value "orespawn:chests/crystal_chest"
execute if score #oi_pick cc_lucky matches 6 run data modify storage lucky_blocks:storage value set value "orespawn:chests/ruby_dungeon"
execute if score #oi_pick cc_lucky matches 7 run data modify storage lucky_blocks:storage value set value "orespawn:chests/challenge_tower_level1"
execute if score #oi_pick cc_lucky matches 8 run data modify storage lucky_blocks:storage value set value "orespawn:chests/challenge_tower_level2"
execute if score #oi_pick cc_lucky matches 9 run data modify storage lucky_blocks:storage value set value "orespawn:chests/challenge_tower_level3"
function orespawn_integrations:lucky/loot_spawn with storage lucky_blocks:storage
