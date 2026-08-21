package danger.orespawn.integrations.compat.jade;

import java.util.Map;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Names the boss behind OreSpawn's summoning blocks and explains how they go
 * off. Behavior verified from {@code BossSpawnerBlock} bytecode: the block
 * schedules a fuse in {@code onPlace} and detonates into the boss shortly
 * after being placed (king_spawner / queen_spawner / dungeon_spawner), while
 * {@code godzilla_spawn_block} is the craft-from-parts Mobzilla egg whose boss
 * is once-per-world ({@code MobzillaSpawnTracker}).
 *
 * <p>Registered on both {@code BossSpawnerBlock} and {@code OreGenericEgg}
 * classes; the id filter below keeps it silent on the ~120 decorative
 * *_spawn_block eggs.
 */
enum BossSpawnerProvider implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    private static final Component FUSE_HINT = Component
            .translatableWithFallback("tooltip.orespawn_integrations.jade.spawner.fuse",
                    "Erupts moments after being placed — stand clear!")
            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
    private static final Component ONCE_HINT = Component
            .translatableWithFallback("tooltip.orespawn_integrations.jade.spawner.once_per_world",
                    "Only one may awaken per world")
            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);

    private record Lines(Component summons, Component hint) {}

    /** block path -> pre-built tooltip lines; boss names reuse OreSpawn's own lang keys. */
    private static final Map<String, Lines> SPAWNERS = Map.of(
            "king_spawner", new Lines(summons("the_king"), FUSE_HINT),
            "queen_spawner", new Lines(summons("the_queen"), FUSE_HINT),
            "dungeon_spawner", new Lines(summons("dungeon_beast"), FUSE_HINT),
            "godzilla_spawn_block", new Lines(summons("godzilla"), ONCE_HINT));

    private static boolean warned = false;

    private static Component summons(String entityPath) {
        return Component
                .translatableWithFallback("tooltip.orespawn_integrations.jade.spawner.summons",
                        "Summons: %s", Component.translatable("entity.orespawn." + entityPath))
                .withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    @Override
    public ResourceLocation getUid() {
        return OreSpawnJadePlugin.UID_BOSS_SPAWNER;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        try {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(accessor.getBlock());
            if (!OreSpawnJadePlugin.ORESPAWN.equals(id.getNamespace())) {
                return;
            }
            Lines lines = SPAWNERS.get(id.getPath());
            if (lines == null) {
                return;
            }
            tooltip.add(lines.summons());
            if (config.get(OreSpawnJadePlugin.CFG_BOSS_INFO)) {
                tooltip.add(lines.hint());
            }
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                OreSpawnIntegrations.LOGGER.error(
                        "Jade compat: boss-spawner tooltip failed; suppressing further errors", t);
            }
        }
    }
}
