# A province stamp (system-court-ranks, "The stamp, one a day"), called by each province advancement's reward with
# its own path. The day is the Land's day (#dia cc_terra, the Land's Week), which a /time set never rewinds; before
# the Land's first dawn it is unset and reads 0. No stamp yet: store today. A stamp from an earlier day: Two
# Provinces, whose grant makes a Squire. The same day: re-arm this province's advancement so its next entry fires
# again, and wait. The tags tell the Java handler which subtitle to show; it removes them.
scoreboard objectives add cc_carimbo dummy
scoreboard objectives add cc_carimbo_espera dummy
scoreboard objectives add cc_terra dummy
execute store result score #hoje cc_carimbo run scoreboard players get #dia cc_terra
execute unless score @s cc_carimbo matches -2147483648.. run return run function orespawn_integrations:court/carimbo_primeiro
execute if score @s cc_carimbo < #hoje cc_carimbo run return run advancement grant @s only orespawn_integrations:court/duas_provincias
$advancement revoke @s only orespawn_integrations:court/provincia/$(path)
execute unless score @s cc_carimbo_espera matches 1 run tag @s add cc_carimbo_um_por_dia
scoreboard players set @s cc_carimbo_espera 1
