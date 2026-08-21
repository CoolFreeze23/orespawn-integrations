package danger.orespawn.integrations.compat.statues;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.shynieke.statues.blockentities.StatueBlockEntity;
import com.shynieke.statues.blocks.AbstractStatueBase;
import com.shynieke.statues.items.StatueBlockItem;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Thread 5 "The World Remembers" — the Mobzilla Statue: a full first-class
 * Statues-mod statue of OreSpawn's Godzilla, carved from a humble zombie
 * statue with Godzilla scales, uranium, and a statue core. It rides the
 * Statues mod's whole progression untouched: statue-table upgrades (including
 * this addon's uranium mob-killer catalyst, pure data in
 * {@code data/orespawn_integrations/recipe/upgrade/}), the LOOTING upgrade
 * dripping {@code orespawn:godzilla_scale} (a {@code statues:loot} recipe),
 * and — the thread's flex — the SPAWNER upgrade, which lets a fully-ground
 * statue summon the real thing. The world remembers, and if you feed the
 * memory enough uranium it remembers OUT LOUD. North star: every step is a
 * reward for engaging with both mods' grinds; nothing here punishes.
 *
 * <p>POLICY 4 — light-code integration, verified by javap against
 * {@code Statues-1.21.1-0.4.18.jar} (pack mods folder; compileOnly via
 * {@code libs/}):
 * <ul>
 *   <li>{@code blocks.AbstractStatueBase} — public ctor
 *       {@code (BlockBehaviour.Properties)} (bytecode forces
 *       {@code strength(0.6F)} and defaults FACING=north, WATERLOGGED=false,
 *       INTERACTIVE=false, so properties only need a map color, matching
 *       their own {@code blockBuilder()} which passes
 *       {@code Properties.of().mapColor(...)} alone). {@code getEntity()}
 *       defaults to {@code EntityType.EGG} and {@code getSound} to
 *       {@code ANVIL_LAND} — both overridden here with registry-guarded
 *       OreSpawn lookups.</li>
 *   <li>CRITICAL — own BlockEntityType: upstream
 *       {@code newBlockEntity} binds {@code new StatueBlockEntity(pos,state)}
 *       to the {@code statues:statue} BE type whose valid-blocks set is fixed
 *       at registration. Vanilla 1.21.1 {@code LevelChunk} gates both
 *       {@code setBlockEntity} and the ticker on
 *       {@code BlockEntityType.isValid(state)}, so reusing their type means
 *       the statue NEVER ticks (no mob-killer, no loot drip). Statues exposes
 *       {@code StatueBlockEntity(BlockEntityType<?>,BlockPos,BlockState)}
 *       (public, javap-verified) exactly for this; we register
 *       {@code orespawn_integrations:mobzilla_statue} bound to our block and
 *       route {@code newBlockEntity}/{@code getTicker} through it.
 *       {@code getTicker} mirrors upstream bytecode: null unless the
 *       blockstate's INTERACTIVE is true, else
 *       {@code createStatueTicker(level, givenType, ourType)} (protected
 *       static, reachable from our subclass).</li>
 *   <li>{@code items.StatueBlockItem} (public ctor {@code (Block,
 *       Item.Properties)}) — REQUIRED, not optional: its
 *       {@code getPlacementState} bytecode sets INTERACTIVE=true when the
 *       stack carries the UPGRADED data component. A plain BlockItem would
 *       place upgraded statues inert forever.</li>
 *   <li>Capabilities — {@code registry.StatueBlockEntities
 *       .registerCapabilities} bytecode registers
 *       {@code Capabilities.EnergyStorage.BLOCK} for the plain statue type
 *       via {@code AbstractStatueBlockEntity.getEnergyStorage(Direction)}
 *       (ItemHandler.BLOCK goes only to shulker statue + statue table), so
 *       that is exactly what our type mirrors.</li>
 *   <li>Creative tab — Statues registers {@code statues:blocks}
 *       ("itemGroup.statues.blocks"); we append via
 *       {@link BuildCreativeModeTabContentsEvent} against the tab's
 *       ResourceKey, no compile dep on their registry class.</li>
 *   <li>Recipe conventions — the statue-table center slot filters on item
 *       tag {@code statues:statues/upgradeable}
 *       ({@code StatueTableBlockEntity$1} bytecode), which our datapack tag
 *       joins (optional entries, ungated — tags are the sanctioned
 *       exception). The crafting recipe is plain
 *       {@code minecraft:crafting_shaped} mirroring their player-statue
 *       pattern slot-for-slot, NOT {@code statues:hardcore_shaped} — that
 *       serializer's {@code matches()} bytecode also requires
 *       {@code LevelData.isHardcore()}, i.e. it only ever crafts in hardcore
 *       worlds. Deliberately NOT joined: item tag {@code statues:statues} —
 *       {@code CityStatuesLootModifier} draws ancient-city chest statues
 *       from it, which would leak the trophy past its catalyst grind.</li>
 * </ul>
 * Version-range note: verified against Statues 0.4.18; re-verify the
 * BE-type/ticker plumbing and the capability set on any Statues bump.
 *
 * <p>No client code at all — Statues renders plain statues as baked JSON
 * models (their {@code client/ber} package only has a BER for the statue
 * TABLE), so our blockstate/model/texture JSONs carry the whole client side
 * and no {@code FMLEnvironment.dist} guard is needed here.
 *
 * <p>Only ever classloaded when the statues mod is present — the main mod
 * class invokes {@link #init(IEventBus)} reflectively after a ModList check,
 * so the direct references to {@code com.shynieke.statues.*} above are safe.
 * OreSpawn ids used: entity {@code orespawn:godzilla}, sound
 * {@code orespawn:godzilla_living} (both verified against ORESPAWN-IDS.json /
 * the port's sounds.json), each resolved defensively with a log-once miss.
 * Block/item registration is unconditional once statues is present (same
 * pattern as the decoy mines); recipes and the upgrade catalysts gate on
 * {@code neoforge:mod_loaded} + the Thread-5
 * {@code orespawn_integrations:thread_enabled "world_remembers"} condition;
 * the block's self-drop loot table stays ungated so a mid-world config flip
 * never voids the trophy (north star: never punish).
 */
public final class MobzillaStatueCompat {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(OreSpawnIntegrations.MODID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(OreSpawnIntegrations.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, OreSpawnIntegrations.MODID);

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    /** Statues' "blocks" creative tab, referenced by id — no compile dep needed. */
    private static final ResourceKey<CreativeModeTab> STATUES_BLOCKS_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath("statues", "blocks"));

    public static final DeferredBlock<MobzillaStatueBlock> MOBZILLA_STATUE =
            BLOCKS.register("mobzilla_statue", () -> new MobzillaStatueBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN)));

    /**
     * MUST be a {@link StatueBlockItem}: its getPlacementState carries the
     * UPGRADED data component into the INTERACTIVE blockstate flag (see class
     * header). Registered under the same path so block/item ids line up.
     */
    public static final DeferredItem<StatueBlockItem> MOBZILLA_STATUE_ITEM =
            ITEMS.register("mobzilla_statue",
                    () -> new StatueBlockItem(MOBZILLA_STATUE.get(), new Item.Properties()));

    /**
     * Our OWN BE type wired with statues' public
     * {@code StatueBlockEntity(BlockEntityType, BlockPos, BlockState)} ctor —
     * the qualified self-reference inside the lambda is only evaluated after
     * registration, the standard vanilla BE-type pattern.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StatueBlockEntity>> MOBZILLA_STATUE_BE =
            BLOCK_ENTITIES.register("mobzilla_statue",
                    () -> BlockEntityType.Builder.<StatueBlockEntity>of(
                            (pos, state) -> new StatueBlockEntity(
                                    MobzillaStatueCompat.MOBZILLA_STATUE_BE.get(), pos, state),
                            MOBZILLA_STATUE.get()).build(null));

    private MobzillaStatueCompat() {
    }

    /** Invoked reflectively by the main mod class when statues is loaded. */
    public static void init(IEventBus modBus) {
        // orespawn is a required dep of the whole addon; belt-and-braces only.
        if (!ModList.get().isLoaded("orespawn")) {
            OreSpawnIntegrations.LOGGER.warn("Mobzilla statue skipped - orespawn not loaded");
            return;
        }
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(MobzillaStatueCompat::registerCapabilities);
        modBus.addListener(MobzillaStatueCompat::addToCreativeTabs);
    }

    /**
     * Mirrors exactly what Statues gives its own plain-statue BE type:
     * EnergyStorage.BLOCK backed by AbstractStatueBlockEntity.getEnergyStorage
     * (their SPEED upgrade's power hookup). They give ItemHandler.BLOCK only
     * to the shulker statue and the table, so we do not invent one.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        try {
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MOBZILLA_STATUE_BE.get(),
                    (be, side) -> be.getEnergyStorage(side));
        } catch (Throwable t) {
            logOnce("registerCapabilities", t);
        }
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        try {
            if (event.getTabKey() == STATUES_BLOCKS_TAB) {
                event.accept(MOBZILLA_STATUE_ITEM);
            }
        } catch (Throwable t) {
            logOnce("addToCreativeTabs", t);
        }
    }

    // --- defensive plumbing (house style, mirrors AliveWorldCompat) ---

    static void logOnce(String key, Throwable t) {
        if (LOGGED.add(key)) {
            OreSpawnIntegrations.LOGGER.error(
                    "[mobzilla-statue] '{}' failed; muting further reports of this failure", key, t);
        }
    }

    static void logMissingOnce(String what) {
        if (LOGGED.add("missing:" + what)) {
            OreSpawnIntegrations.LOGGER.warn(
                    "[mobzilla-statue] '{}' is not registered/loaded; falling back to the statues default",
                    what);
        }
    }

    /**
     * The statue block itself. Everything gameplay-bearing is inherited from
     * AbstractStatueBase — this class only points the machinery at Godzilla
     * and at our own BE type (see class header for why both are required).
     */
    public static final class MobzillaStatueBlock extends AbstractStatueBase {

        private static final ResourceLocation GODZILLA_ID =
                ResourceLocation.fromNamespaceAndPath("orespawn", "godzilla");
        private static final ResourceLocation GODZILLA_LIVING_ID =
                ResourceLocation.fromNamespaceAndPath("orespawn", "godzilla_living");

        public MobzillaStatueBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }

        /**
         * Drives the statue's identity: mob-killer credit, the SPAWNER
         * upgrade's summon, and Jade/JEI displays. containsKey guard because
         * ENTITY_TYPE is a defaulted registry and a bare get() would silently
         * hand back the default entry (house rule, per AliveWorldCompat).
         */
        @Override
        public EntityType<?> getEntity() {
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(GODZILLA_ID)) {
                logMissingOnce("entity " + GODZILLA_ID);
                return super.getEntity();
            }
            return BuiltInRegistries.ENTITY_TYPE.get(GODZILLA_ID);
        }

        /** SOUND_EVENT is not defaulted — a plain null check suffices. */
        @Override
        public SoundEvent getSound(BlockState state) {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(GODZILLA_LIVING_ID);
            if (sound == null) {
                logMissingOnce("sound " + GODZILLA_LIVING_ID);
                return super.getSound(state);
            }
            return sound;
        }

        /** Bind to OUR type, not statues' — see class header (ticking gate). */
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new StatueBlockEntity(MOBZILLA_STATUE_BE.get(), pos, state);
        }

        /**
         * Mirrors upstream getTicker bytecode (ticker only while the placed
         * state is INTERACTIVE, i.e. the statue has been through the table),
         * but hands createStatueTicker OUR BE type so the vanilla
         * type-validity gate passes and serverTick actually runs.
         */
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                BlockEntityType<T> type) {
            if (!state.getValue(INTERACTIVE)) {
                return null;
            }
            return createStatueTicker(level, type, MOBZILLA_STATUE_BE.get());
        }
    }
}
