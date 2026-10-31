# A snagged kite's scrap (item-ledger-scrap): at most 10 per player, counted in cc_scrap_kite. Run as the player.
# The 1-in-3 roll is the caller's. Off with [story] scraps.
execute unless score #retalhos cc_cfg matches 1 run return fail
execute if score @s cc_scrap_kite matches 10.. run return fail
scoreboard players add @s cc_scrap_kite 1
give @s orespawn_integrations:ledger_scrap 1
