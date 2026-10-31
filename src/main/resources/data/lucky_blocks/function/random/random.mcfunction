# The Royal Casket's root (system-hoard-caskets), replacing lullaby's Lucky Blocks' own root; OI loads after ly, so
# this copy wins. It runs as the casket's item_display a tick after the break, at the display (about 1.5 above the
# broken block). Twelve rolls: our own outcomes 3 in 12, the traps 2 in 12, every other ly branch 1 in 12. ly's root
# rolls 1..9, so a lucky_blocks.random of 10-12 left on the caller can only come from this file (the load-order check).
scoreboard objectives add cc_lucky dummy
scoreboard objectives add cc_shell_casket dummy
scoreboard objectives add cc_lucky_dia dummy
scoreboard objectives add cc_ovo_semana dummy
execute store result score @s lucky_blocks.random run random value 1..12
scoreboard players operation #oi_root cc_lucky = @s lucky_blocks.random

# story.scaled_caskets off (written to storage by the mod at start-up): the branches as OI 0.9.0 shipped them.
execute if data storage orespawn_integrations:config {scaled_caskets:0b} run function orespawn_integrations:lucky/rank/shipped
execute unless data storage orespawn_integrations:config {scaled_caskets:0b} run function orespawn_integrations:lucky/rank/read

# Every branch rolls its own numbers on the same score; the root's roll goes back on the caller last.
scoreboard players operation @s lucky_blocks.random = #oi_root cc_lucky
