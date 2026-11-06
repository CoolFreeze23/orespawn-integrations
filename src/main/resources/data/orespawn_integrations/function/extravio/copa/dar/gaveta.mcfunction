# What the Wrong Drawer item row gives, with its rustle and its line.
loot give @s loot orespawn_integrations:copa/gaveta
playsound minecraft:block.azalea_leaves.break player @s ~ ~ ~ 1 1
# A ticket handover still showing holds the line 10 s (system-extravio); the week code shows it then.
execute if score @s cc_senha_hold matches 1 run return run tag @s add cc_linha_copa_gaveta
title @s actionbar {"translate": "orespawn_integrations.extravio.copa.gaveta"}
