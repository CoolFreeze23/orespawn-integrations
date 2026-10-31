# Macro, after odete/stamp: gives $(item) with the countersign lore line for carimbo.dia.
# Use: data modify storage orespawn_integrations:registro carimbo.item set value "minecraft:paper"
#      function orespawn_integrations:odete/carimbar with storage orespawn_integrations:registro carimbo
$give @s $(item)[minecraft:lore=['{"translate":"orespawn_integrations.odete.visto","with":["$(dia)"],"italic":false}']] 1
