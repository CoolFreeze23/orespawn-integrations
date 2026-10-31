package danger.orespawn.integrations.story.freguesia;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every Freguesia's record (overworld SavedData {@code orespawn_integrations_freguesias}), one per stone ever founded:
 * the stone's spot and dimension, its name, up to four signers (founder first), standing (in half points), the
 * week's gain, stone days and today's bit, the stage, the last two Wednesdays' visits, founding day, the keys seen,
 * the plots its stakes closed, and today's scored positions (at most 200, cleared at dawn). Beside them, the bed
 * counters of players who sign no stone, and the few per-player flags the market needs.
 *
 * <p>Lookups by place go through a grid of 64-block cells, rebuilt on load and kept on every change, so a decor
 * placement looks at the four cells around it and never at every stone.</p>
 */
public final class FreguesiaData extends SavedData {

    public static final String NAME = "orespawn_integrations_freguesias";
    private static final int CELL = 64;

    /** One Freguesia. */
    public static final class Stone {
        public int id;
        public String dim = "minecraft:overworld";
        public BlockPos pos = BlockPos.ZERO;
        public boolean standing = true;
        public long fallenDay = -1;
        public boolean filed;
        /** A name tag's name, kept as typed; null while the default or an epithet names the stone. */
        public String customName;
        /** The founder's epithet's lang key once a retelling has given one; renames the stone once. */
        public String epithetKey;
        /** An epithet given at a retelling, applied at the next dawn. */
        public String pendingEpithet;
        public final List<UUID> signers = new ArrayList<>();
        public final List<String> signerNames = new ArrayList<>();
        public int standingHalves;
        public int weekGainHalves;
        public int todayHalves;
        public final LongOpenHashSet today = new LongOpenHashSet();
        public int stoneDays;
        public boolean todayBit;
        public int stage;
        public int visits;
        public long foundedDay;
        public long lastSignerDay;
        public boolean bossKill;
        public boolean knight;
        public int deeds;
        public int keyNoted;
        public boolean pendingCheck;
        public boolean plaque;
        public String founderOrigin = "";
        public UUID marker;
        public final List<BlockPos> stakes = new ArrayList<>();
        /** Closed plots: minX, minZ, maxX, maxZ, y, used (0/1). */
        public final List<int[]> plots = new ArrayList<>();

        public int standing() {
            return standingHalves / 2;
        }

        public UUID founder() {
            return signers.isEmpty() ? null : signers.get(0);
        }

        public String founderName() {
            return signerNames.isEmpty() ? "?" : signerNames.get(0);
        }

        public boolean isSigner(UUID player) {
            return signers.contains(player);
        }
    }

    /** A player who signs no stone counts around their respawn point (64 blocks). */
    public static final class Bed {
        public UUID player;
        public String name = "?";
        public int standingHalves;
        public int weekGainHalves;
        public int todayHalves;
        public final LongOpenHashSet today = new LongOpenHashSet();
        public int visits;
    }

    private final Map<Integer, Stone> stones = new LinkedHashMap<>();
    private final Map<UUID, Bed> beds = new HashMap<>();
    /** Players the market has visited (any kind); a newcomer's turn is once. */
    public final java.util.Set<UUID> hadMarket = new java.util.HashSet<>();
    /** Newcomers who were in their first week on a Wednesday that went to another: they keep their turn. */
    public final java.util.Set<UUID> newcomerTurn = new java.util.LinkedHashSet<>();
    /** Deeds waiting for their player's next login: player -> [key, arg] pairs. */
    public final Map<UUID, List<String[]>> pendingDeeds = new HashMap<>();
    private int nextId = 1;

    private final transient Map<Long, List<Stone>> grid = new HashMap<>();
    private transient java.util.Set<UUID> signerIndex;

    private static final Factory<FreguesiaData> FACTORY = new Factory<>(FreguesiaData::new, FreguesiaData::load, null);

    public static FreguesiaData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public java.util.Collection<Stone> stones() {
        return stones.values();
    }

    public Stone stone(int id) {
        return stones.get(id);
    }

    public Stone create(String dim, BlockPos pos) {
        Stone s = new Stone();
        s.id = nextId++;
        s.dim = dim;
        s.pos = pos.immutable();
        stones.put(s.id, s);
        index(s);
        setDirty();
        return s;
    }

    /** Moves a record to a new spot (a resumed stone), keeping the grid right. */
    public void move(Stone s, BlockPos pos) {
        unindex(s);
        s.pos = pos.immutable();
        index(s);
        setDirty();
    }

    public Bed bed(UUID player) {
        return beds.computeIfAbsent(player, k -> {
            Bed b = new Bed();
            b.player = k;
            return b;
        });
    }

    public java.util.Collection<Bed> beds() {
        return beds.values();
    }

    /**
     * True when {@code player} signs any unfiled stone: one hash-set lookup, the set rebuilt only after a signature
     * or a filing changed it.
     */
    public boolean signsAny(UUID player) {
        if (signerIndex == null) {
            signerIndex = new java.util.HashSet<>();
            for (Stone s : stones.values()) {
                if (!s.filed) {
                    signerIndex.addAll(s.signers);
                }
            }
        }
        return signerIndex.contains(player);
    }

