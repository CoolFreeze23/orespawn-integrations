# Sets #oi_grace to 1 while the opener's first join (cc_lucky_dia, written by the mod at login) is under three game
# days ago. A missing day reads as 0, which is long ago: grace over.
execute store result score #oi_today cc_lucky run time query day
scoreboard players operation #oi_age cc_lucky = #oi_today cc_lucky
scoreboard players operation #oi_age cc_lucky -= @p[distance=..6] cc_lucky_dia
execute if score #oi_age cc_lucky matches ..2 run scoreboard players set #oi_grace cc_lucky 1
