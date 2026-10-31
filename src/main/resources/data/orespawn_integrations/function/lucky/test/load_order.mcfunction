# Operator test, script line 8: forty root rolls from one marker deep under the world; the count of 10-12 results goes
# to #oi_load_hits cc_lucky (ly's own root can never leave one). The marker is removed afterwards.
scoreboard objectives add cc_lucky dummy
scoreboard players set #oi_load_hits cc_lucky 0
scoreboard players set #oi_load_runs cc_lucky 0
summon minecraft:marker ~ -120 ~ {Tags:["cc_lucky_probe"]}
execute as @e[type=minecraft:marker,tag=cc_lucky_probe,limit=1] at @s run function orespawn_integrations:lucky/test/load_order_step
kill @e[type=minecraft:marker,tag=cc_lucky_probe]
tellraw @s ["load order: ",{"score":{"name":"#oi_load_hits","objective":"cc_lucky"}}," of 40 rolls were 10-12"]
