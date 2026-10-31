package danger.orespawn.integrations.story.firsthour;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Unit;

import java.util.List;
import java.util.UUID;

/**
 * The Royal Summons, the Prince's first letter: a written book of three translatable pages, so each client reads its
 * own language. Page 1 the letter; page 2 the postscript the label calls for (our Saci, Royal Blood and Kraken Spawn
 * have their own; everyone else gets their origin group's), the ants hint and the First Fall's P.P.S.; page 3 Form
 * 7-B. Its custom data carries {@code orespawn_integrations:letter = "summons"} and the addressee, so only that
 * player's opening counts as their P04. The book title and author are written as translatable name and lore lines
 * (a book's own title and author are plain strings), with the vanilla author line hidden.
 */
public final class RoyalSummons {

    public static final String LETTER_KEY = "orespawn_integrations:letter";
    public static final String ADDRESSEE_KEY = "orespawn_integrations:addressee";
    public static final String SUMMONS = "summons";

    private static final String K = "orespawn_integrations.princes_letter.";

    private RoyalSummons() {
    }

    public static ItemStack build(ServerPlayer player) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        Component page1 = Component.translatable(K + "page1");
        MutableComponent page2 = Component.empty()
                .append(Component.translatable(postscriptKey(player)))
                .append("\n\n")
                .append(Component.translatable(K + "hint_ants"))
                .append("\n\n")
                .append(Component.translatable(K + "pps_first_fall"));
        Component page3 = Component.translatable(K + "page3_form_7b");
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(SUMMONS), "", 0,
                List.of(Filterable.passThrough(page1), Filterable.passThrough(page2), Filterable.passThrough(page3)),
                true));
        book.set(DataComponents.CUSTOM_NAME, Component.translatable(K + "title").withStyle(s -> s.withItalic(false)));
        book.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable(K + "author"))));
        book.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
        CompoundTag tag = new CompoundTag();
        tag.putString(LETTER_KEY, SUMMONS);
        tag.putUUID(ADDRESSEE_KEY, player.getUUID());
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return book;
    }

    /** True when {@code stack} is a Summons addressed to {@code player}. */
    public static boolean isSummonsFor(ItemStack stack, Player player) {
        UUID to = addressee(stack);
        return to != null && to.equals(player.getUUID());
    }

    /** True when {@code stack} is any Summons. */
    public static boolean isSummons(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && SUMMONS.equals(data.copyTag().getString(LETTER_KEY));
    }

    private static UUID addressee(ItemStack stack) {
        if (!stack.is(Items.WRITTEN_BOOK) || !isSummons(stack)) {
            return null;
        }
        CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        return tag.hasUUID(ADDRESSEE_KEY) ? tag.getUUID(ADDRESSEE_KEY) : null;
    }

    /** True when the player carries their Summons (inventory or ender chest). */
    public static boolean holdsOwn(ServerPlayer player) {
        for (ItemStack s : player.getInventory().items) {
            if (isSummonsFor(s, player)) {
                return true;
            }
        }
        for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) {
            if (isSummonsFor(player.getEnderChestInventory().getItem(i), player)) {
                return true;
            }
        }
        return false;
    }

    /** The origin's own postscript, else the label group's, read from the Egg's Label tags and score. */
    static String postscriptKey(ServerPlayer player) {
        for (String own : new String[]{"saci", "royal_blood", "kraken_spawn"}) {
            if (player.getTags().contains(EggLabel.ORIGIN_TAG + own)) {
                return K + "ps.origin." + own;
            }
        }
        int group = FirstHourUtil.score(player, EggLabel.GROUP_SCORE, 10);
        // Group 2 (our CrazyCraft origins) has per-origin lines only for the three above; the rest read "Others".
        if (group < 1 || group > 10 || group == 2) {
            group = 10;
        }
        return K + "ps.group." + group;
    }
}
