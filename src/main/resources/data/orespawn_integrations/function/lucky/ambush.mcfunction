execute store result score @s lucky_blocks.random run random value 1..10

execute if score @s lucky_blocks.random matches 1 run data modify storage lucky_blocks:storage value set value "orespawn:mosquito"
execute if score @s lucky_blocks.random matches 2 run data modify storage lucky_blocks:storage value set value "orespawn:cater_killer"
execute if score @s lucky_blocks.random matches 3 run data modify storage lucky_blocks:storage value set value "orespawn:scorpion"
execute if score @s lucky_blocks.random matches 4 run data modify storage lucky_blocks:storage value set value "orespawn:alien"
execute if score @s lucky_blocks.random matches 5 run data modify storage lucky_blocks:storage value set value "orespawn:robot_1"
execute if score @s lucky_blocks.random matches 6 run data modify storage lucky_blocks:storage value set value "orespawn:red_ant"
execute if score @s lucky_blocks.random matches 7 run data modify storage lucky_blocks:storage value set value "orespawn:velocity_raptor"
execute if score @s lucky_blocks.random matches 8 run data modify storage lucky_blocks:storage value set value "orespawn:creeping_horror"
execute if score @s lucky_blocks.random matches 9 run data modify storage lucky_blocks:storage value set value "orespawn:trooper_bug"
execute if score @s lucky_blocks.random matches 10 run data modify storage lucky_blocks:storage value set value "orespawn:spit_bug"

execute store result score @s lucky_blocks.random run random value 3..5

function orespawn_integrations:lucky/summon with storage lucky_blocks:storage
function orespawn_integrations:lucky/summon with storage lucky_blocks:storage
function orespawn_integrations:lucky/summon with storage lucky_blocks:storage
execute if score @s lucky_blocks.random matches 4..5 run function orespawn_integrations:lucky/summon with storage lucky_blocks:storage
execute if score @s lucky_blocks.random matches 5 run function orespawn_integrations:lucky/summon with storage lucky_blocks:storage
