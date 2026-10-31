# A water bucket, twelve blocks up and 15 s of slow falling, for the opener; under a roof nobody is lifted into it.
give @p[distance=..10] minecraft:water_bucket
effect give @p[distance=..10] minecraft:slow_falling 15 0
execute as @p[distance=..10] at @s if block ~ ~12 ~ #minecraft:air if block ~ ~13 ~ #minecraft:air run tp @s ~ ~12 ~
