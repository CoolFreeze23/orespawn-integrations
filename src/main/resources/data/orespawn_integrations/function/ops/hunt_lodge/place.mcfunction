# /cc hunt_lodge place (structure-hunt-lodge, the hub's checklist step 4): the real structure at the operator's feet
# through /place structure, which projects the start to the surface as a new world would. The template carries
# Captain Iracema's marker, so she arrives with it (npc-iracema); the /cc command writes the hub list entry, which
# the Registry's Lodge errand and the Wall's sign writer read before any ring lookup. A failed placement reports
# placed:0b back to /cc, which then says so.
execute store success storage orespawn_integrations:cc_result placed byte 1 run place structure orespawn_integrations:hunt_lodge ~ ~ ~
execute if data storage orespawn_integrations:cc_result {placed:1b} run tellraw @s {"translate":"orespawn_integrations.hunt_lodge.op.placed"}
execute if data storage orespawn_integrations:cc_result {placed:0b} run tellraw @s {"translate":"orespawn_integrations.hunt_lodge.op.refused"}
