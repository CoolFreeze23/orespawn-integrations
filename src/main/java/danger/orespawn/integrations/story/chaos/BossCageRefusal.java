package danger.orespawn.integrations.story.chaos;

import danger.orespawn.entity.EntityCage;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.util.MyUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The boss cage refusal (npc-mobzilla, decision J2): OreSpawn's cage takes no boss. The port's empty cage pressed on
 * a mob ({@code EmptyCageItem.interactLivingEntity}) takes any mob, every boss included, and its thrown cage
 * ({@code EntityCage}, entity {@code orespawn:cage}) takes about 110 listed classes. One listener refuses both paths for
 * every mob in {@code #orespawn_integrations:no_copies}: the pressed cage stays in hand, and the thrown one drops as an
 * empty cage where it hit, the way the port drops a cage the mob escaped from. A zombie is still caged both ways.
 * Multipart bosses are read through their parent, as the cage itself does ({@code MyUtils.behindPart}).
 * Switch: {@code bigame.boss_cage_refusal}.
 */
public final class BossCageRefusal {

    public static final TagKey<EntityType<?>> NO_COPIES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "no_copies"));
    static final ResourceLocation EMPTY_CAGE = ResourceLocation.fromNamespaceAndPath("orespawn", "cage_empty");
    static final String LINE = "orespawn_integrations.mobzilla.cage";

    private BossCageRefusal() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BossCageRefusal::onPress);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BossCageRefusal::onThrownHit);
    }

    /** The mob a cage would take for {@code target}: the parent of a part. */
    public static Entity body(Entity target) {
        if (target instanceof EnderDragonPart part) {
            return part.parentMob;
        }
        Entity behind = MyUtils.behindPart(target);
        return behind == null ? target : behind;
    }

    public static boolean refuses(Entity target) {
        return IntegrationsConfig.on(IntegrationsConfig.bigameBossCageRefusal) && body(target).getType().is(NO_COPIES);
    }

    private static void onPress(PlayerInteractEvent.EntityInteract event) {
        ItemStack held = event.getItemStack();
        if (!BuiltInRegistries.ITEM.getKey(held.getItem()).equals(EMPTY_CAGE)) {
            return;
        }
        try {
            if (refuses(event.getTarget())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                if (event.getEntity() instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.translatable(LINE), true);
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("cage_refusal.press", t);
        }
    }

    private static void onThrownHit(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof EntityCage cage) || !(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        try {
            if (!refuses(hit.getEntity())) {
                return;
            }
            event.setCanceled(true);
            if (cage.level().isClientSide) {
                return;
            }
            Item empty = BuiltInRegistries.ITEM.get(EMPTY_CAGE);
            Block.popResource(cage.level(), cage.blockPosition(), new ItemStack(empty));
            cage.discard();
            if (cage.getOwner() instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable(LINE), true);
            }
        } catch (Throwable t) {
            StoryCore.logOnce("cage_refusal.thrown", t);
        }
    }
}
