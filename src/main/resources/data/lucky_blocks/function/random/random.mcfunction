execute store result score @s lucky_blocks.random run random value 1..12

execute if score @s lucky_blocks.random matches 1 run return run function lucky_blocks:random/trap/random
execute if score @s lucky_blocks.random matches 2 run return run function lucky_blocks:random/trap/random
execute if score @s lucky_blocks.random matches 3 run return run function lucky_blocks:random/summon/item/random
execute if score @s lucky_blocks.random matches 4 run return run function lucky_blocks:random/summon/entity/random
execute if score @s lucky_blocks.random matches 5 run return run function lucky_blocks:random/summon/block/random
execute if score @s lucky_blocks.random matches 6 run return run function lucky_blocks:random/summon/chest/random
execute if score @s lucky_blocks.random matches 7 run return run function lucky_blocks:random/summon/loot/random
execute if score @s lucky_blocks.random matches 8 run return run function lucky_blocks:random/summon/custom/random
execute if score @s lucky_blocks.random matches 9 run return run function lucky_blocks:random/effect/random
execute if score @s lucky_blocks.random matches 10 run return run function orespawn_integrations:lucky/random
execute if score @s lucky_blocks.random matches 11 run return run function orespawn_integrations:lucky/random
execute if score @s lucky_blocks.random matches 12 run return run function orespawn_integrations:lucky/random
