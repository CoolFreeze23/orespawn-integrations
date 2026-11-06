# The landing: the day it fell goes into the storage, then the macro summons it and hands over its tag.
execute store result storage orespawn_integrations:extravio bicho.day int 1 run time query day
function orespawn_integrations:extravio/copa/dar/bicho_macro with storage orespawn_integrations:extravio bicho
playsound minecraft:block.azalea_leaves.break player @s ~ ~ ~ 1 1
title @s actionbar {"translate": "orespawn_integrations.extravio.copa.bicho"}
