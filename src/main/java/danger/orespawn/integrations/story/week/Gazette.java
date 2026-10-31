package danger.orespawn.integrations.story.week;

import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.Ranks;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.deed.DeedLog;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Interregnum Gazette (item-diario-oficial): a newspaper in each subscriber's mailbox every dawn, built last in
 * the Land's dawn job so it prints what the day's hooks decided. Three pages of translate components with their
 * names and numbers baked in as plain-text arguments, so each reader's own client reads it in their language: page 1
 * the court's Diário Oficial (masthead, yesterday's biggest deed, Today's Misfile, the posted court lines), page 2
 * the Land's Fuxico (two deeds retold, the Almanac's forecast, Lindalva's Sunday notice, what Saturday couldn't fit),
 * page 3 the Classifieds (the posted ads, the joke ad, what was held over). Each page keeps to 120 words in either
 * language ({@link GazetteLayout}); a line that doesn't fit waits a dawn.
 *
 * <p><b>The Newcomer's Edition</b> (system-senha-da-primeira-hora's first week): a subscriber before their eighth dawn
 * and below Squire gets one page, the masthead and today's fixture; Sunday's names the Jade marker, and the first
 * Saturday adds the four-purse box.</p>
 *
 * <p><b>Delivery</b>, to subscribers online at any time in the last 3 game days: the first Refurbished mailbox they
 * own, else their registered P&Z mailbox, else the rack at any Odete desk ("Any post for me?"). Mail for an offline
 * friend waits in the box's queue until its chunk loads. Never two unread: an unread copy still in the box is
 * replaced by today's.</p>
 */
public final class Gazette {

    static final String KEY = "orespawn_integrations.gazette.";
    static final ResourceLocation QUERO_QUERO = ResourceLocation.fromNamespaceAndPath("brasilcoisas", "queroquero");
    /** Delivered to subscribers seen in the last three game days. */
    public static final int RECENT_DAYS = 3;
    private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?s");

    /** Where a copy went: a box (its level and position), or null for the rack. */
    public record Delivery(ResourceKey<Level> dim, BlockPos pos) {}

    /** A mail route. The Refurbished and P&Z routes register themselves when their mod is present. */
    public interface Mailman {
        /** Delivers {@code copy} to {@code player}'s box, or returns null when they have none on this route. */
        Delivery deliver(MinecraftServer server, UUID player, ItemStack copy);
    }

    static Mailman refurbished;
    static Mailman plantz;

    private Gazette() {
    }

    static void init() {
        LandsWeek.onDawnLast("gazette", Gazette::dawn);
        NeoForge.EVENT_BUS.addListener(Gazette::onLogin);
        WeekCommands.add(root -> root.then(GazetteCommand.branch()));
    }

    // --- the API other systems post through ---

    /**
     * Posts a line for the next dawn's paper on {@code page} (1 the Diário Oficial, 2 the Fuxico, 3 the Classifieds),
     * after the page's own slots, oldest first. Arguments are baked in as plain text; a translate key goes in as
     * {@code DeedLog.key(...)}-style {@link #keyArg}. A line that doesn't fit waits one dawn and is dropped after three.
     */
    public static void post(MinecraftServer server, int page, String key, Object... args) {
        GazetteStore store = GazetteStore.get(server);
        List<String> plain = new ArrayList<>();
        for (Object a : args) {
            plain.add(a instanceof Component c ? encode(c) : String.valueOf(a));
        }
        store.posted.add(new GazetteStore.Posted(Math.max(1, Math.min(3, page)), key, plain, 0));
        store.setDirty();
    }

    /** An argument that is itself a translate key, read in the reader's language. */
    public static String keyArg(String key) {
        return "§k:" + key;
    }

    private static String encode(Component c) {
        if (c.getContents() instanceof TranslatableContents t && t.getArgs().length == 0 && c.getSiblings().isEmpty()) {
            return keyArg(t.getKey());
        }
        return c.getString();
    }

    private static Object decode(String arg) {
        return arg.startsWith("§k:") ? Component.translatable(arg.substring(3)) : arg;
    }

    // --- subscriptions ---

    public static boolean subscribed(MinecraftServer server, UUID player) {
        return GazetteStore.get(server).subs.containsKey(player);
    }

