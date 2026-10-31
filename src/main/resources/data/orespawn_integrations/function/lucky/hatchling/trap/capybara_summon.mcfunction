# A macro, so the Brasil e Coisas id is read when the trap runs, never when the pack loads.
$summon $(id) ~ ~ ~ {PersistenceRequired:1b,Passengers:[{id:"$(id)",PersistenceRequired:1b,Passengers:[{id:"$(id)",PersistenceRequired:1b}]}]}
