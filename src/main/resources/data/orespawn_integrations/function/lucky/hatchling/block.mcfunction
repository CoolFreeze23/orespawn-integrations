# ly's block branch without the three blocks that burn a barn (lava, fire, soul fire), by the same dry run.
execute positioned ~ -25000000 ~ run function lucky_blocks:random/summon/block/random
execute if function orespawn_integrations:lucky/hatchling/block_banned run return run function orespawn_integrations:lucky/hatchling/block
function lucky_blocks:random/summon/block/place with storage lucky_blocks:storage