    static void subscribe(ServerPlayer player) {
        GazetteStore store = GazetteStore.get(player.getServer());
        GazetteStore.Sub sub = store.subs.computeIfAbsent(player.getUUID(), id -> new GazetteStore.Sub());
        refresh(player, sub);
        store.setDirty();
    }

    static boolean cancel(ServerPlayer player) {
        GazetteStore store = GazetteStore.get(player.getServer());
        boolean had = store.subs.remove(player.getUUID()) != null;
        store.setDirty();
        return had;
    }

    /** The desk rack's copies for a player with no mailbox, all of them, handed over now. */
    static int collect(ServerPlayer player) {
        GazetteStore store = GazetteStore.get(player.getServer());
        List<ItemStack> copies = store.rack.remove(player.getUUID());
        if (copies == null) {
            return 0;
        }
        store.setDirty();
        for (ItemStack copy : copies) {
            FirstHourUtil.give(player, copy);
        }
        return copies.size();
    }

    private static void refresh(ServerPlayer player, GazetteStore.Sub sub) {
        WeekState s = WeekData.of(player);
        sub.name = player.getGameProfile().getName();
        sub.lastOnline = (int) LandsWeek.landDay(player.getServer());
        sub.weekStart = s.weekStart;
        sub.squire = Ranks.atLeast(player, Ranks.SQUIRE);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer player) {
                GazetteStore store = GazetteStore.get(player.getServer());
                GazetteStore.Sub sub = store.subs.get(player.getUUID());
                if (sub != null) {
                    refresh(player, sub);
                    store.setDirty();
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("gazette.login", t);
        }
    }

    // --- the dawn ---

