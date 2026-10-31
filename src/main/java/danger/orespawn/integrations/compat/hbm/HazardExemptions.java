package danger.orespawn.integrations.compat.hbm;

import com.hbm.hazard.HazardSystem;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * The royal diet's exemptions (item-royal-diet): HBM calls eight vanilla things dangerous (green wool radioactive,
 * white wool asbestos, a gold ingot blinding, blaze powder hot, gunpowder, pumpkin pie and TNT explosive, coal a
 * black-lung dust). One call clears them: {@code HazardSystem.blacklist(Object)} (hbmsntm-198A, javap) keeps an
 * {@code ItemStack} or an item {@code TagKey} and ignores anything else, so an {@code Item} would compile and do
 * nothing. With the tag {@code #orespawn_integrations:hbm_hazard_exempt} blacklisted, {@code getHazardsFromStack}
 * returns nothing for those stacks: no effects and no hazard tooltip. HBM's own materials keep every hazard. The
 * coal-ore gas comes from a block-break handler, not an item, so the pack's {@code disableCoaldust} handles that.
 * Switch: {@code story.hbm_exemptions}.
 */
public final class HazardExemptions {

    public static final TagKey<Item> EXEMPT = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "hbm_hazard_exempt"));

    private HazardExemptions() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(HazardExemptions::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (!ModList.get().isLoaded("hbmsntm") || !IntegrationsConfig.on(IntegrationsConfig.storyHbmExemptions)) {
                return;
            }
            try {
                HazardSystem.blacklist(EXEMPT);
            } catch (Throwable t) {
                OreSpawnIntegrations.LOGGER.error("HBM hazard exemptions failed; HBM keeps its vanilla hazards", t);
            }
        });
    }

    /** True once the exemption tag is in HBM's blacklist (read by the GameTests). */
    public static boolean installed() {
        return HazardSystem.tagBlacklist.contains(EXEMPT);
    }
}
