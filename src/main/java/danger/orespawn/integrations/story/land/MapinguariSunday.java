package danger.orespawn.integrations.story.land;

import com.mojang.serialization.Codec;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import danger.orespawn.integrations.story.Ranks;
import danger.orespawn.integrations.story.StoryClock;
import danger.orespawn.integrations.story.StoryCore;
import danger.orespawn.integrations.story.StoryScheduler;
import danger.orespawn.integrations.story.court.CourtRanks;
import danger.orespawn.integrations.story.firsthour.EggLabel;
import danger.orespawn.integrations.story.firsthour.FirstHourUtil;
import danger.orespawn.integrations.story.hunt.WallOfTheWanted;
import danger.orespawn.integrations.story.week.Almanac;
import danger.orespawn.integrations.story.week.LandsWeek;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * The Mapinguari's Sunday (npc-mapinguari, 0.10.1): the keeper of the Land's day of rest. On a Sunday of the Land's
 * Week, a player's kills of the Land's own animals ({@code #orespawn_integrations:bichos_da_terra}) away from home are
 * counted: the second brings the cobra-coral's warning (one tagged, never-saved snake for 60 s and the actionbar
 * naming the toll), the third one Mapinguari at dusk within 32 blocks (the Mapinguary in snowy or taiga country),
 * if the Almanac lets him have the night. A player below Squire or in their first 3 game days hears the grace line
 * instead. Home is never hunting: a kill within 64 blocks of the killer's respawn point, or of an animal bred by a
 * player (tagged {@code cc_criado} at birth by our {@code BabyEntitySpawnEvent} handler), never counts; the Parish
 * Stone half of the yard rule is the parish system's ({@link #nearSignedStone}).
 *
 * <p>The toll: while a tagged Sunday Mapinguari ({@code cc_domingo}) lives, once a second, raw meat dropped within 8
 * blocks of him is counted, three items (one for a Mapinguari-origin offender); he eats it with his roar, stops
 * targeting anyone and leaves 30 s later. At dawn the natural Mapinguaris with no target are discarded, and at
 * Monday's dawn each player's Sunday is settled: three clean Sundays in a row leave his shed fang at their bed and
 * grant The Sunday Fang. The count and the streak live in the player attachment {@code cc_domingo}; the toggle is the
 * Land's Week's ({@code story.lands_week}).</p>
 */
public final class MapinguariSunday {

    static final String KEY = "orespawn_integrations.mapinguari.";
    public static final String BRED_TAG = "cc_criado";
    public static final String SUNDAY_TAG = "cc_domingo";
    public static final String WARNING_TAG = "cc_aviso";
    static final ResourceLocation MAPINGUARI = ResourceLocation.fromNamespaceAndPath("brazil_legends", "mapinguari");
    static final ResourceLocation MAPINGUARY = ResourceLocation.fromNamespaceAndPath("brazil_legends", "mapinguary");
    static final TagKey<EntityType<?>> BICHOS = TagKey.create(Registries.ENTITY_TYPE, FirstHourUtil.oi("bichos_da_terra"));
    static final TagKey<Item> RAW_MEAT = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "foods/raw_meat"));
    static final TagKey<Biome> SNOWY = TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("c", "is_snowy"));
    static final int HOME_RADIUS = 64;
    static final int GRACE_TICKS = 3 * 24000;

    /** The yard half the parish system fills: true when {@code pos} is within 48 blocks of a stone the player signed. */
    public static volatile BiPredicate<ServerPlayer, BlockPos> nearSignedStone = (p, pos) -> false;

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, OreSpawnIntegrations.MODID);

    /**
     * {@code cc_domingo}: [0] the Land's day of the Sunday counted, [1] kills that Sunday, [2] the clean-Sunday streak,
     * [3] the last Sunday settled, [4] flags (1 a summon waits for dusk, 2 the warning came, 4 a first Mapinguari came).
     */
    public static final Supplier<AttachmentType<int[]>> STATE = TYPES.register("cc_domingo",
            () -> AttachmentType.builder(() -> new int[]{-1, 0, 0, -1, 0})
                    .serialize(Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of)).copyOnDeath().build());

    static final int PENDING = 1;
    static final int WARNED = 2;

    /** Live Sunday giants: their UUID and their offender. Transient: the dawn check clears any left after a restart. */
    private static final Map<UUID, UUID> GIANTS = new HashMap<>();
    /** Giants that took the toll: no target, gone soon. */
    private static final List<UUID> PAID = new ArrayList<>();

    private MapinguariSunday() {
    }

    public static void init(IEventBus modBus) {
        TYPES.register(modBus);
        NeoForge.EVENT_BUS.addListener(MapinguariSunday::onDeath);
        NeoForge.EVENT_BUS.addListener(MapinguariSunday::onBaby);
        NeoForge.EVENT_BUS.addListener(MapinguariSunday::onTarget);
        NeoForge.EVENT_BUS.addListener(MapinguariSunday::onJoin);
        NeoForge.EVENT_BUS.addListener(MapinguariSunday::onLogin);
        StoryClock.everySecondPerPlayer("mapinguari_dusk", MapinguariSunday::dusk);
        StoryClock.everySecond("mapinguari_toll", MapinguariSunday::toll);
        LandsWeek.onDawn("mapinguari", MapinguariSunday::dawn);
    }

    /** {@code story.lands_week}, the Land's Week's own switch, read by name so this compiles before it exists. */
    static boolean on() {
        try {
            Object value = IntegrationsConfig.SPEC.getValues().get(List.of("story", "lands_week"));
            if (value instanceof ModConfigSpec.BooleanValue b) {
                return IntegrationsConfig.on(b);
            }
        } catch (Throwable ignored) {
            // the switch isn't there yet: the Land's Week is on by default
        }
        return true;
    }

    // --- the Sunday count ---

    private static void onBaby(BabyEntitySpawnEvent event) {
        // Any animal born of breeding is home-raised, whoever paired the parents.
        if (event.getChild() != null) {
            event.getChild().addTag(BRED_TAG);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        try {
            LivingEntity dead = event.getEntity();
            if (dead.level().isClientSide() || !on()) {
                return;
            }
            MinecraftServer server = dead.getServer();
            if (server == null || LandsWeek.today(server) != LandsWeek.SUNDAY || !dead.getType().is(BICHOS)) {
                return;
            }
            ServerPlayer killer = WallOfTheWanted.creditOf(dead);
            if (killer == null || dead.getTags().contains(BRED_TAG) || atHome(killer, dead.blockPosition())) {
                return;
            }
            count(killer);
        } catch (Throwable t) {
            StoryCore.logOnce("mapinguari_count", t);
        }
    }

    /** Home: within 64 blocks of the killer's respawn point (same dimension), or 48 of a Parish Stone they signed. */
    public static boolean atHome(ServerPlayer player, BlockPos pos) {
        BlockPos bed = player.getRespawnPosition();
        if (bed != null && player.getRespawnDimension() == player.level().dimension()
                && bed.distSqr(pos) <= (double) HOME_RADIUS * HOME_RADIUS) {
            return true;
        }
        return nearSignedStone.test(player, pos);
    }

    /** One Sunday kill counted: the warning at two, the dusk summon (or the grace line) at three. */
    public static int count(ServerPlayer player) {
        int sunday = CourtRanks.landDay(player.getServer());
        int[] s = player.getData(STATE.get()).clone();
        if (s[0] != sunday) {
            s[0] = sunday;
            s[1] = 0;
            s[4] &= ~(PENDING | WARNED);
        }
        s[1]++;
        if (s[1] == 2 && (s[4] & WARNED) == 0) {
            s[4] |= WARNED;
            warn(player);
        } else if (s[1] == 3) {
            if (grace(player)) {
                FirstHourUtil.actionbar(player, Component.translatable(KEY + "grace"));
            } else {
                s[4] |= PENDING;
            }
        }
        player.setData(STATE.get(), s);
        return s[1];
    }

    /** Hatchling grace: below Squire, or in the player's first 3 game days of play. */
    static boolean grace(ServerPlayer player) {
        return !Ranks.atLeast(player, Ranks.SQUIRE)
                || player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) < GRACE_TICKS;
    }

    /** The cobra-coral's warning: one tagged snake 4-6 blocks ahead, never saved, gone after 60 s; the toll named. */
    static void warn(ServerPlayer player) {
        FirstHourUtil.actionbar(player, Component.translatable("orespawn_integrations.cobra_coral.sunday_warning"));
        FirstHourUtil.award(player, "lands_week/nao_encosta");
        ResourceLocation snakeId = ResourceLocation.fromNamespaceAndPath("brasilcoisas", "cobra_coral");
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(snakeId)) {
            return;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(snakeId);
        ServerLevel level = player.serverLevel();
        var look = player.getLookAngle().multiply(1, 0, 1).normalize().scale(5);
        BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(player.getX() + look.x, player.getY(), player.getZ() + look.z));
        if (!level.hasChunkAt(at)) {
            return;
        }
        Entity snake = type.spawn(level, at, MobSpawnType.EVENT);
        if (snake != null) {
            snake.addTag(WARNING_TAG);
            snake.addTag("cc_event_npc");
            StoryScheduler.schedule(level.getServer(), 60 * 20, "cobra_coral_warning", () -> {
                if (snake.isAlive()) {
                    snake.discard();
                }
            });
        }
    }

    // --- dusk: the summon ---

    private static void dusk(ServerPlayer player) {
        int[] s = player.getData(STATE.get());
        if ((s[4] & PENDING) == 0 || !on()) {
            return;
        }
        MinecraftServer server = player.getServer();
        long time = server.overworld().getDayTime() % 24000L;
        if (time < 12000L || time >= 13800L) {
            return;
        }
        int[] next = s.clone();
        next[4] &= ~PENDING;
        player.setData(STATE.get(), next);
        if (player.level().dimension() != Level.OVERWORLD || !Almanac.claimNight(server, "mapinguari")) {
            return;
        }
        summon(player);
    }

    /** One tagged giant within 32 blocks of the player, on loaded ground: the Mapinguary in snowy or taiga country. */
    public static Mob summon(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        var biome = level.getBiome(player.blockPosition());
        ResourceLocation id = biome.is(SNOWY) || biome.is(BiomeTags.IS_TAIGA) ? MAPINGUARY : MAPINGUARI;
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        for (int tries = 0; tries < 8; tries++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            int d = 16 + level.random.nextInt(16);
            BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(player.getX() + Math.cos(a) * d, player.getY(), player.getZ() + Math.sin(a) * d));
            if (!level.hasChunkAt(at) || !level.getBlockState(at.below()).isSolid()) {
                continue;
            }
            Entity e = type.spawn(level, at, MobSpawnType.EVENT);
            if (e instanceof Mob giant) {
                giant.addTag(SUNDAY_TAG);
                giant.setTarget(player);
                GIANTS.put(giant.getUUID(), player.getUUID());
                int[] s = player.getData(STATE.get()).clone();
                s[4] |= 4;
                player.setData(STATE.get(), s);
                return giant;
            }
        }
        return null;
    }

    // --- the toll ---

    private static void toll(MinecraftServer server) {
        if (GIANTS.isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        for (Map.Entry<UUID, UUID> e : List.copyOf(GIANTS.entrySet())) {
            Entity giant = level.getEntity(e.getKey());
            if (giant == null || !giant.isAlive()) {
                GIANTS.remove(e.getKey());
                continue;
            }
            if (PAID.contains(giant.getUUID())) {
                continue;
            }
            ServerPlayer offender = server.getPlayerList().getPlayer(e.getValue());
            boolean kin = offender != null && offender.getTags().contains(EggLabel.ORIGIN_TAG + "mapinguari");
            int wanted = kin ? 1 : 3;
            List<ItemEntity> meat = level.getEntitiesOfClass(ItemEntity.class, giant.getBoundingBox().inflate(8),
                    i -> i.getItem().is(RAW_MEAT));
            int have = 0;
            for (ItemEntity i : meat) {
                have += i.getItem().getCount();
            }
            if (have < wanted) {
                continue;
            }
            int left = wanted;
            for (ItemEntity i : meat) {
                ItemStack stack = i.getItem();
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
                if (stack.isEmpty()) {
                    i.discard();
                }
                if (left <= 0) {
                    break;
                }
            }
            paid((Mob) giant, offender, kin);
        }
    }

    static void paid(Mob giant, ServerPlayer offender, boolean kin) {
        PAID.add(giant.getUUID());
        giant.setTarget(null);
        SoundEvent roar = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("brazil_legends", "mapinguariroar"));
        if (roar != null) {
            giant.level().playSound(null, giant.blockPosition(), roar, SoundSource.HOSTILE, 2.0F, 1.0F);
        }
        if (offender != null) {
            offender.sendSystemMessage(Component.translatable(KEY + (kin ? "toll_kin" : "toll")));
        }
        StoryScheduler.schedule(giant.getServer(), 30 * 20, "mapinguari_leaves", () -> {
            PAID.remove(giant.getUUID());
            GIANTS.remove(giant.getUUID());
            if (giant.isAlive()) {
                giant.discard();
            }
        });
    }

    /** The target veto on a giant that took the toll. */
    private static void onTarget(LivingChangeTargetEvent event) {
        if (!PAID.isEmpty() && PAID.contains(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    /** A warning snake is never saved: one left over from before a restart is refused when it loads. */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(WARNING_TAG)) {
            event.setCanceled(true);
        }
    }

    // --- dawn ---

    static void dawn(MinecraftServer server) {
        if (!on()) {
            return;
        }
        // Natural giants with no target are gone at dawn (they no longer pile up); a Sunday one always goes.
        for (Entity e : server.overworld().getAllEntities()) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            if ((MAPINGUARI.equals(id) || MAPINGUARY.equals(id)) && e instanceof Mob mob
                    && (mob.getTags().contains(SUNDAY_TAG) || mob.getTarget() == null)) {
                mob.discard();
            }
        }
        GIANTS.clear();
        PAID.clear();
        int today = LandsWeek.today(server);
        if (today == LandsWeek.SUNDAY) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.level().dimension() == Level.OVERWORLD) {
                    StoryScheduler.schedule(server, 60, "mapinguari_sunday_line",
                            () -> FirstHourUtil.actionbar(p, Component.translatable(KEY + "sunday")));
                }
            }
        } else if (today == LandsWeek.MONDAY) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                settle(p, CourtRanks.landDay(server) - 1);
            }
        }
    }

    /** A player who logs in after missing Monday's dawn has last Sunday settled now. */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer p && on()) {
                MinecraftServer server = p.getServer();
                int fromMonday = (LandsWeek.today(server) + 6) % 7;
                int lastSunday = CourtRanks.landDay(server) - fromMonday - 1;
                if (LandsWeek.today(server) != LandsWeek.SUNDAY && p.getData(STATE.get())[3] < lastSunday) {
                    settle(p, lastSunday);
                }
            }
        } catch (Throwable t) {
            StoryCore.logOnce("mapinguari_login", t);
        }
    }

    /** Settles the Sunday on Land's day {@code sunday}: clean adds one to the streak, three leave the fang. */
    public static void settle(ServerPlayer player, int sunday) {
        int[] s = player.getData(STATE.get()).clone();
        if (s[3] >= sunday) {
            return;
        }
        s[3] = sunday;
        boolean clean = s[0] != sunday || s[1] == 0;
        s[2] = clean ? s[2] + 1 : 0;
        if (s[2] >= 3) {
            s[2] = 0;
            leaveFang(player);
        }
        player.setData(STATE.get(), s);
    }

    /** The shed fang at the player's bed (their respawn point, when loaded), else in their hands; and the advancement. */
    static void leaveFang(ServerPlayer player) {
        ItemStack fang = FirstHourUtil.stack("brazil_legends:mapinguari_fang", 1);
        if (!fang.isEmpty()) {
            BlockPos bed = player.getRespawnPosition();
            ServerLevel level = player.getServer().getLevel(player.getRespawnDimension());
            if (bed != null && level != null && level.hasChunkAt(bed)) {
                level.addFreshEntity(new ItemEntity(level, bed.getX() + 0.5, bed.getY() + 1.0, bed.getZ() + 0.5, fang));
            } else {
                FirstHourUtil.give(player, fang);
            }
        }
        FirstHourUtil.award(player, "lands_week/dente_de_domingo");
    }
}
