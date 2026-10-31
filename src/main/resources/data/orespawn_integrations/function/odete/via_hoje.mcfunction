# The copy handed out the same day (a copia_hoje clause said so): to the first player tagged cc_recebe_via
# (the compadre clause), else to the applicant. Then the tag is cleared.
execute if entity @a[tag=cc_recebe_via,limit=1] run give @a[tag=cc_recebe_via,limit=1] orespawn_integrations:guia_de_averbacao 1
execute unless entity @a[tag=cc_recebe_via,limit=1] run give @s orespawn_integrations:guia_de_averbacao 1
tag @a remove cc_recebe_via
tellraw @s {"translate":"orespawn_integrations.odete.via_hoje"}
