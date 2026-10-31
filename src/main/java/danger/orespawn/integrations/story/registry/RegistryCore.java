package danger.orespawn.integrations.story.registry;

import com.mojang.serialization.MapCodec;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The Registry's always-on half (no partner mod): the {@code registry_switch} data condition, and at server start the
 * scoreboard the desk functions use. Functions cannot read the config, so the switches are mirrored into the dummy
 * objective {@code cc_cfg} as fake players ({@code #registro}, {@code #correcoes}, {@code #retalhos}): 1 on, 0 off.
 * Every desk function tests {@code matches 1}, so a world whose scores were never written (OI absent) stays shut.
 */
public final class RegistryCore {

    /** Every objective the Registry's functions and the scrap caps read or write. */
    public static final String[] OBJECTIVES = {
            "cc_cfg", "cc_tmp", "cc_vias", "cc_via_dia", "cc_via_reg", "cc_guia_dia",
            ScrapCaps.Source.KITE.objective, ScrapCaps.Source.NOTEBOOK.objective,
    };

    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, OreSpawnIntegrations.MODID);

    static {
        CONDITIONS.register("registry_switch", () -> RegistrySwitchCondition.CODEC);
    }

    private RegistryCore() {
    }

    public static void init(IEventBus modBus) {
        CONDITIONS.register(modBus);
        NeoForge.EVENT_BUS.addListener(RegistryCore::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        try {
            writeSwitches(event.getServer());
        } catch (Throwable t) {
            StoryCore.logOnce("registro.scores", t);
        }
    }

    /** Creates the objectives if missing and writes the three switch scores. */
    public static void writeSwitches(MinecraftServer server) {
        Scoreboard board = server.getScoreboard();
        for (String name : OBJECTIVES) {
            objective(board, name);
        }
        Objective cfg = objective(board, "cc_cfg");
        set(board, cfg, "#registro", IntegrationsConfig.on(IntegrationsConfig.storyRegistry));
        set(board, cfg, "#correcoes", IntegrationsConfig.on(IntegrationsConfig.storyCorrections));
        set(board, cfg, "#retalhos", IntegrationsConfig.on(IntegrationsConfig.storyScraps));
    }

    static Objective objective(Scoreboard board, String name) {
        Objective objective = board.getObjective(name);
        if (objective == null) {
            objective = board.addObjective(name, ObjectiveCriteria.DUMMY, Component.literal(name),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
        }
        return objective;
    }

    private static void set(Scoreboard board, Objective objective, String holder, boolean on) {
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), objective).set(on ? 1 : 0);
    }
}
