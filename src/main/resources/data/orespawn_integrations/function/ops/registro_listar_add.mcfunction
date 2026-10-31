# Macro for ops/registro_listar: refuses a position already listed, else appends it and reports the count.
$execute if data storage orespawn_integrations:registro desks[{pos:[I;$(x),$(y),$(z)],dim:"$(dim)"}] run return run tellraw @s {"translate":"orespawn_integrations.registro.ops.ja_listado","with":["$(x)","$(y)","$(z)"]}
$data modify storage orespawn_integrations:registro desks append value {pos:[I;$(x),$(y),$(z)],dim:"$(dim)"}
execute store result score #n cc_tmp run data get storage orespawn_integrations:registro desks
tellraw @s {"translate":"orespawn_integrations.registro.ops.listado","with":[{"score":{"name":"#n","objective":"cc_tmp"}},"32"]}
