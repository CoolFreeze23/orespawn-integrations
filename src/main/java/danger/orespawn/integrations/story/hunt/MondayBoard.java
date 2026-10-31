package danger.orespawn.integrations.story.hunt;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryClock;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.court.CourtRanks;
import danger.orespawn.integrations.story.deed.DeedLog;
import danger.orespawn.integrations.story.firsthour.EggLabel;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import danger.orespawn.integrations.story.registry.ScrapCaps;
import danger.orespawn.integrations.story.week.LandsWeek;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Captain Iracema's Monday board (system-boss-ladder "Iracema reads the ladder", npc-iracema, B15): three bounties a
 * Land week per player, one from their row of the Wall, one from the row below (easy money), one from the row above
 * (pays triple), drawn from {@code data/orespawn_integrations/hunt/bounties.json}. The board opens wherever her
 * dialog does: the Lodge, any Monster Hunter tent (a sneak-use of its workbench) or a Registry desk. The dialog's
 * opening command tags the player {@code cc_quadro}; the shared clock reads the tag (one check a player a second)
 * and posts the board in chat, so the bounties read in each player's own language.
 *
 * <p>A bounty is done when its boss dies with the player credited or within 32 blocks, and paid at the next board:
 * emeralds for the two lower rows (and a Ledger Scrap for your own), Reais for the row above. The first board taken
 * grants the Royal Hunt tab's root and R5; on Sunday she takes no bounties.</p>
 */
public final class MondayBoard {

