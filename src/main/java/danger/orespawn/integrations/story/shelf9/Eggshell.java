package danger.orespawn.integrations.story.shelf9;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.items.ItemsInit;
import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.fml.ModList;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Your Eggshell (item-hatchling-shell): the one item a new player holds after joining, and what happens when it is
 * opened. The kit itself is data ({@code orespawn_integrations:gameplay/hatchling_shell}); the mage's Worn Notebook is
 * a second small table ({@code gameplay/hatchling_shell_mage}) rolled only for an opener on
 * {@code #orespawn_integrations:origin/mage}.
 *
 * <p>The "given" bit lives in the PlayerPersisted compound (kept across death), the same place the shipped Guide's flag
 * is kept: {@code NeoForgeData.PlayerPersisted."orespawn_integrations:hatchling_shell_given"}, readable from a function
 * with {@code execute if data entity @s ...}, so an Odete's "My eggshell" option can check it.
 */
public final class Eggshell {

    public static final String GIVEN_FLAG = "orespawn_integrations:hatchling_shell_given";
    /** Read by the casket root function: the first Royal Casket opened after a shell rolls the rank-0 table. */
    public static final String CASKET_SCORE = "cc_shell_casket";
    public static final String COPA = "cc_copa";
    public static final String COPA_DAY = "cc_copa_dia";

    public static final ResourceKey<LootTable> KIT = ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "gameplay/hatchling_shell"));
    public static final ResourceKey<LootTable> KIT_MAGE = ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "gameplay/hatchling_shell_mage"));
    static final ResourceLocation CAP_FUNCTION =
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "hatchling_shell/cap");
    static final ResourceLocation MAGE_TAG_FILE =
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "tags/origin/mage.json");

    private Eggshell() {
    }

    public static boolean isGiven(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(GIVEN_FLAG);
    }

    public static void markGiven(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putBoolean(GIVEN_FLAG, true);
        // getCompound hands back a detached tag when the key is missing, so it is written back explicitly.
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /** Gives one shell and sets the bit; false (nothing given) when the bit was already set. */
    public static boolean giveOnce(ServerPlayer player) {
        if (isGiven(player)) {
            return false;
        }
        ItemStack shell = new ItemStack(ItemsInit.HATCHLING_SHELL.get());
        if (!player.getInventory().add(shell)) {
            player.drop(shell, false);
        }
        markGiven(player);
        return true;
    }

    /** Opens a shell: the kit into the inventory (overflow at the feet), the casket rule, the cap, the crumble. */
    public static void open(ServerPlayer player, ItemStack stack) {
        MinecraftServer server = player.getServer();
        if (server == null || stack.isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        List<ItemStack> loot = new ArrayList<>(roll(server, level, player, KIT));
        if (isMage(server, player)) {
            loot.addAll(roll(server, level, player, KIT_MAGE));
        }
        stack.shrink(1);
        for (ItemStack item : loot) {
            if (!player.getInventory().add(item)) {
                player.drop(item, false);
            }
        }
        // The casket rule: whatever the opener's rank when the casket is placed, the first Royal Casket opened after
        // the shell rolls the rank-0 table (the casket root function reads and clears this score).
        try {
            Scores.set(server, player, CASKET_SCORE, 1);
        } catch (Throwable t) {
            StoryCore.logOnce("hatchling_shell.casket_score", t);
        }
        giveCap(server, player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TURTLE_EGG_CRACK, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("orespawn_integrations.hatchling_shell.open"), false);
    }

    private static List<ItemStack> roll(MinecraftServer server, ServerLevel level, ServerPlayer player,
                                        ResourceKey<LootTable> key) {
        LootTable table = server.reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.GIFT);
        return table.getRandomItems(params);
    }

    /** The Egged cap from HatsRenewed, into the Wardrobe unworn; skipped without the hats mod. */
    private static void giveCap(MinecraftServer server, ServerPlayer player) {
        if (!ModList.get().isLoaded("hats")) {
            return;
        }
        try {
            var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            var function = server.getFunctions().get(CAP_FUNCTION);
            if (function.isPresent()) {
                server.getFunctions().execute(function.get(), source);
            } else {
                server.getCommands().performPrefixedCommand(source, "hats give @s hat Egged");
            }
        } catch (Throwable t) {
            StoryCore.logOnce("hatchling_shell.cap", t);
        }
    }

    static boolean isMage(MinecraftServer server, ServerPlayer player) {
        Set<String> list = mageList(server);
        List<String> picks = new ArrayList<>();
        if (ModList.get().isLoaded("neoorigins")) {
            try {
                for (ResourceLocation pick : OriginLookup.picks(player)) {
                    picks.add(pick.toString());
                }
            } catch (Throwable t) {
                StoryCore.logOnce("hatchling_shell.origins", t);
            }
        }
        return MageList.isMage(list, picks, player.getTags());
    }

    private static Set<String> mageList(MinecraftServer server) {
        try {
            Optional<Resource> resource = server.getResourceManager().getResource(MAGE_TAG_FILE);
            if (resource.isPresent()) {
                try (Reader reader = resource.get().openAsReader()) {
                    StringBuilder text = new StringBuilder();
                    char[] buffer = new char[1024];
                    int n;
                    while ((n = reader.read(buffer)) > 0) {
                        text.append(buffer, 0, n);
                    }
                    return MageList.parse(text.toString());
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("hatchling_shell.mage_list", t);
        }
        return new LinkedHashSet<>(MageList.DEFAULT);
    }

    /**
     * The canopy's daily cap ({@code cc_copa}) resets at a player's first login of a new game day, until the Land's
     * dawn job takes it over in 0.10.1: the day ({@code time query day}) is compared with the stored {@code cc_copa_dia}.
     */
    public static void resetCopaOnNewDay(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        int day = gameDay(server);
        int stored = Scores.get(server, player, COPA_DAY, -1);
        if (stored != day) {
            Scores.set(server, player, COPA, 0);
            Scores.set(server, player, COPA_DAY, day);
        }
    }

    /** The value {@code /time query day} prints. */
    static int gameDay(MinecraftServer server) {
        return (int) (server.overworld().getDayTime() / 24000L % Integer.MAX_VALUE);
    }
}
