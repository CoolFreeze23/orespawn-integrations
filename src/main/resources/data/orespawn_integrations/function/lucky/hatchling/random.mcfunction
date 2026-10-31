# Our OreSpawn outcomes at rank 0: ambush 10 in 20 (two or three small creatures), gear 9 in 20 (early chests, or the
# Loaner Bertha 2 in 9), jackpot 1 in 20 (a royal egg part). Of all caskets: a part 1 in 80, a Loaner 1 in 40.
scoreboard players add #oi_ours cc_lucky 1
execute store result score #oi_pick cc_lucky run random value 1..20
execute if score #oi_pick cc_lucky matches 1..10 run return run function orespawn_integrations:lucky/hatchling/ambush
execute if score #oi_pick cc_lucky matches 11..19 run return run function orespawn_integrations:lucky/hatchling/gear
execute if score #oi_pick cc_lucky matches 20 run return run function orespawn_integrations:lucky/hatchling/jackpot
