package danger.orespawn.integrations.story.shelf9;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.List;

/**
 * The Four Ways (structure-hatching-ground): one sign at each side of Shelf 9 names the nearest real thing on that
 * side, with the Prince's comment. Four locates ({@code findNearestMapStructure}, 32 chunks, never forcing a chunk to
 * generate past structure starts): a village, a Pandora henge, a tower, a bar, each through a structure tag of ours
 * ({@code #orespawn_integrations:shelf9/<kind>}) so 0.10.1 can add the boteco by data. The hub list is read first for
 * the bar and the Hunt Lodge, which an operator places by hand. They run once, while the server starts or at the
 * operator's command, never while players wait.
 */
public final class FourWays {

    public static final String[] KINDS = {"village", "henge", "tower", "bar"};
    /** The version whose locates wrote the signs; 0.10.1 compares it to re-run them once. */
    public static final String VERSION = "0.10.0";
    private static final int RADIUS_CHUNKS = 32;
    private static final String KEY = "orespawn_integrations.shelf9.ways.";

    private FourWays() {
    }

    public static void locateAndWrite(ServerLevel level, BlockPos centre, HatchingGroundData data) {
        long start = System.nanoTime();
        List<WaysPlan.Found> found = new ArrayList<>();
        for (String kind : KINDS) {
            BlockPos hub = nearestHubPlace(level, centre, "bar".equals(kind) ? "boteco" : null);
            BlockPos pos = hub != null ? hub : locate(level, centre, kind);
            if (pos != null) {
                found.add(new WaysPlan.Found(kind, pos.getX() - centre.getX(), pos.getZ() - centre.getZ()));
            }
        }
        BlockPos lodge = nearestHubPlace(level, centre, "hunt_lodge");
        if (lodge != null) {
            found.add(new WaysPlan.Found("hunt_lodge", lodge.getX() - centre.getX(), lodge.getZ() - centre.getZ()));
        }
        WaysPlan.Found[] sides = WaysPlan.assign(found);
        for (int side = 0; side < 4; side++) {
            write(level, data.waysSigns[side], side, sides[side]);
        }
        data.waysVersion = VERSION;
        data.setDirty();
        OreSpawnIntegrations.LOGGER.info("Shelf 9's Four Ways: {} found, the locates took {} ms", found.size(),
                (System.nanoTime() - start) / 1_000_000L);
    }

    private static BlockPos locate(ServerLevel level, BlockPos centre, String kind) {
        TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE,
                ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "shelf9/" + kind));
        try {
            return level.findNearestMapStructure(tag, centre, RADIUS_CHUNKS, false);
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.warn("Shelf 9: the {} locate failed; that way stays empty", kind, t);
            return null;
        }
    }

    private static BlockPos nearestHubPlace(ServerLevel level, BlockPos centre, String thing) {
        if (thing == null) {
            return null;
        }
        BlockPos best = null;
        String dim = level.dimension().location().toString();
        for (HubPlaces.Place place : HubPlaces.of(level.getServer(), thing)) {
            if (place.dim().equals(dim) && (best == null || place.pos().distSqr(centre) < best.distSqr(centre))) {
                best = place.pos();
            }
        }
        return best;
    }

    private static void write(ServerLevel level, BlockPos sign, int side, WaysPlan.Found found) {
        String kind = found == null ? "nothing" : found.kind();
        Component heading = Component.translatable(KEY + "heading", WaysPlan.ARROWS[side],
                Component.translatable(KEY + "dir." + WaysPlan.SIDES[side]));
        Component thing = Component.translatable(KEY + kind + ".thing");
        Component distance = found == null ? Component.empty()
                : Component.translatable(KEY + "distance", WaysPlan.roundedDistance(found));
        Component comment = Component.translatable(KEY + kind + ".comment");
        HatchRegister.editSign(level, sign, true, t -> t
                .setMessage(0, heading).setMessage(1, thing).setMessage(2, distance).setMessage(3, comment));
    }
}
