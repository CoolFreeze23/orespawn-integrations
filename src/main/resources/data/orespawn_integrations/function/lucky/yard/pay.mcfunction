# The Land's return, at the stone's marker (run as and at it). Items lie with Age -32768, so they never despawn and wait
# for the keeper. cc_arcas counts the swaps (cc_dez_arcas at ten, the plaque's tag); cc_quintal asks the mod to write
# the keeper's deed.
scoreboard players add @s cc_arcas 1
scoreboard players add @s cc_quintal 1
execute if score @s cc_arcas matches 10.. run tag @s add cc_dez_arcas
execute if score #oi_pick cc_lucky matches 1 run summon minecraft:item ~ ~1 ~ {Age:-32768s,PickupDelay:10s,Item:{id:"distantlandsmc:ipe_amarelo_sapling",count:1}}
execute if score #oi_pick cc_lucky matches 2 run summon minecraft:item ~ ~1 ~ {Age:-32768s,PickupDelay:10s,Item:{id:"distantlandsmc:jacaranda_sapling",count:1}}
execute if score #oi_pick cc_lucky matches 3 run summon minecraft:item ~ ~1 ~ {Age:-32768s,PickupDelay:10s,Item:{id:"distantlandsmc:pau_brasil_sapling",count:1}}
execute if score #oi_pick cc_lucky matches 4 run function orespawn_integrations:lucky/yard/seeds
execute if score #oi_pick cc_lucky matches 5 run summon minecraft:item ~ ~1 ~ {Age:-32768s,PickupDelay:10s,Item:{id:"minecraft:bone_meal",count:8}}
execute if score #oi_pick cc_lucky matches 6 run function orespawn_integrations:lucky/yard/capivara {id:"brasilcoisas:capivara"}
