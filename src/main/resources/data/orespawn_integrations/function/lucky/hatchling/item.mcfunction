# ly's item branch with the boss spawn eggs rerolled. ly's pick is run far below the world, where its summon fails, so
# only the pick is kept (lucky_blocks:storage value and count); a banned pick rolls again, then ly's own summon runs
# here.
execute positioned ~ -25000000 ~ run function lucky_blocks:random/summon/item/random
execute if function orespawn_integrations:lucky/hatchling/item_banned run return run function orespawn_integrations:lucky/hatchling/item
function lucky_blocks:random/summon/item/summon with storage lucky_blocks:storage
