package danger.orespawn.integrations.story.firsthour;

import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.Ranks;
import danger.orespawn.integrations.story.StoryClock;
import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The Priority Ticket's conductor: it rides the shared 20-tick clock and, once a second per open player, asks
 * {@link TicketRules} what to hand over, then hands it over (the actionbar number, the Guide's staple, the scene).
 * Listeners only mark steps used: opening a paper ({@code RightClickItem}), a verb's advancement, the kit casket's
 * break, the first larder egg, the desk visit tag {@code cc_registro_visita} (removed once read).
 *
 * <p>Players from before 0.10.0 (play time on record, no ticket yet) start closed: they get one Summons drop (if they
 * hold none), then their Back-Dated Fall and its Field Odete, under the same gap and scene cap and with no number.</p>
 */
public final class PriorityTicket {

    public static final String VISIT_TAG = "cc_registro_visita";
    /** The ticket's current step for any dialog that reads it (0-10; 11 closed). */
    public static final String STEP_SCORE = "cc_senha";

    private static final String KEY = "orespawn_integrations.senha.";
    private static final String[] STAPLES = {"guia/p01", "guia/p03", "guia/p07", "guia/p08", "guia/close"};
    private static final ResourceLocation REGENCY_ROOT = FirstHourUtil.oi("wonder/2");
    private static final TagKey<Item> LARDER_EGGS =
            TagKey.create(Registries.ITEM, FirstHourUtil.oi("ovos_da_despensa"));
    private static final int ARRIVAL_RADIUS_SQ = 12 * 12;

    private PriorityTicket() {
    }

    static void init() {
        StoryClock.everySecondPerPlayer("first_hour_ticket", PriorityTicket::second);
        NeoForge.EVENT_BUS.addListener(PriorityTicket::onLogin);
        NeoForge.EVENT_BUS.addListener(PriorityTicket::onDamage);
        NeoForge.EVENT_BUS.addListener(PriorityTicket::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(PriorityTicket::onAdvancement);
        NeoForge.EVENT_BUS.addListener(PriorityTicket::onBreak);
        NeoForge.EVENT_BUS.addListener(PriorityTicket::onServerStopped);
    }

    // --- login: new, from before 0.10.0, or returning ---

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer player) {
                start(player);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("first_hour_login", t);
        }
    }

    /** Gives a player their ticket on their first login with this version. */
    public static void start(ServerPlayer player) {
        if (FirstHourData.hasTicket(player)) {
            // A Summons whose flyover ended while its addressee was away is handed over at the next login.
            TicketState s = FirstHourData.ticket(player);
            if (s.handed(4) && IntegrationsConfig.on(IntegrationsConfig.storyPrincesLetter)
                    && FirstHourScenes.letter != null
                    && !FirstHourData.letterSent(player, FirstHourData.LETTER_SUMMONS)) {
                FirstHourData.markLetterSent(player, FirstHourData.LETTER_SUMMONS);
                FirstHourUtil.give(player, RoyalSummons.build(player));
            }
            return;
        }
        boolean veteran = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 0;
        TicketState s;
        if (!veteran) {
            s = TicketState.fresh();
        } else {
            s = TicketState.closed();
            s.setFlag(TicketState.FLAG_VETERAN);
            boolean letter = IntegrationsConfig.on(IntegrationsConfig.storyPrincesLetter)
                    && !RoyalSummons.holdsOwn(player);
            boolean fall = IntegrationsConfig.on(IntegrationsConfig.aliveFirstFall)
                    && IntegrationsConfig.on(IntegrationsConfig.aliveFirstFallExistingPlayers);
            if (letter) {
                s.step = TicketState.VET_LETTER;
            } else if (fall) {
                s.step = TicketState.VET_FALL;
            }
            // A veteran's first wait counts from login, so their scene never lands on top of it.
            s.lastHandover = -TicketRules.GAP + 30 * 20;
        }
        player.setData(FirstHourData.FIRST_HOUR.get(), s);
    }

    // --- the once-a-second pass ---

    private static void second(ServerPlayer player) {
        if (!FirstHourData.hasTicket(player)) {
            return;
        }
        TicketState s = FirstHourData.ticket(player);
        afterRows(player, s);
        if (s.step == TicketState.CLOSED) {
            return;
        }
        TicketRules.tick(s, StoryClock.PERIOD);
        readVisits(player, s);
        if (s.open() && !s.used(7) && s.onlineTicks % 100 == 0 && holdsTag(player, LARDER_EGGS)) {
            TicketRules.markUsed(s, 7);
        }
        int next = TicketRules.decide(s, conditions(player, s));
        if (next == TicketRules.CLOSE) {
            closeQueue(player, s);
        } else if (next != TicketRules.NONE) {
            handOver(player, s, next);
        }
        FirstHourUtil.setScore(player, STEP_SCORE, Math.min(s.step, TicketState.CLOSED));
    }

    private static TicketRules.Conditions conditions(ServerPlayer player, TicketState s) {
        boolean ticketOn = IntegrationsConfig.on(IntegrationsConfig.storyFirstHourTicket);
        boolean scrap = s.open() && !s.handed(3) && FirstHourUtil.holds(player,
                FirstHourUtil.item("orespawn_integrations:ledger_scrap"));
        boolean notebook = s.handed(9) && !s.handed(10)
                && (FirstHourUtil.score(player, EggLabel.GROUP_SCORE, 0) == 8
                || FirstHourUtil.item("ars_nouveau:worn_notebook") == null
                || FirstHourUtil.holds(player, FirstHourUtil.item("ars_nouveau:worn_notebook")));
        boolean letterOn = IntegrationsConfig.on(IntegrationsConfig.storyPrincesLetter)
                && FirstHourScenes.letter != null;
        boolean fallOn = IntegrationsConfig.on(IntegrationsConfig.aliveFirstFall) && FirstHourScenes.fall != null;
        Level level = player.level();
        boolean daylight = level.dimension() == Level.OVERWORLD && level.isDay();
        return new TicketRules.Conditions(ticketOn, FirstHourScenes.originChosen.test(player),
                player.fallDistance > 3.0F, scrap, letterOn, fallOn,
                s.open() && Ranks.atLeast(player, Ranks.SQUIRE), daylight, notebook);
    }

    /** The desk's visit tag, and arrival at the Registry pin, both count as reaching the Registry. */
    private static void readVisits(ServerPlayer player, TicketState s) {
        boolean reached = player.getTags().contains(VISIT_TAG);
        if (reached) {
            player.removeTag(VISIT_TAG);
        }
        if (s.pin == TicketState.PIN_REGISTRY) {
            double dx = player.getX() - s.pinX;
            double dz = player.getZ() - s.pinZ;
            reached |= dx * dx + dz * dz <= ARRIVAL_RADIUS_SQ;
        }
        if (reached) {
            s.setFlag(TicketState.FLAG_REGISTRY_REACHED);
            GoldPin.clearIf(player, TicketState.PIN_REGISTRY);
        }
    }

    // --- handing over ---

    private static void handOver(ServerPlayer player, TicketState s, int step) {
        boolean numbered = IntegrationsConfig.on(IntegrationsConfig.storyFirstHourTicket) && !s.veteranPending();
        switch (step) {
            case 1 -> {
                if (numbered) {
                    FirstHourUtil.award(player, "guia/p01");
                } else {
                    for (String staple : STAPLES) {
                        FirstHourUtil.award(player, staple);
                    }
                }
            }
            case 3 -> FirstHourUtil.award(player, "guia/p03");
            case 4, TicketState.VET_LETTER -> {
                FirstHourScenes.Result r = FirstHourScenes.letter == null ? FirstHourScenes.Result.UNAVAILABLE
                        : FirstHourScenes.letter.start(player, numbered);
                if (r == FirstHourScenes.Result.BUSY) {
                    return;
                }
                if (r == FirstHourScenes.Result.UNAVAILABLE) {
                    numbered = false;
                }
            }
            case 5, TicketState.VET_FALL -> {
                FirstHourScenes.Result r = FirstHourScenes.fall == null ? FirstHourScenes.Result.UNAVAILABLE
                        : FirstHourScenes.fall.start(player, step == TicketState.VET_FALL);
                if (r == FirstHourScenes.Result.BUSY) {
                    return;
                }
                if (r == FirstHourScenes.Result.UNAVAILABLE) {
                    s.setFlag(TicketState.FLAG_FALL_LANDED);
                    TicketRules.markUsed(s, 5);
                    numbered = false;
                }
            }
            case 6, TicketState.VET_ODETE -> {
                BlockPos crate = FallRecord.crateOf(player);
                FirstHourScenes.Result r = FirstHourScenes.odete == null ? FirstHourScenes.Result.UNAVAILABLE
                        : FirstHourScenes.odete.start(player, s.used(5) ? crate : null,
                        step == TicketState.VET_ODETE);
                if (r == FirstHourScenes.Result.BUSY) {
                    return;
                }
                // The Field Odete's actionbar carries P06 herself, once she arrives.
                numbered = false;
            }
            case 7 -> FirstHourUtil.award(player, "guia/p07");
            case 8 -> FirstHourUtil.award(player, "guia/p08");
            case 9 -> {
                ItemStack catalogue = FirstHourUtil.patchouliBookStack(FirstHourUtil.oi("orespawn_guide"));
                if (!catalogue.isEmpty()) {
                    catalogue.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            Component.translatable(KEY + "catalogue_title").withStyle(st -> st.withItalic(false)));
                    FirstHourUtil.give(player, catalogue);
                }
                GoldPin.clearIf(player, TicketState.PIN_REGISTRY);
                boolean outOfTurn = earlierWaiting(s);
                player.sendSystemMessage(Component.translatable(KEY + (outOfTurn ? "out_of_turn" : "last_call")));
            }
            case 10 -> {
                FirstHourUtil.give(player, FirstHourUtil.stack("ars_nouveau:worn_notebook", 1));
                Component line = Component.translatable(KEY + "notebook_fallback");
                danger.orespawn.integrations.story.StoryScheduler.schedule(player.getServer(), 60,
                        "first_hour_notebook_line", () -> FirstHourUtil.actionbar(player, line));
            }
            default -> {
            }
        }
        TicketRules.recordHandover(s, step);
        if (numbered && step >= 1 && step <= TicketState.P10) {
            announce(player, step);
        }
    }

    private static boolean earlierWaiting(TicketState s) {
        for (int p = 2; p < 9; p++) {
            if (!s.handed(p)) {
                return true;
            }
        }
        return false;
    }

    /** The handover's number on the actionbar: "Ticket P05. Priority service: Hatchling." */
    public static void announce(ServerPlayer player, int step) {
        FirstHourUtil.actionbar(player, Component.translatable(KEY + "handover", String.format("%02d", step)));
    }

    private static void closeQueue(ServerPlayer player, TicketState s) {
        for (String staple : STAPLES) {
            FirstHourUtil.award(player, staple);
        }
        if (!s.flag(TicketState.FLAG_CLOSE_SHOWN)) {
            s.setFlag(TicketState.FLAG_CLOSE_SHOWN);
            if (IntegrationsConfig.on(IntegrationsConfig.storyFirstHourTicket)) {
                FirstHourUtil.actionbar(player, Component.translatable(KEY + "closed"));
            }
        }
        TicketRules.close(s);
    }

    /** The "after" rows that fire on their own triggers, open or closed: the first dusk online staples p.4's margins. */
    private static void afterRows(ServerPlayer player, TicketState s) {
        if (s.flag(TicketState.FLAG_DUSK_DONE) || s.flag(TicketState.FLAG_VETERAN)) {
            return;
        }
        Level level = player.level();
        long time = level.getDayTime() % 24000L;
        if (level.dimension() == Level.OVERWORLD && time >= 12000L && time < 13800L) {
            s.setFlag(TicketState.FLAG_DUSK_DONE);
            FirstHourUtil.award(player, "guia/dusk");
        }
    }

    // --- marks from the scenes ---

    /** The First Fall landed for {@code player}: its crate stands at {@code crate}, and the Fall pin goes up. */
    public static void fallLanded(ServerPlayer player, BlockPos crate) {
        TicketState s = FirstHourData.ticket(player);
        s.setFlag(TicketState.FLAG_FALL_LANDED);
        GoldPin.set(player, TicketState.PIN_FALL, crate.getX(), crate.getY(), crate.getZ());
    }

    /** The recipient opened their crate: P05 is used and the Fall pin comes down. */
    public static void crateOpened(ServerPlayer player) {
        TicketState s = FirstHourData.ticket(player);
        TicketRules.markUsed(s, 5);
        GoldPin.clearIf(player, TicketState.PIN_FALL);
    }

    // --- listeners that mark steps used ---

    private static void onDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getNewDamage() > 0
                && FirstHourData.hasTicket(player)) {
            TicketState s = FirstHourData.ticket(player);
            s.lastDamage = s.onlineTicks;
        }
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player) || !FirstHourData.hasTicket(player)) {
                return;
            }
            TicketState s = FirstHourData.ticket(player);
            ItemStack stack = event.getItemStack();
            if (stack.is(FirstHourUtil.item("orespawn_integrations:hatchling_shell"))) {
                TicketRules.markUsed(s, 1);
            } else if (RoyalSummons.isSummonsFor(stack, player)) {
                summonsOpened(player, s);
            } else if (stack.is(FirstHourUtil.item("ars_nouveau:worn_notebook"))) {
                TicketRules.markUsed(s, 10);
            } else {
                ResourceLocation book = FirstHourUtil.patchouliBook(stack);
                if (FirstHourUtil.oi("guia_de_campo").equals(book)) {
                    TicketRules.markUsed(s, 8);
                    if (!s.flag(TicketState.FLAG_GUIDE_OPENED)) {
                        s.setFlag(TicketState.FLAG_GUIDE_OPENED);
                        FirstHourUtil.award(player, "livro/why_is_nothing_where_it_belongs");
                    }
                } else if (FirstHourUtil.oi("orespawn_guide").equals(book)) {
                    TicketRules.markUsed(s, 9);
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("first_hour_use", t);
        }
    }

    /** Opening one's own Summons: P04's use, the Regency root and R4 once, and the Regency tab selected. */
    static void summonsOpened(ServerPlayer player, TicketState s) {
        TicketRules.markUsed(s, 4);
        if (s.flag(TicketState.FLAG_SUMMONS_OPENED)) {
            return;
        }
        s.setFlag(TicketState.FLAG_SUMMONS_OPENED);
        FirstHourUtil.award(player, REGENCY_ROOT);
        FirstHourUtil.award(player, "livro/where_are_the_king_and_queen");
        FirstHourUtil.selectTab(player, REGENCY_ROOT);
    }

    private static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player) || !FirstHourData.hasTicket(player)) {
                return;
            }
            ResourceLocation id = event.getAdvancement().id();
            if (!danger.orespawn.integrations.OreSpawnIntegrations.MODID.equals(id.getNamespace())) {
                return;
            }
            TicketState s = FirstHourData.ticket(player);
            String path = id.getPath();
            if (path.equals("correcoes/primeira_emenda")) {
                TicketRules.markUsed(s, 3);
            } else if (path.startsWith("avistamentos/")) {
                TicketRules.markUsed(s, 6);
                if (path.contains("anthill") || path.contains("formig") || path.contains("butterfl")
                        || path.contains("borboleta") || path.contains("henge")) {
                    s.setFlag(TicketState.FLAG_EARLY_STAMP);
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("first_hour_advancement", t);
        }
    }

    /**
     * A Royal Casket (the ly Lucky Block: a hay block under its tagged item display) broken by its opener is P02's
     * use: the casket's own function reads the block, so this only notices it.
     */
    private static void onBreak(BlockEvent.BreakEvent event) {
        try {
            if (!(event.getPlayer() instanceof ServerPlayer player) || !event.getState().is(Blocks.HAY_BLOCK)
                    || !FirstHourData.hasTicket(player) || !(event.getLevel() instanceof ServerLevel level)) {
                return;
            }
            TicketState s = FirstHourData.ticket(player);
            if (!s.open() || s.used(2)) {
                return;
            }
            BlockPos pos = event.getPos();
            AABB above = new AABB(pos.getX(), pos.getY() + 0.5, pos.getZ(),
                    pos.getX() + 1, pos.getY() + 2.5, pos.getZ() + 1);
            boolean casket = !level.getEntitiesOfClass(Display.ItemDisplay.class, above,
                    d -> d.getTags().contains("lucky_blocks.item_display")).isEmpty();
            if (casket) {
                TicketRules.markUsed(s, 2);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("first_hour_casket", t);
        }
    }

    private static boolean holdsTag(ServerPlayer player, TagKey<Item> tag) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        FirstHourScenes.CAP.reset();
    }
}
