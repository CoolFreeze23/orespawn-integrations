package danger.orespawn.integrations.world.sightings;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData {@code cc_avistamentos} (system-sightings, "The record"): per player the stamped families (a 64-bit set),
 * the sighted starts (a ring of at most 512 start chunk positions with the structure id, the oldest dropping first),
 * the stamps handed in, the dusk's butterflies and the auto-pin ring per dimension. Structure ids are kept once in a
 * shared table and referenced by index, so a player at the cap stays near 6 KB. Stored on the overworld.
 */
public final class SightingsData extends SavedData {

    public static final String FILE = "cc_avistamentos";

    public static final class Player {
        public long families;
        public final SightingRules.Ring starts = new SightingRules.Ring(SightingRules.MAX_STARTS);
        /** Stamps already paid at an Odete's hand-in (read and written by the Registry's dialog). */
        public int handedIn;
        public long butterflyDay = -1;
        public int butterflyMask;
        public final Map<String, Deque<String>> pins = new HashMap<>();

        public int stamps() {
            return Long.bitCount(families);
        }

        public boolean has(int family) {
            return (families & SightingRules.bit(family)) != 0;
        }
    }

    private final List<String> structures = new ArrayList<>();
    private final Map<String, Integer> structureIndex = new HashMap<>();
    private final Map<UUID, Player> players = new HashMap<>();

    public static SightingsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SightingsData::new, SightingsData::load), FILE);
    }

    public Player player(UUID id) {
        return players.computeIfAbsent(id, k -> new Player());
    }

    public int structureId(String id) {
        return structureIndex.computeIfAbsent(id, k -> {
            structures.add(k);
            return structures.size() - 1;
        });
    }

    public String structureName(int index) {
        return index >= 0 && index < structures.size() ? structures.get(index) : "";
    }

    private static SightingsData load(CompoundTag tag, HolderLookup.Provider registries) {
        SightingsData data = new SightingsData();
        ListTag names = tag.getList("structures", Tag.TAG_STRING);
        for (int i = 0; i < names.size(); i++) {
            data.structureId(names.getString(i));
        }
        CompoundTag all = tag.getCompound("players");
        for (String key : all.getAllKeys()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompoundTag p = all.getCompound(key);
            Player player = data.player(id);
            player.families = p.getLong("f");
            long[] starts = p.getLongArray("s");
            int[] kinds = p.getIntArray("st");
            for (int i = 0; i < starts.length && i < kinds.length; i++) {
                player.starts.add(starts[i], kinds[i]);
            }
            player.handedIn = p.getInt("paid");
            player.butterflyDay = p.contains("bd") ? p.getLong("bd") : -1;
            player.butterflyMask = p.getInt("bm");
            CompoundTag pins = p.getCompound("pins");
            for (String dim : pins.getAllKeys()) {
                ListTag list = pins.getList(dim, Tag.TAG_STRING);
                Deque<String> ring = new ArrayDeque<>();
                for (int i = 0; i < list.size(); i++) {
                    ring.addLast(list.getString(i));
                }
                player.pins.put(dim, ring);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag names = new ListTag();
        structures.forEach(s -> names.add(StringTag.valueOf(s)));
        tag.put("structures", names);
        CompoundTag all = new CompoundTag();
        players.forEach((id, player) -> {
            CompoundTag p = new CompoundTag();
            p.putLong("f", player.families);
            int n = player.starts.size();
            long[] starts = new long[n];
            int[] kinds = new int[n];
            for (int i = 0; i < n; i++) {
                starts[i] = player.starts.valueAt(i);
                kinds[i] = player.starts.tagAt(i);
            }
            p.put("s", new LongArrayTag(starts));
            p.putIntArray("st", kinds);
            p.putInt("paid", player.handedIn);
            p.putLong("bd", player.butterflyDay);
            p.putInt("bm", player.butterflyMask);
            CompoundTag pins = new CompoundTag();
            player.pins.forEach((dim, ring) -> {
                ListTag list = new ListTag();
                ring.forEach(name -> list.add(StringTag.valueOf(name)));
                pins.put(dim, list);
            });
            p.put("pins", pins);
            all.put(id.toString(), p);
        });
        tag.put("players", all);
        return tag;
    }
}
