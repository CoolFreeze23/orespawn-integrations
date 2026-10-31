# Operator test (the /function command needs permission 2): one rank-0 'small_fireball' roll, run as the casket would for a
# casket placed two blocks in front of the caller (the display sits 1.01 over the block's centre).
scoreboard objectives add cc_lucky dummy
scoreboard players set #oi_grace cc_lucky 0
execute rotated ~ 0 positioned ^ ^ ^2 align xyz positioned ~0.5 ~1.01 ~0.5 run function orespawn_integrations:lucky/hatchling/trap/small_fireball
