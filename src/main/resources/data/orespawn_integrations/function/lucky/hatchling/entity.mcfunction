# ly's entity branch with the big summons (the dragon, the Wither, the Warden, the elder guardian, the giant) rerolled,
# by the same dry run as the item branch.
execute positioned ~ -25000000 ~ run function lucky_blocks:random/summon/entity/random
execute if function orespawn_integrations:lucky/hatchling/entity_banned run return run function orespawn_integrations:lucky/hatchling/entity_reroll
function lucky_blocks:random/summon/entity/summon with storage lucky_blocks:storage
