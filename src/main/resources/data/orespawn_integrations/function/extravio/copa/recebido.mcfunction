# A canopy find reached its breaker's inventory (system-extravio): the token turns into what it stands for. The
# advancement is re-armed at once, so every later token fires it again. One token is handled a pass; a stack of
# two runs this again until none is left.
advancement revoke @s only orespawn_integrations:extravio/copa
scoreboard objectives add cc_copa dummy
execute if items entity @s container.* minecraft:paper[minecraft:custom_data~{"orespawn_integrations:copa":{row:"arca"}}] run return run function orespawn_integrations:extravio/copa/arca
execute if items entity @s container.* minecraft:paper[minecraft:custom_data~{"orespawn_integrations:copa":{row:"chapeu"}}] run return run function orespawn_integrations:extravio/copa/chapeu
execute if items entity @s container.* minecraft:paper[minecraft:custom_data~{"orespawn_integrations:copa":{row:"gaveta"}}] run return run function orespawn_integrations:extravio/copa/gaveta
execute if items entity @s container.* minecraft:paper[minecraft:custom_data~{"orespawn_integrations:copa":{row:"retalho"}}] run return run function orespawn_integrations:extravio/copa/retalho
execute if items entity @s container.* minecraft:paper[minecraft:custom_data~{"orespawn_integrations:copa":{row:"presente"}}] run return run function orespawn_integrations:extravio/copa/presente
execute if items entity @s container.* minecraft:paper[minecraft:custom_data~{"orespawn_integrations:copa":{row:"parte"}}] run return run function orespawn_integrations:extravio/copa/parte
