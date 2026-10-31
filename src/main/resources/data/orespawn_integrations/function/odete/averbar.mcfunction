# The averbacao (system-corrections): one Ledger Scrap becomes one Correction Slip now and its copy the next day.
# Run from the desk dialog as the player, at the NPC. Pays any copy already due first, silently.
execute unless score #correcoes cc_cfg matches 1 run return run tellraw @s {"translate":"orespawn_integrations.odete.fechado"}
execute store result score #hoje cc_tmp run time query day
#   from 0.10.1 this line is: scoreboard players operation #hoje cc_tmp = #dia cc_terra
scoreboard players set #silencio cc_tmp 1
function orespawn_integrations:odete/segunda_via
scoreboard players set #silencio cc_tmp 0
scoreboard players set #fechado cc_tmp 0
function #orespawn_integrations:odete/guiche_fechado
execute if score #fechado cc_tmp matches 1 run return fail
execute store result score #tem cc_tmp run clear @s orespawn_integrations:ledger_scrap 0
execute if score #tem cc_tmp matches 0 run return run tellraw @s {"translate":"orespawn_integrations.odete.sem_retalho"}
clear @s orespawn_integrations:ledger_scrap 1
give @s orespawn_integrations:guia_de_averbacao 1
advancement grant @s only orespawn_integrations:correcoes/primeiro_gasto
scoreboard players set #via_hoje cc_tmp 0
function #orespawn_integrations:odete/copia_hoje
execute if score #via_hoje cc_tmp matches 1 run return run function orespawn_integrations:odete/via_hoje
scoreboard players add @s cc_vias 1
scoreboard players operation @s cc_via_dia = #hoje cc_tmp
scoreboard players operation @s cc_via_reg = #hoje cc_tmp
function #orespawn_integrations:odete/copia_depois
tellraw @s {"translate":"orespawn_integrations.odete.averbado"}
