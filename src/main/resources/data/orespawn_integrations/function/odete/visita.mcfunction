# A player reached a Registry desk (the desk dialog's opening command). firsthour's ticket reads the tag every
# 20 ticks (P09, or out of turn) and removes it. The first Registry dialog also grants R1.
execute unless score #registro cc_cfg matches 1 run return fail
tag @s add cc_registro_visita
advancement grant @s only orespawn_integrations:livro/r1
