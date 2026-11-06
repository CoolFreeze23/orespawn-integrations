# What the creature row gives: one OreSpawn misfit of the Base's two shelves (Water: the Gold Fish and the
# Rubber Ducky; the Royal Orchard: the Gold Cow, the Red Cow, the Gazelle, the T-Shirt and the Cricket), and its
# Misfile Tag to the breaker. With story.misfile off the find is a casket.
execute unless score #extravio cc_cfg matches 1 run return run function orespawn_integrations:extravio/copa/dar/arca
execute store result score #oi_bicho cc_copa run random value 1..7
execute if score #oi_bicho cc_copa matches 1 run data modify storage orespawn_integrations:extravio bicho set value {id:"orespawn:gold_fish",shelf:"agua",ekey:"entity.orespawn.gold_fish"}
execute if score #oi_bicho cc_copa matches 2 run data modify storage orespawn_integrations:extravio bicho set value {id:"orespawn:rubber_ducky",shelf:"agua",ekey:"entity.orespawn.rubber_ducky"}
execute if score #oi_bicho cc_copa matches 3 run data modify storage orespawn_integrations:extravio bicho set value {id:"orespawn:gold_cow",shelf:"pomar",ekey:"entity.orespawn.gold_cow"}
execute if score #oi_bicho cc_copa matches 4 run data modify storage orespawn_integrations:extravio bicho set value {id:"orespawn:red_cow",shelf:"pomar",ekey:"entity.orespawn.red_cow"}
execute if score #oi_bicho cc_copa matches 5 run data modify storage orespawn_integrations:extravio bicho set value {id:"orespawn:gazelle",shelf:"pomar",ekey:"entity.orespawn.gazelle"}
execute if score #oi_bicho cc_copa matches 6 run data modify storage orespawn_integrations:extravio bicho set value {id:"orespawn:tshirt",shelf:"pomar",ekey:"entity.orespawn.tshirt"}
execute if score #oi_bicho cc_copa matches 7 run data modify storage orespawn_integrations:extravio bicho set value {id:"orespawn:cricket",shelf:"pomar",ekey:"entity.orespawn.cricket"}
function orespawn_integrations:extravio/copa/dar/bicho_pousa
