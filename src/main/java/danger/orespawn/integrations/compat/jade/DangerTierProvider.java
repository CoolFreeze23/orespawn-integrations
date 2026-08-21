package danger.orespawn.integrations.compat.jade;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Adds two lines to every {@code orespawn:*} entity: a danger tier
 * (Boss / Hostile / Passive) and, for iconic mobs, a signature-drop hint taken
 * from the entity loot tables shipped in the OreSpawn jar.
 *
 * <p>Registered on {@code LivingEntity.class}, so this runs for every crosshair
 * target: the namespace check happens before anything else and every Component
 * is built exactly once.
 */
enum DangerTierProvider implements IComponentProvider<EntityAccessor> {
    INSTANCE;

    /**
     * Cross-mod boss convention used by other pack mods (Cataclysm, Mowzie's).
     * OreSpawn beta.3 ships no such tag itself, so a static fallback set keeps
     * the "Boss" tier working even before the pack datapack adds the entries.
     */
    private static final TagKey<EntityType<?>> BOSSES_TAG =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "bosses"));

    private static final Set<String> FALLBACK_BOSSES = Set.of(
            "the_king", "the_queen", "the_prince", "the_prince_teen", "the_prince_adult",
            "godzilla", "kraken", "kyuubi", "mothra", "alien_boss");

    private static final Component TIER_BOSS = Component
            .translatableWithFallback("tooltip.orespawn_integrations.jade.tier.boss", "Boss")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    private static final Component TIER_HOSTILE = Component
            .translatableWithFallback("tooltip.orespawn_integrations.jade.tier.hostile", "Hostile")
            .withStyle(ChatFormatting.RED);
    private static final Component TIER_PASSIVE = Component
            .translatableWithFallback("tooltip.orespawn_integrations.jade.tier.passive", "Passive")
            .withStyle(ChatFormatting.GREEN);

    /**
     * entity path -> pre-built "Drops: ..." line. Contents verified against
     * {@code data/orespawn/loot_table/entities/<path>.json} in beta.3.
     */
    private static final Map<String, Component> DROP_HINTS = buildDropHints();

    private static boolean warned = false;

    @Override
    public ResourceLocation getUid() {
        return OreSpawnJadePlugin.UID_DANGER_TIER;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        try {
            Entity entity = accessor.getEntity();
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (!OreSpawnJadePlugin.ORESPAWN.equals(id.getNamespace())) {
                return;
            }
            tooltip.add(tierOf(entity, id));
            if (config.get(OreSpawnJadePlugin.CFG_DROP_HINTS)) {
                Component hint = DROP_HINTS.get(id.getPath());
                if (hint != null) {
                    tooltip.add(hint);
                }
            }
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                OreSpawnIntegrations.LOGGER.error(
                        "Jade compat: danger-tier tooltip failed; suppressing further errors", t);
            }
        }
    }

    private static Component tierOf(Entity entity, ResourceLocation id) {
        if (entity.getType().is(BOSSES_TAG) || FALLBACK_BOSSES.contains(id.getPath())) {
            return TIER_BOSS;
        }
        return entity instanceof Enemy ? TIER_HOSTILE : TIER_PASSIVE;
    }

    private static Map<String, Component> buildDropHints() {
        Map<String, String> raw = new HashMap<>();
        raw.put("the_king", "Royal Guardian Sword & Royal armor");
        raw.put("the_queen", "Queen Scale & Prince Spawn Egg");
        raw.put("godzilla", "Ultimate gear & Godzilla Scale");
        raw.put("kraken", "Kraken Tooth & Ultimate gear");
        raw.put("mothra", "Nether Star & Moth Scale");
        raw.put("alien_boss", "Netherite Ingot & Ender Pearls");
        raw.put("basilisk", "Basilisk Scale & Emerald gear");
        raw.put("emperor_scorpion", "Emperor Scorpion Scale & Ultimate weapons");
        raw.put("trex", "T-Rex Tooth & Uranium/Titanium Nuggets");
        raw.put("water_dragon", "Water Dragon Scale & Ultimate tools");
        raw.put("cephadrome", "Thunder Staff & Ruby gear");
        raw.put("pitch_black", "Nightmare Scale");
        raw.put("vortex", "Vortex Eye & crystal ingots");
        raw.put("hammerhead", "Experience Catcher & Creeper Launcher");
        raw.put("leonopteryx", "Battle Axe & Kraken Repellent");
        raw.put("sea_monster", "Sea Monster Scale");

        Map<String, Component> built = new HashMap<>(raw.size() * 2);
        raw.forEach((path, text) -> built.put(path, Component
                .translatableWithFallback("tooltip.orespawn_integrations.jade.drop_hint", "Drops: %s",
                        Component.translatableWithFallback(
                                "tooltip.orespawn_integrations.jade.drop." + path, text))
                .withStyle(ChatFormatting.GRAY)));
        return Map.copyOf(built);
    }
}
