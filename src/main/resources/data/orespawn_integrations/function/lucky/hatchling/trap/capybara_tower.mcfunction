# Three stacked capybaras where ly stacks five blazes: persistent, and tameable.
data modify storage orespawn_integrations:lucky summon set value {id:"brasilcoisas:capivara"}
function orespawn_integrations:lucky/hatchling/trap/capybara_summon with storage orespawn_integrations:lucky summon
title @p[distance=..10] actionbar {"translate":"orespawn_integrations.lucky.capybara_stack"}
