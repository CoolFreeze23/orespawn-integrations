# The cobblestone cell around the opener, with air where they stand (ly's cells hold water, lava or TNT). Bare hands
# break cobblestone, slowly; the first pickaxe is quicker.
execute as @p[distance=..6] at @s run fill ~-1 ~2 ~-1 ~1 ~-1 ~1 minecraft:cobblestone replace #minecraft:air
execute as @p[distance=..6] at @s run fill ~ ~ ~ ~ ~1 ~ minecraft:air replace minecraft:cobblestone
