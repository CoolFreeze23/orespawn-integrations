# Rank 0, the Hatchling table: ly's branch odds, every outcome aimed at the opener and none that ends a run (no boss,
# no whole boss egg, no lava, no OP enemy).
execute if score #oi_root cc_lucky matches 1..2 run return run function orespawn_integrations:lucky/hatchling/trap
execute if score #oi_root cc_lucky matches 3 run return run function orespawn_integrations:lucky/hatchling/item
execute if score #oi_root cc_lucky matches 4 run return run function orespawn_integrations:lucky/hatchling/entity
execute if score #oi_root cc_lucky matches 5 run return run function orespawn_integrations:lucky/hatchling/block
execute if score #oi_root cc_lucky matches 6 run return run function lucky_blocks:random/summon/chest/random
execute if score #oi_root cc_lucky matches 7 run return run function lucky_blocks:random/summon/loot/random
execute if score #oi_root cc_lucky matches 8 run return run function orespawn_integrations:lucky/hatchling/custom
execute if score #oi_root cc_lucky matches 9 run return run function orespawn_integrations:lucky/hatchling/effect
execute if score #oi_root cc_lucky matches 10..12 run return run function orespawn_integrations:lucky/hatchling/random
