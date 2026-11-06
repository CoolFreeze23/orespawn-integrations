package danger.orespawn.integrations.story.court;

import com.mojang.serialization.Codec;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryClock;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.StoryScheduler;
import danger.orespawn.integrations.story.firsthour.FirstHourScenes;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Court ranks (system-court-ranks, 0.10.1). {@code cc_court} holds a player's rank, 0 Hatchling to 4 Regent, and
 * {@link #raise} is the one shared rank function: it grants every skipped rank's reward in order, in one ceremony,
 * files one Pretender claim for the top title and selects the Regency tab.
 *
 * <p>Two handlers and no tick of their own: the {@code AdvancementEarnEvent} handler turns Apotheosis's tiers, the
 * Two Provinces node and Journey's End into the rank advancements' criteria ({@code court/rank_0} to
 * {@code court/rank_4}, each "either one" of two), and each rank advancement into a rise; the login handler does the
 * backfill once (Apotheosis tiers and the seven shipped entry advancements, dated before today), stamps the province
 * the player stands in, and completes a waiting Squire. The province stamps themselves are data: nine hidden
 * advancements on {@code changed_dimension} whose rewards call {@code court/carimbo}, which reads the Land's day
 * ({@code #dia cc_terra}).</p>
 */
public final class CourtRanks {

    public static final String COURT = "cc_court";
    public static final String STAMP = "cc_carimbo";
    public static final String WAITING = "cc_carimbo_espera";
    public static final String LAND = "cc_terra";

    private static final String KEY = "orespawn_integrations.court.";
    private static final String TAG_FIRST = "cc_carimbo_1de2";
    private static final String TAG_SAME_DAY = "cc_carimbo_um_por_dia";
    private static final ResourceLocation REGENCY_ROOT = FirstHourUtil.oi("wonder/2");

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OreSpawnIntegrations.MODID);

    /**
     * {@code cc_corte}: three ints per player. [0] flags (bit 0 backfill done), [1] the rank of the Pretender claim
     * waiting to be filed (0 none), [2] the Land's day it was earned. The claim itself is read from 0.10.2
     * (system-who-comes-for-it row 9).
     */
    public static final Supplier<AttachmentType<int[]>> CORTE = TYPES.register("cc_corte",
            () -> AttachmentType.builder(() -> new int[3])
                    .serialize(Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of)).copyOnDeath().build());

    private static final int FLAG_BACKFILLED = 1;

    /** Players this run already checked for the Awakening (rank 0); a transient cache over one advancement read. */
    private static final Set<java.util.UUID> AWAKE = new LinkedHashSet<>();

    private CourtRanks() {
    }

    public static void init(IEventBus modBus) {
        TYPES.register(modBus);
        NeoForge.EVENT_BUS.addListener(CourtRanks::onServerStarted);
        NeoForge.EVENT_BUS.addListener(CourtRanks::onLogin);
        NeoForge.EVENT_BUS.addListener(CourtRanks::onAdvancement);
        NeoForge.EVENT_BUS.addListener(CourtRanks::onChangedDimension);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> AWAKE.remove(e.getEntity().getUUID()));
        StoryClock.everySecondPerPlayer("court_awakening", CourtRanks::awakening);
    }

    // --- scores ---

    private static void onServerStarted(ServerStartedEvent event) {
        Scoreboard board = event.getServer().getScoreboard();
        for (String name : new String[]{COURT, STAMP, WAITING, LAND}) {
            FirstHourUtil.objective(board, name);
        }
    }

    /** The player's rank, or -1 when {@code cc_court} is unset (never backfilled). */
    public static int rank(ServerPlayer player) {
        return FirstHourUtil.score(player, COURT, -1);
    }

    /** The Land's day ({@code #dia cc_terra}, kept by the Land's Week); the overworld's day index before its first dawn. */
    public static int landDay(MinecraftServer server) {
        Scoreboard board = server.getScoreboard();
        Objective o = board.getObjective(LAND);
        if (o != null) {
            ReadOnlyScoreInfo info = board.getPlayerScoreInfo(ScoreHolder.forNameOnly("#dia"), o);
            if (info != null) {
                return info.value();
            }
        }
        return (int) (server.overworld().getDayTime() / 24000L);
    }

    private static Integer stampDay(ServerPlayer player) {
        int v = FirstHourUtil.score(player, STAMP, Integer.MIN_VALUE);
        return v == Integer.MIN_VALUE ? null : v;
    }

    // --- the shared rank function ---

    /**
     * Raises {@code player} to {@code target}: every rank between their current one and the target gets its
     * advancement and its reward, lowest first, in one ceremony; one Pretender claim is filed for the top title, and
     * the Regency tab is selected. Nothing happens when the player already holds {@code target}.
     */
    public static void raise(ServerPlayer player, int target) {
        int current = rank(player);
        int[] steps = RankRules.steps(current, target);
        if (steps.length == 0) {
            return;
        }
        // The score moves first, so the rank advancements granted below find the rise already done.
        FirstHourUtil.setScore(player, COURT, target);
        if (RankRules.skips(current, target)) {
            player.sendSystemMessage(Component.translatable(KEY + "skipped"));
        }
        for (int rank : steps) {
            FirstHourUtil.award(player, "court/rank_" + rank);
            reward(player, rank);
        }
        if (target >= RankRules.SQUIRE) {
            FirstHourUtil.setScore(player, WAITING, 0);
            int[] corte = player.getData(CORTE.get()).clone();
            corte[1] = Math.max(corte[1], target);
            corte[2] = landDay(player.getServer());
            player.setData(CORTE.get(), corte);
        }
        FirstHourUtil.selectTab(player, REGENCY_ROOT);
    }

    /**
     * One rank's own reward: the Court Card of level rank + 1 (item-treasury-locks-and-files), named in both languages
     * and carrying its owner. The Knight's averbação and page, the Fidalgo's charter and the Regent's fare egg arrive
     * with their own cards (0.10.2, 0.10.3).
     */
    private static void reward(ServerPlayer player, int rank) {
        ItemStack card = FirstHourUtil.stack("securitycraft:keycard_lv" + RankRules.keycardLevel(rank), 1);
        if (card.isEmpty()) {
            return;
        }
        card.set(DataComponents.CUSTOM_NAME, Component.translatable(KEY + "card",
                Component.translatable(KEY + "rank." + rank)).withStyle(s -> s.withItalic(false)));
        CompoundTag tag = new CompoundTag();
        tag.putUUID("cc_dono", player.getUUID());
        tag.putString("cc_dono_nome", player.getGameProfile().getName());
        tag.putInt("cc_patente", rank);
        card.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        FirstHourUtil.give(player, card);
    }

    // --- the Awakening: rank 0 once the origin is chosen ---

    private static void awakening(ServerPlayer player) {
        if (AWAKE.contains(player.getUUID())) {
            return;
        }
        if (rank(player) >= 0) {
            AWAKE.add(player.getUUID());
            return;
        }
        if (FirstHourScenes.originChosen.test(player)) {
            AWAKE.add(player.getUUID());
            FirstHourUtil.award(player, "court/rank_0");
            raise(player, RankRules.HATCHLING);
        }
    }

    // --- login: backfill, the province underfoot, a waiting Squire ---

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer player) {
                login(player);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("court_login", t);
        }
    }

    public static void login(ServerPlayer player) {
        int[] corte = player.getData(CORTE.get());
        if ((corte[0] & FLAG_BACKFILLED) == 0) {
            int[] next = corte.clone();
            next[0] |= FLAG_BACKFILLED;
            player.setData(CORTE.get(), next);
            backfill(player);
        }
        String here = RankRules.province(player.level().dimension().location().toString());
        if (here != null) {
            FirstHourUtil.award(player, "court/provincia/" + here);
        }
        checkWaiting(player, here != null);
    }

    /**
     * The first login after 0.10.1: the highest Apotheosis tier held becomes its rank, and each shipped entry
     * advancement held stamps its province, the first dated before today, so two old visits make a Squire at once.
     */
    private static void backfill(ServerPlayer player) {
        int tier = 0;
        for (int r = RankRules.SQUIRE; r <= RankRules.REGENT; r++) {
            if (has(player, RankRules.TIERS.get(r))) {
                tier = r;
            }
        }
        if (tier > 0) {
            awardCriterion(player, "court/rank_" + tier, "apotheosis");
            raise(player, tier);
        }
        Set<String> visited = new LinkedHashSet<>();
        RankRules.ENTRY_ADVANCEMENTS.forEach((id, province) -> {
            if (has(player, id)) {
                visited.add(province);
            }
        });
        int today = landDay(player.getServer());
        for (String province : visited) {
            if (has(player, OreSpawnIntegrations.MODID + ":court/provincia/" + province)) {
                continue;
            }
            boolean first = stampDay(player) == null;
            FirstHourUtil.award(player, "court/provincia/" + province);
            if (first && stampDay(player) != null) {
                // Dated before today, and no subtitle: the visit happened long ago.
                FirstHourUtil.setScore(player, STAMP, today - 1);
                player.removeTag(TAG_FIRST);
            }
        }
        player.removeTag(TAG_SAME_DAY);
    }

    /** A player who waits (a second province on the stamp's day) is Squire in any province on a later Land's day. */
    private static void checkWaiting(ServerPlayer player, boolean inProvince) {
        boolean waiting = FirstHourUtil.score(player, WAITING, 0) == 1;
        if (RankRules.waitingCompletes(waiting, stampDay(player), landDay(player.getServer()), inProvince)) {
            FirstHourUtil.award(player, "court/duas_provincias");
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer player) {
                checkWaiting(player, RankRules.province(event.getTo().location().toString()) != null);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("court_dimension", t);
        }
    }

    // --- the AdvancementEarnEvent handler ---

    private static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            ResourceLocation id = event.getAdvancement().id();
            String full = id.toString();
            int tier = RankRules.rankOfTier(full);
            boolean on = IntegrationsConfig.on(IntegrationsConfig.storyCourtRanks);
            if (tier > 0) {
                awardCriterion(player, "court/rank_" + tier, "apotheosis");
            } else if (full.equals(RankRules.JOURNEYS_END)) {
                if (on) {
                    awardCriterion(player, "court/rank_" + RankRules.FIDALGO, "deed");
                }
            } else if (OreSpawnIntegrations.MODID.equals(id.getNamespace())) {
                String path = id.getPath();
                if (path.equals("court/duas_provincias")) {
                    if (on) {
                        awardCriterion(player, "court/rank_" + RankRules.SQUIRE, "deed");
                    }
                } else if (path.startsWith("court/rank_")) {
                    raise(player, Integer.parseInt(path.substring("court/rank_".length())));
                } else if (path.startsWith("court/provincia/")) {
                    stampSubtitle(player);
                }
                TabFull.check(player, event.getAdvancement());
            }
        } catch (Throwable t) {
            StoryCore.logOnce("court_advancement", t);
        }
    }

    /**
     * The stamp's subtitle under the region name, pt-BR first in the card: "1 of 2 toward Squire" at the first stamp,
     * "One stamp a day" the first time a second province waits. court/carimbo leaves a tag saying which; the line is
     * sent half a second later, after the region's own title has gone up, so it lands as that title's subtitle.
     */
    private static void stampSubtitle(ServerPlayer player) {
        String key = null;
        if (player.getTags().contains(TAG_FIRST)) {
            key = KEY + "stamp.first";
        } else if (player.getTags().contains(TAG_SAME_DAY)) {
            key = KEY + "stamp.one_a_day";
        }
        player.removeTag(TAG_FIRST);
        player.removeTag(TAG_SAME_DAY);
        if (key == null || rank(player) >= RankRules.SQUIRE) {
            return;
        }
        Component line = Component.translatable(key);
        StoryScheduler.schedule(player.getServer(), 10, "court_stamp_subtitle", () -> {
            if (!player.hasDisconnected()) {
                player.connection.send(new ClientboundSetSubtitleTextPacket(line));
            }
        });
    }

    // --- helpers ---

    public static boolean has(ServerPlayer player, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        MinecraftServer server = player.getServer();
        AdvancementHolder holder = rl == null || server == null ? null : server.getAdvancements().get(rl);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** Grants one named criterion of an OI advancement (the rank nodes' "either one"). */
    public static void awardCriterion(ServerPlayer player, String oiPath, String criterion) {
        MinecraftServer server = player.getServer();
        AdvancementHolder holder = server == null ? null : server.getAdvancements().get(FirstHourUtil.oi(oiPath));
        if (holder == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (!progress.isDone()) {
            player.getAdvancements().award(holder, criterion);
        }
    }
}
