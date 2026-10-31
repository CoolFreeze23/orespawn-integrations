package danger.orespawn.integrations.story.court;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.deed.DeedLog;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * The tab-full helpers (system-advancement-tree, "Tab completion epithets"). On every OI advancement grant this
 * checks the granted node's tab: once every node on it with a display is done (or belongs to a system whose switch is
 * off), the tab's hidden helper {@code <folder>/tab_full} is granted, the chat line names the epithet and the deed log
 * keeps it for Saturday. The rank handler's {@code AdvancementEarnEvent} calls it, so it costs one walk of one tab per
 * grant and nothing otherwise.
 */
public final class TabFull {

    /** Each tab's root, its helper and its number (the tab name and epithet keys use the number). */
    private record Tab(String helper, int number) {}

    private static final Map<String, Tab> TABS = Map.of(
            "wonder/2", new Tab("court/tab_full", 1),
            "wonder/1", new Tab("livro/tab_full", 2),
            "week/root", new Tab("week/tab_full", 3),
            "hunt/root", new Tab("hunt/tab_full", 4),
            "parish/root", new Tab("parish/tab_full", 5),
            "property/root", new Tab("property/tab_full", 6),
            "causos/root", new Tab("causos/tab_full", 7),
            "secrets/root", new Tab("secrets/tab_full", 8));

    /** Node folders whose owning switch, when off, takes them out of the count (a hidden branch never blocks a tab). */
    private static final Map<String, ModConfigSpec.BooleanValue> OWNERS = Map.of(
            "court/", IntegrationsConfig.storyCourtRanks,
            "hunt/row_", IntegrationsConfig.storyBossLadder,
            "correcoes/", IntegrationsConfig.storyCorrections,
            "avistamentos/", IntegrationsConfig.storySightings);

    public static final String DEED_KEY = "orespawn_integrations.deed.tab_full";

    private TabFull() {
    }

    static void check(ServerPlayer player, AdvancementHolder granted) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        AdvancementNode node = server.getAdvancements().tree().get(granted.id());
        if (node == null) {
            return;
        }
        AdvancementNode root = node.root();
        ResourceLocation rootId = root.holder().id();
        if (!OreSpawnIntegrations.MODID.equals(rootId.getNamespace())) {
            return;
        }
        Tab tab = TABS.get(rootId.getPath());
        if (tab == null || CourtRanks.has(player, OreSpawnIntegrations.MODID + ":" + tab.helper())) {
            return;
        }
        if (!complete(player, root)) {
            return;
        }
        if (!FirstHourUtil.award(player, tab.helper())) {
            return;
        }
        String name = player.getGameProfile().getName();
        String tabKey = "orespawn_integrations.tab." + tab.number() + ".name";
        String epithetKey = "orespawn_integrations.epithet.tab" + tab.number();
        player.sendSystemMessage(Component.translatable(DEED_KEY, name, Component.translatable(tabKey),
                Component.translatable(epithetKey, name)));
        DeedLog.record(player, DEED_KEY, name, DeedLog.key(tabKey), DeedLog.key(epithetKey, name));
    }

    /** True when every displayed node under {@code root} is done or switched off. */
    private static boolean complete(ServerPlayer player, AdvancementNode root) {
        Deque<AdvancementNode> todo = new ArrayDeque<>();
        todo.add(root);
        while (!todo.isEmpty()) {
            AdvancementNode n = todo.poll();
            for (AdvancementNode child : n.children()) {
                todo.add(child);
            }
            AdvancementHolder h = n.holder();
            if (h.value().display().isEmpty() || switchedOff(h.id())) {
                continue;
            }
            if (!player.getAdvancements().getOrStartProgress(h).isDone()) {
                return false;
            }
        }
        return true;
    }

    private static boolean switchedOff(ResourceLocation id) {
        if (!OreSpawnIntegrations.MODID.equals(id.getNamespace())) {
            return false;
        }
        for (Map.Entry<String, ModConfigSpec.BooleanValue> e : OWNERS.entrySet()) {
            if (id.getPath().startsWith(e.getKey()) && !IntegrationsConfig.on(e.getValue())) {
                return true;
            }
        }
        return false;
    }
}
