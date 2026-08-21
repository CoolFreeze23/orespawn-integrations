package danger.orespawn.integrations;

import danger.orespawn.integrations.config.ConfigInit;
import danger.orespawn.integrations.items.ItemsInit;
import danger.orespawn.integrations.items.ModernItemsInit;
import danger.orespawn.integrations.items.RoyalEggInit;
import danger.orespawn.integrations.items.UraniumItemsInit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

@Mod(OreSpawnIntegrations.MODID)
public class OreSpawnIntegrations {
    public static final String MODID = "orespawn_integrations";
    public static final Logger LOGGER = LoggerFactory.getLogger("OreSpawnIntegrations");

    /**
     * Compat modules. Each entry: required modid -> compat class with a
     * {@code public static void init(IEventBus modBus)} entry point.
     * Classes are only loaded when their mod is present, so no compat
     * class may be referenced directly from here.
     */
    private static final String[][] COMPAT = {
            {"orespawn", "danger.orespawn.integrations.compat.alive.AliveWorldCompat"},
            {"walkers", "danger.orespawn.integrations.compat.walkers.WalkersCompat"},
            {"curios", "danger.orespawn.integrations.compat.curios.CuriosCompat"},
            {"waystones", "danger.orespawn.integrations.compat.waystones.WaystonesCompat"},
            {"patchouli", "danger.orespawn.integrations.guide.GuideCompat"},
            {"customnpcs", "danger.orespawn.integrations.npc.QuestCompat"},
            // Thread 4 "Big Game" (cross-mod threads, CROSSMOD-THREADS.md)
            {"securitycraft", "danger.orespawn.integrations.compat.securitycraft.DecoyOreMinesCompat"},
            {"travelersbackpack", "danger.orespawn.integrations.compat.travelersbackpack.OreSpawnPacksCompat"},
            {"artifacts", "danger.orespawn.integrations.compat.artifacts.ChitinBandCompat"},
            {"twilightforest", "danger.orespawn.integrations.compat.twilight.MobzillaPlatingCompat"},
            // Alive-world wave integrations (WAVE-IDEAS.md)
            {"sereneseasons", "danger.orespawn.integrations.compat.sereneseasons.SeasonsBridge"},
            {"guardvillagers", "danger.orespawn.integrations.compat.creatures.GuardVillagersCompat"},
            {"doggytalents", "danger.orespawn.integrations.compat.creatures.DoggyTalentsCompat"},
            {"incendium", "danger.orespawn.integrations.compat.incendium.InfernoCourtCompat"},
            {"orespawn", "danger.orespawn.integrations.compat.yung.YungFlavorCompat"},
            // Thread 1 "It Was Always Uranium"
            {"hbmsntm", "danger.orespawn.integrations.compat.hbm.GeigerProspectingCompat"},
            // Thread 3 "Her Side of the Story"
            {"orespawn", "danger.orespawn.integrations.compat.girlfriend.DateNightCompat"},
            {"ars_nouveau", "danger.orespawn.integrations.compat.arsnouveau.ArsFamiliarCompat"},
            // Thread 5 "The World Remembers"
            {"enchanted", "danger.orespawn.integrations.compat.enchanted.OceanRiteCompat"},
            {"statues", "danger.orespawn.integrations.compat.statues.MobzillaStatueCompat"},
            // JEI / Jade / JourneyMap plugins are discovered via their own
            // annotation scans and need no wiring here.
    };

    public OreSpawnIntegrations(IEventBus modBus, ModContainer container) {
        LOGGER.info("OreSpawn Integrations loaded - Tier 1 (data) active.");
        ConfigInit.init(modBus, container);
        ItemsInit.init(modBus);
        ModernItemsInit.init(modBus);
        UraniumItemsInit.init(modBus);
        RoyalEggInit.init(modBus);
        for (String[] entry : COMPAT) {
            if (!ModList.get().isLoaded(entry[0])) {
                LOGGER.debug("Compat for '{}' skipped (mod absent)", entry[0]);
                continue;
            }
            try {
                Class<?> cls = Class.forName(entry[1]);
                Method init = cls.getMethod("init", IEventBus.class);
                init.invoke(null, modBus);
                LOGGER.info("Tier 2 compat active: {}", entry[0]);
            } catch (Throwable t) {
                LOGGER.error("Compat init failed for {} - continuing without it", entry[0], t);
            }
        }
    }
}
