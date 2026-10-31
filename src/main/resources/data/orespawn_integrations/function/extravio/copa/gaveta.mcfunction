# The canopy's Wrong Drawer item row. The day's cap is checked again here, so tokens left lying about can
# never make a fourth find: one over the cap is only taken away.
clear @s minecraft:paper[minecraft:custom_data~{"orespawn_integrations:copa":{row:"gaveta"}}] 1
execute if score @s cc_copa matches 3.. run return run function orespawn_integrations:extravio/copa/recebido
scoreboard players add @s cc_copa 1
function orespawn_integrations:extravio/copa/dar/gaveta
function orespawn_integrations:extravio/copa/recebido
