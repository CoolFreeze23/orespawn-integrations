# story.scaled_caskets = false: the twelve rolls as OI 0.9.0 shipped them, for every opener.
execute if score #oi_root cc_lucky matches 1..2 run return run function lucky_blocks:random/trap/random
execute if score #oi_root cc_lucky matches 3 run return run function lucky_blocks:random/summon/item/random
execute if score #oi_root cc_lucky matches 4 run return run function lucky_blocks:random/summon/entity/random
execute if score #oi_root cc_lucky matches 5 run return run function lucky_blocks:random/summon/block/random
execute if score #oi_root cc_lucky matches 6 run return run function lucky_blocks:random/summon/chest/random
execute if score #oi_root cc_lucky matches 7 run return run function lucky_blocks:random/summon/loot/random
execute if score #oi_root cc_lucky matches 8 run return run function lucky_blocks:random/summon/custom/random
execute if score #oi_root cc_lucky matches 9 run return run function lucky_blocks:random/effect/random
execute if score #oi_root cc_lucky matches 10..12 run return run function orespawn_integrations:lucky/random
