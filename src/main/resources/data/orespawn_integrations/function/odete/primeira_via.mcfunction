# The first copy a player collects is the Treasurer's address: R21's reveal and one extra line, the day after filing.
tag @s add cc_via_conferida
tellraw @s {"translate":"orespawn_integrations.odete.primeira_via","with":[{"score":{"name":"#dia_visto","objective":"cc_tmp"}}]}
advancement grant @s only orespawn_integrations:livro/where_did_the_treasurer_go
