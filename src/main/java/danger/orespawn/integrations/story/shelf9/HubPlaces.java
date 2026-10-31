package danger.orespawn.integrations.story.shelf9;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * The hub list, command storage {@code orespawn_integrations:hub_places}: every place an operator put down with
 * {@code /cc <thing> place}, in list {@code places}, each {@code {thing, pos:[I;x,y,z], dim, day, version}}, at most 32.
 * Every "nearest X" reader checks it before any locate. Also writes Shelf 9's own storage,
 * {@code orespawn_integrations:hatching_ground} {@code {pos:[I;x,y,z], dim, placed:1b}}.
 */
public final class HubPlaces {

    public static final ResourceLocation STORAGE =
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "hub_places");
    public static final ResourceLocation SHELF9_STORAGE =
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "hatching_ground");
    public static final int MAX = 32;
    public static final String VERSION = "0.10.0";

    public record Place(String thing, BlockPos pos, String dim) {}

    private HubPlaces() {
    }

    public static List<Place> all(MinecraftServer server) {
        List<Place> out = new ArrayList<>();
        ListTag list = server.getCommandStorage().get(STORAGE).getList("places", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int[] p = entry.getIntArray("pos");
            if (p.length == 3) {
                out.add(new Place(entry.getString("thing"), new BlockPos(p[0], p[1], p[2]), entry.getString("dim")));
            }
        }
        return out;
    }

    public static List<Place> of(MinecraftServer server, String thing) {
        List<Place> out = new ArrayList<>();
        for (Place place : all(server)) {
            if (place.thing().equals(thing)) {
                out.add(place);
            }
        }
        return out;
    }

    /** Appends a place; false when the list already holds {@link #MAX}. */
    public static boolean add(MinecraftServer server, String thing, BlockPos pos, String dim) {
        CompoundTag root = server.getCommandStorage().get(STORAGE);
        ListTag list = root.getList("places", Tag.TAG_COMPOUND);
        if (list.size() >= MAX) {
            return false;
        }
        CompoundTag entry = new CompoundTag();
        entry.putString("thing", thing);
        entry.put("pos", new IntArrayTag(new int[]{pos.getX(), pos.getY(), pos.getZ()}));
        entry.putString("dim", dim);
        entry.putInt("day", Eggshell.gameDay(server));
        entry.putString("version", VERSION);
        list.add(entry);
        root.put("places", list);
        server.getCommandStorage().set(STORAGE, root);
        return true;
    }

    public static void writeShelf9(MinecraftServer server, BlockPos pos, String dim) {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", new IntArrayTag(new int[]{pos.getX(), pos.getY(), pos.getZ()}));
        tag.putString("dim", dim);
        tag.putBoolean("placed", true);
        server.getCommandStorage().set(SHELF9_STORAGE, tag);
    }
}
