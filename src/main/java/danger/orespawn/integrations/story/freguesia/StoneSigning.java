package danger.orespawn.integrations.story.freguesia;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.StoryScheduler;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who may act for the stone (item-marco-da-freguesia, "Using them"), all by sneak-use on the stone:
 * <ul>
 *   <li>a non-signer holding a Ledger Scrap asks to sign; the scrap is held for 30 s while one pending entry per stone
 *       waits, and the founder or any signer countersigns with an empty hand. With no countersign the scrap comes
 *       back; a fifth signer is refused;</li>
 *   <li>an empty hand with nothing pending opens the list: the founder may withdraw another signer, any other signer
 *       their own signature (the founder's goes only with the stone). The list's options run {@code /marco}, which
 *       checks again who is asking and where they stand;</li>
 *   <li>a signer's named name tag renames the stone once;</li>
 *   <li>a non-signer holding a sign is told only signers can change the stone (the open gate comes with 0.14.0).</li>
 * </ul>
 */
final class StoneSigning {

    private static final String K = StoneRecords.K + "marco.";
    private static final int PENDING_TICKS = 30 * 20;
    private static final double LIST_REACH = 8.0;

    private record Pending(UUID requester, long since) {}

    private static final Map<Integer, Pending> PENDING = new HashMap<>();

    private StoneSigning() {
    }

    static void reset() {
        PENDING.clear();
    }

    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)
                || event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                || !StoneRecords.enabled()) {
            return;
        }
        BlockState state = level.getBlockState(event.getPos());
        if (!FreguesiaBlocks.isMarco(state.getBlock())) {
            return;
        }
        try {
            BlockPos lower = state.getValue(MarcoBlock.HALF) == DoubleBlockHalf.UPPER ? event.getPos().below() : event.getPos();
            FreguesiaData data = FreguesiaData.get(level.getServer());
            FreguesiaData.Stone s = data.at(StoneRecords.dim(level), lower);
            if (s == null) {
                return;
            }
            if (use(level, data, s, player, player.getMainHandItem())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("freguesia.signing", t);
        }
    }

    /** One sneak-use with {@code held}; true when the stone handled it. Public for the GameTests. */
    static boolean use(ServerLevel level, FreguesiaData data, FreguesiaData.Stone s, ServerPlayer player, ItemStack held) {
        MinecraftServer server = level.getServer();
        boolean signer = s.isSigner(player.getUUID());
        if (held.is(danger.orespawn.integrations.story.firsthour.FirstHourUtil.item("orespawn_integrations:ledger_scrap"))) {
            if (signer) {
                return false;
            }
            if (s.signers.size() >= StandingRules.MAX_SIGNERS) {
                FirstHourUtil.actionbar(player, Component.translatable(K + "full"));
                player.sendSystemMessage(Component.translatable(StoneRecords.K + "signers_full"));
                return true;
            }
            if (PENDING.containsKey(s.id)) {
                return true; // one pending signature per stone
            }
            if (!player.isCreative()) {
                held.shrink(1);
            }
            long now = server.getTickCount();
            PENDING.put(s.id, new Pending(player.getUUID(), now));
            FirstHourUtil.actionbar(player, Component.translatable(K + "asking"));
            int id = s.id;
            StoryScheduler.schedule(server, PENDING_TICKS, "freguesia.pending", () -> lapse(server, id, now));
            return true;
        }
        if (held.is(Items.NAME_TAG)) {
            if (!signer) {
                FirstHourUtil.actionbar(player, Component.translatable(K + "not_signer"));
                return true;
            }
            Component name = held.get(DataComponents.CUSTOM_NAME);
            if (name == null || s.customName != null) {
                return false;
            }
            s.customName = name.getString();
            if (!player.isCreative()) {
                held.shrink(1);
            }
            data.setDirty();
            FirstHourUtil.actionbar(player, StoneRecords.name(s));
            return true;
        }
        if (held.is(ItemTags.SIGNS) || held.is(ItemTags.HANGING_SIGNS)) {
            if (!signer) {
                FirstHourUtil.actionbar(player, Component.translatable(K + "not_signer"));
                return true;
            }
            return false; // Porteira Aberta and the Capital's opt-in come with their slices
        }
        if (!held.isEmpty() || !signer) {
            return false;
        }
        Pending pending = PENDING.get(s.id);
        if (pending != null) {
            countersign(server, data, s, pending, player);
            return true;
        }
        list(s, player);
        return true;
    }

    private static void countersign(MinecraftServer server, FreguesiaData data, FreguesiaData.Stone s, Pending pending,
                                    ServerPlayer by) {
        PENDING.remove(s.id);
        ServerPlayer requester = server.getPlayerList().getPlayer(pending.requester());
        if (s.signers.size() >= StandingRules.MAX_SIGNERS || s.isSigner(pending.requester())) {
            if (requester != null) {
                FirstHourUtil.give(requester, FirstHourUtil.stack("orespawn_integrations:ledger_scrap", 1));
                FirstHourUtil.actionbar(requester, Component.translatable(K + "full"));
            }
            return;
        }
        String name = requester != null ? requester.getGameProfile().getName() : pending.requester().toString();
        data.addSigner(s, pending.requester(), name);
        Component line = Component.translatable(K + "signed");
        FirstHourUtil.actionbar(by, line);
        if (requester != null) {
            FirstHourUtil.actionbar(requester, line);
            requester.sendSystemMessage(Component.translatable(StoneRecords.K + "signed_on"));
            StoneCounter.seen(requester);
        }
    }

    private static void lapse(MinecraftServer server, int stoneId, long since) {
        Pending p = PENDING.get(stoneId);
        if (p == null || p.since() != since) {
            return;
        }
        PENDING.remove(stoneId);
        ServerPlayer requester = server.getPlayerList().getPlayer(p.requester());
        if (requester != null) {
            FirstHourUtil.give(requester, FirstHourUtil.stack("orespawn_integrations:ledger_scrap", 1));
            FirstHourUtil.actionbar(requester, Component.translatable(K + "lapsed"));
        }
    }

    /** The withdrawal list: one option per signer the asker may remove. */
    private static void list(FreguesiaData.Stone s, ServerPlayer player) {
        boolean founder = player.getUUID().equals(s.founder());
        for (int i = 1; i < s.signers.size(); i++) {
            UUID target = s.signers.get(i);
            boolean self = target.equals(player.getUUID());
            if (!founder && !self) {
                continue;
            }
            MutableComponent option = Component.literal("[")
                    .append(Component.translatable(K + (self ? "withdraw_own" : "withdraw_other")))
                    .append("] ").append(s.signerNames.get(i));
            option.withStyle(style -> style.withColor(ChatFormatting.GOLD).withClickEvent(
                    new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/marco retirar " + s.id + " " + target)));
            player.sendSystemMessage(option);
        }
    }

    // --- /marco retirar: the list's options ---

    static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("marco")
                .then(Commands.literal("retirar")
                        .then(Commands.argument("stone", IntegerArgumentType.integer(1))
                                .then(Commands.argument("signer", StringArgumentType.word())
                                        .executes(ctx -> withdraw(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "stone"),
                                                StringArgumentType.getString(ctx, "signer")))))));
    }

    private static int withdraw(CommandSourceStack source, int stoneId, String signer) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        UUID target;
        try {
            target = UUID.fromString(signer);
        } catch (IllegalArgumentException e) {
            return 0;
        }
        FreguesiaData data = FreguesiaData.get(source.getServer());
        FreguesiaData.Stone s = data.stone(stoneId);
        if (s == null || !s.standing || !s.dim.equals(StoneRecords.dim(player.level()))
                || player.distanceToSqr(Vec3.atCenterOf(s.pos)) > LIST_REACH * LIST_REACH) {
            return 0;
        }
        boolean founder = player.getUUID().equals(s.founder());
        boolean self = target.equals(player.getUUID());
        if (target.equals(s.founder()) || !s.isSigner(target) || (!founder && !self)) {
            FirstHourUtil.actionbar(player, Component.translatable(K + "not_signer"));
            return 0;
        }
        data.removeSigner(s, target);
        Component line = Component.translatable(StoneRecords.K + "signed_off");
        FirstHourUtil.actionbar(player, line);
        ServerPlayer gone = source.getServer().getPlayerList().getPlayer(target);
        if (gone != null && gone != player) {
            FirstHourUtil.actionbar(gone, line);
        }
        return 1;
    }
}
