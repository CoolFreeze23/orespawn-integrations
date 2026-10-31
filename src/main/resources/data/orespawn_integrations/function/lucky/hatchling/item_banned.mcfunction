# True for ly's item picks a Hatchling never gets: the four boss spawn eggs.
execute if data storage lucky_blocks:storage {value:"ender_dragon_spawn_egg"} run return 1
execute if data storage lucky_blocks:storage {value:"wither_spawn_egg"} run return 1
execute if data storage lucky_blocks:storage {value:"warden_spawn_egg"} run return 1
execute if data storage lucky_blocks:storage {value:"elder_guardian_spawn_egg"} run return 1
return 0
