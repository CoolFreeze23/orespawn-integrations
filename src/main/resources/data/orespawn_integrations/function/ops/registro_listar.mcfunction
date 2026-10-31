# Operator line (npc-odetes, the hub's desks): run at a desk to add its position to the desk list read by the Field
# Odete's pin, storage orespawn_integrations:registro desks (at most 32 entries, {pos:[I;x,y,z], dim:"..."}).
# Run by a player standing at the desk (permission 2); a second run at the same block is refused.
execute unless score #registro cc_cfg matches 1 run return run tellraw @s {"translate":"orespawn_integrations.registro.ops.desligado"}
execute store result score #n cc_tmp run data get storage orespawn_integrations:registro desks
execute if score #n cc_tmp matches 32.. run return run tellraw @s {"translate":"orespawn_integrations.registro.ops.cheio","with":["32"]}
data modify storage orespawn_integrations:registro novo set value {dim:"minecraft:overworld"}
execute if entity @s[type=minecraft:player] run data modify storage orespawn_integrations:registro novo.dim set from entity @s Dimension
execute align xyz run summon minecraft:marker ~ ~ ~ {Tags:["cc_registro_tmp"]}
execute store result storage orespawn_integrations:registro novo.x int 1 run data get entity @e[type=minecraft:marker,tag=cc_registro_tmp,limit=1,sort=nearest] Pos[0]
execute store result storage orespawn_integrations:registro novo.y int 1 run data get entity @e[type=minecraft:marker,tag=cc_registro_tmp,limit=1,sort=nearest] Pos[1]
execute store result storage orespawn_integrations:registro novo.z int 1 run data get entity @e[type=minecraft:marker,tag=cc_registro_tmp,limit=1,sort=nearest] Pos[2]
kill @e[type=minecraft:marker,tag=cc_registro_tmp]
function orespawn_integrations:ops/registro_listar_add with storage orespawn_integrations:registro novo
data remove storage orespawn_integrations:registro novo
