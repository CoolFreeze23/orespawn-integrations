# One Field Notebook scrap (one per five stamps; the caller counts stamps): at most 8 per player, in cc_scrap_notebook.
execute unless score #retalhos cc_cfg matches 1 run return fail
execute if score @s cc_scrap_notebook matches 8.. run return fail
scoreboard players add @s cc_scrap_notebook 1
give @s orespawn_integrations:ledger_scrap 1
