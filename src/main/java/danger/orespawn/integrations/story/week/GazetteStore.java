package danger.orespawn.integrations.story.week;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Gazette's server-side record (overworld, {@code data/orespawn_integrations_gazette.dat}). The dawn job delivers
 * to offline subscribers too, so a subscriber's flag, last day online, first-week start and Squire mark live here
 * rather than only on the player (16 B a subscriber in use). Beside them: the lines other systems posted for the next
 * paper and the ones held over, the desk rack's copies for subscribers with no mailbox, and the P&Z mailbox registry
 * (the card's {@code mailbox_registry}: box to owner, one box a player) with each unloaded box's waiting copy.
 */
public final class GazetteStore extends SavedData {

    public static final String FILE = "orespawn_integrations_gazette";
    /** The rack keeps a week of copies; the Odete hands them all over. */
    public static final int RACK_MAX = 7;

    public static final class Sub {
        public String name = "";
        public int lastOnline = -1;
        public int weekStart = -1;
        public boolean squire;
        public int lastCopy = -1;
    }

    /** A line another system posted for the next paper; {@code waits} counts the dawns it has been held over. */
    public record Posted(int page, String key, List<String> args, int waits) {}

    /** A registered P&Z mailbox. */
    public record Box(String dim, BlockPos pos) {
        String id() {
            return dim + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        }

        static Box parse(String id) {
            int bar = id.indexOf('|');
            String[] p = id.substring(bar + 1).split(",");
            return new Box(id.substring(0, bar),
                    new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
        }
    }

    public final Map<UUID, Sub> subs = new LinkedHashMap<>();
    public final List<Posted> posted = new ArrayList<>();
    public final Map<UUID, List<ItemStack>> rack = new LinkedHashMap<>();
    public final Map<UUID, Box> boxes = new LinkedHashMap<>();
    public final Map<String, ItemStack> waiting = new LinkedHashMap<>();

    public static GazetteStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(GazetteStore::new, GazetteStore::load), FILE);
    }

    /** The owner of a registered box, or null. */
    public UUID ownerOf(Box box) {
        for (Map.Entry<UUID, Box> e : boxes.entrySet()) {
            if (e.getValue().equals(box)) {
                return e.getKey();
            }
        }
        return null;
    }

    private static GazetteStore load(CompoundTag tag, HolderLookup.Provider registries) {
        GazetteStore d = new GazetteStore();
        CompoundTag subs = tag.getCompound("subs");
        for (String k : subs.getAllKeys()) {
            CompoundTag c = subs.getCompound(k);
            Sub s = new Sub();
            s.name = c.getString("name");
            s.lastOnline = c.getInt("online");
            s.weekStart = c.getInt("week");
            s.squire = c.getBoolean("squire");
            s.lastCopy = c.contains("copy") ? c.getInt("copy") : -1;
            d.subs.put(UUID.fromString(k), s);
        }
        for (Tag t : tag.getList("posted", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            List<String> args = new ArrayList<>();
            for (Tag a : c.getList("args", Tag.TAG_STRING)) {
                args.add(a.getAsString());
            }
            d.posted.add(new Posted(c.getInt("page"), c.getString("key"), args, c.getInt("waits")));
        }
        CompoundTag rack = tag.getCompound("rack");
        for (String k : rack.getAllKeys()) {
            List<ItemStack> copies = new ArrayList<>();
            for (Tag t : rack.getList(k, Tag.TAG_COMPOUND)) {
                ItemStack.parse(registries, t).ifPresent(copies::add);
            }
            d.rack.put(UUID.fromString(k), copies);
        }
        CompoundTag boxes = tag.getCompound("boxes");
        for (String k : boxes.getAllKeys()) {
            d.boxes.put(UUID.fromString(k), Box.parse(boxes.getString(k)));
        }
        CompoundTag waiting = tag.getCompound("waiting");
        for (String k : waiting.getAllKeys()) {
            ItemStack.parse(registries, waiting.getCompound(k)).ifPresent(s -> d.waiting.put(k, s));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag subs = new CompoundTag();
        this.subs.forEach((id, s) -> {
            CompoundTag c = new CompoundTag();
            c.putString("name", s.name);
            c.putInt("online", s.lastOnline);
            c.putInt("week", s.weekStart);
            c.putBoolean("squire", s.squire);
            c.putInt("copy", s.lastCopy);
            subs.put(id.toString(), c);
        });
        tag.put("subs", subs);
        ListTag posted = new ListTag();
        for (Posted p : this.posted) {
            CompoundTag c = new CompoundTag();
            c.putInt("page", p.page());
            c.putString("key", p.key());
            ListTag args = new ListTag();
            p.args().forEach(a -> args.add(StringTag.valueOf(a)));
            c.put("args", args);
            c.putInt("waits", p.waits());
            posted.add(c);
        }
        tag.put("posted", posted);
        CompoundTag rack = new CompoundTag();
        this.rack.forEach((id, copies) -> {
            ListTag list = new ListTag();
            copies.forEach(s -> list.add(s.save(registries)));
            rack.put(id.toString(), list);
        });
        tag.put("rack", rack);
        CompoundTag boxes = new CompoundTag();
        this.boxes.forEach((id, b) -> boxes.putString(id.toString(), b.id()));
        tag.put("boxes", boxes);
        CompoundTag waiting = new CompoundTag();
        this.waiting.forEach((k, s) -> waiting.put(k, s.save(registries)));
        tag.put("waiting", waiting);
        return tag;
    }
}
