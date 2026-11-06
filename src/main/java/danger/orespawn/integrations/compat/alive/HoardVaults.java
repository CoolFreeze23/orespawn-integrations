package danger.orespawn.integrations.compat.alive;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The vaults {@link HoardVault#placeVault} set down (overworld SavedData {@code orespawn_integrations_hoard_vaults}):
 * at most the 64 newest, each as dimension and position, so the open handler can tell a Fall's vault from any other
 * crystal chest. A broken vault leaves the list.
 */
final class HoardVaults extends SavedData {

    static final String NAME = "orespawn_integrations_hoard_vaults";
    private static final int MAX = 64;

    private record Spot(String dim, long pos) {}

    private final Deque<Spot> spots = new ArrayDeque<>();

    private static final Factory<HoardVaults> FACTORY = new Factory<>(HoardVaults::new, HoardVaults::load, null);

    static HoardVaults get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    void add(ServerLevel level, BlockPos pos) {
        spots.addLast(new Spot(level.dimension().location().toString(), pos.asLong()));
        while (spots.size() > MAX) {
            spots.removeFirst();
        }
        setDirty();
    }

    boolean contains(ServerLevel level, BlockPos pos) {
        return spots.contains(new Spot(level.dimension().location().toString(), pos.asLong()));
    }

    void remove(ServerLevel level, BlockPos pos) {
        if (spots.remove(new Spot(level.dimension().location().toString(), pos.asLong()))) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Spot s : spots) {
            CompoundTag c = new CompoundTag();
            c.putString("dim", s.dim());
            c.putLong("pos", s.pos());
            list.add(c);
        }
        tag.put("vaults", list);
        return tag;
    }

    private static HoardVaults load(CompoundTag tag, HolderLookup.Provider registries) {
        HoardVaults v = new HoardVaults();
        for (Tag t : tag.getList("vaults", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            v.spots.addLast(new Spot(c.getString("dim"), c.getLong("pos")));
        }
        return v;
    }
}
