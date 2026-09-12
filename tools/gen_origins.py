"""Generates the CrazyCraft origins (Neo Origins content) from the specs in this file.

Emits, under src/main/resources:
  data/orespawn_integrations/origins/<id>.json
  data/orespawn_integrations/powers/<id>_<power>.json
  data/orespawn_integrations/tags/item/origins/*.json
  data/orespawn_integrations/tags/worldgen/biome/origins/*.json
  data/neoorigins/origins/origin_layers/origin.json        (merge into the main picker layer)
  assets/orespawn_integrations/lang/en_us.json + pt_br.json (keys merged, others kept)

Run from the repo root: python tools/gen_origins.py
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
NS = "orespawn_integrations"
N = "neoorigins:"

EN = {}
PT = {}
ORIGIN_OBJECTS = []
POWERS = {}      # power id -> json
ORIGINS = []     # origin ids in picker order


# ----------------------------------------------------------------------------- helpers
def pid(origin, power):
    return f"{NS}:{origin}_{power}"


def t(**kw):
    """Small dict builder that drops None values."""
    return {k: v for k, v in kw.items() if v is not None}


# conditions
def c_night(): return {"type": N + "night"}
def c_day(): return {"type": N + "daytime"}
def c_sun(): return {"type": N + "exposed_to_sun"}
def c_water(): return {"type": N + "in_water"}
def c_rain(): return {"type": N + "in_rain"}
def c_sneak(): return {"type": N + "sneaking"}
def c_sprint(): return {"type": N + "sprinting"}
def c_ground(): return {"type": N + "on_ground"}
def c_sub(): return {"type": N + "submerged_in_water"}
def c_biome(tag): return {"type": N + "biome", "tag": tag}
def c_moon(cmp, val): return {"type": N + "moon_phase", "comparison": cmp, "compare_to": val}
def c_light(cmp, val): return {"type": N + "light_level", "comparison": cmp, "compare_to": val}
def c_ooc(ticks=100): return {"type": N + "out_of_combat", "ticks": ticks}
def c_not(c): return {"type": N + "not", "condition": c}
def c_and(*cs): return {"type": N + "and", "conditions": list(cs)}
def c_or(*cs): return {"type": N + "or", "conditions": list(cs)}
def c_power(power): return {"type": N + "power_active", "power": power}
def c_equipped(slot, item_condition): return {"type": N + "equipped_item", "equipment_slot": slot, "item_condition": item_condition}
def c_target_type(entity_type): return {"type": N + "target_type", "entity_type": entity_type}


# actions
def a_and(*acts): return {"type": N + "and", "actions": list(acts)}
def a_effect(effect, duration, amplifier=0, particles=True):
    return {"type": N + "apply_effect", "effect": effect, "duration": duration, "amplifier": amplifier, "show_particles": particles}
def a_effects(*entries):
    return {"type": N + "apply_effect", "effects": [{"effect": e, "duration": d, "amplifier": a} for e, d, a in entries]}
def a_heal(amount): return {"type": N + "heal", "amount": amount}
def a_dmg(amount, damage_type="minecraft:generic"): return {"type": N + "damage", "amount": amount, "damage_type": damage_type}
def a_explode(power, fire=False): return {"type": N + "explode", "power": power, "destruction_type": "none", "create_fire": fire}
def a_aoe(radius, action, entity_condition=None, include_source=False):
    return t(type=N + "area_of_effect", radius=radius, entity_action=action, entity_condition=entity_condition, include_source=include_source)
def a_cloud(radius, duration, interval, action, particle="minecraft:witch"):
    return {"type": N + "spawn_lingering_area", "radius": radius, "duration_ticks": duration, "interval_ticks": interval, "particle_type": particle, "entity_action": action}
def a_tornado(radius, duration, pull, lift, spin, dmg, move=0.2):
    return {"type": N + "spawn_tornado", "radius": radius, "duration_ticks": duration, "pull_strength": pull, "lift_strength": lift, "spin_strength": spin, "damage_per_interval": dmg, "damage_interval_ticks": 10, "move_speed": move}
def a_pull(radius, strength): return {"type": N + "pull_entities", "radius": radius, "strength": strength, "include_players": False}
def a_chain(radius, speed): return {"type": N + "chain_to_nearest", "radius": radius, "speed": speed}
def a_steal(slot="mainhand"): return {"type": N + "steal_item", "slot": slot}
def a_spell(spell, level=1): return {"type": N + "cast_iron_spell", "spell": "irons_spellbooks:" + spell, "level": level, "consume_mana": False, "trigger_cooldown": False, "mode": "instant"}
def a_rtp(h, v): return {"type": N + "random_teleport", "horizontal_range": h, "vertical_range": v}
def a_drop(slot="mainhand"): return {"type": N + "force_drop", "slot": slot}
def a_clear(effect=None): return t(type=N + "clear_effect", effect=effect)
def a_particles(particle, count=20, spread=0.8):
    return {"type": N + "spawn_particles", "particle": particle, "count": count, "speed": 0.05, "offset_y": 1.0, "spread": {"x": spread, "y": spread, "z": spread}}
def a_sound(sound, volume=1.0, pitch=1.0): return {"type": N + "play_sound", "sound": sound, "volume": volume, "pitch": pitch}
def a_fire(ticks): return {"type": N + "set_on_fire", "ticks": ticks}
def a_launch(speed): return {"type": N + "launch", "speed": speed}
def a_velocity(x, y, z, space="local_horizontal_normalized"): return {"type": N + "add_velocity", "x": x, "y": y, "z": z, "space": space}
def a_proj(entity, speed=1.5, count=1, inaccuracy=0.0): return t(type=N + "spawn_projectile", entity_type=entity, speed=speed, count=count, inaccuracy=inaccuracy)
def a_ignite_attacker(ticks): return {"type": N + "ignite_attacker", "ticks": ticks}
def a_effect_attacker(effect, duration, amplifier=0): return {"type": N + "effect_on_attacker", "effect": effect, "duration": duration, "amplifier": amplifier}
def a_delay(ticks, action): return {"type": N + "delay", "ticks": ticks, "action": action}
def a_feed(food, sat=0.0): return {"type": N + "feed", "food": food, "saturation": sat}


# powers
def attr(attribute, amount, op="add_value", condition=None):
    return t(type=N + "attribute_modifier", attribute="minecraft:" + attribute, amount=amount, operation=op, condition=condition)
def peff(effects, condition=None, icon=None):
    return t(type=N + "persistent_effect", toggleable=False, ambient=True, show_particles=False, show_icon=False,
             effects=[{"effect": e, "amplifier": a} for e, a in effects], condition=condition, cooldown_icon=icon)
def cpass(condition, action, interval=20, else_action=None):
    return t(type=N + "condition_passive", interval=interval, condition=condition, entity_action=action, else_action=else_action)
def active(action, cooldown, icon, resource=None, cost=0, hunger=0, condition=None):
    return t(type=N + "active_ability", cooldown_ticks=cooldown, cooldown_icon=icon, entity_action=action,
             resource_cost=resource, resource_cost_amount=cost if resource else None, hunger_cost=hunger or None, condition=condition)
def resource(maximum, regen, label, color, interval=20, start=None, regen_condition=None):
    return t(type=N + "resource", min=0, max=maximum, start_value=start if start is not None else maximum, regen_rate=regen,
             regen_interval=interval, regen_condition=regen_condition, hud_render={"label": label, "color": color})
def summon(mob, max_count, cooldown, despawn, icon, death_damage=0.5, quantity=1, resource_id=None, cost=0, hunger=0, attributes=None):
    return t(type=N + "summon_minion", mob_type=mob, max_count=max_count, quantity=quantity, cooldown_ticks=cooldown, despawn_ticks=despawn,
             death_damage=death_damage, cooldown_icon=icon, resource_cost=resource_id, resource_cost_amount=cost if resource_id else None,
             hunger_cost=hunger, attributes=attributes)
def ignore(types=None, passive=False): return t(type=N + "mobs_ignore_player", entity_types=types, passive=passive or None)
def hunted(types, rng=16.0, condition=None): return t(type=N + "mobs_target_player", entity_types=types, range=rng, power_condition=condition, power_condition_mode="ALLOW" if condition else None)
def scare(types, condition=None): return t(type=N + "scare_entities", entity_types=types, power_condition=condition, power_condition_mode="ALLOW" if condition else None)
def size(scale, reach=False, reach_bonus=0.0): return t(type=N + "size_scaling", scale=scale, modify_reach=reach, reach_bonus=reach_bonus or None)
def invuln(tags=None, types=None): return t(type=N + "invulnerability", damage_tags=tags, damage_types=types)
def mdmg(direction, mult, damage_type=None, condition=None):
    return t(type=N + "modify_damage", direction=direction, multiplier=mult, damage_type=damage_type, condition=condition)
def rarmor(slots, tag): return {"type": N + "restrict_armor", "restrictions": [{"slot": s, "tag": tag} for s in slots]}
def ritems(item_condition, prevent_use=True, prevent_equip=False):
    return {"type": N + "restrict_items", "item_condition": item_condition, "prevent_use": prevent_use, "prevent_equip": prevent_equip, "deny": True}
def foodr(tags, mode="whitelist"): return {"type": N + "food_restriction", "mode": mode, "item_tag": tags}
def nutr(nutrition, tag=None, item=None): return t(type=N + "modify_food_nutrition", nutrition=nutrition, food_tag=tag, food_item=item)
def hunger(mult): return {"type": N + "hunger_drain_modifier", "multiplier": mult}
def edible(nutrition, sat=0.6, items=None, tags=None, always=False): return t(type=N + "edible_item", items=items, tags=tags, nutrition=nutrition, saturation=sat, always_edible=always)
def brk(mult, tag=None): return t(type=N + "break_speed_modifier", multiplier=mult, block_tag=tag)
def immune(effects): return {"type": N + "effect_immunity", "effects": effects}
def dmg_water(dps, rain=True): return {"type": N + "damage_in_water", "damage_per_second": dps, "include_rain": rain}
def llight(max_light, effect, amp=0): return {"type": N + "light_level_effect", "max_light_level": max_light, "effect": effect, "amplifier": amp, "ambient": True, "show_particles": False, "show_icon": False}
def simple(kind, **kw): return {"type": N + kind, **kw}
def prevent(action, **kw): return {"type": N + "prevent_action", "action": action, **kw}
def on_hit(action, **kw): return {"type": N + "action_on_hit", "action": action, **kw}
def on_hit_taken(action, **kw):
    act = a_ignite_attacker(kw.get("duration", 60)) if action == "ignite_attacker" else a_effect_attacker(kw["effect"], kw.get("duration", 100), kw.get("amplifier", 0))
    return on_event("hit_taken", act)
def on_event(event, action, condition=None, cooldown=0): return t(type=N + "action_on_event", event=event, entity_action=action, condition=condition, cooldown_ticks=cooldown or None)
def aura(interval, action, condition=None): return t(type=N + "effect_over_time", activation="passive", interval=interval, entity_action=action, condition=condition)
def dmg_biome(tag, dps, dtype="freeze"): return cpass(c_biome(tag), a_dmg(dps, "minecraft:" + dtype))
def bbuff(tag, effect, amp=0): return cpass(c_biome(tag), a_effect(effect, 40, amp, particles=False))
def prevent_death(set_health, cooldown, action=None, condition=None):
    return t(type=N + "prevent_death", set_health=set_health, cooldown_ticks=cooldown, entity_action=action, condition=condition)
def starting(grant_id, item, count=1): return {"type": N + "starting_equipment", "grant_id": grant_id, "item": item, "count": count}
def morph(entity_type, scale=1.0): return {"type": N + "entity_model", "entity_type": entity_type, "scale": scale, "hitbox": False, "render_held_item": True, "render_armor": False, "first_person": "item"}
def gated(power_json, condition, mode="ALLOW"):
    """Whole-power gate (works on every power type)."""
    out = dict(power_json); out["power_condition"] = condition; out["power_condition_mode"] = mode; return out


# tags referenced below (created by write_tags)
ITEM_TAGS = {}
BIOME_TAGS = {}


def itag(name, entries):
    ITEM_TAGS[name] = entries
    return f"{NS}:origins/{name}"


def btag(name, entries):
    BIOME_TAGS[name] = entries
    return f"{NS}:origins/{name}"


def opt(id_):
    return {"id": id_, "required": False}


# ----------------------------------------------------------------------------- registration
class Origin:
    def __init__(self, oid, en_name, pt_name, en_desc, pt_desc, icon, impact, order, spawn_location=None):
        self.oid = oid; self.icon = icon; self.impact = impact; self.order = order; self.spawn_location = spawn_location
        self.powers = []; self.tiers = {1: ([], []), 2: ([], []), 3: ([], [])}
        EN[f"origins.{NS}.{oid}.name"] = en_name; PT[f"origins.{NS}.{oid}.name"] = pt_name
        ORIGIN_OBJECTS.append(self)
        EN[f"origins.{NS}.{oid}.description"] = en_desc; PT[f"origins.{NS}.{oid}.description"] = pt_desc
        ORIGINS.append(oid)

    def power(self, name, js, en, pt, tier=0, remove=None, hidden=False):
        """Adds a power. tier=0 -> base kit; tier 1..3 -> added at that evolution tier (remove = ids replaced)."""
        full = pid(self.oid, name)
        js = dict(js)
        js["name"] = f"power.{NS}.{self.oid}_{name}.name"; js["description"] = f"power.{NS}.{self.oid}_{name}.description"
        if hidden: js["hidden"] = True
        EN[js["name"]] = en[0]; PT[js["name"]] = pt[0]; EN[js["description"]] = en[1]; PT[js["description"]] = pt[1]
        assert full not in POWERS, full
        POWERS[full] = js
        if tier == 0:
            self.powers.append(full)
        else:
            self.tiers[tier][0].append(full)
            for r in (remove or []):
                self.tiers[tier][1].append(pid(self.oid, r))
        return full

    def emit(self):
        js = {"name": f"origins.{NS}.{self.oid}.name", "description": f"origins.{NS}.{self.oid}.description", "icon": self.icon,
              "impact": self.impact, "order": self.order, "powers": self.powers, "upgrades": [],
              "tier_powers": [{"tier": k, "add": v[0], "remove": v[1]} for k, v in sorted(self.tiers.items())]}
        if self.spawn_location: js["spawn_location"] = self.spawn_location
        return js


# ----------------------------------------------------------------------------- shared tags
HEAVY = itag("heavy_armor", ["#neoorigins:heavy_armor"] + [f"orespawn:{g}_{p}" for g in ("ruby", "emerald", "amethyst", "tigerseye", "queen", "royal", "ultimate", "peacock") for p in ("helmet", "chestplate", "leggings", "boots")]
             + ["orespawn:mobzilla_leggings", "orespawn:lavaeel_leggings", "orespawn:experience_leggings", "orespawn:lapis_leggings", "orespawn:pink_leggings"])
ALL_ARMOR = itag("all_armor", ["#minecraft:head_armor", "#minecraft:chest_armor", "#minecraft:leg_armor", "#minecraft:foot_armor", "#" + HEAVY, "#neoorigins:light_armor"])
SWEET = itag("sweet_foods", ["minecraft:sugar", "minecraft:honey_bottle", "minecraft:cookie", "minecraft:cake", "minecraft:pumpkin_pie", "minecraft:sweet_berries", "minecraft:glow_berries",
                             "orespawn:popcorn", "orespawn:buttered_popcorn", "orespawn:buttered_salted_popcorn", "orespawn:corn_cob", "orespawn:strawberry", opt("brasilcoisas:manga_com_leite")])
PLANT = itag("plant_foods", ["minecraft:apple", "minecraft:carrot", "minecraft:golden_carrot", "minecraft:potato", "minecraft:baked_potato", "minecraft:beetroot", "minecraft:melon_slice",
                             "minecraft:sweet_berries", "minecraft:glow_berries", "minecraft:dried_kelp", "minecraft:bread", "minecraft:pumpkin_pie", "minecraft:mushroom_stew", "minecraft:beetroot_soup",
                             "orespawn:corn_cob", "orespawn:strawberry", "orespawn:crystal_apple", opt("brasilcoisas:manga"), opt("brasilcoisas:manga_com_leite")])
FRUIT = itag("fruit_foods", ["minecraft:apple", "minecraft:sweet_berries", "minecraft:glow_berries", "minecraft:melon_slice", "minecraft:chorus_fruit", "minecraft:honey_bottle", "minecraft:brown_mushroom",
                             "minecraft:red_mushroom", "minecraft:mushroom_stew", "orespawn:strawberry", "orespawn:crystal_apple", opt("brasilcoisas:manga")])
SEA = itag("sea_foods", ["#minecraft:fishes", "minecraft:cooked_cod", "minecraft:cooked_salmon", "minecraft:dried_kelp", "minecraft:kelp", "minecraft:sea_pickle", "orespawn:raw_crab_meat", "orespawn:cooked_crab_meat", "orespawn:crabby_patty"])
CRAB = itag("crab_meat", ["orespawn:raw_crab_meat", "orespawn:cooked_crab_meat", "orespawn:crabby_patty"])
URANIUM = itag("uranium_foods", ["orespawn:ingot_uranium", "orespawn:uranium_nugget"])
RAW_MEAT = itag("raw_meats", ["minecraft:beef", "minecraft:porkchop", "minecraft:chicken", "minecraft:mutton", "minecraft:rabbit", "orespawn:raw_bacon", "orespawn:raw_peacock", "orespawn:raw_crab_meat"])
MEAT = itag("meat_foods", ["#neoorigins:meat_foods", "#" + RAW_MEAT, "orespawn:cooked_bacon", "orespawn:cooked_peacock", "orespawn:cooked_crab_meat", "orespawn:corn_dog", "orespawn:raw_corn_dog", opt("brazil_legends:cooked_tongue_slices"), opt("brazil_legends:tongue_slices")])
GIANT = itag("mapinguari_foods", ["#" + MEAT, "minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:mushroom_stew", "minecraft:honey_bottle", "minecraft:honeycomb"])
WOOD = itag("beaver_foods", ["#minecraft:logs", "#minecraft:saplings", "minecraft:stick", "minecraft:bamboo"])
MOTH_ARMOR = itag("moth_armor", ["orespawn:mothscale_helmet", "orespawn:mothscale_chestplate", "orespawn:mothscale_leggings", "orespawn:mothscale_boots", "minecraft:leather_helmet", "minecraft:leather_chestplate", "minecraft:leather_leggings", "minecraft:leather_boots"])
IRON_WEAPONS = itag("iron_weapons", ["minecraft:iron_sword", "minecraft:iron_axe", "minecraft:iron_pickaxe", "minecraft:iron_shovel", "minecraft:iron_hoe", "minecraft:mace", "minecraft:chainmail_chestplate"])
RANGED = itag("ranged_weapons", ["minecraft:bow", "minecraft:crossbow", "minecraft:trident", "orespawn:ultimate_bow", "orespawn:ray_gun", "orespawn:squid_zooka"])
COLD = btag("cold", ["minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:snowy_taiga", "minecraft:frozen_river", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean", "minecraft:snowy_beach",
                     "minecraft:snowy_slopes", "minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:grove", opt("#c:is_cold"), opt("#c:is_snowy")])
FOREST = btag("forest", ["#minecraft:is_forest", "#minecraft:is_jungle", "#minecraft:is_taiga", "minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:dark_forest", opt("#c:is_forest"), opt("#c:is_jungle"), opt("#c:is_swamp")])

ANTS = ["orespawn:ant", "orespawn:red_ant", "orespawn:rainbow_ant", "orespawn:unstable_ant", "orespawn:termite", "orespawn:ant_robot"]
ARTHROPODS = ["minecraft:spider", "minecraft:cave_spider", "orespawn:scorpion", "orespawn:emperor_scorpion"]
SEA_LIFE = ["minecraft:guardian", "minecraft:elder_guardian", "minecraft:drowned", "minecraft:squid", "minecraft:glow_squid", "orespawn:sea_viper", "orespawn:hammerhead", "orespawn:irukandji",
            "orespawn:attack_squid", "orespawn:kraken", "orespawn:sea_monster", "orespawn:skate", "orespawn:whale", "orespawn:urchin", "orespawn:sunspot_urchin", "orespawn:water_dragon"]
ROYALS = ["orespawn:the_king", "orespawn:the_queen", "orespawn:the_prince", "orespawn:the_prince_teen", "orespawn:the_prince_adult", "orespawn:the_princess", "orespawn:godzilla"]
COURT_ENEMIES = ["orespawn:kraken", "orespawn:mothra", "orespawn:basilisk", "orespawn:alien_boss", "orespawn:ender_reaper", "orespawn:ender_knight", "orespawn:leonopteryx", "orespawn:giant_robot",
                 "orespawn:water_dragon", "orespawn:cephadrome", "minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:pillager", "minecraft:vindicator"]
DINOS = ["orespawn:velocity_raptor", "orespawn:trex", "orespawn:alosaurus", "orespawn:baryonyx", "orespawn:cryolophosaurus", "orespawn:nastysaurus", "orespawn:pointysaurus", "orespawn:camarasaurus"]
DOGS = ["minecraft:wolf", "brasilcoisas:cachorro_caramelo", "orespawn:cliff_racer"]
FOREST_FRIENDS = ["minecraft:wolf", "minecraft:fox", "minecraft:ocelot", "minecraft:parrot", "minecraft:bee", "brasilcoisas:quero_quero", "brasilcoisas:urutau", "brasilcoisas:capivara", "orespawn:beaver", "orespawn:chipmunk", "orespawn:gazelle", "orespawn:butterfly"]
ANIMALS = ["minecraft:cow", "minecraft:sheep", "minecraft:pig", "minecraft:chicken", "minecraft:horse", "minecraft:donkey", "minecraft:rabbit", "minecraft:villager", "minecraft:wandering_trader", "orespawn:apple_cow", "orespawn:gold_cow", "orespawn:crystal_cow"]
CUCA_KIN = ["brazil_legends:cuca", "brazil_legends:cuclin", "brazil_legends:cuclin_ally", "brazil_legends:possessed_bowl", "brazil_legends:possessed_dish", "brazil_legends:possessed_spoon"]

FOREST_ONLY = c_biome(FOREST)
NOT_FOREST = c_not(FOREST_ONLY)
DRY_SUN = c_and(c_sun(), c_not(c_water()), c_not(c_rain()))
WET = c_or(c_water(), c_rain())

# ============================================================================= ORESPAWN

# ---- 1. Antling
o = Origin("antling", "Antling", "Formiga", "One of the ants that run OreSpawn's dimension gates: small, tireless, and never alone.",
           "Uma das formigas que guardam os portais dimensionais do OreSpawn: pequena, incansável e nunca sozinha.", "orespawn:ant_robot_kit", "high", 101)
o.power("swarm_call", summon("orespawn:red_ant", 3, 600, 1800, "orespawn:ant_robot_kit", death_damage=0.5, hunger=6),
        ("Swarm Call", "Calls up to 3 red ants to fight beside you for 90 seconds. Costs 6 hunger; 30-second cooldown."),
        ("Chamado do Formigueiro", "Chama até 3 formigas vermelhas para lutar ao seu lado por 90 segundos. Custa 6 de fome; 30 segundos de recarga."))
o.power("tunnel", simple("active_phase", max_depth=3, cooldown_ticks=160, cooldown_icon="minecraft:iron_shovel"),
        ("Tunnel", "Phase through up to 3 blocks of wall. 8-second cooldown."), ("Túnel", "Atravessa até 3 blocos de parede. 8 segundos de recarga."))
o.power("small", size(0.66, reach=False, reach_bonus=0.5), ("Small", "Two-thirds player size with full reach."), ("Pequenina", "Dois terços do tamanho de um jogador, com alcance normal."))
o.power("climb", simple("wall_climbing"), ("Six Legs", "Climbs any wall."), ("Seis Patas", "Escala qualquer parede."))
o.power("no_fall", prevent("FALL_DAMAGE"), ("Light Landing", "No fall damage."), ("Pouso Leve", "Não sofre dano de queda."))
o.power("quick", attr("movement_speed", 0.3, "add_multiplied_base"), ("Scurry", "30% faster."), ("Correria", "30% mais rápida."))
o.power("colony", ignore(ANTS), ("Colony", "OreSpawn ants, termites and ant robots never attack you."), ("Colônia", "Formigas, cupins e formigas-robô do OreSpawn nunca te atacam."))
o.power("sweet_tooth", nutr(8, tag=SWEET), ("Sweet Tooth", "Sugar, honey, cookies, popcorn, corn and berries restore double hunger."), ("Boca Doce", "Açúcar, mel, biscoitos, pipoca, milho e frutinhas restauram o dobro de fome."))
o.power("fragile", attr("max_health", -6.0), ("Fragile", "Seven hearts."), ("Frágil", "Sete corações."))
o.power("light_only", rarmor(["chest", "legs"], HEAVY), ("Thin Carapace", "Cannot wear heavy chestplates or leggings (iron and above, OreSpawn gem armour)."), ("Carapaça Fina", "Não usa peitorais nem calças pesados (ferro ou melhor, armaduras de gema do OreSpawn)."))
o.power("prey", hunted(ARTHROPODS, 24.0), ("Prey", "Spiders and scorpions hunt you from 24 blocks."), ("Presa", "Aranhas e escorpiões te caçam a 24 blocos."))
o.power("form", gated(morph("orespawn:ant", 1.0), c_power(pid("antling", "form_toggle"))), ("Ant Form", "Look like an ant while the form toggle is on (visual only)."), ("Forma de Formiga", "Aparência de formiga enquanto a forma estiver ligada (apenas visual)."))
o.power("form_toggle", simple("toggle", default=False), ("Toggle Ant Form", "Keybind: switch the ant look on or off."), ("Alternar Forma de Formiga", "Tecla: liga ou desliga a aparência de formiga."))
o.power("evolved_hp", attr("max_health", 4.0), ("Evolved Carapace", "+2 hearts."), ("Carapaça Evoluída", "+2 corações."), tier=1)
o.power("ascended_swarm", summon("orespawn:ant_robot", 3, 600, 1800, "orespawn:ant_robot_kit", death_damage=0.5, hunger=6),
        ("Ascended Swarm", "Swarm Call now summons ant robots."), ("Enxame Ascendido", "O Chamado do Formigueiro agora invoca formigas-robô."), tier=2, remove=["swarm_call"])
o.power("apex_swarm", summon("orespawn:ant_robot", 6, 400, 1800, "orespawn:ant_robot_kit", death_damage=0.5, hunger=6, quantity=2),
        ("Apex Swarm", "Six ant robots, two per call, 20-second cooldown."), ("Enxame Apex", "Seis formigas-robô, duas por chamado, 20 segundos de recarga."), tier=3, remove=["ascended_swarm"])

# ---- 2. Kraken Spawn
o = Origin("kraken_spawn", "Kraken Spawn", "Cria do Kraken", "Raised by the sea's monster. The ocean is home; the shore is a slow death.",
           "Criado pelo monstro do mar. O oceano é o lar; a praia é uma morte lenta.", "orespawn:kraken_tooth", "high", 102,
           spawn_location={"dimension": "minecraft:overworld", "biome_tag": "minecraft:is_ocean", "allow_ocean_floor": True})
TIDE = o.power("tide", resource(100, 3, "Tide", "#2266CC", regen_condition=c_water()), ("Tide", "Fills while you are in water."), ("Maré", "Enche enquanto você está na água."))
o.power("ink_cloud", active(a_cloud(4.0, 120, 20, a_effects(("minecraft:blindness", 120, 0), ("minecraft:slowness", 120, 1)), "minecraft:squid_ink"), 240, "minecraft:ink_sac"),
        ("Ink Cloud", "A 4-block cloud that blinds and slows everything inside for 6 seconds. 12-second cooldown."), ("Nuvem de Tinta", "Uma nuvem de 4 blocos que cega e retarda tudo dentro dela por 6 segundos. 12 segundos de recarga."))
o.power("undertow", active(a_and(a_pull(12.0, 0.9), a_delay(10, a_aoe(4.0, a_dmg(6.0), include_source=False))), 300, "orespawn:kraken_tooth", resource=TIDE, cost=30),
        ("Undertow", "Drags every mob within 12 blocks toward you, then a crushing wave for 6 damage. Costs 30 Tide; 15-second cooldown."),
        ("Ressaca", "Arrasta todos os mobs num raio de 12 blocos até você e depois uma onda esmagadora de 6 de dano. Custa 30 de Maré; 15 segundos de recarga."))
o.power("gills", simple("water_breathing"), ("Gills", "Breathes water."), ("Guelras", "Respira debaixo d'água."))
o.power("deep_sight", simple("enhanced_vision", exposure=0.6), ("Deep Sight", "Sees clearly in dark water."), ("Visão das Profundezas", "Enxerga bem na água escura."))
o.power("swift_swim", attr("movement_speed", 0.6, "add_multiplied_base", condition="in_water"), ("Swift Swim", "60% faster in water."), ("Nado Veloz", "60% mais rápido na água."))
o.power("crush", mdmg("out", 1.25, condition=c_sub()), ("Crushing Grip", "Hits 25% harder while submerged."), ("Aperto Esmagador", "Golpes 25% mais fortes quando submerso."))
o.power("sea_kin", ignore(SEA_LIFE), ("Sea Kin", "Guardians, drowned, squid and OreSpawn's sea monsters, the Kraken included, ignore you."), ("Parentes do Mar", "Guardiões, afogados, lulas e os monstros marinhos do OreSpawn, Kraken incluído, te ignoram."))
o.power("sea_diet", foodr([SEA]), ("Sea Diet", "Only fish, kelp and crab feed you."), ("Dieta do Mar", "Só peixe, alga e caranguejo te alimentam."))
o.power("crab_feast", nutr(12, tag=CRAB), ("Crab Feast", "Crab meat restores triple hunger."), ("Banquete de Caranguejo", "Carne de caranguejo restaura o triplo de fome."))
o.power("dries_out", simple("breath_out_of_fluid", fluid="water", drain_interval_ticks=3), ("Dries Out", "Loses air on land; about 45 seconds before it hurts."), ("Resseca", "Perde ar em terra; cerca de 45 segundos antes de doer."))
o.power("sunburn", cpass(DRY_SUN, a_dmg(1.0, "minecraft:dry_out")), ("Sunburn", "1 damage per second in direct sun while dry."), ("Queimadura de Sol", "1 de dano por segundo sob o sol direto enquanto estiver seco."))
o.power("fire_weak", mdmg("in", 2.0, damage_type="#minecraft:is_fire"), ("Fire Weakness", "Fire does double damage."), ("Fraqueza ao Fogo", "Fogo causa o dobro de dano."))
o.power("evolved_gills", simple("breath_out_of_fluid", fluid="water", drain_interval_ticks=6), ("Evolved Gills", "Air lasts twice as long on land."), ("Guelras Evoluídas", "O ar dura o dobro em terra."), tier=1, remove=["dries_out"])
o.power("ascended_undertow", active(a_and(a_pull(12.0, 0.9), a_delay(10, a_aoe(4.0, a_dmg(6.0))), a_aoe(4.0, a_heal(2.0), include_source=True)), 300, "orespawn:kraken_tooth", resource=TIDE, cost=30),
        ("Ascended Undertow", "Undertow also heals you for each mob it catches."), ("Ressaca Ascendida", "A Ressaca também te cura a cada mob que pega."), tier=2, remove=["undertow"])
o.power("apex_kraken", summon("orespawn:kraken", 1, 18000, 2400, "orespawn:kraken_tooth", death_damage=0.0, hunger=10),
        ("Apex: The Kraken Answers", "Once every 15 minutes the Kraken itself fights beside you for 2 minutes."), ("Apex: O Kraken Responde", "A cada 15 minutos o próprio Kraken luta ao seu lado por 2 minutos."), tier=3)

# ---- 3. Royal Blood
o = Origin("royal_blood", "Royal Blood", "Sangue Real", "A distant cousin of the King and Queen. The court knows your face; so does everyone who hates the court.",
           "Um primo distante do Rei e da Rainha. A corte conhece o seu rosto; e todo mundo que odeia a corte também.", "orespawn:queen_scale", "medium", 103)
o.power("decree", active(a_and(a_aoe(10.0, a_effects(("minecraft:weakness", 160, 1), ("minecraft:slowness", 160, 0)), include_source=False), a_effect("minecraft:strength", 160, 0)), 900, "orespawn:queen_scale"),
        ("Royal Decree", "Hostiles within 10 blocks get Weakness II and Slowness for 8 seconds while you get Strength. 45-second cooldown."),
        ("Decreto Real", "Hostis num raio de 10 blocos recebem Fraqueza II e Lentidão por 8 segundos enquanto você recebe Força. 45 segundos de recarga."))
o.power("court", ignore(ROYALS), ("The Court", "The King, the Queen, the Princes, the Princess and Mobzilla will not attack you."), ("A Corte", "O Rei, a Rainha, os Príncipes, a Princesa e Mobzilla não te atacam."))
o.power("vigor", attr("max_health", 4.0), ("Royal Vigor", "+2 hearts."), ("Vigor Real", "+2 corações."))
o.power("might", attr("attack_damage", 1.0), ("Royal Might", "+1 attack damage."), ("Força Real", "+1 de dano de ataque."))
o.power("poise", attr("knockback_resistance", 0.15), ("Poise", "15% knockback resistance."), ("Postura", "15% de resistência a empurrões."))
o.power("patronage", simple("trade_availability"), ("Patronage", "Villagers refresh their trades for you."), ("Patrocínio", "Aldeões renovam as trocas para você."))
o.power("heirloom", starting("royal_blood_heirloom", "orespawn:queen_scale", 1), ("Heirloom", "Starts with one Queen's Scale, proof of lineage."), ("Herança", "Começa com uma Escama da Rainha, prova da linhagem."))
o.power("appetite", hunger(1.4), ("Royal Appetite", "Hunger burns 40% faster."), ("Apetite Real", "A fome cai 40% mais rápido."))
o.power("marked", hunted(COURT_ENEMIES, 32.0), ("Marked", "Enemies of the court and common hostiles hunt you from 32 blocks."), ("Marcado", "Inimigos da corte e hostis comuns te caçam a 32 blocos."))
o.power("haughty", scare(["minecraft:villager", "minecraft:wandering_trader"], condition=c_sprint()), ("Haughty", "Villagers flee while you sprint."), ("Arrogante", "Aldeões fogem enquanto você corre."))
o.power("evolved_decree", active(a_and(a_aoe(10.0, a_effects(("minecraft:weakness", 160, 1), ("minecraft:slowness", 160, 0)), include_source=False), a_effect("minecraft:strength", 160, 0)), 600, "orespawn:queen_scale"),
        ("Evolved Decree", "Decree cooldown 30 seconds."), ("Decreto Evoluído", "Recarga do Decreto de 30 segundos."), tier=1, remove=["decree"])
o.power("ascended_guard", summon("orespawn:the_prince_teen", 1, 24000, 3600, "orespawn:queen_scale", death_damage=0.0, hunger=8),
        ("Ascended: The Prince's Guard", "The Prince answers as a bodyguard for 3 minutes, once every 20 minutes."), ("Ascendido: A Guarda do Príncipe", "O Príncipe atende como guarda-costas por 3 minutos, a cada 20 minutos."), tier=2)
o.power("apex_vigor", attr("max_health", 6.0), ("Apex Vigor", "+3 more hearts."), ("Vigor Apex", "+3 corações a mais."), tier=3)
o.power("apex_decree", active(a_and(a_aoe(10.0, a_effects(("minecraft:weakness", 160, 1), ("minecraft:slowness", 160, 0)), include_source=False), a_effects(("minecraft:strength", 160, 0), ("minecraft:resistance", 160, 0))), 600, "orespawn:queen_scale"),
        ("Apex Decree", "Decree also grants Resistance."), ("Decreto Apex", "O Decreto também concede Resistência."), tier=3, remove=["evolved_decree"])

# ---- 4. Uranium-Born
o = Origin("uranium_born", "Uranium-Born", "Filho do Urânio", "Born in the glow of an OreSpawn uranium vein. You run hot.",
           "Nascido no brilho de um veio de urânio do OreSpawn. Você funciona quente.", "orespawn:ingot_uranium", "medium", 104)
HEAT = o.power("heat", resource(100, 2, "Heat", "#66FF33"), ("Heat", "Builds up over time."), ("Calor", "Acumula com o tempo."))
o.power("meltdown", active(a_and(a_explode(4.0), a_fire(40), a_effect("minecraft:fire_resistance", 200, 0)), 800, "orespawn:ingot_uranium", resource=HEAT, cost=60),
        ("Meltdown", "A 4-power blast that breaks no blocks. Sets you alight for 2 seconds, then Fire Resistance for 10. Costs 60 Heat; 40-second cooldown."),
        ("Fusão do Núcleo", "Uma explosão de força 4 que não quebra blocos. Te incendeia por 2 segundos e depois dá Resistência ao Fogo por 10. Custa 60 de Calor; 40 segundos de recarga."))
o.power("glow", peff([("minecraft:glowing", 0)]), ("Afterglow", "You glow in the dark."), ("Brilho Residual", "Você brilha no escuro."))
o.power("own_light", llight(4, "minecraft:night_vision"), ("Own Light", "Night vision in the dark."), ("Luz Própria", "Visão noturna no escuro."))
o.power("hot_touch", on_hit_taken("effect_on_attacker", effect="minecraft:poison", duration=100, amplifier=1), ("Hot Touch", "Whoever hits you gets Poison II for 5 seconds."), ("Toque Quente", "Quem te acerta recebe Veneno II por 5 segundos."))
o.power("fuel", edible(8, 1.0, tags=[URANIUM], always=True), ("Fuel", "Eats uranium ingots and nuggets."), ("Combustível", "Come lingotes e pepitas de urânio."))
o.power("prospector", brk(1.5, "orespawn:ore_uranium"), ("Prospector", "Mines uranium ore 50% faster."), ("Garimpeiro", "Minera minério de urânio 50% mais rápido."))
o.power("hardened", immune(["minecraft:poison", "minecraft:wither"]), ("Hardened", "Immune to Poison and Wither."), ("Endurecido", "Imune a Veneno e Wither."))
o.power("unstable", mdmg("in", 2.0, damage_type="#minecraft:is_explosion"), ("Unstable", "Explosions do double damage to you."), ("Instável", "Explosões causam o dobro de dano em você."))
o.power("hiss", dmg_water(0.5, rain=False), ("Hiss", "0.5 damage per second in water."), ("Chiado", "0,5 de dano por segundo na água."))
o.power("radiant", scare(ANIMALS), ("Radiant", "Animals and villagers flee from you."), ("Radiante", "Animais e aldeões fogem de você."))
o.power("evolved_heat", resource(100, 3, "Heat", "#66FF33"), ("Evolved Reactor", "Heat regenerates faster."), ("Reator Evoluído", "O Calor regenera mais rápido."), tier=1, remove=["heat"])
o.power("ascended_meltdown", active(a_and(a_explode(4.0), a_fire(40), a_effect("minecraft:fire_resistance", 200, 0), a_cloud(4.0, 120, 20, a_effect("minecraft:poison", 60, 1), "minecraft:sneeze")), 800, "orespawn:ingot_uranium", resource=HEAT, cost=60),
        ("Ascended Meltdown", "Meltdown leaves a 6-second poison cloud."), ("Fusão Ascendida", "A Fusão deixa uma nuvem de veneno por 6 segundos."), tier=2, remove=["meltdown"])
o.power("apex_touch", on_hit_taken("effect_on_attacker", effect="minecraft:wither", duration=100, amplifier=0), ("Apex Touch", "Attackers get Wither instead of Poison."), ("Toque Apex", "Atacantes recebem Wither em vez de Veneno."), tier=3, remove=["hot_touch"])

# ---- 5. Luna Moth
o = Origin("luna_moth", "Luna Moth", "Mariposa-Luna", "A child of Mothra. Light is life; fire is the end of it.",
           "Uma filha de Mothra. A luz é vida; o fogo é o fim dela.", "orespawn:moth_scale", "medium", 105)
o.power("dust", active(a_cloud(3.0, 100, 20, a_effects(("minecraft:blindness", 100, 0), ("minecraft:levitation", 60, 0)), "minecraft:end_rod"), 200, "orespawn:moth_scale"),
        ("Moth Dust", "A 3-block cloud that blinds and lifts everything inside for 3 seconds. 10-second cooldown."), ("Pó de Mariposa", "Uma nuvem de 3 blocos que cega e levita tudo dentro dela por 3 segundos. 10 segundos de recarga."))
o.power("flutter", active(a_launch(1.2), 120, "minecraft:feather"), ("Flutter", "A strong upward hop that chains into a glide. 6-second cooldown."), ("Esvoaçar", "Um salto forte para cima que emenda num planeio. 6 segundos de recarga."))
o.power("glide", gated(simple("natural_glide"), c_not(c_rain())), ("Wings", "Glides, except in rain."), ("Asas", "Plana, exceto na chuva."))
o.power("no_fall", prevent("FALL_DAMAGE"), ("Soft Landing", "No fall damage."), ("Pouso Suave", "Não sofre dano de queda."))
o.power("night_eyes", simple("night_vision"), ("Night Eyes", "Sees in the dark."), ("Olhos Noturnos", "Enxerga no escuro."))
o.power("drawn_to_light", cpass(c_light(">=", 12), a_effects(("minecraft:speed", 40, 0), ("minecraft:regeneration", 40, 0)), else_action=None), ("Drawn to Light", "Speed and Regeneration near bright light."), ("Atraída pela Luz", "Velocidade e Regeneração perto de luz forte."))
o.power("dim", cpass(c_light("<=", 4), a_effect("minecraft:weakness", 40, 0, particles=False)), ("Dim", "Weakness in darkness."), ("Apagada", "Fraqueza na escuridão."))
o.power("nectar", nutr(8, tag=FRUIT), ("Nectar", "Fruit, berries, honey and mushrooms restore double."), ("Néctar", "Frutas, frutinhas, mel e cogumelos restauram o dobro."))
o.power("no_meat", nutr(1, tag=MEAT), ("Not a Hunter", "Meat barely feeds you."), ("Não Caçadora", "Carne mal te alimenta."))
o.power("fire_weak", mdmg("in", 3.0, damage_type="#minecraft:is_fire"), ("Moth to a Flame", "Fire and lava do triple damage."), ("Mariposa na Chama", "Fogo e lava causam o triplo de dano."))
o.power("light_armor", rarmor(["head", "chest", "legs", "feet"], HEAVY), ("Delicate", "Cannot wear heavy armour (moth scale and leather are fine)."), ("Delicada", "Não usa armadura pesada (escama de mariposa e couro são permitidos)."))
o.power("evolved_dust", active(a_cloud(3.0, 100, 20, a_effects(("minecraft:blindness", 100, 0), ("minecraft:levitation", 60, 0), ("minecraft:regeneration", 60, 0)), "minecraft:end_rod"), 200, "orespawn:moth_scale"),
        ("Evolved Dust", "Moth Dust also heals whoever stands in it (allies too)."), ("Pó Evoluído", "O Pó de Mariposa também cura quem estiver dentro dele (aliados também)."), tier=1, remove=["dust"])
o.power("ascended_flight", gated(simple("creative_flight"), c_and(c_light(">=", 12), c_not(c_rain()))), ("Ascended Wings", "True flight while in bright light."), ("Asas Ascendidas", "Voo livre enquanto estiver em luz forte."), tier=2)
o.power("apex_mothra", summon("orespawn:mothra", 1, 24000, 2400, "orespawn:moth_scale", death_damage=0.0, hunger=10), ("Apex: Mothra", "Once every 20 minutes Mothra fights beside you for 2 minutes."), ("Apex: Mothra", "A cada 20 minutos Mothra luta ao seu lado por 2 minutos."), tier=3)
o.power("apex_kin", ignore(["orespawn:mothra", "orespawn:luna_moth", "orespawn:butterfly", "orespawn:vampire_butterfly"]), ("Apex Kin", "Mothra and the moths ignore you."), ("Parentes Apex", "Mothra e as mariposas te ignoram."), tier=3)

# ---- 6. Kyuubi
o = Origin("kyuubi", "Kyuubi", "Kyuubi", "A fox spirit wearing a player's skin. Fire, mischief, and never being where the sword lands.",
           "Um espírito de raposa vestindo a pele de um jogador. Fogo, travessura e nunca estar onde a espada cai.", "orespawn:nightmare_scale", "high", 106)
FOX = o.power("foxfire_pool", resource(100, 4, "Fox-fire", "#FF6A00", regen_condition=c_and(c_ooc(100), c_not(WET))), ("Fox-fire", "Regenerates out of combat while dry."), ("Fogo-de-Raposa", "Regenera fora de combate enquanto estiver seco."))
o.power("foxfire", active(a_spell("firebolt", 3), 120, "minecraft:fire_charge", resource=FOX, cost=20, condition=c_not(WET)),
        ("Fox-fire", "Casts Iron's Spellbooks Firebolt at level 3, no book needed. Costs 20 Fox-fire; 6-second cooldown; not while wet."),
        ("Fogo-de-Raposa", "Lança o Raio de Fogo do Iron's Spellbooks no nível 3, sem livro. Custa 20 de Fogo-de-Raposa; 6 segundos de recarga; não funciona molhado."))
o.power("nine_steps", simple("active_teleport", range=24.0, cooldown_ticks=240, mode="target", cooldown_icon="minecraft:ender_pearl"), ("Nine Steps", "Teleport to where you look, 24 blocks. 12-second cooldown."), ("Nove Passos", "Teleporta para onde você olha, 24 blocos. 12 segundos de recarga."))
o.power("trade", simple("active_swap", range=10.0, cooldown_ticks=500, cooldown_icon="minecraft:orange_dye"), ("Trickster's Trade", "Swap places with the mob you look at. 25-second cooldown."), ("Troca do Trapaceiro", "Troca de lugar com o mob que você olha. 25 segundos de recarga."))
o.power("dodge", simple("dodge_chance", chance=0.25), ("Never There", "25% chance to dodge any hit."), ("Nunca Ali", "25% de chance de desviar de qualquer golpe."))
o.power("fire_immune", invuln(tags=["minecraft:is_fire"]), ("Fireborn", "Immune to fire and lava."), ("Nascido do Fogo", "Imune a fogo e lava."))
o.power("quick", attr("movement_speed", 0.2, "add_multiplied_base"), ("Fleet", "20% faster."), ("Ligeiro", "20% mais rápido."))
o.power("night_eyes", simple("night_vision"), ("Fox Eyes", "Sees in the dark."), ("Olhos de Raposa", "Enxerga no escuro."))
o.power("kin", ignore(["minecraft:cat", "minecraft:ocelot", "minecraft:fox", "orespawn:kyuubi"]), ("Kin", "Cats, foxes and the Kyuubi ignore you."), ("Parentes", "Gatos, raposas e o Kyuubi te ignoram."))
o.power("frail", attr("max_health", -8.0), ("Spirit Body", "Six hearts."), ("Corpo de Espírito", "Seis corações."))
o.power("hounded", hunted(DOGS, 20.0), ("Hounded", "Wolves, caramel mutts and cliff racers hunt you."), ("Perseguido por Cães", "Lobos, vira-latas caramelo e cliff racers te caçam."))
o.power("form", gated(morph("orespawn:kyuubi", 0.6), c_power(pid("kyuubi", "form_toggle"))), ("Fox Form", "Look like a Kyuubi while the form toggle is on (visual only)."), ("Forma de Raposa", "Aparência de Kyuubi enquanto a forma estiver ligada (apenas visual)."))
o.power("form_toggle", simple("toggle", default=False), ("Toggle Fox Form", "Keybind: switch the fox look on or off."), ("Alternar Forma de Raposa", "Tecla: liga ou desliga a aparência de raposa."))
o.power("evolved_dodge", simple("dodge_chance", chance=0.33), ("Evolved Reflexes", "Dodge chance 33%."), ("Reflexos Evoluídos", "Chance de desviar de 33%."), tier=1, remove=["dodge"])
o.power("ascended_foxfire", active(a_spell("fireball", 2), 120, "minecraft:fire_charge", resource=FOX, cost=25, condition=c_not(WET)), ("Ascended Fox-fire", "Fox-fire becomes Fireball."), ("Fogo-de-Raposa Ascendido", "O Fogo-de-Raposa vira Bola de Fogo."), tier=2, remove=["foxfire"])
o.power("apex_tails", summon("orespawn:kyuubi", 2, 6000, 1200, "orespawn:nightmare_scale", death_damage=0.0, hunger=8, quantity=2), ("Apex: Nine Tails", "Two Kyuubi fight beside you for a minute, every 5 minutes."), ("Apex: Nove Caudas", "Dois Kyuubi lutam ao seu lado por um minuto, a cada 5 minutos."), tier=3)

# ---- 7. Fairy
o = Origin("fairy", "Fairy", "Fada", "A fairy from Utopia, half a block tall, healing everything around it, and terrified of iron.",
           "Uma fada de Utopia, com meio bloco de altura, curando tudo ao redor e apavorada com ferro.", "orespawn:fairy_sword", "high", 107)
SPARK = o.power("sparkle", resource(100, 3, "Sparkle", "#FF99FF", regen_condition=c_sun()), ("Sparkle", "Regenerates in daylight."), ("Brilho", "Regenera à luz do dia."))
o.power("blessing", active(a_aoe(8.0, a_effects(("minecraft:regeneration", 200, 1), ("minecraft:absorption", 600, 0)), entity_condition=None, include_source=True), 400, "orespawn:fairy_sword", resource=SPARK, cost=40),
        ("Blessing", "Regeneration II and Absorption for everyone within 8 blocks (you included). Costs 40 Sparkle; 20-second cooldown."),
        ("Bênção", "Regeneração II e Absorção para todos num raio de 8 blocos (você inclusive). Custa 40 de Brilho; 20 segundos de recarga."))
o.power("flight", simple("creative_flight"), ("Fairy Flight", "True flight."), ("Voo de Fada", "Voo livre."))
o.power("tiny", size(0.5, reach=False, reach_bonus=1.0), ("Tiny", "Half size, normal reach."), ("Minúscula", "Metade do tamanho, alcance normal."))
o.power("green_thumb", simple("crop_growth_accelerator", radius=4, tick_interval=40, growths_per_interval=1), ("Green Thumb", "Crops grow while you stand near them."), ("Mão Boa", "Plantações crescem enquanto você está por perto."))
o.power("mist", aura(160, a_aoe(5.0, a_heal(2.0), entity_condition={"type": N + "entity_type", "entity_type": "minecraft:player"}, include_source=False)), ("Healing Mist", "Heals nearby players a heart every 8 seconds."), ("Névoa Curativa", "Cura jogadores próximos em um coração a cada 8 segundos."))
o.power("gentle", ignore(["orespawn:fairy", "minecraft:bee"]), ("Gentle", "Fairies and bees treat you as kin."), ("Gentil", "Fadas e abelhas te tratam como parente."))
o.power("frail", attr("max_health", -14.0), ("Glass Wings", "Three hearts."), ("Asas de Vidro", "Três corações."))
o.power("no_armor", rarmor(["head", "chest", "legs", "feet"], ALL_ARMOR), ("Unarmoured", "Cannot wear armour."), ("Sem Armadura", "Não usa armadura."))
o.power("cold_iron", mdmg("in", 1.5, condition={"type": N + "actor_condition", "condition": c_equipped("mainhand", {"tag": IRON_WEAPONS})}), ("Cold Iron", "Iron weapons do 50% more damage to you."), ("Ferro Frio", "Armas de ferro causam 50% mais dano em você."))
o.power("weak_hands", mdmg("out", 0.6), ("Weak Hands", "Melee does 40% less."), ("Mãos Fracas", "Corpo a corpo causa 40% menos dano."))
o.power("evolved_hp", attr("max_health", 4.0), ("Evolved Wings", "Five hearts."), ("Asas Evoluídas", "Cinco corações."), tier=1)
o.power("ascended_blessing", active(a_and(a_aoe(8.0, a_effects(("minecraft:regeneration", 200, 1), ("minecraft:absorption", 600, 0)), include_source=True), a_aoe(8.0, a_and(a_clear("minecraft:poison"), a_clear("minecraft:wither")), include_source=True)), 400, "orespawn:fairy_sword", resource=SPARK, cost=40),
        ("Ascended Blessing", "Blessing also cures poison and wither."), ("Bênção Ascendida", "A Bênção também cura veneno e wither."), tier=2, remove=["blessing"])
o.power("apex_second_dawn", prevent_death(2.0, 18000, action=a_and(a_particles("minecraft:end_rod", 60, 1.5), a_aoe(6.0, a_effect("minecraft:regeneration", 200, 1), include_source=True))),
        ("Apex: Second Dawn", "Once every 15 minutes a killing blow leaves you at one heart in a sparkle cloud that heals everyone near you."), ("Apex: Segunda Aurora", "A cada 15 minutos um golpe mortal te deixa com um coração numa nuvem de brilho que cura todos ao redor."), tier=3)

# ---- 8. Gamma Metroid
o = Origin("gamma_metroid", "Gamma Metroid", "Metroide Gama", "The energy parasite from the OreSpawn caves. Everything you touch feeds you; cold is the only thing that kills you.",
           "O parasita de energia das cavernas do OreSpawn. Tudo o que você toca te alimenta; o frio é a única coisa que te mata.", "orespawn:rock_crystal_green", "high", 108)
o.power("latch", active(a_and(a_chain(8.0, 1.2), a_aoe(3.0, a_effect("minecraft:wither", 80, 0), include_source=False), a_heal(4.0)), 300, "orespawn:rock_crystal_green"),
        ("Latch", "Pulls the nearest mob to you, withers it for 4 seconds and heals you two hearts. 15-second cooldown."), ("Grudar", "Puxa o mob mais próximo até você, aplica Wither por 4 segundos e te cura dois corações. 15 segundos de recarga."))
o.power("drain_hit", on_hit("restore_health", amount=1.5, min_damage=1.0), ("Feeding Bite", "Every melee hit heals you."), ("Mordida Faminta", "Cada golpe corpo a corpo te cura."))
o.power("float", simple("natural_glide"), ("Float", "Drifts down slowly."), ("Flutuar", "Desce flutuando devagar."))
o.power("spring", attr("jump_strength", 0.2), ("Low Gravity", "Jumps higher."), ("Baixa Gravidade", "Pula mais alto."))
o.power("dark_eyes", simple("night_vision"), ("Cave Eyes", "Sees in the dark."), ("Olhos de Caverna", "Enxerga no escuro."))
o.power("hardened", immune(["minecraft:poison", "minecraft:wither"]), ("Parasite", "Immune to Poison and Wither."), ("Parasita", "Imune a Veneno e Wither."))
o.power("aura", aura(40, a_and(a_aoe(3.0, a_dmg(0.5, "minecraft:magic"), include_source=False), a_heal(0.5))), ("Energy Drain", "Mobs within 3 blocks slowly lose health to you."), ("Dreno de Energia", "Mobs num raio de 3 blocos perdem vida lentamente para você."))
o.power("no_regen", simple("no_natural_regen"), ("No Regeneration", "You only heal by feeding."), ("Sem Regeneração", "Você só se cura se alimentando."))
o.power("cold_death", dmg_biome(COLD, 1.0, "freeze"), ("Cold Death", "1 damage per second in snowy or frozen biomes."), ("Morte Fria", "1 de dano por segundo em biomas nevados ou congelados."))
o.power("frost_weak", mdmg("in", 3.0, damage_type="#minecraft:is_freezing"), ("Frost Weakness", "Freezing damage is tripled."), ("Fraqueza ao Gelo", "Dano de congelamento triplicado."))
o.power("unsettling", scare(ANIMALS), ("Unsettling", "Villagers and animals flee from you."), ("Perturbador", "Aldeões e animais fogem de você."))
o.power("evolved_drain", on_hit("restore_health", amount=3.0, min_damage=1.0), ("Evolved Feeding", "Melee heals more."), ("Alimentação Evoluída", "Corpo a corpo cura mais."), tier=1, remove=["drain_hit"])
o.power("ascended_latch", active(a_and(a_chain(8.0, 1.2), a_aoe(3.0, a_effect("minecraft:wither", 80, 0), include_source=False), a_heal(4.0), a_effect("minecraft:resistance", 80, 0)), 300, "orespawn:rock_crystal_green"),
        ("Ascended Latch", "Latch also grants 4 seconds of Resistance."), ("Grudar Ascendido", "Grudar também concede 4 segundos de Resistência."), tier=2, remove=["latch"])
o.power("apex_aura", aura(40, a_and(a_aoe(5.0, a_dmg(0.5, "minecraft:magic"), include_source=False), a_heal(0.5))), ("Apex Drain", "Energy Drain reaches 5 blocks."), ("Dreno Apex", "O Dreno de Energia alcança 5 blocos."), tier=3, remove=["aura"])
o.power("apex_split", prevent_death(4.0, 24000, action=a_particles("minecraft:sculk_soul", 40, 1.0)), ("Apex: Split", "Once every 20 minutes you survive a killing blow at two hearts."), ("Apex: Divisão", "A cada 20 minutos você sobrevive a um golpe mortal com dois corações."), tier=3)

# ---- 9. Raptor
o = Origin("raptor", "Raptor", "Raptor", "A Velocity Raptor with a crafting table. Speed, pounce, pack.",
           "Um Velocity Raptor com uma mesa de trabalho. Velocidade, bote, alcateia.", "orespawn:trex_tooth", "high", 109)
o.power("pounce", simple("active_dash", power=2.2, cooldown_ticks=100, allow_vertical=False, set_velocity=True, damage=5.0, damage_radius=1.5, weapon_damage_scale=0.5, cooldown_icon="orespawn:trex_tooth"),
        ("Pounce", "A dash that deals 5 damage plus half your weapon damage to anything in its path. 5-second cooldown."), ("Bote", "Uma investida que causa 5 de dano mais metade do dano da sua arma em tudo no caminho. 5 segundos de recarga."))
o.power("howl", active(a_aoe(12.0, a_effects(("minecraft:strength", 300, 1), ("minecraft:speed", 300, 0)), entity_condition={"type": N + "living"}, include_source=True), 600, "minecraft:bone"),
        ("Pack Howl", "Strength II and Speed for you and every creature within 12 blocks for 15 seconds (point your pets first). 30-second cooldown."),
        ("Uivo da Alcateia", "Força II e Velocidade para você e toda criatura num raio de 12 blocos por 15 segundos (aponte seus pets primeiro). 30 segundos de recarga."))
o.power("command", simple("command_pack", range=32.0, cooldown_ticks=40, cooldown_icon="minecraft:lead"), ("Sic 'Em", "Your tamed animals attack the mob you look at."), ("Pega!", "Seus animais domesticados atacam o mob que você olha."))
o.power("quick", attr("movement_speed", 0.4, "add_multiplied_base"), ("Velocity", "40% faster."), ("Velocidade", "40% mais rápido."))
o.power("spring", attr("jump_strength", 0.25), ("Spring", "Jumps higher."), ("Impulso", "Pula mais alto."))
o.power("bleed", on_hit("target_effect", effect="minecraft:wither", duration=60, amplifier=0), ("Bleeding Bite", "Hits cause bleeding (Wither for 3 seconds)."), ("Mordida Sangrenta", "Golpes causam sangramento (Wither por 3 segundos)."))
o.power("raw", nutr(8, tag=RAW_MEAT), ("Raw Diet", "Raw meat restores full hunger."), ("Dieta Crua", "Carne crua restaura a fome inteira."))
o.power("meat_only", foodr([MEAT]), ("Carnivore", "Only meat feeds you."), ("Carnívoro", "Só carne te alimenta."))
o.power("kin", ignore(DINOS), ("Dinosaur Kin", "OreSpawn's dinosaurs ignore you."), ("Parente dos Dinossauros", "Os dinossauros do OreSpawn te ignoram."))
o.power("pack_bond", simple("tamed_animal_boost", health_bonus=6.0, speed_bonus=0.1, radius=32.0), ("Pack Bond", "Your tamed animals get +3 hearts and speed."), ("Laço de Alcateia", "Seus animais domesticados ganham +3 corações e velocidade."))
o.power("no_ranged", ritems({"tag": RANGED}, prevent_use=True, prevent_equip=False), ("Claws, Not Bows", "Cannot use bows, crossbows, tridents or OreSpawn guns."), ("Garras, Não Arcos", "Não usa arcos, bestas, tridentes nem armas do OreSpawn."))
o.power("cold_blood", bbuff(COLD, "minecraft:slowness", 1), ("Cold Blood", "Slowness II in cold biomes."), ("Sangue Frio", "Lentidão II em biomas frios."))
o.power("form", gated(morph("orespawn:velocity_raptor", 0.7), c_power(pid("raptor", "form_toggle"))), ("Raptor Form", "Look like a raptor while the form toggle is on (visual only)."), ("Forma de Raptor", "Aparência de raptor enquanto a forma estiver ligada (apenas visual)."))
o.power("form_toggle", simple("toggle", default=False), ("Toggle Raptor Form", "Keybind: switch the raptor look on or off."), ("Alternar Forma de Raptor", "Tecla: liga ou desliga a aparência de raptor."))
o.power("evolved_pounce", simple("active_dash", power=2.2, cooldown_ticks=60, allow_vertical=False, set_velocity=True, damage=5.0, damage_radius=1.5, weapon_damage_scale=0.5, cooldown_icon="orespawn:trex_tooth"),
        ("Evolved Pounce", "Pounce cooldown 3 seconds."), ("Bote Evoluído", "Recarga do Bote de 3 segundos."), tier=1, remove=["pounce"])
o.power("ascended_bleed", on_hit("target_effect", effect="minecraft:wither", duration=60, amplifier=1), ("Ascended Bite", "Bleeding becomes Wither II."), ("Mordida Ascendida", "O sangramento vira Wither II."), tier=2, remove=["bleed"])
o.power("apex_pack", summon("orespawn:velocity_raptor", 3, 12000, 2400, "orespawn:trex_tooth", death_damage=0.5, hunger=8, quantity=3), ("Apex: The Pack", "Three raptors hunt with you for 2 minutes, every 10 minutes."), ("Apex: A Alcateia", "Três raptores caçam com você por 2 minutos, a cada 10 minutos."), tier=3)

# ---- 10. Beaver
o = Origin("beaver", "Beaver", "Castor", "The cosy one. Trees fall, dams rise, nothing is in a hurry.",
           "O tranquilo. Árvores caem, represas sobem, nada tem pressa.", "minecraft:oak_log", "low", 110)
o.power("plank", simple("active_place_block", block_id="minecraft:oak_planks", max_distance=6.0, cooldown_ticks=30, cooldown_icon="minecraft:oak_planks"),
        ("Dam Plank", "Places an oak plank where you look, 6 blocks away, every 1.5 seconds. Bridges, dams, stairs."), ("Tábua da Represa", "Coloca uma tábua de carvalho onde você olha, a 6 blocos, a cada 1,5 segundo. Pontes, represas, escadas."))
o.power("timber", simple("tree_felling", max_blocks=64), ("Timber", "Breaking a log fells the whole tree."), ("Madeira!", "Quebrar um tronco derruba a árvore inteira."))
o.power("swim", attr("movement_speed", 0.5, "add_multiplied_base", condition="in_water"), ("Paddle", "50% faster in water."), ("Remada", "50% mais rápido na água."))
o.power("lungs", simple("breath_in_fluid", fluid="water", drain_interval_ticks=60), ("Big Lungs", "Holds breath three times longer."), ("Pulmões Grandes", "Segura a respiração três vezes mais."))
o.power("wet_work", simple("underwater_mining_speed"), ("Wet Work", "Mines underwater at full speed."), ("Trabalho Molhado", "Minera debaixo d'água em velocidade normal."))
o.power("wood_diet", edible(3, 0.6, tags=[WOOD]), ("Wood Diet", "Eats logs, sticks, saplings and bamboo."), ("Dieta de Madeira", "Come troncos, gravetos, mudas e bambu."))
o.power("sawmill", simple("craft_amount_bonus", item_id="minecraft:oak_planks", bonus_count=2), ("Sawmill", "Oak logs give 6 planks instead of 4."), ("Serraria", "Troncos de carvalho rendem 6 tábuas em vez de 4."))
o.power("teeth", simple("bare_hand_tool", tool="minecraft:stone_axe"), ("Teeth", "Bare hands chop like a stone axe."), ("Dentes", "Mãos nuas cortam como um machado de pedra."))
o.power("lodge", simple("extra_inventory", size=9, drop_on_death=False, title="Lodge"), ("Lodge", "An extra 9-slot pouch (keybind)."), ("Toca", "Uma bolsa extra de 9 espaços (tecla)."))
o.power("soft", mdmg("out", 0.7), ("Soft Paws", "Melee does 30% less."), ("Patas Macias", "Corpo a corpo causa 30% menos dano."))
o.power("waddle", gated(hunger(2.0), c_sprint()), ("Waddle", "Sprinting drains hunger twice as fast."), ("Gingado", "Correr gasta fome duas vezes mais rápido."))
o.power("no_shield", ritems({"tag": "c:tools/shield"}, prevent_use=True), ("No Shields", "Cannot raise a shield."), ("Sem Escudo", "Não consegue levantar um escudo."))
o.power("evolved_lodge", simple("extra_inventory", size=18, drop_on_death=False, title="Lodge"), ("Evolved Lodge", "The pouch grows to 18 slots."), ("Toca Evoluída", "A bolsa cresce para 18 espaços."), tier=1, remove=["lodge"])
o.power("ascended_hp", attr("max_health", 4.0), ("Ascended Bulk", "+2 hearts."), ("Volume Ascendido", "+2 corações."), tier=2)
o.power("apex_plank", simple("active_place_block", block_id="minecraft:oak_planks", max_distance=8.0, cooldown_ticks=10, cooldown_icon="minecraft:oak_planks"), ("Apex Plank", "Planks every half second, 8 blocks away."), ("Tábua Apex", "Tábuas a cada meio segundo, a 8 blocos."), tier=3, remove=["plank"])

# ============================================================================= BRAZIL

# ---- 11. Saci
o = Origin("saci", "Saci", "Saci-Pererê", "The one-legged trickster with the red cap. You are never where they look.",
           "O travesso de uma perna só e gorro vermelho. Você nunca está onde eles olham.", "brazil_legends:cachimbo", "high", 111)
TRAV = o.power("travessura", resource(100, 2, "Mischief", "#CC1111"), ("Mischief", "Builds up over time."), ("Travessura", "Acumula com o tempo."))
o.power("redemoinho", active(a_and(a_tornado(4.0, 100, 1.0, 0.8, 0.8, 1.0, 0.25), a_velocity(0.0, 0.6, 1.6, "local_horizontal_normalized")), 500, "brazil_legends:cachimbo", resource=TRAV, cost=40),
        ("Redemoinho", "Spins up a whirlwind that lifts and tumbles mobs for 5 seconds while you hop forward out of it. Costs 40 Mischief; 25-second cooldown."),
        ("Redemoinho", "Levanta um redemoinho que ergue e gira mobs por 5 segundos enquanto você pula para fora dele. Custa 40 de Travessura; 25 segundos de recarga."))
o.power("cachimbo", active(a_cloud(3.0, 100, 20, a_effects(("minecraft:blindness", 100, 0), ("minecraft:nausea", 100, 0)), "minecraft:campfire_cosy_smoke"), 200, "minecraft:campfire"),
        ("Pipe Smoke", "A smoke cloud that blinds and confuses for 5 seconds. 10-second cooldown."), ("Fumaça do Cachimbo", "Uma nuvem de fumaça que cega e confunde por 5 segundos. 10 segundos de recarga."))
o.power("furto", active(a_aoe(5.0, a_steal("mainhand"), include_source=False, entity_condition={"type": N + "living"}), 800, "minecraft:leather"),
        ("Furto", "Steals the held item of mobs within 5 blocks. 40-second cooldown."), ("Furto", "Rouba o item na mão dos mobs num raio de 5 blocos. 40 segundos de recarga."))
o.power("hop", attr("jump_strength", 0.35), ("One-Legged Hop", "Jumps two blocks high."), ("Pulo de Uma Perna", "Pula dois blocos de altura."))
o.power("no_fall", prevent("FALL_DAMAGE"), ("Bounce", "No fall damage."), ("Quique", "Não sofre dano de queda."))
o.power("dodge", simple("dodge_chance", chance=0.3), ("Never Where They Look", "30% chance to dodge any hit."), ("Nunca Onde Olham", "30% de chance de desviar de qualquer golpe."))
o.power("sneaky", simple("stealth", activation_ticks=40, cooldown_icon="brazil_legends:cachimbo"), ("Sneaky", "Invisible after 2 seconds of sneaking."), ("Sorrateiro", "Invisível após 2 segundos agachado."))
o.power("kin", ignore(["brazil_legends:saci_perere", "brasilcoisas:saci"]), ("Kin", "Both Sacis in the pack ignore you."), ("Parentes", "Os dois Sacis do pacote te ignoram."))
o.power("one_leg", attr("movement_speed", -0.2, "add_multiplied_base"), ("One Leg", "Walks 20% slower; hopping is the way to travel."), ("Uma Perna", "Anda 20% mais devagar; pular é o jeito de viajar."))
o.power("running_water", peff([("minecraft:slowness", 2)], condition=c_water()), ("Running Water", "Slowness III in water."), ("Água Corrente", "Lentidão III na água."))
o.power("frail", attr("max_health", -8.0), ("Small Frame", "Six hearts."), ("Corpo Pequeno", "Seis corações."))
o.power("form", gated(morph("brazil_legends:saci_perere", 1.0), c_power(pid("saci", "form_toggle"))), ("Saci Form", "Look like the Saci while the form toggle is on (visual only)."), ("Forma de Saci", "Aparência de Saci enquanto a forma estiver ligada (apenas visual)."))
o.power("form_toggle", simple("toggle", default=False), ("Toggle Saci Form", "Keybind: switch the Saci look on or off."), ("Alternar Forma de Saci", "Tecla: liga ou desliga a aparência de Saci."))
o.power("evolved_dodge", simple("dodge_chance", chance=0.4), ("Evolved Trickery", "Dodge chance 40%."), ("Travessura Evoluída", "Chance de desviar de 40%."), tier=1, remove=["dodge"])
o.power("ascended_redemoinho", active(a_and(a_tornado(4.0, 160, 1.0, 0.8, 0.8, 1.0, 0.25), a_velocity(0.0, 0.6, 1.6, "local_horizontal_normalized")), 500, "brazil_legends:cachimbo", resource=TRAV, cost=40),
        ("Ascended Redemoinho", "The whirlwind lasts 8 seconds."), ("Redemoinho Ascendido", "O redemoinho dura 8 segundos."), tier=2, remove=["redemoinho"])
o.power("apex_twin", summon("brazil_legends:saci_perere", 1, 6000, 1200, "brazil_legends:cachimbo", death_damage=0.0, hunger=6), ("Apex: Twin Saci", "A second Saci joins your mischief for a minute, every 5 minutes."), ("Apex: Saci Gêmeo", "Um segundo Saci entra na travessura por um minuto, a cada 5 minutos."), tier=3)

# ---- 12. Mapinguari
o = Origin("mapinguari", "Mapinguari", "Mapinguari", "The one-eyed giant sloth of the Amazon. Slow, enormous, unstoppable.",
           "A preguiça-gigante de um olho só da Amazônia. Lenta, enorme, imparável.", "brazil_legends:mapinguari_fang", "high", 112)
o.power("terremoto", simple("ground_slam", damage=9.0, knockback_strength=2.0, radius=6.0, cooldown_ticks=240, cooldown_icon="brazil_legends:mapinguari_fang"), ("Terremoto", "A ground slam for 9 damage in 6 blocks with heavy knockback. 12-second cooldown."), ("Terremoto", "Uma pancada no chão de 9 de dano num raio de 6 blocos com empurrão forte. 12 segundos de recarga."))
o.power("rugido", active(a_aoe(12.0, a_effects(("minecraft:weakness", 160, 0), ("minecraft:slowness", 60, 0)), include_source=False), 600, "brazil_legends:mapinguari_eye"), ("Roar", "Weakens and staggers every mob within 12 blocks. 30-second cooldown."), ("Rugido", "Enfraquece e cambaleia todo mob num raio de 12 blocos. 30 segundos de recarga."))
o.power("huge", size(1.3, reach=True), ("Huge", "30% larger, and reach to match."), ("Enorme", "30% maior, com alcance à altura."))
o.power("hp", attr("max_health", 10.0), ("Giant's Heart", "+5 hearts."), ("Coração de Gigante", "+5 corações."))
o.power("might", attr("attack_damage", 4.0), ("Giant's Arm", "+4 attack damage."), ("Braço de Gigante", "+4 de dano de ataque."))
o.power("unmovable", attr("knockback_resistance", 1.0), ("Unmovable", "Cannot be knocked back."), ("Inamovível", "Não pode ser empurrado."))
o.power("hide", peff([("minecraft:resistance", 0)]), ("Thick Hide", "Permanent Resistance I."), ("Couro Grosso", "Resistência I permanente."))
o.power("claws", simple("bare_hand_tool", tool="minecraft:diamond_axe"), ("Claws", "Bare hands work like a diamond axe."), ("Garras", "Mãos nuas funcionam como um machado de diamante."))
o.power("kin", ignore(["brazil_legends:mapinguari", "brazil_legends:mapinguary"]), ("Kin", "The Mapinguari ignore you."), ("Parentes", "Os Mapinguaris te ignoram."))
o.power("slow", attr("movement_speed", -0.25, "add_multiplied_base"), ("Lumbering", "25% slower."), ("Pesadão", "25% mais lento."))
o.power("fire_weak", mdmg("in", 2.0, damage_type="#minecraft:is_fire"), ("Fur", "Fire does double damage."), ("Pelagem", "Fogo causa o dobro de dano."))
o.power("appetite", hunger(1.6), ("Bottomless", "Hunger burns 60% faster."), ("Sem Fundo", "A fome cai 60% mais rápido."))
o.power("diet", foodr([GIANT]), ("Beast's Diet", "Only meat, mushrooms and honey feed you."), ("Dieta de Fera", "Só carne, cogumelos e mel te alimentam."))
o.power("no_ranged", ritems({"tag": RANGED}, prevent_use=True), ("Too Big for Bows", "Cannot use bows, crossbows, tridents or OreSpawn guns."), ("Grande Demais para Arcos", "Não usa arcos, bestas, tridentes nem armas do OreSpawn."))
o.power("hunted", hunted(["brazil_legends:capelobo", "brazil_legends:capelobo_stare"], 24.0), ("Old Enemy", "The Capelobo hunts you."), ("Velho Inimigo", "O Capelobo te caça."))
o.power("evolved_terremoto", simple("ground_slam", damage=9.0, knockback_strength=2.0, radius=6.0, cooldown_ticks=160, cooldown_icon="brazil_legends:mapinguari_fang"), ("Evolved Terremoto", "Terremoto cooldown 8 seconds."), ("Terremoto Evoluído", "Recarga do Terremoto de 8 segundos."), tier=1, remove=["terremoto"])
o.power("ascended_hide", peff([("minecraft:resistance", 1)]), ("Ascended Hide", "Resistance II."), ("Couro Ascendido", "Resistência II."), tier=2, remove=["hide"])
o.power("apex_rugido", active(a_aoe(12.0, a_effects(("minecraft:weakness", 160, 0), ("minecraft:slowness", 80, 4)), include_source=False), 600, "brazil_legends:mapinguari_eye"), ("Apex Roar", "The roar also roots enemies for 4 seconds."), ("Rugido Apex", "O rugido também prende inimigos por 4 segundos."), tier=3, remove=["rugido"])
o.power("apex_regen", cpass(c_ooc(100), a_heal(2.0), interval=60), ("Apex Vigour", "Regenerates a heart every 3 seconds out of combat."), ("Vigor Apex", "Regenera um coração a cada 3 segundos fora de combate."), tier=3)

# ---- 13. Capivara
o = Origin("capivara", "Capivara", "Capivara", "Everyone's friend. The pack's chill mode.",
           "Amiga de todo mundo. O modo tranquilo do pacote.", "brasilcoisas:manga", "low", 113)
o.power("banho", active(a_aoe(4.0, a_effect("minecraft:regeneration", 160, 1), include_source=True), 400, "minecraft:water_bucket", condition=c_water()),
        ("Bath", "Regeneration II for 8 seconds for you and everyone within 4 blocks, only while in water. 20-second cooldown."), ("Banho", "Regeneração II por 8 segundos para você e todos num raio de 4 blocos, só dentro d'água. 20 segundos de recarga."))
o.power("tranquil", ignore(None, passive=False), ("Tranquility", "Nothing attacks you unless you attack first."), ("Tranquilidade", "Nada te ataca a menos que você ataque primeiro."))
o.power("swim", attr("movement_speed", 0.6, "add_multiplied_base", condition="in_water"), ("River Legs", "60% faster in water."), ("Pernas de Rio", "60% mais rápida na água."))
o.power("lungs", simple("breath_in_fluid", fluid="water", drain_interval_ticks=80), ("Long Dive", "Holds breath four times longer."), ("Mergulho Longo", "Segura a respiração quatro vezes mais."))
o.power("grazer", nutr(8, tag=PLANT), ("Grazer", "Plants, fruit, mango, corn and strawberries restore double."), ("Herbívora", "Plantas, frutas, manga, milho e morangos restauram o dobro."))
o.power("gather", simple("attract_mobs", radius=8.0, speed=1.0), ("Everyone's Friend", "Animals drift toward you."), ("Amiga de Todos", "Animais se aproximam de você."))
o.power("sit", peff([("minecraft:slow_falling", 0)], condition=c_and(c_sneak(), c_not(c_ground()))), ("Sit", "Slow falling while sneaking in the air. Style."), ("Sentar", "Queda lenta agachada no ar. Estilo."))
o.power("soft", mdmg("out", 0.5), ("Soft", "Melee does half damage."), ("Mansa", "Corpo a corpo causa metade do dano."))
o.power("no_hurry", gated(hunger(3.0), c_sprint()), ("No Hurry", "Sprinting drains hunger triple; capybaras do not run."), ("Sem Pressa", "Correr gasta fome em triplo; capivara não corre."))
o.power("ears", prevent("ARMOR_EQUIP", head=True), ("Ears", "Cannot wear helmets."), ("Orelhas", "Não usa capacetes."))
o.power("form", gated(morph("brasilcoisas:capivara", 1.0), c_power(pid("capivara", "form_toggle"))), ("Capybara Form", "Look like a capybara while the form toggle is on (visual only)."), ("Forma de Capivara", "Aparência de capivara enquanto a forma estiver ligada (apenas visual)."))
o.power("form_toggle", simple("toggle", default=False), ("Toggle Capybara Form", "Keybind: switch the capybara look on or off."), ("Alternar Forma de Capivara", "Tecla: liga ou desliga a aparência de capivara."))
o.power("evolved_hp", attr("max_health", 4.0), ("Evolved Calm", "+2 hearts."), ("Calma Evoluída", "+2 corações."), tier=1)
o.power("ascended_banho", active(a_aoe(4.0, a_and(a_effect("minecraft:regeneration", 160, 1), a_clear("minecraft:poison")), include_source=True), 400, "minecraft:water_bucket", condition=c_water()), ("Ascended Bath", "The bath also cures poison."), ("Banho Ascendido", "O banho também cura veneno."), tier=2, remove=["banho"])
o.power("apex_entourage", summon("brasilcoisas:capivara", 2, 6000, 2400, "brasilcoisas:manga", death_damage=0.0, hunger=4, quantity=2), ("Apex: Entourage", "Two capybaras keep you company and soak up attention, every 5 minutes."), ("Apex: Comitiva", "Duas capivaras te fazem companhia e chamam a atenção, a cada 5 minutos."), tier=3)

# ---- 14. Lobisomem
o = Origin("lobisomem", "Lobisomem", "Lobisomem", "By day a person, by night the wolf. The moon decides how much wolf.",
           "De dia uma pessoa, de noite o lobo. A lua decide quanto lobo.", "minecraft:bone", "high", 114)
FULL = c_and(c_night(), c_moon("==", 0))
o.power("uivo", active(a_effect("minecraft:speed", 200, 1), 900, "minecraft:bone", condition=c_night()), ("Howl", "Speed II for 10 seconds, night only. 45-second cooldown."), ("Uivo", "Velocidade II por 10 segundos, só à noite. 45 segundos de recarga."))
o.power("pack", gated(summon("minecraft:wolf", 2, 900, 1800, "minecraft:bone", death_damage=0.5, hunger=4, quantity=2), c_night()), ("Call the Pack", "Two wolves answer for 90 seconds, night only."), ("Chamar a Alcateia", "Dois lobos atendem por 90 segundos, só à noite."))
o.power("salto", gated(simple("active_dash", power=2.0, cooldown_ticks=160, allow_vertical=False, set_velocity=True, damage=6.0, damage_radius=1.5, cooldown_icon="minecraft:bone"), c_night()), ("Lunge", "A pounce for 6 damage, night only. 8-second cooldown."), ("Salto", "Um bote de 6 de dano, só à noite. 8 segundos de recarga."))
o.power("day_form", attr("attack_damage", -1.0, condition=c_not(c_night())), ("Day Form", "By day: 1 less attack damage."), ("Forma Diurna", "De dia: 1 de dano de ataque a menos."))
o.power("night_attack", attr("attack_damage", 4.0, condition=c_night()), ("Night Fangs", "At night: +4 attack."), ("Presas Noturnas", "À noite: +4 de ataque."))
o.power("night_speed", attr("movement_speed", 0.3, "add_multiplied_base", condition=c_night()), ("Night Legs", "At night: 30% faster."), ("Pernas Noturnas", "À noite: 30% mais rápido."))
o.power("night_hp", attr("max_health", 4.0, condition=c_night()), ("Night Hide", "At night: +2 hearts."), ("Couro Noturno", "À noite: +2 corações."))
o.power("night_eyes", peff([("minecraft:night_vision", 0)], condition=c_night()), ("Night Eyes", "Night vision at night."), ("Olhos Noturnos", "Visão noturna à noite."))
o.power("night_jump", attr("jump_strength", 0.25, condition=c_night()), ("Night Spring", "At night: jumps higher."), ("Impulso Noturno", "À noite: pula mais alto."))
o.power("bleed", gated(on_hit("target_effect", effect="minecraft:wither", duration=60, amplifier=0), c_night()), ("Bleeding Bite", "At night hits cause bleeding."), ("Mordida Sangrenta", "À noite os golpes causam sangramento."))
o.power("full_moon", peff([("minecraft:strength", 1), ("minecraft:speed", 0)], condition=FULL), ("Full Moon", "Under a full moon: Strength II and Speed on top of everything."), ("Lua Cheia", "Sob a lua cheia: Força II e Velocidade além de tudo."))
o.power("full_form", gated(morph("brasilcoisas:lobisomem", 1.0), FULL), ("Full Moon Form", "Under a full moon you look like the Lobisomem (visual)."), ("Forma da Lua Cheia", "Sob a lua cheia você parece o Lobisomem (visual)."))
o.power("kin", ignore(["minecraft:wolf", "brasilcoisas:cachorro_caramelo", "brasilcoisas:lobisomem"]), ("Kin", "Wolves, caramel mutts and the Lobisomem ignore you."), ("Parentes", "Lobos, vira-latas caramelo e o Lobisomem te ignoram."))
o.power("no_sleep", prevent("SLEEP"), ("Restless", "Cannot sleep in a bed."), ("Inquieto", "Não consegue dormir numa cama."))
o.power("night_diet", gated(foodr([MEAT]), c_night()), ("Night Hunger", "At night only meat feeds you."), ("Fome Noturna", "À noite só carne te alimenta."))
o.power("hunted", hunted(["minecraft:iron_golem", "guardvillagers:guard"], 24.0, condition=c_night()), ("Hunted by Night", "Iron golems and village guards attack you on sight at night."), ("Caçado à Noite", "Golens de ferro e guardas da vila te atacam à noite."))
o.power("evolved_day", attr("attack_damage", 0.0, condition=c_not(c_night())), ("Evolved Day Form", "The day form loses its attack penalty."), ("Forma Diurna Evoluída", "A forma diurna perde a penalidade de ataque."), tier=1, remove=["day_form"])
o.power("ascended_pack", gated(summon("minecraft:wolf", 4, 900, 1800, "minecraft:bone", death_damage=0.5, hunger=4, quantity=4), c_night()), ("Ascended Pack", "Four wolves answer the call."), ("Alcateia Ascendida", "Quatro lobos atendem ao chamado."), tier=2, remove=["pack"])
o.power("apex_moon", peff([("minecraft:strength", 1), ("minecraft:speed", 0)], condition=c_night()), ("Apex: Every Night a Full Moon", "The full-moon form every night."), ("Apex: Toda Noite é Lua Cheia", "A forma da lua cheia toda noite."), tier=3, remove=["full_moon"])
o.power("apex_bleed", gated(on_hit("target_effect", effect="minecraft:wither", duration=60, amplifier=1), c_night()), ("Apex Bite", "Bleeding becomes Wither II."), ("Mordida Apex", "O sangramento vira Wither II."), tier=3, remove=["bleed"])

# ---- 15. Cuca
o = Origin("cuca", "Cuca", "Cuca", "The crocodile witch. Brews, minions, and a swamp that answers to you.",
           "A bruxa jacaré. Poções, lacaios e um pântano que te obedece.", "brazil_legends:magic_hat_helmet", "high", 115)
BRUX = o.power("bruxaria", resource(100, 3, "Witchcraft", "#55AA33"), ("Witchcraft", "Builds up over time."), ("Bruxaria", "Acumula com o tempo."))
o.power("feitico", active(a_and(a_proj("minecraft:potion", 1.4), a_aoe(3.0, a_effects(("minecraft:poison", 100, 1), ("minecraft:slowness", 100, 1)), include_source=False)), 120, "minecraft:splash_potion", resource=BRUX, cost=15),
        ("Hex", "Hurls a potion and poisons and slows every mob within 3 blocks of you. Costs 15 Witchcraft; 6-second cooldown."), ("Feitiço", "Arremessa uma poção e envenena e retarda todo mob num raio de 3 blocos de você. Custa 15 de Bruxaria; 6 segundos de recarga."))
o.power("louca", summon("brazil_legends:cuclin_ally", 3, 600, 2400, "minecraft:bowl", death_damage=0.5, hunger=4, quantity=3, resource_id=BRUX, cost=40),
        ("Possessed Tableware", "Summons three Cuclins for 2 minutes. Costs 40 Witchcraft; 30-second cooldown."), ("Louça Possuída", "Invoca três Cuclins por 2 minutos. Custa 40 de Bruxaria; 30 segundos de recarga."))
o.power("caldeirao", active(a_cloud(5.0, 300, 20, {"type": N + "if_else", "condition": {"type": N + "entity_type", "entity_type": "minecraft:player"}, "if_action": a_effect("minecraft:regeneration", 40, 0), "else_action": a_effect("minecraft:weakness", 60, 0)}, "minecraft:witch"), 1200, "minecraft:cauldron"),
        ("Cauldron", "A 5-block cloud for 15 seconds: players inside regenerate, everything else weakens. 60-second cooldown."), ("Caldeirão", "Uma nuvem de 5 blocos por 15 segundos: jogadores dentro regeneram, o resto enfraquece. 60 segundos de recarga."))
o.power("swim", attr("movement_speed", 0.5, "add_multiplied_base", condition="in_water"), ("Crocodile Legs", "50% faster in water."), ("Pernas de Jacaré", "50% mais rápida na água."))
o.power("gills", simple("water_breathing"), ("Crocodile Lungs", "Breathes water."), ("Pulmões de Jacaré", "Respira debaixo d'água."))
o.power("immune", immune(["minecraft:poison"]), ("Brewer", "Immune to poison."), ("Alquimista", "Imune a veneno."))
o.power("night_eyes", simple("night_vision"), ("Swamp Eyes", "Sees in the dark."), ("Olhos de Pântano", "Enxerga no escuro."))
o.power("kin", ignore(CUCA_KIN), ("Kin", "The Cuca, her Cuclins and the possessed tableware ignore you."), ("Parentes", "A Cuca, seus Cuclins e a louça possuída te ignoram."))
o.power("sun_weak", cpass(c_sun(), a_effects(("minecraft:weakness", 40, 0), ("minecraft:slowness", 40, 0))), ("Sun-shy", "Weakness and Slowness in direct sunlight."), ("Avessa ao Sol", "Fraqueza e Lentidão sob o sol direto."))
o.power("frail", attr("max_health", -6.0), ("Old Bones", "Seven hearts."), ("Ossos Velhos", "Sete corações."))
o.power("fire_weak", mdmg("in", 2.0, damage_type="#minecraft:is_fire"), ("Dry Scales", "Fire does double damage."), ("Escamas Secas", "Fogo causa o dobro de dano."))
o.power("evolved_feitico", active(a_and(a_proj("minecraft:potion", 1.4), a_aoe(3.0, a_effects(("minecraft:poison", 100, 1), ("minecraft:slowness", 100, 1), ("minecraft:blindness", 60, 0)), include_source=False)), 120, "minecraft:splash_potion", resource=BRUX, cost=15), ("Evolved Hex", "The hex also blinds."), ("Feitiço Evoluído", "O feitiço também cega."), tier=1, remove=["feitico"])
o.power("ascended_louca", summon("brazil_legends:cuclin_ally", 5, 600, 2400, "minecraft:bowl", death_damage=0.5, hunger=4, quantity=5, resource_id=BRUX, cost=40), ("Ascended Tableware", "Five Cuclins."), ("Louça Ascendida", "Cinco Cuclins."), tier=2, remove=["louca"])
o.power("apex_cuca", summon("brazil_legends:cuca", 1, 18000, 2400, "brazil_legends:magic_hat_helmet", death_damage=0.0, hunger=10), ("Apex: The Cuca Answers", "Every 15 minutes the Cuca herself fights beside you for 2 minutes."), ("Apex: A Cuca Responde", "A cada 15 minutos a própria Cuca luta ao seu lado por 2 minutos."), tier=3)

# ---- 16. Curupira
o = Origin("curupira", "Curupira", "Curupira", "The guardian of the forest with backward feet. Hunters get lost; the woods heal.",
           "O guardião da floresta com os pés virados para trás. Caçadores se perdem; a mata cura.", "minecraft:jungle_sapling", "medium", 116)
o.power("chamado", active(a_aoe(16.0, a_effect("minecraft:slowness", 120, 3), include_source=False), 800, "minecraft:jungle_sapling"),
        ("Call of the Woods", "Every creature within 16 blocks is rooted in place for 6 seconds. 40-second cooldown."), ("Chamado da Mata", "Toda criatura num raio de 16 blocos fica presa no lugar por 6 segundos. 40 segundos de recarga."))
o.power("raiz", active(a_spell("root", 2), 240, "minecraft:vine"), ("Root", "Casts Iron's Spellbooks Root at level 2. 12-second cooldown."), ("Raiz", "Lança a Raiz do Iron's Spellbooks no nível 2. 12 segundos de recarga."))
o.power("backward_feet", simple("dodge_chance", chance=0.35), ("Backward Feet", "35% chance any hit misses you."), ("Pés Virados", "35% de chance de qualquer golpe errar."))
o.power("forest_feet", gated(simple("dodge_chance", chance=0.15), FOREST_ONLY), ("Forest Feet", "Another 15% dodge inside forests."), ("Pés da Floresta", "Mais 15% de desvio dentro de florestas."))
o.power("forest_grace", peff([("minecraft:speed", 0), ("minecraft:regeneration", 0)], condition=FOREST_ONLY), ("Forest's Grace", "Speed and Regeneration in forests, jungles and swamps."), ("Graça da Floresta", "Velocidade e Regeneração em florestas, selvas e pântanos."))
o.power("green_thumb", gated(simple("crop_growth_accelerator", radius=6, tick_interval=20, growths_per_interval=2), FOREST_ONLY), ("Green Thumb", "Saplings and crops grow fast around you in the woods."), ("Mão Boa", "Mudas e plantações crescem rápido ao seu redor na mata."))
o.power("wild_friend", ignore(FOREST_FRIENDS), ("Wild Friend", "Wolves, foxes, ocelots, birds and the forest animals never attack you."), ("Amigo Selvagem", "Lobos, raposas, jaguatiricas, pássaros e os animais da mata nunca te atacam."))
o.power("attract", simple("attract_mobs", radius=6.0, speed=1.0), ("Gathering", "Animals come to you."), ("Reunião", "Animais vêm até você."))
o.power("forager", nutr(8, tag=FRUIT), ("Forager", "Fruit, berries and mushrooms restore double."), ("Coletor", "Frutas, frutinhas e cogumelos restauram o dobro."))
o.power("silent", simple("muffle_sound", strength=1.0), ("Silent Steps", "Sculk never hears you."), ("Passos Silenciosos", "O sculk nunca te ouve."))
o.power("out_of_woods", peff([("minecraft:slowness", 0)], condition=NOT_FOREST), ("Out of the Woods", "Slowness outside forests, jungles and swamps."), ("Fora da Mata", "Lentidão fora de florestas, selvas e pântanos."))
o.power("out_hunger", gated(hunger(1.5), NOT_FOREST), ("Homesick", "Hunger burns 50% faster outside the woods."), ("Saudade", "A fome cai 50% mais rápido fora da mata."))
o.power("no_felling", brk(0.25, "#minecraft:logs"), ("Not Yours to Fell", "Logs break four times slower."), ("Não é Sua para Derrubar", "Troncos quebram quatro vezes mais devagar."))
o.power("evolved_grace", peff([("minecraft:speed", 0), ("minecraft:regeneration", 1)], condition=FOREST_ONLY), ("Evolved Grace", "Regeneration II in the woods."), ("Graça Evoluída", "Regeneração II na mata."), tier=1, remove=["forest_grace"])
o.power("ascended_raiz", active(a_spell("root", 4), 240, "minecraft:vine"), ("Ascended Root", "Root at level 4."), ("Raiz Ascendida", "Raiz no nível 4."), tier=2, remove=["raiz"])
o.power("apex_rebirth", prevent_death(2.0, 24000, action=a_particles("minecraft:happy_villager", 40, 1.0), condition=FOREST_ONLY), ("Apex: Rebirth", "Once every 20 minutes the forest refuses to let you die."), ("Apex: Renascimento", "A cada 20 minutos a floresta se recusa a te deixar morrer."), tier=3)

# ---- 17. Mula sem Cabeça
o = Origin("mula", "Mula sem Cabeça", "Mula sem Cabeça", "The cursed woman turned fire mule, fastest thing on four hooves after dark.",
           "A mulher amaldiçoada virada mula de fogo, a coisa mais rápida em quatro cascos depois de escurecer.", "brazil_legends:flaming_leather", "medium", 117)
o.power("galope", active(a_effect("minecraft:speed", 160, 3), 300, "brazil_legends:flaming_leather", condition=c_night()), ("Gallop", "Speed IV for 8 seconds, night only. 15-second cooldown."), ("Galope", "Velocidade IV por 8 segundos, só à noite. 15 segundos de recarga."))
o.power("relincho", active(a_spell("fire_breath", 2), 600, "minecraft:blaze_powder"), ("Neigh of Fire", "Casts Iron's Spellbooks Fire Breath at level 2. 30-second cooldown."), ("Relincho de Fogo", "Lança o Sopro de Fogo do Iron's Spellbooks no nível 2. 30 segundos de recarga."))
o.power("fire_immune", invuln(tags=["minecraft:is_fire"]), ("Made of Fire", "Immune to fire and lava."), ("Feita de Fogo", "Imune a fogo e lava."))
o.power("hot_hide", on_hit_taken("ignite_attacker", duration=60), ("Burning Hide", "Whoever hits you catches fire."), ("Couro em Chamas", "Quem te acerta pega fogo."))
o.power("hooves", on_hit("target_effect", effect="minecraft:weakness", duration=40, amplifier=0), ("Hooves", "Hits stagger."), ("Cascos", "Golpes cambaleiam."))
o.power("night_speed", attr("movement_speed", 0.25, "add_multiplied_base", condition=c_night()), ("Night Gallop", "At night: 25% faster."), ("Galope Noturno", "À noite: 25% mais rápida."))
o.power("night_jump", attr("jump_strength", 0.25, condition=c_night()), ("Night Leap", "At night: jumps higher."), ("Salto Noturno", "À noite: pula mais alto."))
o.power("night_glow", peff([("minecraft:glowing", 0)], condition=c_night()), ("Fire Mane", "Glows at night."), ("Crina de Fogo", "Brilha à noite."))
o.power("fire_eyes", llight(4, "minecraft:night_vision"), ("Fire Eyes", "Night vision in the dark."), ("Olhos de Fogo", "Visão noturna no escuro."))
o.power("terror", scare(ANIMALS, condition=c_night()), ("Terror of the Night", "Animals and villagers flee at night."), ("Terror da Noite", "Animais e aldeões fogem à noite."))
o.power("kin", ignore(["brazil_legends:headless_mule"]), ("Kin", "The Headless Mule ignores you."), ("Parentes", "A Mula sem Cabeça te ignora."))
o.power("cursed", prevent("SLEEP"), ("Cursed", "Cannot sleep in a bed."), ("Amaldiçoada", "Não consegue dormir numa cama."))
o.power("quenched", gated(dmg_water(1.0, rain=True), c_night()), ("Quenched", "Water and rain hurt at night."), ("Apagada", "Água e chuva machucam à noite."))
o.power("night_hunger", gated(hunger(1.5), c_night()), ("Burning Hunger", "Hunger burns 50% faster at night."), ("Fome Ardente", "A fome cai 50% mais rápido à noite."))
o.power("day_slow", attr("movement_speed", -0.1, "add_multiplied_base", condition=c_not(c_night())), ("Daylight Weariness", "By day: 10% slower."), ("Cansaço Diurno", "De dia: 10% mais lenta."))
o.power("evolved_galope", active(a_effect("minecraft:speed", 240, 3), 300, "brazil_legends:flaming_leather", condition=c_night()), ("Evolved Gallop", "Gallop lasts 12 seconds."), ("Galope Evoluído", "O Galope dura 12 segundos."), tier=1, remove=["galope"])
o.power("ascended_day", attr("movement_speed", 0.0, "add_multiplied_base", condition=c_not(c_night())), ("Ascended Day", "No daylight penalty."), ("Dia Ascendido", "Sem penalidade diurna."), tier=2, remove=["day_slow"])
o.power("apex_flame", prevent_death(4.0, 24000, action=a_and(a_particles("minecraft:flame", 60, 1.5), a_effect("minecraft:fire_resistance", 200, 0)), condition=c_night()), ("Apex: Rise in Flame", "At night, once every 20 minutes, a killing blow leaves you at two hearts in a burst of flame."), ("Apex: Erguer-se em Chamas", "À noite, a cada 20 minutos, um golpe mortal te deixa com dois corações numa explosão de chamas."), tier=3)

# ============================================================================= OTHER

# ---- 18. Time Lord
o = Origin("time_lord", "Time Lord", "Senhor do Tempo", "Two hearts, a long life, and one more regeneration than everyone else.",
           "Dois corações, uma vida longa e uma regeneração a mais que todo mundo.", "vortexmod:tardis_key", "medium", 118)
o.power("regeneration", prevent_death(8.0, 18000, action=a_and(a_particles("minecraft:end_rod", 80, 1.5), a_sound("minecraft:block.beacon.power_select", 1.0, 1.4), a_effects(("minecraft:regeneration", 200, 2), ("minecraft:weakness", 1200, 0)), a_rtp(10.0, 4.0), a_drop("mainhand"))),
        ("Regeneration", "Once every 15 minutes a killing blow instead leaves you at four hearts in a golden burst. You are Weak for a minute, land a few blocks away and drop what you were holding."),
        ("Regeneração", "A cada 15 minutos um golpe mortal te deixa com quatro corações numa explosão dourada. Você fica Fraco por um minuto, cai alguns blocos adiante e derruba o que segurava."))
o.power("salto", simple("active_teleport", range=16.0, cooldown_ticks=400, mode="target", cooldown_icon="vortexmod:tardis_key"), ("Time Skip", "A 16-block blink. 20-second cooldown."), ("Salto Temporal", "Um teleporte de 16 blocos. 20 segundos de recarga."))
o.power("rebobinar", simple("active_recall", cooldown_ticks=1800, cooldown_icon="minecraft:clock"), ("Rewind", "Return to your bed or spawn. 90-second cooldown."), ("Rebobinar", "Volta para a sua cama ou spawn. 90 segundos de recarga."))
o.power("two_hearts", attr("max_health", 6.0), ("Two Hearts", "+3 hearts."), ("Dois Corações", "+3 corações."))
o.power("steady", immune(["minecraft:nausea", "minecraft:darkness", "minecraft:slowness"]), ("Steady Mind", "Immune to Nausea, Darkness and Slowness."), ("Mente Firme", "Imune a Náusea, Escuridão e Lentidão."))
o.power("long_potions", simple("longer_potions", duration_multiplier=1.5), ("Slow Metabolism", "Potions last 50% longer."), ("Metabolismo Lento", "Poções duram 50% mais."))
o.power("tinker", simple("efficient_repairs", cost_multiplier=0.5), ("Tinkerer", "Anvils cost half."), ("Inventor", "Bigornas custam metade."))
o.power("pacifist", attr("attack_damage", -2.0), ("Pacifist", "2 less attack damage."), ("Pacifista", "2 de dano de ataque a menos."))
o.power("evolved_regen", prevent_death(8.0, 12000, action=a_and(a_particles("minecraft:end_rod", 80, 1.5), a_sound("minecraft:block.beacon.power_select", 1.0, 1.4), a_effects(("minecraft:regeneration", 200, 2), ("minecraft:weakness", 1200, 0)), a_rtp(10.0, 4.0), a_drop("mainhand"))),
        ("Evolved Regeneration", "Regeneration every 10 minutes."), ("Regeneração Evoluída", "Regeneração a cada 10 minutos."), tier=1, remove=["regeneration"])
o.power("ascended_salto", simple("active_teleport", range=24.0, cooldown_ticks=400, mode="target", cooldown_icon="vortexmod:tardis_key"), ("Ascended Time Skip", "24 blocks."), ("Salto Temporal Ascendido", "24 blocos."), tier=2, remove=["salto"])
o.power("apex_regen", prevent_death(8.0, 12000, action=a_and(a_particles("minecraft:end_rod", 120, 2.0), a_sound("minecraft:block.beacon.power_select", 1.0, 1.4), a_effects(("minecraft:regeneration", 200, 2), ("minecraft:weakness", 1200, 0)), a_aoe(6.0, a_heal(20.0), entity_condition={"type": N + "entity_type", "entity_type": "minecraft:player"}, include_source=False), a_rtp(10.0, 4.0), a_drop("mainhand"))),
        ("Apex Regeneration", "The burst also fully heals players within 6 blocks."), ("Regeneração Apex", "A explosão também cura totalmente jogadores num raio de 6 blocos."), tier=3, remove=["evolved_regen"])


# ----------------------------------------------------------------------------- writers
def write_json(rel, obj):
    path = os.path.join(RES, rel); os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False); f.write("\n")


def merge_lang(rel, entries):
    path = os.path.join(RES, rel)
    cur = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
    cur = {k: v for k, v in cur.items() if not (k.startswith(f"origins.{NS}.") or k.startswith(f"power.{NS}."))}
    cur.update(entries)
    write_json(rel, cur)


def main():
    import shutil
    for d in ("origins", "powers"):
        shutil.rmtree(os.path.join(RES, "data", NS, d), ignore_errors=True)
    for oid_json in ORIGIN_OBJECTS:
        write_json(f"data/{NS}/origins/{oid_json.oid}.json", oid_json.emit())
    for full, js in POWERS.items():
        write_json(f"data/{NS}/powers/{full.split(':')[1]}.json", js)
    for name, entries in ITEM_TAGS.items():
        write_json(f"data/{NS}/tags/item/origins/{name}.json", {"replace": False, "values": entries})
    for name, entries in BIOME_TAGS.items():
        write_json(f"data/{NS}/tags/worldgen/biome/origins/{name}.json", {"replace": False, "values": entries})
    write_json("data/neoorigins/origins/origin_layers/origin.json", {"replace": False, "origins": [f"{NS}:{o}" for o in ORIGINS]})
    merge_lang(f"assets/{NS}/lang/en_us.json", EN)
    merge_lang(f"assets/{NS}/lang/pt_br.json", PT)
    # sanity: every power referenced by an origin exists
    for ob in ORIGIN_OBJECTS:
        for p in ob.powers + [x for t in ob.tiers.values() for x in t[0] + t[1]]:
            assert p in POWERS, f"{ob.oid} references missing power {p}"
    print(f"origins: {len(ORIGINS)} | powers: {len(POWERS)} | item tags: {len(ITEM_TAGS)} | biome tags: {len(BIOME_TAGS)} | lang keys: {len(EN)} en / {len(PT)} pt")


ORIGIN_OBJECTS.sort(key=lambda x: x.order)

if __name__ == "__main__":
    main()
