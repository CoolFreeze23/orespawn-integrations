# /cc boteco upgrade (structure-boteco-do-seu-ze): brings a placed or older boteco level with every later piece, each
# shipped as its own small template with its offset from the origin. 0.10.1 is the first boteco, so there is nothing
# to add yet; each later slice adds its piece here once, guarded by the version on the hub-list entry, so a second run
# places nothing.
data modify storage orespawn_integrations:cc_result pieces set value 0
data modify storage orespawn_integrations:cc_result version set value "0.10.1"
