package danger.orespawn.integrations.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.event.FamiliarSummonEvent;
import com.hollingsworth.arsnouveau.common.entity.familiar.FamiliarEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * Pocket-sized Girlfriend familiar — the Ars Nouveau wrapper entity summoned by
 * {@link GirlfriendFamiliarHolder}.
 *
 * <p>Extends AN's {@code common.entity.familiar.FamiliarEntity}
 * (ars_nouveau-1.21.1-5.13.0, javap): {@code PathfinderMob + GeoEntity} with
 * follow/owner goals, self-managed lifecycle (ctor adds to
 * {@code FAMILIAR_SET}; {@code tick()} discards when the owner is gone —
 * bytecode-verified), and exactly one abstract member left for subclasses,
 * {@code getTexture()}. The geckolib plumbing
 * ({@code registerControllers}/{@code getAnimatableInstanceCache}) is fully
 * implemented by the base class; it stays dormant because this addon renders
 * the familiar with the port's vanilla humanoid model instead of a geo model
 * (see {@code ArsFamiliarClient}) — the port's Girlfriend is a
 * {@code HumanoidMobRenderer}, not a geckolib entity (mirror
 * entity/client/GirlfriendRenderer.java), so reusing its baked layer + skin
 * textures is the faithful look.
 *
 * <p><b>Skins:</b> the port ships 41 girlfriend skins
 * ({@code orespawn:textures/entity/girlfriend<0..40>.png}, mirror
 * Girlfriend.java:87 MAX_SKINS=41). The familiar picks a random skin at
 * construction, then — when the summon event confirms her owner
 * ({@code FamiliarEntity.onFamiliarSpawned} is broadcast for every summon, so
 * the {@code getEntity() == this} guard is required, bytecode-verified) —
 * settles on a skin derived from the owner's UUID: the same player always gets
 * "his" girlfriend back. Synched so the client renderer sees it; persisted in
 * NBT for save/reload within one summon.
 */
public class FamiliarGirlfriend extends FamiliarEntity {

    private static final EntityDataAccessor<Integer> DATA_SKIN =
            SynchedEntityData.defineId(FamiliarGirlfriend.class, EntityDataSerializers.INT);

    /** Mirror Girlfriend.java:87 — girlfriend0.png .. girlfriend40.png. */
    private static final int MAX_SKINS = 41;

    private static final String SKIN_TAG = "OrespawnIntegrationsSkin";

    /** Weakness II — amplifier 1 — for 5 seconds (100 ticks). */
    private static final int JEALOUSY_WEAKNESS_TICKS = 100;
    private static final int JEALOUSY_WEAKNESS_AMPLIFIER = 1;
    /** Retaliation rate limit so a mob crowd cannot spam particles. */
    private static final int JEALOUSY_COOLDOWN_TICKS = 40;

    private long lastJealousyGameTime = Long.MIN_VALUE;

    public FamiliarGirlfriend(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.entityData.set(DATA_SKIN, this.random.nextInt(MAX_SKINS));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN, 0);
    }

    public int getSkin() {
        return Mth.clamp(this.entityData.get(DATA_SKIN), 0, MAX_SKINS - 1);
    }

    /**
     * IFamiliar hook, broadcast to every live familiar on each summon (that is
     * how AN terminates the previous familiar). For our own summon, lock the
     * skin to the owner's UUID so she is recognizably the same girl every time.
     */
    @Override
    public void onFamiliarSpawned(FamiliarSummonEvent event) {
        super.onFamiliarSpawned(event);
        if (!this.level().isClientSide && event.getEntity() == this && event.owner != null) {
            this.entityData.set(DATA_SKIN,
                    Math.floorMod(event.owner.getUUID().hashCode(), MAX_SKINS));
        }
    }

    /** Reused by the client renderer; port skin textures, verified on disk. */
    @Override
    public ResourceLocation getTexture() {
        return ResourceLocation.fromNamespaceAndPath("orespawn",
                "textures/entity/girlfriend" + this.getSkin() + ".png");
    }

    /**
     * The jealousy payoff, invoked by {@code ArsFamiliarCompat.onOwnerDamaged}
     * (server side): Weakness II for 5s on whoever hurt her player, plus angry
     * particles over her head. Cooldown-limited; reward-not-punish — the buff
     * only ever lands on the owner's attacker.
     */
    public void defendOwner(LivingEntity attacker) {
        if (this.level().isClientSide || !attacker.isAlive()) {
            return;
        }
        long now = this.level().getGameTime();
        if (now - this.lastJealousyGameTime < JEALOUSY_COOLDOWN_TICKS) {
            return;
        }
        this.lastJealousyGameTime = now;
        attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                JEALOUSY_WEAKNESS_TICKS, JEALOUSY_WEAKNESS_AMPLIFIER));
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    this.getX(), this.getEyeY() + 0.35, this.getZ(),
                    6, 0.25, 0.25, 0.25, 0.02);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(SKIN_TAG, this.getSkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(SKIN_TAG)) {
            this.entityData.set(DATA_SKIN, Mth.clamp(tag.getInt(SKIN_TAG), 0, MAX_SKINS - 1));
        }
    }
}
