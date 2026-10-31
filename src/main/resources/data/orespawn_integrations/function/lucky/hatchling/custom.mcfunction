# The rank-0 custom branch (system-hoard-caskets): ly's 47 custom outcomes minus the twelve OP enemies, so a
# Hatchling's casket rerolls into the other 35 (the god-roll gear, the elytra and the mace stay in). Written by
# tools/lucky/gen_hatchling_lists.py from ly's custom/random; it calls ly's own spawn functions by id.
execute store result score #oi_pick cc_lucky run random value 1..35

execute if score #oi_pick cc_lucky matches 1 run return run function lucky_blocks:random/summon/custom/functions/spawn/snow_fox
execute if score #oi_pick cc_lucky matches 2 run return run function lucky_blocks:random/summon/custom/functions/spawn/brown_panda
execute if score #oi_pick cc_lucky matches 3 run return run function lucky_blocks:random/summon/custom/functions/spawn/blue_axolotl
execute if score #oi_pick cc_lucky matches 4 run return run function lucky_blocks:random/summon/custom/functions/spawn/brown_mooshroom
execute if score #oi_pick cc_lucky matches 5 run return run function lucky_blocks:random/summon/custom/functions/spawn/charged_creeper
execute if score #oi_pick cc_lucky matches 6 run return run function lucky_blocks:random/summon/custom/functions/spawn/pink_sheep
execute if score #oi_pick cc_lucky matches 7 run return run function lucky_blocks:random/summon/custom/functions/spawn/screaming_goat
execute if score #oi_pick cc_lucky matches 8 run return run function lucky_blocks:random/summon/custom/functions/spawn/skeleton_horse_trap
execute if score #oi_pick cc_lucky matches 9 run return run function lucky_blocks:random/summon/custom/functions/spawn/spider_jockey
execute if score #oi_pick cc_lucky matches 10 run return run function lucky_blocks:random/summon/custom/functions/spawn/ore_pilar
execute if score #oi_pick cc_lucky matches 11 run return run function lucky_blocks:random/summon/custom/functions/spawn/ore_block_pilar
execute if score #oi_pick cc_lucky matches 12 run return run function lucky_blocks:random/summon/custom/functions/spawn/zoglin
execute if score #oi_pick cc_lucky matches 13 run return run function lucky_blocks:random/summon/custom/functions/spawn/chicken_jockey
execute if score #oi_pick cc_lucky matches 14 run return run function lucky_blocks:random/summon/custom/functions/spawn/op/item/axe
execute if score #oi_pick cc_lucky matches 15 run return run function lucky_blocks:random/summon/custom/functions/spawn/op/item/boots
execute if score #oi_pick cc_lucky matches 16 run return run function lucky_blocks:random/summon/custom/functions/spawn/op/item/bow
execute if score #oi_pick cc_lucky matches 17 run return run function lucky_blocks:random/summon/custom/functions/spawn/op/item/chestplate
execute if score #oi_pick cc_lucky matches 18 run return run function lucky_blocks:random/summon/custom/functions/spawn/op/item/crossbow
execute if score #oi_pick cc_lucky matches 19 run return run function lucky_blocks:random/summon/custom/functions/spawn/op/item/elytra
execute if score #oi_pick cc_lucky matches 20 run return run function lucky_blocks:random/summon/custom/functions/spawn/op/item/fishing_rod
execute if score #oi_pick cc_lucky matches 21 run return run function lucky_blocks:random/summon/custom/functions/spawn/op/item/helmet
execute if score #oi_pick cc_lucky matches 22 run return run function lucky_blocks:random/summon/custom/functions/spawn/op/item/hoe
execute if score #oi_pick cc_lucky matches 23 run return run function lucky_blocks:random/summon/custom/functions/spawn/op/item/leggings
execute if score #oi_pick cc_lucky matches 24 run return run function lucky_blocks:random/summon/custom/functions/spawn/op/item/mace
execute if score #oi_pick cc_lucky matches 25 run return run function lucky_blocks:random/summon/custom/functions/spawn/op/item/pickaxe
execute if score #oi_pick cc_lucky matches 26 run return run function lucky_blocks:random/summon/custom/functions/spawn/op/item/shield
execute if score #oi_pick cc_lucky matches 27 run return run function lucky_blocks:random/summon/custom/functions/spawn/op/item/shovel
execute if score #oi_pick cc_lucky matches 28 run return run function lucky_blocks:random/summon/custom/functions/spawn/op/item/sword
execute if score #oi_pick cc_lucky matches 29 run return run function lucky_blocks:random/summon/custom/functions/spawn/op/item/trident
execute if score #oi_pick cc_lucky matches 30 run return run function lucky_blocks:random/summon/custom/functions/spawn/kit/chainmail
execute if score #oi_pick cc_lucky matches 31 run return run function lucky_blocks:random/summon/custom/functions/spawn/kit/diamond
execute if score #oi_pick cc_lucky matches 32 run return run function lucky_blocks:random/summon/custom/functions/spawn/kit/gold
execute if score #oi_pick cc_lucky matches 33 run return run function lucky_blocks:random/summon/custom/functions/spawn/kit/iron
execute if score #oi_pick cc_lucky matches 34 run return run function lucky_blocks:random/summon/custom/functions/spawn/kit/leather
execute if score #oi_pick cc_lucky matches 35 run return run function lucky_blocks:random/summon/custom/functions/spawn/kit/netherite
