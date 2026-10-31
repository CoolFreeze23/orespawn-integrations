# The Hatchling's traps: ly's 18 slots, each a joke aimed at the opener and none that burns a barn or floods a yard.
# Projectiles, the TNT and the lightning carry the tag cc_lucky_trap, so the mod's listener keeps their blast and
# their fire off blocks and animals: only players are hurt.
execute store result score #oi_pick cc_lucky run random value 1..18
execute if score #oi_pick cc_lucky matches 1 run return run function orespawn_integrations:lucky/hatchling/trap/arrow
execute if score #oi_pick cc_lucky matches 2 run return run function orespawn_integrations:lucky/hatchling/trap/spectral_arrow
execute if score #oi_pick cc_lucky matches 3 run return run function orespawn_integrations:lucky/hatchling/trap/anvil
execute if score #oi_pick cc_lucky matches 4 run return run function orespawn_integrations:lucky/hatchling/trap/lava
execute if score #oi_pick cc_lucky matches 5 run return run function orespawn_integrations:lucky/hatchling/trap/water
execute if score #oi_pick cc_lucky matches 6 run return run function orespawn_integrations:lucky/hatchling/trap/water_drop
execute if score #oi_pick cc_lucky matches 7 run return run function orespawn_integrations:lucky/hatchling/trap/tnt
execute if score #oi_pick cc_lucky matches 8 run return run function orespawn_integrations:lucky/hatchling/trap/tnt
execute if score #oi_pick cc_lucky matches 9 run return run function orespawn_integrations:lucky/hatchling/trap/fireball
execute if score #oi_pick cc_lucky matches 10 run return run function orespawn_integrations:lucky/hatchling/trap/small_fireball
execute if score #oi_pick cc_lucky matches 11 run return run function orespawn_integrations:lucky/hatchling/trap/wither_skull
execute if score #oi_pick cc_lucky matches 12 run return run function orespawn_integrations:lucky/hatchling/trap/lightning
execute if score #oi_pick cc_lucky matches 13 run return run function orespawn_integrations:lucky/hatchling/trap/cell
execute if score #oi_pick cc_lucky matches 14 run return run function orespawn_integrations:lucky/hatchling/trap/cell
execute if score #oi_pick cc_lucky matches 15 run return run function orespawn_integrations:lucky/hatchling/trap/cell
execute if score #oi_pick cc_lucky matches 16 run return run function orespawn_integrations:lucky/hatchling/trap/chicken_tower
execute if score #oi_pick cc_lucky matches 17 run return run function orespawn_integrations:lucky/hatchling/trap/capybara_tower
execute if score #oi_pick cc_lucky matches 18 run return run function orespawn_integrations:lucky/hatchling/trap/breeze
