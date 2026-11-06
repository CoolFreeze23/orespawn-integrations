# The stray row: one more lote egg of the player's batch, at most once per 7 Land's days (the day in #dia cc_terra,
# the player's last stray in cc_desgarrado); sooner, or with story.misfile off, the find is a casket.
execute unless score #extravio cc_cfg matches 1 run return run function orespawn_integrations:extravio/copa/dar/arca
scoreboard objectives add cc_desgarrado dummy
scoreboard players set #oi_desg cc_copa 99
execute if score @s cc_desgarrado = @s cc_desgarrado run scoreboard players operation #oi_desg cc_copa = #dia cc_terra
execute if score @s cc_desgarrado = @s cc_desgarrado run scoreboard players operation #oi_desg cc_copa -= @s cc_desgarrado
execute if score #oi_desg cc_copa matches ..6 run return run function orespawn_integrations:extravio/copa/dar/arca
scoreboard players operation @s cc_desgarrado = #dia cc_terra
loot give @s loot orespawn_integrations:copa/desgarrado
playsound minecraft:block.azalea_leaves.break player @s ~ ~ ~ 1 1
title @s actionbar {"translate": "orespawn_integrations.extravio.copa.desgarrado"}
