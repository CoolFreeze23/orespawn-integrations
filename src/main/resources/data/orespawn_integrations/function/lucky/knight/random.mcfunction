# Our OreSpawn outcomes at Knight and up: ambush 10 in 20, gear 9 in 20, jackpot 1 in 20 (the shipped split).
scoreboard players add #oi_ours cc_lucky 1
execute store result score #oi_pick cc_lucky run random value 1..20
execute if score #oi_pick cc_lucky matches 1..10 run return run function orespawn_integrations:lucky/ambush
execute if score #oi_pick cc_lucky matches 11..19 run return run function orespawn_integrations:lucky/gear
execute if score #oi_pick cc_lucky matches 20 run return run function orespawn_integrations:lucky/knight/jackpot
