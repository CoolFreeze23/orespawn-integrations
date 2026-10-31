# What the Ledger Scrap row gives, with its rustle and its line.
# A canopy scrap counts as a kite scrap (cap 10, item-ledger-scrap); past the cap, or with scraps off,
# the find is a casket instead.
execute unless score #retalhos cc_cfg matches 1 run return run function orespawn_integrations:extravio/copa/dar/arca
execute if score @s cc_scrap_kite matches 10.. run return run function orespawn_integrations:extravio/copa/dar/arca
function orespawn_integrations:retalho/kite
playsound minecraft:block.azalea_leaves.break player @s ~ ~ ~ 1 1
title @s actionbar {"translate": "orespawn_integrations.extravio.copa.retalho"}
