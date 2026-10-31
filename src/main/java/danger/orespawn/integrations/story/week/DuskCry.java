package danger.orespawn.integrations.story.week;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * The mãe-da-lua cries (event-dusk-cry, OI 0.10.1): one actionbar line per player per dusk, the first of the
 * Almanac's dusk beats, so it plays at the dusk check or at the player's first bed attempt that evening, whichever
 * comes first, and never within 30 s of a ticket handover. The 0.10.1 line is the ordinary one; its dream word is a
 * lang key ({@code dream.orespawn_integrations.<id>}) so each client reads it in its own language, and until the
 * King's dreams pick a species (0.12.0) it is ghosts, the spine's line 12. The first cry heard grants The Mãe-da-lua
 * Cried ({@code wonder/7}) with R6, once.
 *
 * <p>The urutau spawns at last: the biome modifier {@code add_spawns/urutau} puts her in the Old Ground and the
 * Greenwood, and this class replaces her missing spawn placement with one on the ground, under open sky, in the dusk
 * window only (without it she would spawn anywhere at any hour). Off with {@code [story] dusk_cry}.</p>
 */
public final class DuskCry {

    static final String KEY = "event.orespawn_integrations.dusk_cry.";
    static final String DREAM_KEY = "dream.orespawn_integrations.";
    /** Tonight's dream until the King's dreams ship (spine 3.1 line 12). */
    static final String DEFAULT_DREAM = "ghost";
    static final ResourceLocation URUTAU = ResourceLocation.fromNamespaceAndPath("brasilcoisas", "urutau");
    /** The urutau's spawn window: around the dusk check at 12000. */
    static final long WINDOW_START = 11800L;
    static final long WINDOW_END = 13800L;

    private DuskCry() {
    }

    static void init(IEventBus modBus) {
        Almanac.onDuskBeat("dusk_cry", DuskCry::cry);
        modBus.addListener(DuskCry::onSpawnPlacements);
    }

    static void cry(ServerPlayer player) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyDuskCry)) {
            return;
        }
        FirstHourUtil.actionbar(player, line(player));
        WeekState s = WeekData.of(player);
        if (!s.flag(WeekState.FLAG_FIRST_CRY)) {
            s.setFlag(WeekState.FLAG_FIRST_CRY);
            WeekData.touch(player);
            FirstHourUtil.award(player, "wonder/7");
            FirstHourUtil.award(player, "livro/r6");
        }
    }

    /** The ordinary dusk line with tonight's dream word. */
    static Component line(ServerPlayer player) {
        String dream = Almanac.today(player.getServer()).dream();
        if (dream == null || dream.isEmpty()) {
            dream = DEFAULT_DREAM;
        }
        return Component.translatable(KEY + "ordinary", Component.translatable(DREAM_KEY + dream));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(URUTAU)) {
            return;
        }
        EntityType type = BuiltInRegistries.ENTITY_TYPE.get(URUTAU);
        event.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                DuskCry::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        OreSpawnIntegrations.LOGGER.debug("[dusk cry] urutau spawn placement registered");
    }

    /** On the ground, under open sky, only in the dusk window; a spawner or an egg is never refused. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static boolean canSpawn(EntityType type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                            RandomSource random) {
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION) {
            return true;
        }
        if (!IntegrationsConfig.on(IntegrationsConfig.storyDuskCry)) {
            return false;
        }
        long time = WeekRules.timeOfDay(level.getLevel().getDayTime());
        return time >= WINDOW_START && time < WINDOW_END && level.canSeeSky(pos)
                && Mob.checkMobSpawnRules((EntityType<? extends Mob>) type, level, reason, pos, random);
    }
}
