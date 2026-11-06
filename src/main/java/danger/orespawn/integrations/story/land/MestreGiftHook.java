package danger.orespawn.integrations.story.land;

import com.mojang.serialization.Codec;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.StoryScheduler;
import danger.orespawn.integrations.story.court.CourtRanks;
import danger.orespawn.integrations.story.firsthour.EggLabel;
import danger.orespawn.integrations.story.firsthour.FirstHourData;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import danger.orespawn.integrations.story.firsthour.TicketState;
import danger.orespawn.integrations.story.registry.ScrapCaps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

/**
 * The Mestre's line after his blessing (npc-mestre, 0.10.1). Brasil e Coisas' {@code brasilcoisas:mestre_ensinador}
 * keeps his shipped gift and his hard-coded "Deus abençoe" on the actionbar; this hook only listens. Our
 * {@code EntityInteract} handler runs before the mod's own {@code mobInteract}: when the Mestre has not given yet (his
 * persistent {@code mestrePresente} is false) it remembers the click, and on the next tick, if the flag is now set,
 * the shipped gift fired. One box read finds the gift he just dropped (a fresh item entity at his feet), and the
 * player's {@code cc_mestre} bits pick exactly one line by the card's queue: the Worn Notebook (P10 of the ticket),
 * the Time Lord's line, the scrap, the Land's exception, the origin group's line, the diamond's pond, else the gift's
 * own line. The line comes 40 ticks later, in chat, after the blessing. The first gift grants R18 and Wonder 6.
 * A used Mestre says nothing, and neither do we. Off with the Brasil thread.
 */
public final class MestreGiftHook {

    static final ResourceLocation MESTRE = ResourceLocation.fromNamespaceAndPath("brasilcoisas", "mestre_ensinador");
    static final String KEY = "chat.orespawn_integrations.mestre.";

