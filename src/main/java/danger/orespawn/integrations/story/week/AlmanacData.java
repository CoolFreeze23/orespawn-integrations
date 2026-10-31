package danger.orespawn.integrations.story.week;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Almanac's record (the card's SavedData {@code cc_almanac}, overworld, under 2 KB): today's Land's day and its
 * picks, the night and day slots booked so far, and the queue of moved events with their next try day. A restart in
 * the middle of a day reads the record and never re-rolls. File {@code data/orespawn_integrations_cc_almanac.dat}.
 */
public final class AlmanacData extends SavedData {

    public static final String FILE = "orespawn_integrations_cc_almanac";

    /** One booked slot: the event, its dimension, its place, the Land's day it belongs to and its start (game time). */
    public record Slot(String event, String dim, int place, long day, long start) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("event", event);
            t.putString("dim", dim);
            t.putInt("place", place);
            t.putLong("day", day);
            t.putLong("start", start);
            return t;
        }

        static Slot load(CompoundTag t) {
            return new Slot(t.getString("event"), t.getString("dim"), t.getInt("place"), t.getLong("day"),
                    t.getLong("start"));
        }
    }

    /** An event that lost its slot and tries again on {@code day}. */
    public record Moved(String event, long day) {}

    public long day = -1;
    /** Each dimension's active habit (ids); none ship before 0.21.0. */
    public final Map<String, String> habits = new LinkedHashMap<>();
    /** Tonight's floating folklore night, or empty. */
    public String folklore = "";
    /** The Dish of the Day slot (filled from 0.16.0) and tonight's dream species (from 0.12.0), or empty. */
    public String dish = "";
    public String dream = "";
    /** The Balança's needle read at dawn (4 until 0.18.0). */
    public int needle = 4;
    public final List<Slot> nights = new ArrayList<>();
    public final List<Slot> days = new ArrayList<>();
    public final List<Moved> moved = new ArrayList<>();
    /** The Land's day of the last night skipped by sleep whose first-light forms already ran. */
    public long firstLightNight = -1;

    public static AlmanacData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(AlmanacData::new, AlmanacData::load), FILE);
    }

    private static AlmanacData load(CompoundTag tag, HolderLookup.Provider registries) {
        AlmanacData d = new AlmanacData();
        d.day = tag.contains("day") ? tag.getLong("day") : -1;
        CompoundTag habits = tag.getCompound("habits");
        for (String dim : habits.getAllKeys()) {
            d.habits.put(dim, habits.getString(dim));
        }
        d.folklore = tag.getString("folklore");
        d.dish = tag.getString("dish");
        d.dream = tag.getString("dream");
        d.needle = tag.contains("needle") ? tag.getInt("needle") : 4;
        for (Tag t : tag.getList("nights", Tag.TAG_COMPOUND)) {
            d.nights.add(Slot.load((CompoundTag) t));
        }
        for (Tag t : tag.getList("days", Tag.TAG_COMPOUND)) {
            d.days.add(Slot.load((CompoundTag) t));
        }
        for (Tag t : tag.getList("moved", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.moved.add(new Moved(c.getString("event"), c.getLong("day")));
        }
        d.firstLightNight = tag.contains("first_light") ? tag.getLong("first_light") : -1;
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("day", day);
        CompoundTag h = new CompoundTag();
        habits.forEach(h::putString);
        tag.put("habits", h);
        tag.putString("folklore", folklore);
        tag.putString("dish", dish);
        tag.putString("dream", dream);
        tag.putInt("needle", needle);
        ListTag n = new ListTag();
        nights.forEach(s -> n.add(s.save()));
        tag.put("nights", n);
        ListTag dd = new ListTag();
        days.forEach(s -> dd.add(s.save()));
        tag.put("days", dd);
        ListTag m = new ListTag();
        for (Moved mv : moved) {
            CompoundTag c = new CompoundTag();
            c.putString("event", mv.event());
            c.putLong("day", mv.day());
            m.add(c);
        }
        tag.put("moved", m);
        tag.putLong("first_light", firstLightNight);
        return tag;
    }
}
