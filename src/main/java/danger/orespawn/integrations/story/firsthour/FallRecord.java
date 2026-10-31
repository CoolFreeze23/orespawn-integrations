package danger.orespawn.integrations.story.firsthour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The First Fall's world record (overworld SavedData {@code orespawn_integrations_first_fall}): per player, whether
 * their Fall has landed, their crate's position while it stands, whether they opened it, and how many landing tries
 * found no spot. The opened crates double as the "First Fall crate list" the landing rule reads: an opened crate
 * that is empty never keeps a later Fall away. A broken crate is dropped from the list. Also holds the night of the
 * last Hoard Fall, so a restart never lets a second one land on the same night.
 */
public final class FallRecord extends SavedData {

    public static final String NAME = "orespawn_integrations_first_fall";

    public static final class Entry {
        public boolean landed;
        public boolean opened;
        public boolean backDated;
        public BlockPos crate;
        public int tries;
        public long lastTry = Long.MIN_VALUE / 2;
    }

    private final Map<UUID, Entry> entries = new HashMap<>();
    private long lastHoardNight = Long.MIN_VALUE;

    private static final Factory<FallRecord> FACTORY = new Factory<>(FallRecord::new, FallRecord::load, null);

    public static FallRecord get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public Entry entry(UUID player) {
        return entries.computeIfAbsent(player, k -> new Entry());
    }

    public Entry find(UUID player) {
        return entries.get(player);
    }

    public Map<UUID, Entry> all() {
        return entries;
    }

    /** The crate's position for {@code player}, or null. */
    public static BlockPos crateOf(ServerPlayer player) {
        if (player.getServer() == null) {
            return null;
        }
        Entry e = get(player.getServer()).find(player.getUUID());
        return e == null ? null : e.crate;
    }

    /** The player whose unopened crate stands at {@code pos}, or null. */
    public UUID unopenedOwnerAt(BlockPos pos) {
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            if (!e.getValue().opened && pos.equals(e.getValue().crate)) {
                return e.getKey();
            }
        }
        return null;
    }

    public boolean isOpenedCrate(BlockPos pos) {
        for (Entry e : entries.values()) {
            if (e.opened && pos.equals(e.crate)) {
                return true;
            }
        }
        return false;
    }

    public long lastHoardNight() {
        return lastHoardNight;
    }

    public void setLastHoardNight(long night) {
        lastHoardNight = night;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            CompoundTag c = new CompoundTag();
            Entry v = e.getValue();
            c.putUUID("player", e.getKey());
            c.putBoolean("landed", v.landed);
            c.putBoolean("opened", v.opened);
            c.putBoolean("back_dated", v.backDated);
            c.putInt("tries", v.tries);
            if (v.crate != null) {
                c.putLong("crate", v.crate.asLong());
            }
            list.add(c);
        }
        tag.put("players", list);
        tag.putLong("last_hoard_night", lastHoardNight);
        return tag;
    }

    private static FallRecord load(CompoundTag tag, HolderLookup.Provider registries) {
        FallRecord r = new FallRecord();
        for (Tag t : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (!c.hasUUID("player")) {
                continue;
            }
            Entry v = new Entry();
            v.landed = c.getBoolean("landed");
            v.opened = c.getBoolean("opened");
            v.backDated = c.getBoolean("back_dated");
            v.tries = c.getInt("tries");
            if (c.contains("crate")) {
                v.crate = BlockPos.of(c.getLong("crate"));
            }
            r.entries.put(c.getUUID("player"), v);
        }
        if (tag.contains("last_hoard_night")) {
            r.lastHoardNight = tag.getLong("last_hoard_night");
        }
        return r;
    }
}
