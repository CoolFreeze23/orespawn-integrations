# The first lote egg picked up (wonder-gemeo-da-arvore): no log drops another twin from now on, and the log it
# fell from is kept per player for the late registration's DNV (0.17.0). Run once per player by livro/lote_pickup.
scoreboard objectives add cc_gemeo dummy
scoreboard players set @s cc_gemeo 1
playsound minecraft:entity.turtle.egg_crack player @a[distance=..16] ~ ~ ~ 1 1
particle minecraft:poof ~ ~0.5 ~ 0.3 0.3 0.3 0.02 6
data remove storage orespawn_integrations:tmp lote
data modify storage orespawn_integrations:tmp lote.uuid set from entity @s UUID
data modify storage orespawn_integrations:tmp lote.log set from entity @s Inventory[{components:{"minecraft:custom_data":{"orespawn_integrations:lote":{}}}}].components."minecraft:custom_data"."orespawn_integrations:lote".log
data modify storage orespawn_integrations:players lote append from storage orespawn_integrations:tmp lote
data remove storage orespawn_integrations:tmp lote
