# The Eggshell's cap (item-hatchling-shell): the top of your own shell goes into the Wardrobe, unworn.
# Run by the open handler as the opener at permission 2, only when HatsRenewed is loaded.
hats give @s hat Egged
tellraw @s {"translate":"orespawn_integrations.hatchling_shell.cap","with":[{"keybind":"hats.key.hatsMenu"}]}
