# The Knight's jackpot: a whole King, Queen or Mobzilla egg (3 in 4) or the Great Refusal (1 in 4). One whole egg a
# player a Land's Week (seven game days); a second rerolls into a part. Needs a named opener, else a part.
execute unless entity @p[distance=..6] run return run function orespawn_integrations:lucky/hatchling/jackpot
execute store result score #oi_pick cc_lucky run random value 1..4
execute if score #oi_pick cc_lucky matches 4 run return run function orespawn_integrations:lucky/grande_recusa/random

scoreboard players set #c7 cc_lucky 7
execute store result score #oi_week cc_lucky run time query day
scoreboard players operation #oi_week cc_lucky /= #c7 cc_lucky
scoreboard players add #oi_week cc_lucky 1
execute if score @p[distance=..6] cc_ovo_semana = #oi_week cc_lucky run return run function orespawn_integrations:lucky/knight/second_egg
scoreboard players operation @p[distance=..6] cc_ovo_semana = #oi_week cc_lucky
scoreboard players add #oi_whole cc_lucky 1
function orespawn_integrations:lucky/jackpot
