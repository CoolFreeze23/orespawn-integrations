# Collect the copy: every slip promised for a day before today. A copy is ready when today is past its day, or when
# today is before the day it was filed (the clock went back: /time set, the 0.10.0 rewind guard).
# Called from averbar with #silencio 1 it says nothing when nothing is ready.
execute unless score #correcoes cc_cfg matches 1 run return run tellraw @s {"translate":"orespawn_integrations.odete.fechado"}
execute store result score #hoje cc_tmp run time query day
execute unless score @s cc_vias matches 1.. unless score #silencio cc_tmp matches 1 run tellraw @s {"translate":"orespawn_integrations.odete.nada_pendente"}
execute unless score @s cc_vias matches 1.. run return fail
scoreboard players set #pronta cc_tmp 0
execute if score #hoje cc_tmp > @s cc_via_dia run scoreboard players set #pronta cc_tmp 1
execute if score #hoje cc_tmp < @s cc_via_reg run scoreboard players set #pronta cc_tmp 1
execute if score #pronta cc_tmp matches 0 unless score #silencio cc_tmp matches 1 run tellraw @s {"translate":"orespawn_integrations.odete.cedo"}
execute if score #pronta cc_tmp matches 0 run return fail
execute store result storage orespawn_integrations:registro guias.n int 1 run scoreboard players get @s cc_vias
function orespawn_integrations:odete/dar_guias with storage orespawn_integrations:registro guias
scoreboard players operation #dia_visto cc_tmp = @s cc_via_reg
scoreboard players add #dia_visto cc_tmp 1
scoreboard players set @s cc_vias 0
tellraw @s {"translate":"orespawn_integrations.odete.segunda_via"}
execute unless entity @s[tag=cc_via_conferida] run function orespawn_integrations:odete/primeira_via
