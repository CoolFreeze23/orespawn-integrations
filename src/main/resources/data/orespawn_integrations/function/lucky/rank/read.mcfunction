# Reads the opener's rank. The opener is the nearest player within 6 of the display (ly records no breaker).
# 0.10.0 has no court ranks yet, so Knight and up is the Apotheosis Summit holder; everyone else, and a casket nobody
# stood by, falls through to the Hatchling table. The rank-0 branch is the fall-through (an unset score fails every
# matches test, matches 0 included).
scoreboard players reset #oi_rank cc_lucky
scoreboard players set #oi_grace cc_lucky 0
scoreboard players set #oi_shell cc_lucky 0

# The first casket opened after an Eggshell rolls the rank-0 table whatever the opener's rank (cc_shell_casket, set
# by the shell); reading it clears it.
execute if score @p[distance=..6] cc_shell_casket matches 1.. run scoreboard players set #oi_shell cc_lucky 1
execute if score #oi_shell cc_lucky matches 1 run scoreboard players reset @p[distance=..6] cc_shell_casket

execute unless score #oi_shell cc_lucky matches 1.. if entity @p[distance=..6,advancements={apotheosis:progression/summit=true}] run scoreboard players set #oi_rank cc_lucky 3

# A Knight still in their Hatchling grace (first join under three game days ago) rolls the Squire row.
execute if score #oi_rank cc_lucky matches 2.. run function orespawn_integrations:lucky/grace
execute if score #oi_rank cc_lucky matches 2.. if score #oi_grace cc_lucky matches 1 run return run function orespawn_integrations:lucky/rank/squire
execute if score #oi_rank cc_lucky matches 2.. run return run function orespawn_integrations:lucky/rank/knight
execute unless score #oi_rank cc_lucky matches 1.. run function orespawn_integrations:lucky/rank/hatchling
