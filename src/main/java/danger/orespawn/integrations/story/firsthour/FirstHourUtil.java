package danger.orespawn.integrations.story.firsthour;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.story.StoryCore;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Small helpers the first hour's systems share: advancements, items, scores and server-side key lookup. */
public final class FirstHourUtil {

    private static final Set<String> MISSING = new HashSet<>();
    private static final Map<String, JsonObject> LANGS = new HashMap<>();

    private FirstHourUtil() {
    }

    public static ResourceLocation oi(String path) {
        return ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, path);
    }

    /** Grants every remaining criterion of {@code id}; false (logged once) when the advancement isn't loaded. */
    public static boolean award(ServerPlayer player, ResourceLocation id) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        AdvancementHolder holder = server.getAdvancements().get(id);
        if (holder == null) {
            if (MISSING.add(id.toString())) {
                OreSpawnIntegrations.LOGGER.warn("[first hour] advancement {} is not loaded; its grant is skipped", id);
            }
            return false;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        boolean any = false;
        for (String criterion : progress.getRemainingCriteria()) {
            any |= player.getAdvancements().award(holder, criterion);
        }
        return any;
    }

    public static boolean award(ServerPlayer player, String oiPath) {
        return award(player, oi(oiPath));
    }

    /** Selects the advancement tab whose root is {@code id}, so the next advancement screen opens on it. */
    public static void selectTab(ServerPlayer player, ResourceLocation id) {
        MinecraftServer server = player.getServer();
        AdvancementHolder holder = server == null ? null : server.getAdvancements().get(id);
        if (holder != null) {
            player.getAdvancements().setSelectedTab(holder);
        }
    }

    /** The item with {@code id}, or null when it isn't registered (ITEM is a defaulted registry). */
    public static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? null : item;
    }

    public static ItemStack stack(String id, int count) {
        Item item = item(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    public static boolean holds(ServerPlayer player, Item item) {
        if (item == null) {
            return false;
        }
        for (ItemStack s : player.getInventory().items) {
            if (s.is(item)) {
                return true;
            }
        }
        return player.getOffhandItem().is(item);
    }

    /** Puts {@code stack} in the inventory, or at the player's feet when it is full. */
    public static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    public static void actionbar(ServerPlayer player, Component line) {
        player.displayClientMessage(line, true);
    }

    /** The Patchouli book id on a stack (the {@code patchouli:book} component), read without Patchouli's classes. */
    public static ResourceLocation patchouliBook(ItemStack stack) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                ResourceLocation.fromNamespaceAndPath("patchouli", "book"));
        if (type == null) {
            return null;
        }
        Object value = stack.get(type);
        return value instanceof ResourceLocation rl ? rl : null;
    }

    /** A Patchouli book stack, or empty when Patchouli is absent. */
    public static ItemStack patchouliBookStack(ResourceLocation book) {
        Item item = item("patchouli:guide_book");
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                ResourceLocation.fromNamespaceAndPath("patchouli", "book"));
        if (item == null || type == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        setUnchecked(stack, type, book);
        return stack;
    }

    @SuppressWarnings("unchecked")
    private static <T> void setUnchecked(ItemStack stack, DataComponentType<T> type, Object value) {
        stack.set(type, (T) value);
    }

    public static Objective objective(Scoreboard board, String name) {
        Objective objective = board.getObjective(name);
        if (objective == null) {
            objective = board.addObjective(name, ObjectiveCriteria.DUMMY, Component.literal(name),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
        }
        return objective;
    }

    public static void setScore(ServerPlayer player, String objective, int value) {
        Scoreboard board = player.getScoreboard();
        board.getOrCreatePlayerScore(player, objective(board, objective)).set(value);
    }

    public static void resetScore(ServerPlayer player, String objective) {
        Scoreboard board = player.getScoreboard();
        Objective o = board.getObjective(objective);
        if (o != null) {
            board.resetSinglePlayerScore(player, o);
        }
    }

    /** The player's score, or {@code fallback} when it is unset (an unset score is never "0"). */
    public static int score(ServerPlayer player, String objective, int fallback) {
        Scoreboard board = player.getScoreboard();
        Objective o = board.getObjective(objective);
        if (o == null) {
            return fallback;
        }
        ReadOnlyScoreInfo info = board.getPlayerScoreInfo(player, o);
        return info == null ? fallback : info.value();
    }

    /**
     * A key read server-side in the player's client language from OI's own lang files, for the few strings a client
     * cannot translate itself (JourneyMap pin names). Falls back to en_us, then to the key.
     */
    public static String translate(ServerPlayer player, String key) {
        String lang = player.clientInformation().language();
        String value = lookup(lang == null ? "en_us" : lang.toLowerCase(java.util.Locale.ROOT), key);
        if (value == null) {
            value = lookup("en_us", key);
        }
        return value == null ? key : value;
    }

    public static String translateIn(String lang, String key) {
        String value = lookup(lang, key);
        return value == null ? key : value;
    }

    private static synchronized String lookup(String lang, String key) {
        JsonObject table = LANGS.computeIfAbsent(lang, l -> {
            try (InputStream in = FirstHourUtil.class.getResourceAsStream(
                    "/assets/" + OreSpawnIntegrations.MODID + "/lang/" + l + ".json")) {
                if (in == null) {
                    return new JsonObject();
                }
                return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Throwable t) {
                StoryCore.logOnce("first_hour_lang_" + l, t);
                return new JsonObject();
            }
        });
        return table.has(key) ? table.get(key).getAsString() : null;
    }
}
