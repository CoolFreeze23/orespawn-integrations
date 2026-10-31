package danger.orespawn.integrations.story.deed;

import com.mojang.serialization.Codec;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.court.CourtRanks;
import danger.orespawn.integrations.story.week.LandsWeek;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * The deed log (system-deed-log, 0.10.1): the world's memory of what each player did, retold on Saturday, printed in
 * the Gazette and read by the court. Each deed is a lang key with its arguments, so every reader hears it in their own
 * language; a boss or legend deed also names the players within 32 blocks.
 *
 * <p>One SavedData on the overworld, {@code cc_deed_log} (the file {@code orespawn_integrations_cc_deed_log.dat}),
 * at most 16 KB of lines per player with the oldest dropped first, written by event handlers and read on occasions:
 * Saturday's retelling ({@link #saturdayLines}, one rung bigger each time), the Gazette's headline
 * ({@link #headline}), the epithets. The per-player attachment {@code deed_heard} keeps the Saturday chat budget and
 * the missed reading's heard mark. Nothing ticks.</p>
 */
public final class DeedLog {

    /**
     * One line of the log: whose deed it is, its lang key and arguments, the Land's day it happened, how far up the
     * truco ladder it has been retold (0-4), the cordel's printed bit, whether it was retold this week, and the
     * players within 32 blocks on boss and legend deeds.
     */
    public record Deed(UUID player, String playerName, String key, List<String> args, long day, int rung,
                       boolean printed, boolean retold, List<UUID> witnesses) {

        /** The line as the listener's own client will read it. */
        public Component text() {
            return Component.translatable(key, args.stream().map(DeedLog::argument).toArray());
        }
    }

    public static final String FILE = "orespawn_integrations_cc_deed_log";
    private static final String TRUCO = "orespawn_integrations.truco.";

    /**
     * Lines too long for one chat line at a 12-character name: each has a second part, key {@code <key>.2}, which
     * the Saturday reader sends 3 s after the first (spine 3.1).
     */
    public static final java.util.Set<String> TWO_PART = java.util.Set.of(
            "orespawn_integrations.deed.hamlet", "orespawn_integrations.deed.town");

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OreSpawnIntegrations.MODID);

    /**
     * {@code deed_heard}: [0] the Saturday chat budget, the Land's day of that Saturday x 16 plus lines sent (0-8);
     * [1] the Land's day of the Saturday whose reading the player heard, plus one (0 never).
     */
    public static final Supplier<AttachmentType<int[]>> HEARD = TYPES.register("deed_heard",
            () -> AttachmentType.builder(() -> new int[2])
                    .serialize(Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of)).copyOnDeath().build());

    private DeedLog() {
    }

    public static void init(IEventBus modBus) {
        TYPES.register(modBus);
        DeedCommand.init();
    }

    // --- writing ---

    /** Writes one plain deed for {@code player}; each argument is kept as text (a name, a number, a {@link #key}). */
    public static void record(ServerPlayer player, String deedKey, Object... args) {
        write(player, deedKey, DeedRules.Kind.PLAIN, List.of(), args);
    }

    /**
     * Writes a deed of a given ladder kind; for {@link DeedRules.Kind#WEAPON} the last argument is the weapon. A
     * {@code party} deed (a boss, a legend) also notes up to four players within 32 blocks.
     */
    public static void record(ServerPlayer player, DeedRules.Kind kind, boolean party, String deedKey, Object... args) {
        List<UUID> witnesses = new ArrayList<>();
        if (party) {
            for (ServerPlayer other : witnessesOf(player)) {
                witnesses.add(other.getUUID());
            }
        }
        write(player, deedKey, kind, witnesses, args);
    }

    /** The other players within 32 blocks of {@code player} (one entity query), at most four. */
    public static List<ServerPlayer> witnessesOf(ServerPlayer player) {
        List<ServerPlayer> out = new ArrayList<>();
        double r = DeedRules.WITNESS_RADIUS;
        for (ServerPlayer other : player.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                player.getBoundingBox().inflate(r), p -> p != player && !p.isSpectator())) {
            if (other.distanceToSqr(player) <= r * r && out.size() < DeedRules.MAX_WITNESSES) {
                out.add(other);
            }
        }
        return out;
    }

    private static void write(ServerPlayer player, String key, DeedRules.Kind kind, List<UUID> witnesses, Object[] args) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyDeedLog) || player.getServer() == null) {
            return;
        }
        try {
            List<String> text = new ArrayList<>();
            for (Object a : args) {
                text.add(a instanceof Component c ? c.getString() : String.valueOf(a));
            }
            MinecraftServer server = player.getServer();
            Store store = Store.get(server);
            DeedRules.Entry entry = new DeedRules.Entry(player.getUUID(), key, text, CourtRanks.landDay(server), kind, witnesses);
            store.add(player.getGameProfile().getName(), entry);
        } catch (Throwable t) {
            StoryCore.logOnce("deed_log_write", t);
        }
    }

    // --- reading ---

    /** Every player's deeds from the current Land week (Monday on), oldest first. */
    public static List<Deed> week(MinecraftServer server) {
        long start = weekStart(server);
        List<Deed> out = new ArrayList<>();
        Store store = Store.get(server);
        for (Map.Entry<UUID, Store.Log> e : store.logs.entrySet()) {
            for (DeedRules.Entry d : e.getValue().lines) {
                if (d.day >= start) {
                    out.add(view(e.getValue().name, d));
                }
            }
        }
        out.sort((a, b) -> Long.compare(a.day(), b.day()));
        return out;
    }

    /** One player's lines, oldest first. */
    public static List<Deed> of(MinecraftServer server, UUID player) {
        Store.Log log = Store.get(server).logs.get(player);
        List<Deed> out = new ArrayList<>();
        if (log != null) {
            for (DeedRules.Entry d : log.lines) {
                out.add(view(log.name, d));
            }
        }
        return out;
    }

    /** Every line written on the Land's day {@code day}, server-wide, in the order written (at most 64). */
    public static List<Deed> day(MinecraftServer server, long day) {
        List<Deed> out = new ArrayList<>();
        for (Store.Ref ref : Store.get(server).dayIndex(day)) {
            out.add(view(ref.log().name, ref.entry()));
        }
        return out;
    }

    /** The Gazette's headline for {@code day}: the biggest deed (boss kill first), or null on a quiet day. */
    public static Deed headline(MinecraftServer server, long day) {
        List<Store.Ref> refs = Store.get(server).dayIndex(day);
        List<DeedRules.Entry> entries = new ArrayList<>();
        for (Store.Ref ref : refs) {
            entries.add(ref.entry());
        }
        DeedRules.Entry best = DeedRules.headline(entries);
        if (best == null) {
            return null;
        }
        return view(refs.get(entries.indexOf(best)).log().name, best);
    }

    /** The first day of the current Land week (its Monday), as a Land's day number. */
    public static long weekStart(MinecraftServer server) {
        int fromMonday = (LandsWeek.today(server) + 6) % 7;
        return CourtRanks.landDay(server) - fromMonday;
    }

    // --- Saturday ---

    /**
     * Saturday's lines for {@code listener} (their own: this week's unretold lines, newest first, then their best older
     * one), at most four, each told one rung bigger than last time. Marks them retold. Empty with the log off. A
     * {@link #TWO_PART} line brings its second part right after it; the reader sends that one 3 s later.
     */
    public static List<Component> saturdayLines(MinecraftServer server, ServerPlayer listener) {
        List<Component> out = new ArrayList<>();
        if (!IntegrationsConfig.on(IntegrationsConfig.storyDeedLog)) {
            return out;
        }
        Store store = Store.get(server);
        Store.Log log = store.logs.get(listener.getUUID());
        if (log == null) {
            return out;
        }
        for (DeedRules.Entry e : DeedRules.saturdayPick(log.lines, weekStart(server))) {
            DeedRules.retell(e);
            out.add(retold(log.name, e));
            if (TWO_PART.contains(e.key)) {
                out.add(Component.translatable(e.key + ".2"));
            }
        }
        log.epithetRung = DeedRules.epithetRung(log.lines);
        store.setDirty();
        return out;
    }

    /** A line as Saturday tells it at its rung: the weapon shrinks, the swell grows, the gift shrinks, the dare stays. */
    static Component retold(String name, DeedRules.Entry e) {
        List<Object> args = new ArrayList<>();
        for (String a : e.args) {
            args.add(argument(a));
        }
        String key = e.key;
        switch (e.kind) {
            case WEAPON -> {
                if (e.rung >= 1 && !args.isEmpty()) {
                    args.set(args.size() - 1, Component.translatable(TRUCO + "weapon." + e.rung));
                }
            }
            case GIFT -> key = e.key + "." + DeedRules.giftStep(e.rung);
            default -> {
            }
        }
        MutableComponent line = Component.translatable(key, args.toArray());
        if (e.kind == DeedRules.Kind.PLAIN && e.rung >= 1) {
            line = line.append(" ").append(Component.translatable(TRUCO + "swell." + e.rung));
        }
        return line;
    }

    /** The player's truco epithet ("Ana of the Spoon"), or null when no weapon line has been retold. */
    public static Component epithet(MinecraftServer server, UUID player) {
        Store.Log log = Store.get(server).logs.get(player);
        if (log == null || log.epithetRung <= 0) {
            return null;
        }
        return Component.translatable(TRUCO + "epithet." + log.epithetRung, log.name);
    }

    /**
     * The Saturday chat budget (system-livro-caixa-do-servidor): every Saturday sender asks before each line. True
     * while the player has had fewer than 8 Saturday lines this Saturday (and counts this one); false once spent, and
     * the caller files the line for Sunday's Gazette with {@link #fileForGazette}.
     */
    public static boolean saturdayBudget(ServerPlayer player) {
        int saturday = CourtRanks.landDay(player.getServer());
        int[] heard = player.getData(HEARD.get()).clone();
        int count = heard[0] / 16 == saturday ? heard[0] % 16 : 0;
        if (count >= DeedRules.CHAT_BUDGET) {
            return false;
        }
        heard[0] = saturday * 16 + count + 1;
        player.setData(HEARD.get(), heard);
        return true;
    }

    /** Files a Saturday line that went over budget, for the Gazette's "Also told on Saturday" box. */
    public static void fileForGazette(MinecraftServer server, String key, Object... args) {
        Store store = Store.get(server);
        CompoundTag tag = new CompoundTag();
        tag.putString("key", key);
        ListTag list = new ListTag();
        for (Object a : args) {
            list.add(StringTag.valueOf(String.valueOf(a)));
        }
        tag.put("args", list);
        if (store.overflow.size() < DeedRules.DAY_INDEX) {
            store.overflow.add(tag);
            store.setDirty();
        }
    }

    /** The lines filed for Sunday's Gazette, then cleared (the Gazette reads them once). */
    public static List<Component> takeGazetteOverflow(MinecraftServer server) {
        Store store = Store.get(server);
        List<Component> out = new ArrayList<>();
        for (CompoundTag tag : store.overflow) {
            ListTag list = tag.getList("args", Tag.TAG_STRING);
            Object[] args = new Object[list.size()];
            for (int i = 0; i < args.length; i++) {
                args[i] = argument(list.getString(i));
            }
            out.add(Component.translatable(tag.getString("key"), args));
        }
        store.overflow.clear();
        store.setDirty();
        return out;
    }

    /** True when the player already heard their lines since the last Saturday dusk ({@code saturday} its Land's day). */
    public static boolean heard(ServerPlayer player, int saturday) {
        return player.getData(HEARD.get())[1] == saturday + 1;
    }

    public static void markHeard(ServerPlayer player, int saturday) {
        int[] heard = player.getData(HEARD.get()).clone();
        heard[1] = saturday + 1;
        player.setData(HEARD.get(), heard);
    }

    /** Sets the cordel's printed bit on every line of {@code player} up to {@code day} (from 0.30.0). */
    public static void markPrinted(MinecraftServer server, UUID player, long day) {
        Store store = Store.get(server);
        Store.Log log = store.logs.get(player);
        if (log != null) {
            log.lines.forEach(e -> e.printed |= e.day <= day);
            store.setDirty();
        }
    }

    /** Drops every line of one player (the operator's {@code /cc deeds clear}). */
    public static int clear(MinecraftServer server, UUID player) {
        Store store = Store.get(server);
        Store.Log log = store.logs.remove(player);
        store.setDirty();
        return log == null ? 0 : log.lines.size();
    }

    // --- arguments ---

    /**
     * An argument that is itself a lang key, so a tab name or an epithet reads in the listener's language too:
     * {@code "#key"} or {@code "#key|arg|arg"}. A player name never starts with '#'.
     */
    public static String key(String key, Object... args) {
        StringBuilder out = new StringBuilder("#").append(key);
        for (Object arg : args) {
            out.append('|').append(arg);
        }
        return out.toString();
    }

    /** One stored argument as a chat component: plain text, or a lang key written by {@link #key}. */
    public static Object argument(String arg) {
        if (arg == null || !arg.startsWith("#") || arg.length() < 2) {
            return arg == null ? "" : arg;
        }
        String[] parts = arg.substring(1).split("[|]", -1);
        Object[] inner = new Object[parts.length - 1];
        System.arraycopy(parts, 1, inner, 0, inner.length);
        return Component.translatable(parts[0], inner);
    }

    private static Deed view(String name, DeedRules.Entry e) {
        return new Deed(e.player, name, e.key, e.args, e.day, e.rung, e.printed, e.retold, e.witnesses);
    }

    // --- the SavedData ---

    /** {@code cc_deed_log}: each player's lines, their name and epithet rung, and the Gazette's overflow box. */
    public static final class Store extends SavedData {

        static final class Log {
            String name = "";
            int epithetRung;
            final List<DeedRules.Entry> lines = new ArrayList<>();
        }

        record Ref(Log log, DeedRules.Entry entry) {}

        final Map<UUID, Log> logs = new LinkedHashMap<>();
        final List<CompoundTag> overflow = new ArrayList<>();

        public static Store get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(Store::new, Store::load), FILE);
        }

        void add(String name, DeedRules.Entry entry) {
            Log log = logs.computeIfAbsent(entry.player, u -> new Log());
            log.name = name;
            log.lines.add(entry);
            DeedRules.trim(log.lines);
            setDirty();
        }

        /** The lines of one day server-wide, at most 64, in the order written (by day, then by player). */
        List<Ref> dayIndex(long day) {
            List<Ref> out = new ArrayList<>();
            for (Log log : logs.values()) {
                for (DeedRules.Entry e : log.lines) {
                    if (e.day == day && out.size() < DeedRules.DAY_INDEX) {
                        out.add(new Ref(log, e));
                    }
                }
            }
            return out;
        }

        public static Store load(CompoundTag tag, HolderLookup.Provider registries) {
            Store store = new Store();
            ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
            for (int i = 0; i < players.size(); i++) {
                CompoundTag p = players.getCompound(i);
                if (!p.hasUUID("uuid")) {
                    continue;
                }
                UUID uuid = p.getUUID("uuid");
                Log log = new Log();
                log.name = p.getString("name");
                log.epithetRung = p.getInt("epithet");
                ListTag lines = p.getList("lines", Tag.TAG_COMPOUND);
                for (int j = 0; j < lines.size(); j++) {
                    CompoundTag l = lines.getCompound(j);
                    List<String> args = new ArrayList<>();
                    for (Tag a : l.getList("args", Tag.TAG_STRING)) {
                        args.add(a.getAsString());
                    }
                    List<UUID> witnesses = new ArrayList<>();
                    for (Tag w : l.getList("witnesses", Tag.TAG_INT_ARRAY)) {
                        witnesses.add(NbtUtils.loadUUID(w));
                    }
                    DeedRules.Kind kind;
                    try {
                        kind = DeedRules.Kind.valueOf(l.getString("kind"));
                    } catch (IllegalArgumentException e) {
                        kind = DeedRules.Kind.PLAIN;
                    }
                    DeedRules.Entry e = new DeedRules.Entry(uuid, l.getString("key"), args, l.getLong("day"), kind, witnesses);
                    int bits = l.getByte("bits");
                    e.rung = bits & 7;
                    e.printed = (bits & 8) != 0;
                    e.retold = (bits & 16) != 0;
                    e.tellings = l.getShort("told");
                    log.lines.add(e);
                }
                store.logs.put(uuid, log);
            }
            ListTag over = tag.getList("overflow", Tag.TAG_COMPOUND);
            for (int i = 0; i < over.size(); i++) {
                store.overflow.add(over.getCompound(i));
            }
            return store;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag players = new ListTag();
            for (Map.Entry<UUID, Log> entry : logs.entrySet()) {
                CompoundTag p = new CompoundTag();
                p.putUUID("uuid", entry.getKey());
                p.putString("name", entry.getValue().name);
                p.putInt("epithet", entry.getValue().epithetRung);
                ListTag lines = new ListTag();
                for (DeedRules.Entry e : entry.getValue().lines) {
                    CompoundTag l = new CompoundTag();
                    l.putString("key", e.key);
                    ListTag args = new ListTag();
                    e.args.forEach(a -> args.add(StringTag.valueOf(a)));
                    l.put("args", args);
                    l.putLong("day", e.day);
                    l.putString("kind", e.kind.name());
                    l.putByte("bits", (byte) ((e.rung & 7) | (e.printed ? 8 : 0) | (e.retold ? 16 : 0)));
                    l.putShort("told", (short) Math.min(e.tellings, Short.MAX_VALUE));
                    if (!e.witnesses.isEmpty()) {
                        ListTag w = new ListTag();
                        e.witnesses.forEach(u -> w.add(NbtUtils.createUUID(u)));
                        l.put("witnesses", w);
                    }
                    lines.add(l);
                }
                p.put("lines", lines);
                players.add(p);
            }
            tag.put("players", players);
            ListTag over = new ListTag();
            over.addAll(overflow);
            tag.put("overflow", over);
            return tag;
        }
    }
}
