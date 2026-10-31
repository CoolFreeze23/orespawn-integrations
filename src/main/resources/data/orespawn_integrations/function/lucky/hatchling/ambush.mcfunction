# Two or three small Hoard creatures (the shipped ambush is 3-5, and its Cater Killer is a heavyweight).
execute store result score #oi_pick cc_lucky run random value 1..9
execute if score #oi_pick cc_lucky matches 1 run data modify storage lucky_blocks:storage value set value "orespawn:mosquito"
execute if score #oi_pick cc_lucky matches 2 run data modify storage lucky_blocks:storage value set value "orespawn:scorpion"
execute if score #oi_pick cc_lucky matches 3 run data modify storage lucky_blocks:storage value set value "orespawn:alien"
execute if score #oi_pick cc_lucky matches 4 run data modify storage lucky_blocks:storage value set value "orespawn:robot_1"
execute if score #oi_pick cc_lucky matches 5 run data modify storage lucky_blocks:storage value set value "orespawn:red_ant"
execute if score #oi_pick cc_lucky matches 6 run data modify storage lucky_blocks:storage value set value "orespawn:velocity_raptor"
execute if score #oi_pick cc_lucky matches 7 run data modify storage lucky_blocks:storage value set value "orespawn:creeping_horror"
execute if score #oi_pick cc_lucky matches 8 run data modify storage lucky_blocks:storage value set value "orespawn:trooper_bug"
execute if score #oi_pick cc_lucky matches 9 run data modify storage lucky_blocks:storage value set value "orespawn:spit_bug"
function orespawn_integrations:lucky/summon with storage lucky_blocks:storage
function orespawn_integrations:lucky/summon with storage lucky_blocks:storage
execute store result score #oi_pick cc_lucky run random value 1..2
execute if score #oi_pick cc_lucky matches 2 run function orespawn_integrations:lucky/summon with storage lucky_blocks:storage