    static void dawn(MinecraftServer server) {
        if (!IntegrationsConfig.on(IntegrationsConfig.aliveGazette)) {
            return;
        }
        GazetteStore store = GazetteStore.get(server);
        long day = LandsWeek.landDay(server);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            GazetteStore.Sub sub = store.subs.get(online.getUUID());
            if (sub != null) {
                refresh(online, sub);
            }
        }
        Edition edition = Edition.build(server, day, store);
        for (var entry : store.subs.entrySet()) {
            GazetteStore.Sub sub = entry.getValue();
            if (sub.lastOnline < day - RECENT_DAYS || sub.lastCopy == day) {
                continue;
            }
            boolean newcomer = WeekRules.inFirstWeek(sub.weekStart, day) && !sub.squire;
            ItemStack copy = newcomer ? edition.newcomer(day, sub.weekStart) : edition.full();
            deliver(server, entry.getKey(), copy, store);
            sub.lastCopy = (int) day;
        }
        edition.settlePosted(store);
        store.setDirty();
    }

    static void deliver(MinecraftServer server, UUID player, ItemStack copy, GazetteStore store) {
        Delivery where = null;
        if (refurbished != null) {
            where = refurbished.deliver(server, player, copy.copy());
        }
        if (where == null && plantz != null) {
            where = plantz.deliver(server, player, copy.copy());
        }
        if (where == null) {
            List<ItemStack> rack = store.rack.computeIfAbsent(player, id -> new ArrayList<>());
            rack.add(copy);
            while (rack.size() > GazetteStore.RACK_MAX) {
                rack.remove(0);
            }
            return;
        }
        ServerPlayer reader = server.getPlayerList().getPlayer(player);
        ServerLevel level = server.getLevel(where.dim());
        if (reader != null && level != null && reader.level() == level
                && reader.blockPosition().closerThan(where.pos(), 16)) {
            SoundEvent call = SoundEvent.createVariableRangeEvent(QUERO_QUERO);
            level.playSound(null, where.pos(), call, SoundSource.AMBIENT, 1.0F, 1.0F);
            FirstHourUtil.actionbar(reader, Component.translatable(KEY + "delivered"));
        }
    }

    /** Takes every unread copy out of a loaded box, so today's replaces it (never two unread). */
    static void clearUnread(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container box) {
            for (int i = 0; i < box.getContainerSize(); i++) {
                if (GazetteItems.unread(box.getItem(i))) {
                    box.setItem(i, ItemStack.EMPTY);
                }
            }
            box.setChanged();
        }
    }

    // --- one dawn's paper ---

    /** One dawn's slots, built once for every subscriber; per-reader editions are cut from it. */
    static final class Edition {
        final long day;
        final Component masthead;
        final List<Component> page1 = new ArrayList<>();
        final List<Component> page2 = new ArrayList<>();
        final List<Component> page3 = new ArrayList<>();
        final List<GazetteStore.Posted> printed = new ArrayList<>();
        final List<GazetteStore.Posted> held = new ArrayList<>();

        private Edition(long day) {
            this.day = day;
            this.masthead = Component.translatable(KEY + "masthead", GazetteLayout.year(day), day)
                    .withStyle(ChatFormatting.BOLD);
        }

        static Edition build(MinecraftServer server, long day, GazetteStore store) {
            Edition e = new Edition(day);
            List<GazetteLayout.Slot<Component>> p1 = new ArrayList<>();
            List<GazetteLayout.Slot<Component>> p2 = new ArrayList<>();
            List<GazetteLayout.Slot<Component>> p3 = new ArrayList<>();
            p1.add(slot(head("head.gazette"), true));
            p1.add(slot(e.masthead, true));
            DeedLog.Deed headline = DeedBridge.headline(server, day - 1);
            if (headline != null) {
                p1.add(slot(Component.translatable(KEY + "headline", headline.playerName(), headline.text())
                        .withStyle(ChatFormatting.BOLD), true));
            }
            p1.add(slot(head("misfile.title"), true));
            p1.add(slot(misfileLine(Misfile.todayRow(server)), true));
            if (Misfile.releasedToday() > 0) {
                p1.add(slot(Component.translatable(KEY + "misfile.van"), false));
            }

            p2.add(slot(head("head.gossip"), true));
            int told = 0;
            for (DeedLog.Deed d : DeedLog.week(server)) {
                if (told >= 2) {
                    break;
                }
                if (d.day() == day - 1 && (headline == null || !d.equals(headline))) {
                    p2.add(slot(Component.translatable(KEY + "fuxico.deed", d.text()), false));
                    told++;
                }
            }
            Component forecast = Almanac.forecast(server);
            if (forecast != null) {
                p2.add(slot(forecast, false));
            }
            if (WeekRules.weekday(day) == 6) {
                DeedLog.Deed biggest = DeedBridge.weekBiggest(server);
                if (biggest != null) {
                    // Lindalva's notice (item-folheto-de-cordel, 0.10.1): every Sunday until the press arrives.
                    p2.add(slot(Component.translatable(KEY + "lindalva", biggest.playerName()), true));
                }
                List<Component> overflow = DeedBridge.takeOverflow(server);
                if (!overflow.isEmpty()) {
                    p2.add(slot(Component.translatable("orespawn_integrations.deed.also_told")
                            .withStyle(ChatFormatting.BOLD), false));
                    for (Component line : overflow) {
                        p2.add(slot(line, false));
                    }
                }
            }

            p3.add(slot(head("head.classifieds"), true));

            // Posted lines, oldest first: those held over from yesterday print under their own heading on page 3.
            List<GazetteLayout.Slot<Component>> heldOver = new ArrayList<>();
            List<GazetteStore.Posted> order = new ArrayList<>();
            for (GazetteStore.Posted p : store.posted) {
                Component line = Component.translatable(p.key(), p.args().stream().map(Gazette::decode).toArray());
                GazetteLayout.Slot<Component> s = slot(line, false);
                if (p.waits() > 0) {
                    heldOver.add(s);
                } else {
                    (p.page() == 1 ? p1 : p.page() == 2 ? p2 : p3).add(s);
                }
                order.add(p);
            }
            p3.add(slot(Component.translatable(KEY + "joke.1"), false));
            if (!heldOver.isEmpty()) {
                p3.add(slot(head("held_over"), false));
                p3.addAll(heldOver);
            }

            List<Component> left = new ArrayList<>();
            left.addAll(fill(p1, e.page1));
            left.addAll(fill(p2, e.page2));
            left.addAll(fill(p3, e.page3));
            for (int i = 0; i < order.size(); i++) {
                GazetteStore.Posted p = order.get(i);
                boolean didFit = true;
                for (Component c : left) {
                    if (c.getContents() instanceof TranslatableContents t && t.getKey().equals(p.key())) {
                        didFit = false;
                        break;
                    }
                }
                (didFit ? e.printed : e.held).add(p);
            }
            return e;
        }

        private static List<Component> fill(List<GazetteLayout.Slot<Component>> slots, List<Component> out) {
            GazetteLayout.Fill<Component> f = GazetteLayout.fill(slots, GazetteLayout.PAGE_WORDS);
            out.addAll(f.kept());
            return f.held();
        }

        void settlePosted(GazetteStore store) {
            store.posted.clear();
            for (GazetteStore.Posted p : held) {
                if (p.waits() + 1 < GazetteLayout.MAX_WAITS) {
                    store.posted.add(new GazetteStore.Posted(p.page(), p.key(), p.args(), p.waits() + 1));
                }
            }
        }

        ItemStack full() {
            return stack(List.of(page(page1), page(page2), page(page3)), day);
        }

        /** One page: the masthead, today's fixture, and on the first Saturday the four-purse box. */
        ItemStack newcomer(long today, long weekStart) {
            List<Component> lines = new ArrayList<>();
            lines.add(masthead);
            int weekday = WeekRules.weekday(today);
            lines.add(Component.translatable(KEY + "newcomer." + weekday));
            if (weekday == TrucoRules.SATURDAY && today - weekStart < 7) {
                lines.add(head("purses.title"));
                lines.add(Component.translatable(KEY + "purses.scraps"));
                lines.add(Component.translatable(KEY + "purses.reais"));
                lines.add(Component.translatable(KEY + "purses.emeralds"));
                lines.add(Component.translatable(KEY + "purses.emc"));
            }
            return stack(List.of(page(lines)), day);
        }
    }

    static GazetteLayout.Slot<Component> slot(Component line, boolean mandatory) {
        return new GazetteLayout.Slot<>(words(line), mandatory, line);
    }

    static Component head(String key) {
        return Component.translatable(KEY + key).withStyle(ChatFormatting.BOLD);
    }

    /** The Misfile box's line for today's row: the row's name, Tuesday's feijoada, or the rare alert. */
    static Component misfileLine(int row) {
        return switch (row) {
            case MisfileRules.NOTHING_MISFILED -> Component.translatable(KEY + "misfile.tuesday");
            case MisfileRules.EVERYTHING_IN_PLACE -> Component.translatable(KEY + "misfile.alert");
            case 0 -> Component.translatable(KEY + "misfile.tuesday");
            default -> Component.translatable(KEY + "misfile.row",
                    Component.translatable(KEY + "misfile.name." + row));
        };
    }

    /** A page: its lines one under another. */
    static Component page(List<Component> lines) {
        MutableComponent page = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                page.append("\n");
            }
            page.append(lines.get(i));
        }
        return page;
    }

    static ItemStack stack(List<Component> pages, long day) {
        ItemStack stack = new ItemStack(GazetteItems.DIARIO_OFICIAL.get());
        List<Filterable<Component>> filtered = new ArrayList<>();
        for (Component p : pages) {
            filtered.add(Filterable.passThrough(p));
        }
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Diario Oficial " + day), "Interregno", 0, filtered, true));
        stack.set(GazetteItems.EDICAO.get(), (int) day);
        return stack;
    }

    // --- word counts, in both languages ---

    /** The larger of the line's English and Portuguese word counts, read from the shipped lang files. */
    static int words(Component line) {
        return Math.max(GazetteLayout.words(render(line, "en_us")), GazetteLayout.words(render(line, "pt_br")));
    }

    /** A component as a client in {@code lang} would print it, from OI's own lang files. */
    static String render(Component c, String lang) {
        StringBuilder sb = new StringBuilder();
        if (c.getContents() instanceof TranslatableContents t) {
            String format = FirstHourUtil.translateIn(lang, t.getKey());
            Object[] args = t.getArgs();
            Matcher m = ARG.matcher(format);
            int next = 0;
            int last = 0;
            while (m.find()) {
                sb.append(format, last, m.start());
                int index = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
                if (index >= 0 && index < args.length) {
                    Object a = args[index];
                    sb.append(a instanceof Component ac ? render(ac, lang) : String.valueOf(a));
                }
                last = m.end();
            }
            sb.append(format.substring(last));
        } else {
            c.getContents().visit(s -> {
                sb.append(s);
                return Optional.empty();
            });
        }
        for (Component sibling : c.getSiblings()) {
            sb.append(render(sibling, lang));
        }
        return sb.toString();
    }
}
