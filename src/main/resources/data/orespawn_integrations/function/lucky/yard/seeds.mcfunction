# Roll 4: four of one Brazilian Delight seed (peanut, coffee, guarana, collard greens), one roll.
execute store result score #oi_seed cc_lucky run random value 1..4
execute if score #oi_seed cc_lucky matches 1 run summon minecraft:item ~ ~1 ~ {Age:-32768s,PickupDelay:10s,Item:{id:"braziliandelight:peanut_seeds",count:4}}
execute if score #oi_seed cc_lucky matches 2 run summon minecraft:item ~ ~1 ~ {Age:-32768s,PickupDelay:10s,Item:{id:"braziliandelight:coffee_seeds",count:4}}
execute if score #oi_seed cc_lucky matches 3 run summon minecraft:item ~ ~1 ~ {Age:-32768s,PickupDelay:10s,Item:{id:"braziliandelight:guarana_seeds",count:4}}
execute if score #oi_seed cc_lucky matches 4 run summon minecraft:item ~ ~1 ~ {Age:-32768s,PickupDelay:10s,Item:{id:"braziliandelight:collard_greens_seeds",count:4}}
