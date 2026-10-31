# The Loaner Bertha (item-hoard-joke-armoury): a Big Bertha with 24 swings counted in its cc_loaner custom data. The
# mod's attack listener takes one off per swing and breaks it at 0; anvils, grindstones and tables refuse it.
scoreboard players add #oi_loaners cc_lucky 1
summon item ~ ~-1 ~ {Motion:[0.0,0.25,0.0],Item:{id:"orespawn:big_bertha",count:1,components:{"minecraft:custom_data":{cc_loaner:24},"minecraft:custom_name":'{"translate":"orespawn_integrations.loaner.name","italic":false}'}}}
tellraw @p[distance=..6] {"translate":"orespawn_integrations.loaner.rolled","color":"gold"}
