execute store result score @s lucky_blocks.random run random value 1..12

execute if score @s lucky_blocks.random matches 1 run data modify storage lucky_blocks:storage value set value "orespawn:chests/mini_dungeon"
execute if score @s lucky_blocks.random matches 2 run data modify storage lucky_blocks:storage value set value "orespawn:chests/generic_dungeon"
execute if score @s lucky_blocks.random matches 3 run data modify storage lucky_blocks:storage value set value "orespawn:chests/crystal_chest"
execute if score @s lucky_blocks.random matches 4 run data modify storage lucky_blocks:storage value set value "orespawn:chests/ruby_dungeon"
execute if score @s lucky_blocks.random matches 5 run data modify storage lucky_blocks:storage value set value "orespawn:chests/challenge_tower_level1"
execute if score @s lucky_blocks.random matches 6 run data modify storage lucky_blocks:storage value set value "orespawn:chests/challenge_tower_level2"
execute if score @s lucky_blocks.random matches 7 run data modify storage lucky_blocks:storage value set value "orespawn:chests/challenge_tower_level3"
execute if score @s lucky_blocks.random matches 8 run data modify storage lucky_blocks:storage value set value "orespawn:chests/ender_knight_dungeon"
execute if score @s lucky_blocks.random matches 9 run data modify storage lucky_blocks:storage value set value "orespawn:chests/kyuubi_dungeon"
execute if score @s lucky_blocks.random matches 10 run data modify storage lucky_blocks:storage value set value "orespawn:chests/challenge_tower_level4"
execute if score @s lucky_blocks.random matches 11 run data modify storage lucky_blocks:storage value set value "orespawn:chests/challenge_tower_level5"
execute if score @s lucky_blocks.random matches 12 run data modify storage lucky_blocks:storage value set value "orespawn_integrations:inject/boss_trophy"

function orespawn_integrations:lucky/loot_spawn with storage lucky_blocks:storage
