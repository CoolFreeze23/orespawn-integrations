package danger.orespawn.integrations.story.week;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The Land's day (the card's SavedData {@code orespawn_integrations:lands_week}, one long, plus the day index the
 * dawn check last saw). Kept on the overworld in {@code data/orespawn_integrations_lands_week.dat}: a colon can't go
 * in a Windows file name.
 */
public final class LandsWeekData extends SavedData {

    public static final String FILE = "orespawn_integrations_lands_week";

    /** False until the first check of this world: then the Land's day starts at the world's current day index. */
    public boolean started;
    public long landDay;
    public long lastIndex;

    public static LandsWeekData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(LandsWeekData::new, LandsWeekData::load), FILE);
    }

    private static LandsWeekData load(CompoundTag tag, HolderLookup.Provider registries) {
        LandsWeekData data = new LandsWeekData();
        data.started = tag.getBoolean("started");
        data.landDay = tag.getLong("land_day");
        data.lastIndex = tag.getLong("last_index");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("started", started);
        tag.putLong("land_day", landDay);
        tag.putLong("last_index", lastIndex);
        return tag;
    }
}
