package danger.orespawn.integrations.story.shelf9;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shelf 9's SavedData (the card's {@code orespawn_integrations:hatching_ground}): where it stands, the straw's three
 * larder spots, the signs our code writes, the waystone, and the Hatch Register (each player's line number, 4 B a
 * player, read from 0.10.3 as Mothra's fare number). Kept on the overworld. The file is
 * {@code data/orespawn_integrations_hatching_ground.dat}: a colon can't go in a Windows file name.
 */
public final class HatchingGroundData extends SavedData {

    public static final String FILE = "orespawn_integrations_hatching_ground";
    /** The Register's lines: 8 signs of 4; line 33 on is counted on the back of the last sign. */
    public static final int REGISTER_LINES = 32;

    /** Set once the first-start placement ran (placed or skipped), so a world never tries twice. */
    public boolean firstStartDone;
    public boolean placed;
    public BlockPos origin = BlockPos.ZERO;
    public String dimension = "minecraft:overworld";
    public String placedBy = "";
    public final List<BlockPos> straw = new ArrayList<>();
    public final List<String> strawBlocks = new ArrayList<>();
    public final List<BlockPos> registerSigns = new ArrayList<>();
    /** Ways signs by side index (0 north, 1 east, 2 south, 3 west). */
    public final BlockPos[] waysSigns = new BlockPos[4];
    public UUID waystone;
    /** The OI version whose locates wrote the Ways signs; 0.10.1 re-runs them once when this is older. */
    public String waysVersion = "";
    public int nextLine = 1;
    public final Map<UUID, Integer> lines = new HashMap<>();
    public final Map<UUID, String> names = new HashMap<>();
    /** Each line's origin id once known (so a row can be rewritten while its player is offline). */
    public final Map<UUID, String> origins = new HashMap<>();
    /** The world day each line was entered on. */
    public final Map<UUID, Integer> days = new HashMap<>();
    /** Players whose Register line still waits for an origin (picked on the Awakening screen after the first join). */
    public final Set<UUID> pendingOrigin = new LinkedHashSet<>();
    /** Players whose Shelf 9 waystone activation is still owed (the stone stood unloaded or wasn't placed yet). */
    public final Set<UUID> pendingWaystone = new LinkedHashSet<>();

    public static HatchingGroundData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(HatchingGroundData::new, HatchingGroundData::load), FILE);
    }

    private static HatchingGroundData load(CompoundTag tag, HolderLookup.Provider registries) {
        HatchingGroundData data = new HatchingGroundData();
        data.firstStartDone = tag.getBoolean("first_start_done");
        data.placed = tag.getBoolean("placed");
        data.origin = NbtUtils.readBlockPos(tag, "origin").orElse(BlockPos.ZERO);
        data.dimension = tag.getString("dim").isEmpty() ? "minecraft:overworld" : tag.getString("dim");
        data.placedBy = tag.getString("placed_by");
        readPositions(tag.getList("straw", Tag.TAG_INT_ARRAY), data.straw);
        for (Tag t : tag.getList("straw_blocks", Tag.TAG_STRING)) {
            data.strawBlocks.add(t.getAsString());
        }
        readPositions(tag.getList("register_signs", Tag.TAG_INT_ARRAY), data.registerSigns);
        for (int i = 0; i < 4; i++) {
            data.waysSigns[i] = NbtUtils.readBlockPos(tag, "ways_" + i).orElse(null);
        }
        if (tag.hasUUID("waystone")) {
            data.waystone = tag.getUUID("waystone");
        }
        data.waysVersion = tag.getString("ways_version");
        data.nextLine = Math.max(1, tag.getInt("next_line"));
        CompoundTag lines = tag.getCompound("lines");
        for (String key : lines.getAllKeys()) {
            UUID id = parse(key);
            if (id != null) {
                data.lines.put(id, lines.getInt(key));
            }
        }
        CompoundTag names = tag.getCompound("names");
        for (String key : names.getAllKeys()) {
            UUID id = parse(key);
            if (id != null) {
                data.names.put(id, names.getString(key));
            }
        }
        CompoundTag origins = tag.getCompound("origins");
        for (String key : origins.getAllKeys()) {
            UUID id = parse(key);
            if (id != null) {
                data.origins.put(id, origins.getString(key));
            }
        }
        CompoundTag days = tag.getCompound("days");
        for (String key : days.getAllKeys()) {
            UUID id = parse(key);
            if (id != null) {
                data.days.put(id, days.getInt(key));
            }
        }
        readUuids(tag.getList("pending_origin", Tag.TAG_STRING), data.pendingOrigin);
        readUuids(tag.getList("pending_waystone", Tag.TAG_STRING), data.pendingWaystone);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("first_start_done", firstStartDone);
        tag.putBoolean("placed", placed);
        tag.put("origin", NbtUtils.writeBlockPos(origin));
        tag.putString("dim", dimension);
        tag.putString("placed_by", placedBy);
        tag.put("straw", writePositions(straw));
        ListTag blocks = new ListTag();
        for (String id : strawBlocks) {
            blocks.add(StringTag.valueOf(id));
        }
        tag.put("straw_blocks", blocks);
        tag.put("register_signs", writePositions(registerSigns));
        for (int i = 0; i < 4; i++) {
            if (waysSigns[i] != null) {
                tag.put("ways_" + i, NbtUtils.writeBlockPos(waysSigns[i]));
            }
        }
        if (waystone != null) {
            tag.putUUID("waystone", waystone);
        }
        tag.putString("ways_version", waysVersion);
        tag.putInt("next_line", nextLine);
        CompoundTag linesTag = new CompoundTag();
        lines.forEach((id, line) -> linesTag.putInt(id.toString(), line));
        tag.put("lines", linesTag);
        CompoundTag namesTag = new CompoundTag();
        names.forEach((id, name) -> namesTag.putString(id.toString(), name));
        tag.put("names", namesTag);
        CompoundTag originsTag = new CompoundTag();
        origins.forEach((id, origin) -> originsTag.putString(id.toString(), origin));
        tag.put("origins", originsTag);
        CompoundTag daysTag = new CompoundTag();
        days.forEach((id, day) -> daysTag.putInt(id.toString(), day));
        tag.put("days", daysTag);
        tag.put("pending_origin", writeUuids(pendingOrigin));
        tag.put("pending_waystone", writeUuids(pendingWaystone));
        return tag;
    }

    private static void readPositions(ListTag list, List<BlockPos> out) {
        for (Tag t : list) {
            int[] a = ((net.minecraft.nbt.IntArrayTag) t).getAsIntArray();
            if (a.length == 3) {
                out.add(new BlockPos(a[0], a[1], a[2]));
            }
        }
    }

    private static ListTag writePositions(List<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos pos : positions) {
            list.add(NbtUtils.writeBlockPos(pos));
        }
        return list;
    }

    private static void readUuids(ListTag list, Set<UUID> out) {
        for (Tag t : list) {
            UUID id = parse(t.getAsString());
            if (id != null) {
                out.add(id);
            }
        }
    }

    private static ListTag writeUuids(Set<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            list.add(StringTag.valueOf(id.toString()));
        }
        return list;
    }

    private static UUID parse(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
