# True for the block picks that set a yard alight.
execute if data storage lucky_blocks:storage {value:"lava"} run return 1
execute if data storage lucky_blocks:storage {value:"fire"} run return 1
execute if data storage lucky_blocks:storage {value:"soul_fire"} run return 1
return 0