    public static final String BOARD_TAG = "cc_quadro";
    public static final String GREETING_TAG = "cc_iracema_ola";
    static final String KEY = "orespawn_integrations.iracema.";
    static final String HUNT = "orespawn_integrations.hunt.";
    private static final ResourceLocation POOL = FirstHourUtil.oi("hunt/bounties.json");

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OreSpawnIntegrations.MODID);

    /**
     * {@code cc_caca}: this Land week's bounties, {@code {week:L, targets:[...], rows:[I;...], done:b, paid:b}}, and the
     * once-per-player marks {@code board:1b} (R5), {@code greeted:1b} (R2), {@code unbowed:1b} (R17).
     */
    public static final Supplier<AttachmentType<CompoundTag>> STATE = TYPES.register("cc_caca",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

    /** The pool, read from the server's data once per reload. */
    private static Pool pool;
    private static Object poolSource;

    record Pool(Map<Integer, List<String>> rows, Map<Integer, int[]> pay) {}

    private MondayBoard() {
    }

    static void init(net.neoforged.bus.api.IEventBus modBus) {
        TYPES.register(modBus);
        StoryClock.everySecondPerPlayer("monday_board", MondayBoard::second);
    }

    private static void second(ServerPlayer player) {
        if (player.getTags().contains(BOARD_TAG)) {
            player.removeTag(BOARD_TAG);
            open(player);
        }
        if (player.getTags().contains(GREETING_TAG)) {
            player.removeTag(GREETING_TAG);
            IracemaLines.greet(player);
        }
    }

    // --- the pool ---

    static Pool pool(MinecraftServer server) {
        Object source = server.getResourceManager();
        if (pool != null && poolSource == source) {
            return pool;
        }
        Map<Integer, List<String>> rows = new HashMap<>();
        Map<Integer, int[]> pay = new HashMap<>();
        try {
            Optional<Resource> res = server.getResourceManager().getResource(
                    ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "hunt/bounties.json"));
            if (res.isPresent()) {
                try (InputStreamReader in = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(in).getAsJsonObject();
                    JsonObject p = json.getAsJsonObject("pool");
                    for (String row : p.keySet()) {
                        List<String> ids = new ArrayList<>();
                        for (JsonElement e : p.getAsJsonArray(row)) {
                            String id = e.getAsString();
                            // Only bosses of mods present: an absent mod's id never goes on a board.
                            if (BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(id))) {
                                ids.add(id);
                            }
                        }
                        rows.put(Integer.parseInt(row), ids);
                    }
                    JsonObject w = json.getAsJsonObject("pay");
                    for (String row : w.keySet()) {
                        JsonObject r = w.getAsJsonObject(row);
                        pay.put(Integer.parseInt(row), new int[]{r.get("emeralds").getAsInt(), r.get("reais").getAsInt()});
                    }
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("monday_board_pool", t);
        }
        pool = new Pool(rows, pay);
        poolSource = source;
        return pool;
    }

    // --- the board ---

    /** Posts the board to {@code player}: pays what is done, then this week's three bounties. */
    public static void open(ServerPlayer player) {
        try {
            if (!IntegrationsConfig.on(IntegrationsConfig.storyRoyalHunt)) {
                return;
            }
            MinecraftServer server = player.getServer();
            if (LandsWeek.today(server) == LandsWeek.SUNDAY) {
                say(player, Component.translatable(KEY + "sunday"));
                return;
            }
            if (player.getTags().contains(EggLabel.ORIGIN_TAG + "lobisomem") && fullMoonNight(player.level())) {
                say(player, Component.translatable(KEY + "full_moon"));
            }
            CompoundTag state = state(player);
            payDone(player, state);
            int[] rows = tagInts(state, "rows");
            ListTag targets = state.getList("targets", Tag.TAG_STRING);
            Pool p = pool(server);
            for (int slot = 0; slot < targets.size(); slot++) {
                String target = targets.getString(slot);
                int row = rows[slot];
                boolean done = (state.getByte("done") & (1 << slot)) != 0;
                say(player, Component.translatable(KEY + "board.bounty", row, bossName(target),
                        payText(p, row, slot), Component.translatable(KEY + (done ? "board.done" : "board.open"))));
                if (!done) {
                    boolean unbowed = target.startsWith("cataclysm:") || target.startsWith("mowziesmobs:");
                    if (unbowed && !state.getBoolean("unbowed")) {
                        state.putBoolean("unbowed", true);
                        say(player, Component.translatable(KEY + "unbowed"));
                        FirstHourUtil.award(player, "livro/r17");
                    } else {
                        say(player, Component.translatable(KEY + switch (slot) {
                            case 1 -> "row_below";
                            case 2 -> "row_above";
                            default -> "your_row";
                        }));
                    }
                }
            }
            if (!state.getBoolean("board")) {
                state.putBoolean("board", true);
                say(player, Component.translatable(KEY + "r5"));
                FirstHourUtil.award(player, "livro/r5");
            }
            if (!targets.isEmpty()) {
                FirstHourUtil.award(player, "hunt/root");
            }
            player.setData(STATE.get(), state);
        } catch (Throwable t) {
            StoryCore.logOnce("monday_board_open", t);
        }
    }

    /** This week's state, rolled the first time the player reads the board in a new Land week. */
    public static CompoundTag state(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        CompoundTag state = player.getData(STATE.get()).copy();
        long week = DeedLog.weekStart(server);
        if (state.contains("week") && state.getLong("week") == week) {
            return state;
        }
        // An unpaid bounty from last week is paid now, before the new week's are drawn.
        payDone(player, state);
        int row = WallRows.yourRow(killedMask(player));
        int[] rows = WallRows.mondayRows(row);
        Pool p = pool(server);
        Random random = new Random(server.overworld().getSeed() ^ player.getUUID().getLeastSignificantBits() ^ (week * 31L));
        ListTag targets = new ListTag();
        List<String> used = new ArrayList<>();
        int[] kept = new int[3];
        int n = 0;
        for (int slot = 0; slot < 3; slot++) {
            List<String> ids = new ArrayList<>(p.rows().getOrDefault(rows[slot], List.of()));
            ids.removeAll(used);
            if (ids.isEmpty()) {
                continue;
            }
            String pick = ids.get(random.nextInt(ids.size()));
            used.add(pick);
            targets.add(StringTag.valueOf(pick));
            kept[n++] = rows[slot];
        }
        CompoundTag fresh = new CompoundTag();
        for (String k : new String[]{"board", "greeted", "unbowed", "knight"}) {
            if (state.getBoolean(k)) {
                fresh.putBoolean(k, true);
            }
        }
        fresh.putLong("week", week);
        fresh.put("targets", targets);
        fresh.putIntArray("rows", java.util.Arrays.copyOf(kept, n));
        fresh.putByte("done", (byte) 0);
        fresh.putByte("paid", (byte) 0);
        return fresh;
    }

    /** Rows with a kill, as a mask: the row advancements a player holds (the Wall grants one per row). */
    static int killedMask(ServerPlayer player) {
        int mask = 0;
        for (int row = 1; row <= WallRows.ROWS; row++) {
            if (CourtRanks.has(player, OreSpawnIntegrations.MODID + ":hunt/row_" + row)) {
                mask |= 1 << (row - 1);
            }
        }
        return mask;
    }

    /** A Wall kill: each player in the party with that boss on this week's board has it done. */
    static void onWallKill(List<ServerPlayer> party, WallOfTheWanted.Hit hit) {
        for (ServerPlayer player : party) {
            CompoundTag state = player.getData(STATE.get());
            if (!state.contains("week") || state.getLong("week") != DeedLog.weekStart(player.getServer())) {
                continue;
            }
            ListTag targets = state.getList("targets", Tag.TAG_STRING);
            for (int slot = 0; slot < targets.size(); slot++) {
                byte done = state.getByte("done");
                if (targets.getString(slot).equals(hit.key()) && (done & (1 << slot)) == 0) {
                    CompoundTag next = state.copy();
                    next.putByte("done", (byte) (done | (1 << slot)));
                    player.setData(STATE.get(), next);
                    say(player, Component.translatable(KEY + "board.marked", bossName(hit.key())));
                    break;
                }
            }
        }
    }

    /** Pays every done, unpaid bounty in {@code state} (and marks it paid). */
    private static void payDone(ServerPlayer player, CompoundTag state) {
        if (!state.contains("targets")) {
            return;
        }
        Pool p = pool(player.getServer());
        int[] rows = tagInts(state, "rows");
        ListTag targets = state.getList("targets", Tag.TAG_STRING);
        byte done = state.getByte("done");
        byte paid = state.getByte("paid");
        for (int slot = 0; slot < targets.size() && slot < rows.length; slot++) {
            int bit = 1 << slot;
            if ((done & bit) == 0 || (paid & bit) != 0) {
                continue;
            }
            paid |= (byte) bit;
            int[] pay = p.pay().getOrDefault(rows[slot], new int[]{2, 10});
            if (slot == 2) {
                giveReais(player, pay[1] * WallRows.multiplier(slot));
            } else {
                FirstHourUtil.give(player, new ItemStack(Items.EMERALD, pay[0]));
                if (slot == 0) {
                    ScrapCaps.give(player, 1);
                }
            }
            say(player, Component.translatable(KEY + "board.paid", bossName(targets.getString(slot)), payText(p, rows[slot], slot)));
        }
        state.putByte("paid", paid);
    }

    static Component payText(Pool p, int row, int slot) {
        int[] pay = p.pay().getOrDefault(row, new int[]{2, 10});
        if (slot == 2) {
            return Component.translatable(KEY + "pay.reais", pay[1] * WallRows.multiplier(slot));
        }
        return Component.translatable(KEY + (slot == 0 ? "pay.emeralds_scrap" : "pay.emeralds"), pay[0]);
    }

    /** Pays {@code amount} Reais in Brasil e Coisas notes, largest first (the R$1 coin for the change). */
    static void giveReais(ServerPlayer player, int amount) {
        int[] values = {200, 100, 50, 20, 10, 5, 2, 1};
        String[] ids = {"nota_de_200", "nota_de_100", "nota_de_50", "nota_de_20", "nota_de_10", "nota_de_5", "nota_de_2", "moeda_1"};
        int left = amount;
        for (int i = 0; i < values.length && left > 0; i++) {
            int count = left / values[i];
            if (count > 0) {
                ItemStack notes = FirstHourUtil.stack("brasilcoisas:" + ids[i], count);
                if (!notes.isEmpty()) {
                    FirstHourUtil.give(player, notes);
                    left -= count * values[i];
                }
            }
        }
        if (left > 0) {
            // No Brasil e Coisas: the Hunt pays the rest in emeralds, one per R$10.
            FirstHourUtil.give(player, new ItemStack(Items.EMERALD, Math.max(1, left / 10)));
        }
    }

    static Component bossName(String key) {
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            return type.getDescription();
        }
        return Component.translatable(HUNT + "boss." + key.replace(':', '.').replace('/', '.').replace('#', '.'));
    }

    static boolean fullMoonNight(Level level) {
        long time = level.getDayTime() % 24000L;
        return level.getMoonPhase() == 0 && time >= 13000L && time < 23000L;
    }

    static void say(ServerPlayer player, Component line) {
        player.sendSystemMessage(Component.translatable(KEY + "speaker", line));
    }

    private static int[] tagInts(CompoundTag tag, String key) {
        return tag.getIntArray(key);
    }
}
