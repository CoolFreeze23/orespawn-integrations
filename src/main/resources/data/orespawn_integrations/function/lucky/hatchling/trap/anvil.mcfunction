# One anvil, six blocks over the opener's feet. A summoned falling anvil hurts nobody, and it lands as a block they keep.
execute as @p[distance=..10] at @s run summon falling_block ~ ~6 ~ {BlockState:{Name:"minecraft:anvil"},Time:1}
title @p[distance=..10] actionbar {"translate":"orespawn_integrations.lucky.anvil"}
