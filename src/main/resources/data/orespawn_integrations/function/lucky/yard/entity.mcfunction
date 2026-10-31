# The Knight's entity branch in Somebody's Yard: ly's big summons (the dragon, the Wither, the Warden, the elder
# guardian, the giant) become the Land's return, by the same dry run the Hatchling table uses; anything else comes.
execute positioned ~ -25000000 ~ run function lucky_blocks:random/summon/entity/random
execute if function orespawn_integrations:lucky/hatchling/entity_banned run return run function orespawn_integrations:lucky/yard
function lucky_blocks:random/summon/entity/summon with storage lucky_blocks:storage
