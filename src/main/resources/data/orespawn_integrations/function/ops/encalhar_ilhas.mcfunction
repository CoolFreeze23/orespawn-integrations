# Operator only, run by hand and never scheduled (system-night-rebalance, mob-patrimonio-que-anda): removes every
# loaded OreSpawn island entity and leaves the island's blocks where they are.
execute store result storage orespawn_integrations:tmp ilhas int 1 if entity @e[type=orespawn:island]
execute store result storage orespawn_integrations:tmp ilhas_too int 1 if entity @e[type=orespawn:island_too]
kill @e[type=orespawn:island]
kill @e[type=orespawn:island_too]
function orespawn_integrations:night/encalhar_ilhas_feito
