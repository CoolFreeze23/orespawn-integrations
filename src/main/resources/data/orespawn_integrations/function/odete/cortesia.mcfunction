# Ask for the courtesy: the free slips the cortesia clauses owe this game day, paid once a day (cc_guia_dia = today).
execute unless score #correcoes cc_cfg matches 1 run return run tellraw @s {"translate":"orespawn_integrations.odete.fechado"}
execute store result score #hoje cc_tmp run time query day
scoreboard players set #cortesia cc_tmp 0
function #orespawn_integrations:odete/cortesia
execute if score #cortesia cc_tmp matches ..0 run return run tellraw @s {"translate":"orespawn_integrations.odete.sem_cortesia"}
execute if score @s cc_guia_dia = #hoje cc_tmp run return run tellraw @s {"translate":"orespawn_integrations.odete.cortesia_ja"}
scoreboard players operation @s cc_guia_dia = #hoje cc_tmp
execute store result storage orespawn_integrations:registro guias.n int 1 run scoreboard players get #cortesia cc_tmp
function orespawn_integrations:odete/dar_guias with storage orespawn_integrations:registro guias
tellraw @s {"translate":"orespawn_integrations.odete.cortesia"}
