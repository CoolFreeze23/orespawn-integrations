execute store result score @s lucky_blocks.random run random value 1..3

data modify storage lucky_blocks:storage count set value 1
execute if score @s lucky_blocks.random matches 1 run data modify storage lucky_blocks:storage value set value "orespawn:the_king_spawn_egg"
execute if score @s lucky_blocks.random matches 2 run data modify storage lucky_blocks:storage value set value "orespawn:the_queen_spawn_egg"
execute if score @s lucky_blocks.random matches 3 run data modify storage lucky_blocks:storage value set value "orespawn:godzilla_spawn_egg"

function orespawn_integrations:lucky/item_spawn with storage lucky_blocks:storage

particle minecraft:totem_of_undying ~ ~-1 ~ 0.5 0.5 0.5 0.5 40 force @a
playsound minecraft:ui.toast.challenge_complete master @a[distance=..24] ~ ~ ~ 1 1
tellraw @a[distance=..24] {"translate":"lucky.orespawn_integrations.jackpot","fallback":"JACKPOT! An OreSpawn boss egg!","color":"gold","bold":true}
