# Somebody's Yard (system-hoard-caskets, 0.10.1). Within 48 blocks of a Parish Stone, at any rank, a casket roll that
# would fill, burn or strike the place becomes one small gift from the Land, paid at the nearest stone's marker so the
# yard's keeper gets it, not the opener. Runs as the casket's display; the marker search is bounded by its distance.
scoreboard objectives add cc_arcas dummy
scoreboard objectives add cc_quintal dummy
tellraw @p[distance=..6] {"translate":"orespawn_integrations.lucky.yard.opener"}
tellraw @p[distance=..6] {"translate":"orespawn_integrations.lucky.yard.opener_paid"}
execute store result score #oi_pick cc_lucky run random value 1..6
execute if score #oi_pick cc_lucky matches 6 run tellraw @p[distance=..6] {"translate":"orespawn_integrations.lucky.yard.capybara"}
execute as @e[type=minecraft:marker,tag=cc_freguesia,distance=..48,sort=nearest,limit=1] at @s run function orespawn_integrations:lucky/yard/pay
