# The countersign: everything issued "tomorrow" is stamped with the day after the request. Stores the day + 1 in
# storage orespawn_integrations:registro carimbo.dia (and #carimbo cc_tmp); odete/carimbar gives a paper with that lore.
execute store result score #carimbo cc_tmp run time query day
#   from 0.10.1 this line is: scoreboard players operation #carimbo cc_tmp = #dia cc_terra
scoreboard players add #carimbo cc_tmp 1
execute store result storage orespawn_integrations:registro carimbo.dia int 1 run scoreboard players get #carimbo cc_tmp
