# The spell branch in Somebody's Yard: its harmful half is the Land's return; the other half is a kind spell on the
# players beside the casket, never on the yard's animals.
execute store result score #oi_pick cc_lucky run random value 1..2
execute if score #oi_pick cc_lucky matches 1 run return run function orespawn_integrations:lucky/yard
execute store result score #oi_pick cc_lucky run random value 1..12
execute if score #oi_pick cc_lucky matches 1 run data modify storage orespawn_integrations:lucky spell.value set value "absorption"
execute if score #oi_pick cc_lucky matches 2 run data modify storage orespawn_integrations:lucky spell.value set value "fire_resistance"
execute if score #oi_pick cc_lucky matches 3 run data modify storage orespawn_integrations:lucky spell.value set value "haste"
execute if score #oi_pick cc_lucky matches 4 run data modify storage orespawn_integrations:lucky spell.value set value "health_boost"
execute if score #oi_pick cc_lucky matches 5 run data modify storage orespawn_integrations:lucky spell.value set value "hero_of_the_village"
execute if score #oi_pick cc_lucky matches 6 run data modify storage orespawn_integrations:lucky spell.value set value "jump_boost"
execute if score #oi_pick cc_lucky matches 7 run data modify storage orespawn_integrations:lucky spell.value set value "luck"
execute if score #oi_pick cc_lucky matches 8 run data modify storage orespawn_integrations:lucky spell.value set value "night_vision"
execute if score #oi_pick cc_lucky matches 9 run data modify storage orespawn_integrations:lucky spell.value set value "regeneration"
execute if score #oi_pick cc_lucky matches 10 run data modify storage orespawn_integrations:lucky spell.value set value "resistance"
execute if score #oi_pick cc_lucky matches 11 run data modify storage orespawn_integrations:lucky spell.value set value "speed"
execute if score #oi_pick cc_lucky matches 12 run data modify storage orespawn_integrations:lucky spell.value set value "strength"
execute store result storage orespawn_integrations:lucky spell.duration int 1 run random value 10..60
function orespawn_integrations:lucky/hatchling/effect_give with storage orespawn_integrations:lucky spell
