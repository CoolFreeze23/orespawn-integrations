# One root roll as the marker; recurses forty times.
function lucky_blocks:random/random
execute if score @s lucky_blocks.random matches 10..12 run scoreboard players add #oi_load_hits cc_lucky 1
scoreboard players add #oi_load_runs cc_lucky 1
execute if score #oi_load_runs cc_lucky matches ..39 run function orespawn_integrations:lucky/test/load_order_step
