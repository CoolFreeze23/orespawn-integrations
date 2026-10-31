package danger.orespawn.integrations.world.regions;

import com.mojang.serialization.Codec;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryClock;
import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Region names on crossing (system-region-names). Once a second, on the shared {@link StoryClock}, each player's
 * place is read: in the overworld the biome at their feet against the fourteen region tags in {@link RegionTable}'s
 * order (at most 14 tag checks), in Chaos their Y against the Nursery's two cutoffs, elsewhere the dimension. A new
 * place shows its title after 10 s there (the hold, so a border never flickers); a dimension change shows at once.
 * The first visit to each overworld region pays one Ledger Scrap, recorded in the 14-bit mask of the
 * {@code region_state} attachment; the spawn region pays at the first hold after join, even with the origin screen
 * open, while the title itself waits for that screen to close.
 *
 * <p>{@code world.region_names = false} stops the titles and the scraps; the timer and the state keep running.
 */
public final class RegionNames {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OreSpawnIntegrations.MODID);

    /** The 4-byte record, see {@link RegionState}. Copied on death: a respawn keeps the mask. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> REGION_STATE =
            ATTACHMENTS.register("region_state", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT).copyOnDeath().build());

    private static final ResourceLocation SCRAP =
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "ledger_scrap");
    private static final String KEY = "orespawn_integrations.";
    private static final int TITLE_GAP_TICKS = 200;
    private static final double LEDGE_RADIUS_SQ = 24 * 24;

    private static final Map<String, TagKey<Biome>> TAGS = new HashMap<>();

    /** Transient per-player state: the candidate of a hold in progress, the last title tick, a title waiting. */
    private static final class Transient {
        int candidate;
        long lastTitleTick = -TITLE_GAP_TICKS;
        int pendingTitle;
        double ledgeX = Double.NaN;
        double ledgeZ;
    }

    private static final Map<UUID, Transient> TRANSIENT = new HashMap<>();

    private RegionNames() {
    }

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        for (RegionTable.Region region : RegionTable.ORDER) {
            TAGS.put(region.slug(), TagKey.create(Registries.BIOME,
                    ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "region/" + region.slug())));
        }
        StoryClock.everySecondPerPlayer("region_names", RegionNames::tick);
        NeoForge.EVENT_BUS.addListener(RegionNames::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(RegionNames::onLoggedOut);
    }

    /**
     * Called by the Antechamber just before a ledge landing: the Oubliette's title shows tier 1's line while the
     * player stands within 24 blocks of the ledge's centre.
     */
    public static void markLedgeArrival(ServerPlayer player, double centreX, double centreZ) {
        Transient t = state(player);
        t.ledgeX = centreX;
        t.ledgeZ = centreZ;
    }

    /** The overworld region (1-14) of a biome. */
    public static int regionOf(Holder<Biome> biome) {
        return RegionTable.regionOf(slug -> biome.is(TAGS.get(slug)));
    }

    /** Where the player stands, as an index of {@link RegionTable}; 0 for a dimension with no title. */
    public static int placeOf(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        String dim = level.dimension().location().toString();
        if (RegionTable.OVERWORLD.equals(dim)) {
            return regionOf(level.getBiome(player.blockPosition()));
        }
        if (RegionTable.CHAOS.equals(dim)) {
            return RegionTable.depthOf(player.getBlockY());
        }
        RegionTable.Dimension d = dimension(level);
        return d == null ? 0 : d.index();
    }

    private static RegionTable.Dimension dimension(ServerLevel level) {
        RegionTable.Dimension d = RegionTable.DIMENSIONS.get(level.dimension().location().toString());
        if (d == null) {
            ResourceLocation type = level.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE)
                    .getKey(level.dimensionType());
            if (type != null) {
                d = RegionTable.DIMENSIONS.get(type.toString());
            }
        }
        return d;
    }

    public static int getState(ServerPlayer player) {
        return player.getData(REGION_STATE.get());
    }

    public static void setState(ServerPlayer player, int state) {
        player.setData(REGION_STATE.get(), state);
    }

    /**
     * One 20-tick step for {@code player} standing at {@code target}; returns true when a crossing completed. Public
     * for the GameTests, which drive the hold without waiting on the clock.
     */
    public static boolean step(ServerPlayer player, int target) {
        Transient t = state(player);
        RegionState.Step step = RegionState.step(getState(player), t.candidate, target);
        t.candidate = step.candidate();
        setState(player, step.state());
        if (step.crossed()) {
            arrive(player, target);
        }
        return step.crossed();
    }

    private static void tick(ServerPlayer player) {
        step(player, placeOf(player));
        flushTitle(player);
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        try {
            int target = placeOf(player);
            Transient t = state(player);
            t.candidate = 0;
            if (RegionTable.CHAOS.equals(event.getFrom().location().toString())) {
                t.ledgeX = Double.NaN;
            }
            setState(player, RegionState.jump(getState(player), target));
            if (RegionTable.CHAOS.equals(event.getTo().location().toString())) {
                // Chaos shows the dimension's title; the depth is taken silently, and its line shows on a change.
                requestTitle(player, RegionTable.DIMENSIONS.get(RegionTable.CHAOS).index());
            } else {
                arrive(player, target);
            }
            flushTitle(player);
        } catch (Throwable t) {
            StoryCore.logOnce("region_names_dimension", t);
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        TRANSIENT.remove(event.getEntity().getUUID());
    }

    /** A completed crossing (or a dimension change) into {@code target}: the first-visit scrap, then the title. */
    private static void arrive(ServerPlayer player, int target) {
        if (target == 0 || !IntegrationsConfig.on(IntegrationsConfig.worldRegionNames)) {
            return;
        }
        if (RegionTable.isRegion(target) && !RegionState.visited(getState(player), target)) {
            if (IntegrationsConfig.on(IntegrationsConfig.storyScraps) && payScrap(player)) {
                setState(player, RegionState.markVisited(getState(player), target));
                player.displayClientMessage(Component.translatable(KEY + "region.first_visit"), true);
            }
        }
        requestTitle(player, target);
    }

    private static boolean payScrap(ServerPlayer player) {
        Item item = BuiltInRegistries.ITEM.get(SCRAP);
        if (item == Items.AIR) {
            return false;
        }
        ItemStack stack = new ItemStack(item);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        return true;
    }

    private static void requestTitle(ServerPlayer player, int index) {
        state(player).pendingTitle = index;
    }

    /**
     * Shows the waiting title once the origin screen is closed and the last title is 10 s old, if the player still
     * stands where it names (a title never names a place they already left).
     */
    private static void flushTitle(ServerPlayer player) {
        Transient t = state(player);
        if (t.pendingTitle == 0 || !IntegrationsConfig.on(IntegrationsConfig.worldRegionNames)) {
            t.pendingTitle = 0;
            return;
        }
        long now = player.server.getTickCount();
        if (now - t.lastTitleTick < TITLE_GAP_TICKS || originScreenOpen(player)) {
            return;
        }
        int index = t.pendingTitle;
        int last = RegionState.last(getState(player));
        boolean chaosTitle = RegionTable.dimensionByIndex(index) != null
                && RegionTable.CHAOS.equals(player.serverLevel().dimension().location().toString());
        if (index != last && !chaosTitle) {
            t.pendingTitle = 0;
            return;
        }
        Component[] lines = lines(player, index);
        t.pendingTitle = 0;
        if (lines == null) {
            return;
        }
        t.lastTitleTick = now;
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetSubtitleTextPacket(lines[1]));
        player.connection.send(new ClientboundSetTitleTextPacket(lines[0]));
    }

    /** Title and subtitle for a place index, coloured by kind; null for an index with no title. */
    static Component[] lines(ServerPlayer player, int index) {
        if (RegionTable.isRegion(index)) {
            return pair(KEY + "region.l" + index + ".title", KEY + "region.l" + index + ".subtitle", RegionTable.GREEN);
        }
        if (RegionTable.isDepth(index)) {
            String slug = RegionTable.depthSlug(index);
            return pair(KEY + "depth." + slug + ".title", KEY + "depth." + slug + ".subtitle", RegionTable.GREY);
        }
        RegionTable.Dimension d = RegionTable.dimensionByIndex(index);
        if (d == null) {
            return null;
        }
        String base = KEY + "dimension." + d.key();
        if (d.tier() == 0) {
            return pair(base + ".title", base + ".subtitle", d.color());
        }
        int tier = d.tier();
        Transient t = state(player);
        if (RegionTable.CHAOS.equals(player.serverLevel().dimension().location().toString()) && !Double.isNaN(t.ledgeX)
                && sq(player.getX() - t.ledgeX) + sq(player.getZ() - t.ledgeZ) <= LEDGE_RADIUS_SQ) {
            tier = 1;
        }
        return pair(base + ".title", KEY + "tier." + tier, d.color());
    }

    private static Component[] pair(String title, String subtitle, int color) {
        MutableComponent t = Component.translatable(title).withColor(color);
        MutableComponent s = Component.translatable(subtitle).withColor(color);
        return new Component[]{t, s};
    }

    private static double sq(double v) {
        return v * v;
    }

    private static boolean originScreenOpen(ServerPlayer player) {
        if (!ModList.get().isLoaded("neoorigins")) {
            return false;
        }
        try {
            return OriginScreenGate.open(player);
        } catch (Throwable t) {
            StoryCore.logOnce("region_names_origin_gate", t);
            return false;
        }
    }

    private static Transient state(ServerPlayer player) {
        return TRANSIENT.computeIfAbsent(player.getUUID(), id -> new Transient());
    }
}
