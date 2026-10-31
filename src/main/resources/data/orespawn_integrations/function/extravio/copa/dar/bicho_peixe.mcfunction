# Fish in the Trees: every canopy creature today is a Gold Fish.
execute unless score #extravio cc_cfg matches 1 run return run function orespawn_integrations:extravio/copa/dar/arca
data modify storage orespawn_integrations:extravio bicho set value {id:"orespawn:gold_fish",shelf:"agua",ekey:"entity.orespawn.gold_fish"}
function orespawn_integrations:extravio/copa/dar/bicho_pousa
