package danger.orespawn.integrations.story.registry;

import danger.orespawn.integrations.config.IntegrationsConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.Scoreboard;

/**
 * Who pays a Ledger Scrap, and how often ({@code item-ledger-scrap}, "Every other source"). Every system that pays a
 * scrap goes through here, so {@code [story] scraps} switches all of them off at once and the per-source caps live in
 * one place. The capped sources keep their count in a per-player score (the names are the card's): a snagged kite
 * pays at most 10 ({@code cc_scrap_kite}), the Field Notebook hand-in at most 8 ({@code cc_scrap_notebook}). Datapack
 * callers use the matching functions {@code orespawn_integrations:retalho/kite} and {@code retalho/caderneta}.
 */
public final class ScrapCaps {

    public static final ResourceLocation SCRAP = ResourceLocation.fromNamespaceAndPath("orespawn_integrations", "ledger_scrap");
    public static final ResourceLocation SLIP = ResourceLocation.fromNamespaceAndPath("orespawn_integrations", "guia_de_averbacao");

    /** The capped sources. */
    public enum Source {
        KITE("cc_scrap_kite", ScrapCapRules.KITE_CAP),
        NOTEBOOK("cc_scrap_notebook", ScrapCapRules.NOTEBOOK_CAP);

        public final String objective;
        public final int cap;

        Source(String objective, int cap) {
            this.objective = objective;
            this.cap = cap;
        }
    }

    private ScrapCaps() {
    }

    public static int allowed(int paid, int cap, int wanted) {
        return ScrapCapRules.allowed(paid, cap, wanted);
    }

    public static Item scrapItem() {
        Item item = BuiltInRegistries.ITEM.get(SCRAP);
        return item == Items.AIR ? null : item;
    }

    /** Pays {@code count} scraps from an uncapped source (a casket, a first visit). False when scraps are off. */
    public static boolean give(ServerPlayer player, int count) {
        if (count <= 0 || !IntegrationsConfig.on(IntegrationsConfig.storyScraps)) {
            return false;
        }
        Item item = scrapItem();
        if (item == null) {
            return false;
        }
        ItemStack stack = new ItemStack(item, count);
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
        return true;
    }

    /** Pays up to {@code wanted} scraps from a capped source and counts them. Returns how many were paid. */
    public static int give(ServerPlayer player, Source source, int wanted) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyScraps)) {
            return 0;
        }
        Scoreboard board = player.getServer().getScoreboard();
        Objective objective = RegistryCore.objective(board, source.objective);
        ScoreAccess score = board.getOrCreatePlayerScore(player, objective);
        int paid = allowed(score.get(), source.cap, wanted);
        if (paid > 0 && give(player, paid)) {
            score.add(paid);
            return paid;
        }
        return 0;
    }
}
