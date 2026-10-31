# The Great Refusal (Knight and up): one of three refused gifts, each a Pandora's Box holder at chance 0.0 that never
# rolls on its own. /pandora opens it as a real box owned by the opener. Nobody named, no gift: a part instead.
execute unless entity @p[distance=..6] run return run function orespawn_integrations:lucky/hatchling/jackpot
execute store result score #oi_pick cc_lucky run random value 1..3
execute if score #oi_pick cc_lucky matches 1 run data modify storage orespawn_integrations:lucky gift set value {effect:"orespawn_integrations:grande_recusa/void_30",sender:"orespawn_integrations.lucky.sender.inner_sea"}
execute if score #oi_pick cc_lucky matches 2 run data modify storage orespawn_integrations:lucky gift set value {effect:"orespawn_integrations:grande_recusa/wither_army",sender:"orespawn_integrations.lucky.sender.towers"}
execute if score #oi_pick cc_lucky matches 3 run data modify storage orespawn_integrations:lucky gift set value {effect:"orespawn_integrations:mobzilla_apocalypse",sender:"orespawn_integrations.lucky.sender.kaiju"}
scoreboard players add #oi_refusals cc_lucky 1
execute as @p[distance=..6] at @s run function orespawn_integrations:lucky/grande_recusa/open with storage orespawn_integrations:lucky gift
