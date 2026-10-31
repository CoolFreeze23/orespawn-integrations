# The Royal Casket's root (system-hoard-caskets), replacing lullaby's Lucky Blocks' own root; OI loads after ly, so
# this copy wins. It runs as the casket's item_display a tick after the break, at the display (about 1.5 above the
# broken block). Twelve rolls: our own outcomes 3 in 12, the traps 2 in 12, every other ly branch 1 in 12. ly's root
# rolls 1..9, so a lucky_blocks.random of 10-12 left on the caller can only come from this file (the load-order check).
scoreboard objectives add cc_lucky dummy
execute store result score @s lucky_blocks.random run random value 1..12
scoreboard players operation #oi_root cc_lucky = @s lucky_blocks.random

# Somebody's Yard (0.10.1): within 48 blocks of a Parish Stone's marker the rank tables swap their traps and harmful
# spells for the Land's return. One marker check per casket, its box bounded by the distance; off with the switch.
scoreboard players set #oi_yard cc_lucky 0
execute unless data storage orespawn_integrations:config {scaled_caskets:0b} if entity @e[type=minecraft:marker,tag=cc_freguesia,distance=..48,limit=1] run scoreboard players set #oi_yard cc_lucky 1

# story.scaled_caskets off (written to storage by the mod at start-up): the branches as OI 0.9.0 shipped them.
execute if data storage orespawn_integrations:config {scaled_caskets:0b} run function orespawn_integrations:lucky/rank/shipped
execute unless data storage orespawn_integrations:config {scaled_caskets:0b} run function orespawn_integrations:lucky/rank/read

# Every branch rolls its own numbers on the same score; the root's roll goes back on the caller last.
scoreboard players operation @s lucky_blocks.random = #oi_root cc_lucky