    // cc_mestre bits
    static final int NOTEBOOK = 1;
    static final int SCRAP = 1 << 1;
    static final int EXCEPT_LAND = 1 << 2;
    static final int TIME_LORD = 1 << 3;
    static final int STICKER_95 = 1 << 4;
    static final int GROUP_LINE = 1 << 5;
    static final int MERCHANT_LINE = 1 << 6;
    static final int FIRST_GIFT = 1 << 10;

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OreSpawnIntegrations.MODID);

    /** {@code cc_mestre}: the once-per-player lines already played and what he already gave (12 bits). */
    public static final Supplier<AttachmentType<Integer>> MESTRE_BITS = TYPES.register("cc_mestre",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    /** What the shipped gift was. */
    public enum Gift { DIAMOND, THING, CIGARRO }

    private MestreGiftHook() {
    }

    public static void init(IEventBus modBus) {
        TYPES.register(modBus);
        NeoForge.EVENT_BUS.addListener(MestreGiftHook::onInteract);
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND
                    || !IntegrationsConfig.on(IntegrationsConfig.threadBrazil)) {
                return;
            }
            Entity target = event.getTarget();
            if (!MESTRE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()))
                    || target.getPersistentData().getBoolean("mestrePresente")) {
                return;
            }
            StoryScheduler.schedule(player.getServer(), 1, "mestre_gift", () -> afterGift(player, target));
        } catch (Throwable t) {
            StoryCore.logOnce("mestre_interact", t);
        }
    }

    private static void afterGift(ServerPlayer player, Entity mestre) {
        if (player.hasDisconnected() || !mestre.getPersistentData().getBoolean("mestrePresente")) {
            return; // the shipped gift did not fire (another player's click won, or the mod changed)
        }
        Gift gift = Gift.THING;
        List<ItemEntity> drops = mestre.level().getEntitiesOfClass(ItemEntity.class, mestre.getBoundingBox().inflate(1.0),
                i -> i.getAge() <= 2 && i.hasPickUpDelay());
        if (!drops.isEmpty()) {
            ItemStack stack = drops.get(0).getItem();
            if (stack.is(Items.DIAMOND)) {
                gift = Gift.DIAMOND;
            } else if ("brasilcoisas:cigarro".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
                gift = Gift.CIGARRO;
            }
        }
        gifted(player, gift);
    }

    /** One gift seen: grants the first-gift nodes, picks the line by the queue and plays it 40 ticks later. */
    public static String gifted(ServerPlayer player, Gift gift) {
        int bits = player.getData(MESTRE_BITS.get());
        if ((bits & FIRST_GIFT) == 0) {
            bits |= FIRST_GIFT;
            FirstHourUtil.award(player, "livro/r18");
            FirstHourUtil.award(player, "wonder/6");
        }
        String line;
        if (notebookDue(player, bits)) {
            bits |= NOTEBOOK;
            FirstHourUtil.give(player, FirstHourUtil.stack("ars_nouveau:worn_notebook", 1));
            TicketState s = FirstHourData.ticket(player);
            s.markHanded(TicketState.P10);
            s.markUsed(TicketState.P10);
            line = "notebook";
        } else if (player.getTags().contains(EggLabel.ORIGIN_TAG + "time_lord") && (bits & TIME_LORD) == 0) {
            bits |= TIME_LORD;
            FirstHourUtil.award(player, "livro/r11");
            line = "time_lord";
        } else if ((bits & SCRAP) == 0) {
            bits |= SCRAP;
            ScrapCaps.give(player, 1);
            FirstHourUtil.award(player, "livro/r1");
            line = "scrap";
        } else if ((bits & EXCEPT_LAND) == 0) {
            bits |= EXCEPT_LAND;
            FirstHourUtil.award(player, "livro/r1");
            line = "except_land";
        } else if ((bits & GROUP_LINE) == 0 && groupLine(player) != null) {
            bits |= GROUP_LINE;
            line = groupLine(player);
        } else if ((bits & MERCHANT_LINE) == 0 && player.getTags().contains(EggLabel.CLASS_TAG + "class_merchant")) {
            bits |= MERCHANT_LINE;
            line = "origin.merchant";
        } else if (gift == Gift.DIAMOND && !CourtRanks.has(player, OreSpawnIntegrations.MODID + ":court/provincia/twilight_forest")) {
            line = "diamond_door";
        } else {
            line = switch (gift) {
                case DIAMOND -> "gift.diamond";
                case CIGARRO -> "gift.cigarro";
                default -> "gift.thing";
            };
        }
        player.setData(MESTRE_BITS.get(), bits);
        Component chat = Component.translatable(KEY + "speaker", Component.translatable(KEY + line));
        StoryScheduler.schedule(player.getServer(), 40, "mestre_line", () -> {
            if (!player.hasDisconnected()) {
                player.sendSystemMessage(chat);
            }
        });
        return line;
    }

    /**
     * The Worn Notebook is due when P10 is next on the ticket (P09 handed) or the ticket is finished, the player has no
     * notebook from us yet, the ticket's own fallback hasn't handed it, and the player is no mage (a mage-class origin
     * found it in the Eggshell).
     */
    static boolean notebookDue(ServerPlayer player, int bits) {
        if ((bits & NOTEBOOK) != 0 || FirstHourUtil.item("ars_nouveau:worn_notebook") == null
                || FirstHourUtil.score(player, EggLabel.GROUP_SCORE, 0) == 8 || !FirstHourData.hasTicket(player)) {
            return false;
        }
        TicketState s = FirstHourData.ticket(player);
        if (s.handed(TicketState.P10)) {
            return false;
        }
        return s.handed(9) || s.step == TicketState.CLOSED;
    }

    /** The origin group's line, for the groups that have one (creature 5, Land 3, undead 7, dragon 6), or null. */
    static String groupLine(ServerPlayer player) {
        return switch (FirstHourUtil.score(player, EggLabel.GROUP_SCORE, 0)) {
            case 3 -> "origin.land";
            case 5 -> "origin.creature";
            case 6 -> "origin.dragon";
            case 7 -> "origin.undead";
            default -> null;
        };
    }
}