    public void addSigner(Stone s, UUID player, String name) {
        s.signers.add(player);
        s.signerNames.add(name);
        signersChanged();
    }

    public void removeSigner(Stone s, UUID player) {
        int i = s.signers.indexOf(player);
        if (i >= 0) {
            s.signers.remove(i);
            s.signerNames.remove(i);
        }
        signersChanged();
    }

    /** Call after a signer list or a record's filed flag changed. */
    public void signersChanged() {
        signerIndex = null;
        setDirty();
    }

    /** Every unfiled record whose spot is within {@code radius} (horizontal) of x, z in {@code dim}. */
    public List<Stone> near(String dim, int x, int z, int radius) {
        List<Stone> out = new ArrayList<>(2);
        int r2 = radius * radius;
        int minCx = Math.floorDiv(x - radius, CELL);
        int maxCx = Math.floorDiv(x + radius, CELL);
        int minCz = Math.floorDiv(z - radius, CELL);
        int maxCz = Math.floorDiv(z + radius, CELL);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                List<Stone> cell = grid.get(key(cx, cz));
                if (cell == null) {
                    continue;
                }
                for (Stone s : cell) {
                    long dx = s.pos.getX() - x;
                    long dz = s.pos.getZ() - z;
                    if (!s.filed && s.dim.equals(dim) && dx * dx + dz * dz <= r2) {
                        out.add(s);
                    }
                }
            }
        }
        return out;
    }

    /** The standing stone at exactly {@code pos} (its lower half), or null. */
    public Stone at(String dim, BlockPos pos) {
        for (Stone s : near(dim, pos.getX(), pos.getZ(), 1)) {
            if (s.standing && s.pos.equals(pos)) {
                return s;
            }
        }
        return null;
    }

    private void index(Stone s) {
        grid.computeIfAbsent(key(Math.floorDiv(s.pos.getX(), CELL), Math.floorDiv(s.pos.getZ(), CELL)),
                k -> new ArrayList<>()).add(s);
    }

    private void unindex(Stone s) {
        List<Stone> cell = grid.get(key(Math.floorDiv(s.pos.getX(), CELL), Math.floorDiv(s.pos.getZ(), CELL)));
        if (cell != null) {
            cell.remove(s);
        }
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    // --- save and load ---

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Stone s : stones.values()) {
            CompoundTag c = new CompoundTag();
            c.putInt("id", s.id);
            c.putString("dim", s.dim);
            c.putLong("pos", s.pos.asLong());
            c.putBoolean("standing", s.standing);
            c.putLong("fallen_day", s.fallenDay);
            c.putBoolean("filed", s.filed);
            if (s.customName != null) {
                c.putString("custom_name", s.customName);
            }
            if (s.epithetKey != null) {
                c.putString("epithet", s.epithetKey);
            }
            if (s.pendingEpithet != null) {
                c.putString("pending_epithet", s.pendingEpithet);
            }
            ListTag signers = new ListTag();
            for (int i = 0; i < s.signers.size(); i++) {
                CompoundTag sg = new CompoundTag();
                sg.putUUID("id", s.signers.get(i));
                sg.putString("name", i < s.signerNames.size() ? s.signerNames.get(i) : "?");
                signers.add(sg);
            }
            c.put("signers", signers);
            c.putInt("standing_halves", s.standingHalves);
            c.putInt("week_gain_halves", s.weekGainHalves);
            c.putInt("today_halves", s.todayHalves);
            c.put("today", new LongArrayTag(s.today.toLongArray()));
            c.putInt("stone_days", s.stoneDays);
            c.putBoolean("today_bit", s.todayBit);
            c.putInt("stage", s.stage);
            c.putInt("visits", s.visits);
            c.putLong("founded_day", s.foundedDay);
            c.putLong("last_signer_day", s.lastSignerDay);
            c.putBoolean("boss_kill", s.bossKill);
            c.putBoolean("knight", s.knight);
            c.putInt("deeds", s.deeds);
            c.putInt("key_noted", s.keyNoted);
            c.putBoolean("pending_check", s.pendingCheck);
            c.putBoolean("plaque", s.plaque);
            c.putString("founder_origin", s.founderOrigin);
            if (s.marker != null) {
                c.putUUID("marker", s.marker);
            }
            long[] stakes = new long[s.stakes.size()];
            for (int i = 0; i < stakes.length; i++) {
                stakes[i] = s.stakes.get(i).asLong();
            }
            c.put("stakes", new LongArrayTag(stakes));
            ListTag plots = new ListTag();
            for (int[] p : s.plots) {
                plots.add(new net.minecraft.nbt.IntArrayTag(p));
            }
            c.put("plots", plots);
            list.add(c);
        }
        tag.put("stones", list);
        tag.putInt("next_id", nextId);
        ListTag bedList = new ListTag();
        for (Bed b : beds.values()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("player", b.player);
            c.putString("name", b.name);
            c.putInt("standing_halves", b.standingHalves);
            c.putInt("week_gain_halves", b.weekGainHalves);
            c.putInt("today_halves", b.todayHalves);
            c.put("today", new LongArrayTag(b.today.toLongArray()));
            c.putInt("visits", b.visits);
            bedList.add(c);
        }
        tag.put("beds", bedList);
        tag.put("had_market", uuids(hadMarket));
        tag.put("newcomer_turn", uuids(newcomerTurn));
        ListTag deeds = new ListTag();
        for (Map.Entry<UUID, List<String[]>> e : pendingDeeds.entrySet()) {
            for (String[] d : e.getValue()) {
                CompoundTag c = new CompoundTag();
                c.putUUID("player", e.getKey());
                c.putString("key", d[0]);
                c.putString("arg", d.length > 1 ? d[1] : "");
                deeds.add(c);
            }
        }
        tag.put("pending_deeds", deeds);
        return tag;
    }

    private static ListTag uuids(java.util.Set<UUID> set) {
        ListTag list = new ListTag();
        for (UUID id : set) {
            list.add(StringTag.valueOf(id.toString()));
        }
        return list;
    }

    private static FreguesiaData load(CompoundTag tag, HolderLookup.Provider registries) {
        FreguesiaData d = new FreguesiaData();
        for (Tag t : tag.getList("stones", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Stone s = new Stone();
            s.id = c.getInt("id");
            s.dim = c.getString("dim");
            s.pos = BlockPos.of(c.getLong("pos"));
            s.standing = c.getBoolean("standing");
            s.fallenDay = c.getLong("fallen_day");
            s.filed = c.getBoolean("filed");
            s.customName = c.contains("custom_name") ? c.getString("custom_name") : null;
            s.epithetKey = c.contains("epithet") ? c.getString("epithet") : null;
            s.pendingEpithet = c.contains("pending_epithet") ? c.getString("pending_epithet") : null;
            for (Tag st : c.getList("signers", Tag.TAG_COMPOUND)) {
                CompoundTag sg = (CompoundTag) st;
                if (sg.hasUUID("id")) {
                    s.signers.add(sg.getUUID("id"));
                    s.signerNames.add(sg.getString("name"));
                }
            }
            s.standingHalves = c.getInt("standing_halves");
            s.weekGainHalves = c.getInt("week_gain_halves");
            s.todayHalves = c.getInt("today_halves");
            for (long l : c.getLongArray("today")) {
                s.today.add(l);
            }
            s.stoneDays = c.getInt("stone_days");
            s.todayBit = c.getBoolean("today_bit");
            s.stage = c.getInt("stage");
            s.visits = c.getInt("visits");
            s.foundedDay = c.getLong("founded_day");
            s.lastSignerDay = c.getLong("last_signer_day");
            s.bossKill = c.getBoolean("boss_kill");
            s.knight = c.getBoolean("knight");
            s.deeds = c.getInt("deeds");
            s.keyNoted = c.getInt("key_noted");
            s.pendingCheck = c.getBoolean("pending_check");
            s.plaque = c.getBoolean("plaque");
            s.founderOrigin = c.getString("founder_origin");
            s.marker = c.hasUUID("marker") ? c.getUUID("marker") : null;
            for (long l : c.getLongArray("stakes")) {
                s.stakes.add(BlockPos.of(l));
            }
            for (Tag p : c.getList("plots", Tag.TAG_INT_ARRAY)) {
                int[] a = ((net.minecraft.nbt.IntArrayTag) p).getAsIntArray();
                if (a.length == 6) {
                    s.plots.add(a);
                }
            }
            d.stones.put(s.id, s);
            d.index(s);
        }
        d.nextId = Math.max(tag.getInt("next_id"), d.stones.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1);
        for (Tag t : tag.getList("beds", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (!c.hasUUID("player")) {
                continue;
            }
            Bed b = new Bed();
            b.player = c.getUUID("player");
            b.name = c.getString("name");
            b.standingHalves = c.getInt("standing_halves");
            b.weekGainHalves = c.getInt("week_gain_halves");
            b.todayHalves = c.getInt("today_halves");
            for (long l : c.getLongArray("today")) {
                b.today.add(l);
            }
            b.visits = c.getInt("visits");
            d.beds.put(b.player, b);
        }
        readUuids(tag.getList("had_market", Tag.TAG_STRING), d.hadMarket);
        readUuids(tag.getList("newcomer_turn", Tag.TAG_STRING), d.newcomerTurn);
        for (Tag t : tag.getList("pending_deeds", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (c.hasUUID("player")) {
                d.pendingDeeds.computeIfAbsent(c.getUUID("player"), k -> new ArrayList<>())
                        .add(new String[]{c.getString("key"), c.getString("arg")});
            }
        }
        return d;
    }

    private static void readUuids(ListTag list, java.util.Set<UUID> into) {
        for (Tag t : list) {
            try {
                into.add(UUID.fromString(t.getAsString()));
            } catch (IllegalArgumentException ignored) {
                // a hand-edited entry; skip it
            }
        }
    }
}
