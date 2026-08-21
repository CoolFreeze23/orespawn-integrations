package danger.orespawn.integrations.compat.yung;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.util.SeasonalDates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * YUNG-structure flavor moments, both keyed off the same chest-open
 * detection:
 *
 * <ul>
 *   <li><b>Tombstone Hauntings</b> — on Halloween (the port's own
 *       {@link SeasonalDates#isHalloween()} clock), first-opening a Better
 *       Dungeons zombie-dungeon tombstone chest raises a friendly
 *       {@code orespawn:ghost} apparition: its ATTACK_DAMAGE attribute is
 *       zeroed (the port's Ghost routes all contact damage through
 *       {@code Attributes.ATTACK_DAMAGE} — verified in the mirror's
 *       Ghost.doHurtTarget), and it dissolves after 30s.</li>
 *   <li><b>Scorpion Tomb Trap</b> — first-opening the Better Desert Temples
 *       pharaoh tomb chest has a 25% chance to wake an
 *       {@code orespawn:emperor_scorpion} (1.5x1.5 blocks, fits the burial
 *       chamber). The mummy's curse is also Thread 4's themed source: the
 *       emperor scorpion feeds the Chitin Band drop GLM. The chest's loot is
 *       untouched — the trap is pure spice on top of an unchanged reward.</li>
 * </ul>
 *
 * <p><b>Detection:</b> {@code PlayerInteractEvent.RightClickBlock}
 * (server-side, main hand) + {@code RandomizableContainerBlockEntity
 * .getLootTable()}. Structure chests keep their loot-table id until the menu
 * first opens and unpacks it, so a non-null id both identifies the chest and
 * makes each trigger naturally once-per-chest — after the first open the id
 * is gone. Target ids verified in the pack jars:
 * {@code betterdungeons:zombie_dungeon/chests/tombstone}
 * (YungsBetterDungeons-1.21.1-NeoForge-5.1.4.jar) and
 * {@code betterdeserttemples:chests/tomb_pharaoh}
 * (YungsBetterDesertTemples-1.21.1-NeoForge-4.1.5.jar). Known edge: if the
 * click doesn't actually open the chest (blocked lid, another handler
 * cancels), the flavor may fire while the table stays armed — harmless, and
 * the sneak-with-item case (a block placement, not an open) is filtered via
 * {@code isSecondaryUseActive}.</p>
 *
 * <p><b>Classload safety:</b> imports the port's {@code SeasonalDates}, so
 * the orchestrator must init this class only after a ModList check for
 * "orespawn" (same reflective pattern as compat/alive/AliveWorldCompat). The
 * YUNG mods themselves are never classloaded — pure loot-table-id string
 * matching; without them no chest ever carries these ids and the class is
 * inert.</p>
 *
 * <p>POLICY 4 — API surfaces verified via javap: MC 1.21.1 (NeoForge
 * 21.1.223 dev jar): {@code RandomizableContainer.getLootTable() ->
 * ResourceKey<LootTable>} (nullable) implemented by
 * {@code RandomizableContainerBlockEntity}; {@code Player.isSecondaryUseActive()},
 * {@code Player.displayClientMessage(Component, boolean)},
 * {@code LivingEntity.getAttribute(Holder<Attribute>)},
 * {@code AttributeInstance.setBaseValue(double)},
 * {@code CollisionGetter.noCollision(Entity)},
 * {@code SoundEvents.ELDER_GUARDIAN_CURSE} (plain SoundEvent field).
 * neoforge-21.1.223-universal.jar: {@code PlayerInteractEvent.RightClickBlock}
 * with {@code getHand()/getPos()/getLevel()}. Port jar
 * orespawn-1.21.1-2.0.0-beta.1 (libs/): {@code
 * danger.orespawn.util.SeasonalDates.isHalloween() -> boolean} (public
 * static). Pack runtime is NeoForge 21.1.248; stable across 21.1.x.</p>
 *
 * <p>House defensive style (compat/alive): try/catch + log-once handler
 * bodies; delayed work (the apparition's dissolve timer) runs through this
 * class's own tiny server-tick queue, wiped on server stop.</p>
 */
public final class YungFlavorCompat {

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private static final ResourceLocation TOMBSTONE_TABLE =
            ResourceLocation.fromNamespaceAndPath("betterdungeons", "zombie_dungeon/chests/tombstone");
    private static final ResourceLocation TOMB_PHARAOH_TABLE =
            ResourceLocation.fromNamespaceAndPath("betterdeserttemples", "chests/tomb_pharaoh");

    private static final float SCORPION_CHANCE = 0.25F;
    private static final int GHOST_LIFETIME_TICKS = 600; // 30s apparition

    /** Candidate wake spots around the tomb chest (dx, dz at chest height). */
    private static final int[][] SCORPION_OFFSETS = {
            {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

    // --- self-contained delayed-task queue (AliveScheduler shape, local copy) ---
    private record ScheduledTask(long runAtTick, Runnable action) {}
    private static final List<ScheduledTask> SCHEDULED = new ArrayList<>();

    private YungFlavorCompat() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(YungFlavorCompat::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(YungFlavorCompat::onServerTick);
        NeoForge.EVENT_BUS.addListener(YungFlavorCompat::onServerStopped);
    }

    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (!(event.getLevel() instanceof ServerLevel level)) {
                return;
            }
            if (event.getHand() != InteractionHand.MAIN_HAND) {
                return;
            }
            Player player = event.getEntity();
            if (player.isSpectator() || player.isSecondaryUseActive()) {
                return; // sneak-use is a placement attempt, not a chest open
            }
            BlockEntity blockEntity = level.getBlockEntity(event.getPos());
            ResourceKey<LootTable> lootKey = null;
            if (blockEntity instanceof RandomizableContainerBlockEntity container) {
                lootKey = container.getLootTable();
            } else if (blockEntity != null
                    && net.neoforged.fml.ModList.get().isLoaded("lootr")) {
                // Lootr converts structure chests to its own BE (NOT a
                // RandomizableContainerBlockEntity) and keeps the table id in
                // ILootrInfo; per-player instancing means the table never
                // clears, so the flavor event can repeat per visitor - fine,
                // both effects are harmless fun. Nested class keeps the Lootr
                // API unloaded when the mod is absent.
                lootKey = LootrHook.tableId(blockEntity);
            }
            if (lootKey == null) {
                return; // no table (or vanilla chest already opened once)
            }
            ResourceLocation id = lootKey.location();
            if (TOMBSTONE_TABLE.equals(id)) {
                hauntTombstone(level, event.getPos(), player);
            } else if (TOMB_PHARAOH_TABLE.equals(id)) {
                springScorpionTrap(level, event.getPos(), player);
            }
        } catch (Throwable t) {
            logOnce("yung_chest_open", t);
        }
    }

    /**
     * Halloween only: a friendly ghost drifts up out of the tombstone. Zeroed
     * ATTACK_DAMAGE turns the port Ghost's contact drain into a harmless
     * bump; 2 max health means a spooked player can pop it like a balloon.
     */
    private static void hauntTombstone(ServerLevel level, BlockPos chestPos, Player player) {
        if (!SeasonalDates.isHalloween()) {
            return;
        }
        EntityType<?> type = entityType("orespawn", "ghost");
        if (type == null) {
            return;
        }
        Entity created = type.create(level);
        if (!(created instanceof Mob ghost)) {
            if (created != null) {
                created.discard();
            }
            logMissingOnce("orespawn:ghost mob class (unexpected entity type)");
            return;
        }
        ghost.moveTo(chestPos.getX() + 0.5, chestPos.getY() + 1.0, chestPos.getZ() + 0.5,
                level.random.nextFloat() * 360.0F, 0.0F);
        AttributeInstance attackDamage = ghost.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.setBaseValue(0.0); // friendly: it haunts, it never hurts
        }
        if (!level.addFreshEntity(ghost)) {
            return;
        }
        player.displayClientMessage(
                Component.translatable("event.orespawn_integrations.tombstone_haunting"), true);
        schedule(level.getServer(), GHOST_LIFETIME_TICKS, () -> {
            if (!ghost.isRemoved()) {
                ghost.discard(); // the apparition dissolves back into the grave
            }
        });
    }

    /**
     * 25% mummy's curse: an emperor scorpion wakes beside the pharaoh's
     * chest. First collision-free spot around the chest wins; a fully sealed
     * nook silently swallows the curse rather than wedging a 1.5-block
     * scorpion into a wall.
     */
    private static void springScorpionTrap(ServerLevel level, BlockPos chestPos, Player player) {
        if (level.random.nextFloat() >= SCORPION_CHANCE) {
            return;
        }
        EntityType<?> type = entityType("orespawn", "emperor_scorpion");
        if (type == null) {
            return;
        }
        Entity created = type.create(level);
        if (!(created instanceof Mob scorpion)) {
            if (created != null) {
                created.discard();
            }
            logMissingOnce("orespawn:emperor_scorpion mob class (unexpected entity type)");
            return;
        }
        boolean placed = false;
        for (int[] offset : SCORPION_OFFSETS) {
            scorpion.moveTo(chestPos.getX() + 0.5 + offset[0], chestPos.getY(),
                    chestPos.getZ() + 0.5 + offset[1], level.random.nextFloat() * 360.0F, 0.0F);
            if (level.noCollision(scorpion)) {
                placed = true;
                break;
            }
        }
        if (!placed) {
            scorpion.discard();
            return;
        }
        scorpion.setPersistenceRequired(); // the curse does not despawn while you loot
        scorpion.setTarget(player);
        if (!level.addFreshEntity(scorpion)) {
            return;
        }
        level.playSound(null, chestPos, SoundEvents.ELDER_GUARDIAN_CURSE,
                SoundSource.HOSTILE, 0.6F, 0.8F);
        player.displayClientMessage(
                Component.translatable("event.orespawn_integrations.mummys_curse"), true);
    }

    /**
     * Resolves an entity type by id, or null (log-once) if absent — local
     * copy of the compat/alive helper (package-private there). containsKey
     * guard because ENTITY_TYPE is a defaulted registry.
     */
    private static EntityType<?> entityType(String namespace, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            logMissingOnce("entity " + id);
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.get(id);
    }

    // --- delayed-task plumbing ---

    private static void schedule(MinecraftServer server, int delayTicks, Runnable action) {
        SCHEDULED.add(new ScheduledTask(server.getTickCount() + delayTicks, action));
    }

    static void onServerTick(ServerTickEvent.Post event) {
        if (SCHEDULED.isEmpty()) {
            return;
        }
        long now = event.getServer().getTickCount();
        List<ScheduledTask> due = null;
        Iterator<ScheduledTask> it = SCHEDULED.iterator();
        while (it.hasNext()) {
            ScheduledTask task = it.next();
            if (now >= task.runAtTick()) {
                it.remove();
                if (due == null) {
                    due = new ArrayList<>();
                }
                due.add(task);
            }
        }
        if (due != null) {
            for (ScheduledTask task : due) {
                try {
                    task.action().run();
                } catch (Throwable t) {
                    logOnce("yung_flavor_task", t);
                }
            }
        }
    }

    static void onServerStopped(ServerStoppedEvent event) {
        SCHEDULED.clear();
    }

    private static void logOnce(String key, Throwable t) {
        if (LOGGED.add(key)) {
            OreSpawnIntegrations.LOGGER.error(
                    "[yung] handler '{}' failed; muting further reports of this failure", key, t);
        }
    }

    private static void logMissingOnce(String what) {
        if (LOGGED.add("missing:" + what)) {
            OreSpawnIntegrations.LOGGER.warn(
                    "[yung] '{}' is not registered/loaded; the feature that needs it stays inert", what);
        }
    }

    /**
     * Lootr soft-hook, classloaded only behind the isLoaded("lootr") guard
     * above. POLICY 4: {@code noobanidus.mods.lootr.common.api.data
     * .blockentity.ILootrBlockEntity extends ILootrInfoProvider extends
     * ILootrInfo}, whose {@code getInfoLootTable()} returns
     * {@code ResourceKey<LootTable>} - javap-verified against
     * lootr-neoforge-1.21.1-1.11.38.123.jar (in libs/ for compileOnly).
     */
    private static final class LootrHook {
        private LootrHook() {}

        static ResourceKey<LootTable> tableId(BlockEntity blockEntity) {
            try {
                if (blockEntity instanceof noobanidus.mods.lootr.common.api.data.blockentity.ILootrBlockEntity lootr) {
                    return lootr.getInfoLootTable();
                }
            } catch (Throwable t) {
                logOnce("lootr_hook", t);
            }
            return null;
        }
    }
}
