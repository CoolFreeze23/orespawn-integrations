package danger.orespawn.integrations.guide;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import vazkii.patchouli.common.item.PatchouliDataComponents;
import vazkii.patchouli.common.item.PatchouliItems;

/**
 * Patchouli compat: hands every player a copy of the OreSpawn Guide
 * ({@code orespawn_integrations:orespawn_guide}) the first time they log in.
 *
 * The "already given" flag lives in the {@link Player#PERSISTED_NBT_TAG}
 * sub-compound of the player's persistent data - that is the only part of
 * entity persistent data NeoForge copies across death, so players don't get
 * a fresh book every time they respawn.
 *
 * Only loaded when Patchouli is present (see the COMPAT table in
 * {@link OreSpawnIntegrations}), so the direct references to Patchouli
 * classes here are safe.
 */
public final class GuideCompat {

    /** Key inside the PlayerPersisted compound marking the book as delivered. */
    private static final String GIVEN_FLAG = "orespawn_integrations:guide_given";

    private static final ResourceLocation GUIDE_BOOK_ID =
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "orespawn_guide");

    private GuideCompat() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(GuideCompat::onPlayerLoggedIn);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            CompoundTag root = player.getPersistentData();
            CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
            if (persisted.getBoolean(GIVEN_FLAG)) {
                return;
            }

            // Patchouli's generic book item + the vazkii.patchouli.common.item.
            // PatchouliDataComponents.BOOK component (DataComponentType<ResourceLocation>)
            // pointing at our book id - same recipe ItemModBook.forBook uses.
            ItemStack book = new ItemStack(PatchouliItems.BOOK);
            book.set(PatchouliDataComponents.BOOK, GUIDE_BOOK_ID);
            if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
            player.displayClientMessage(
                    Component.translatable("message.orespawn_integrations.guide_given"), false);

            // getCompound returns a detached empty tag when the key is absent,
            // so the sub-compound must be written back explicitly.
            persisted.putBoolean(GIVEN_FLAG, true);
            root.put(Player.PERSISTED_NBT_TAG, persisted);
        } catch (Throwable t) {
            OreSpawnIntegrations.LOGGER.error(
                    "[patchouli] failed to hand out the OreSpawn Guide - continuing without it", t);
        }
    }
}
