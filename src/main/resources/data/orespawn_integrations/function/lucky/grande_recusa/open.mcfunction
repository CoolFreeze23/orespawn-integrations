# Runs as the opener. The gift tag first, then the box. The tag cc_recusa tells the box listener that this box already
# showed its tag (the small 'politely' tag is for every other box).
$tellraw @a[distance=..32] {"translate":"orespawn_integrations.lucky.refusal_tag","with":[{"translate":"$(sender)"}],"color":"gold"}
tag @s add cc_recusa
$pandora player=@s effect=$(effect)
