package danger.orespawn.integrations.compat.securitycraft;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.geforcemods.securitycraft.blocks.mines.BaseFullMineBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Thread 4 "Big Game" — Decoy Ore Mines: four SecurityCraft mines disguised as
 * OreSpawn's most tempting ores, plus the "Suspicious Ore" trophy block a
 * defused mine converts into. North star: this is a base-defense fantasy with a
 * FAIRNESS TELL — an attentive raider sees a subtle shimmer (and hears a faint
 * tick) and gets real counterplay: defuse it with wire cutters and keep the
 * trophy. Never a gotcha with no outs.
 *
 * <p>POLICY 4 — light-code integration, verified against
 * {@code SecurityCraft-1.21.1-v1.10.1.jar} (pack mods folder, javap on the
 * shipped classes; compileOnly via {@code libs/}):
 * <ul>
 *   <li>{@code blocks.mines.BaseFullMineBlock} — public ctor
 *       {@code (BlockBehaviour.Properties, Block blockDisguisedAs)}; nothing in
 *       the hierarchy overrides {@code animateTick}, so ours is free to add the
 *       tell; {@code isDefusable()} and {@code defuseMine(Level, BlockPos)} are
 *       stubbed to {@code false} upstream and re-enabled here;
 *       {@code explode(Level, BlockPos)} hardcodes strength 2.5F/5.0F from SC
 *       config, so it is overridden here for a tuned ~2.8F;
 *       {@code newBlockEntity} upstream returns an {@code OwnableBlockEntity}
 *       whose {@code BlockEntityType.validBlocks} would not contain our block
 *       (1.21.1 {@code LevelChunk.setBlockEntity} rejects invalid BEs), so it
 *       is nulled here — decoy mines are ownerless by design.</li>
 *   <li>{@code blocks.mines.ExplosiveBlock.useItemOn} — the inherited wire
 *       cutters path: {@code isActive && isDefusable -> defuseMine}; on true it
 *       damages the cutters and plays SHEEP_SHEAR itself, so
 *       {@link DecoyOreMineBlock#defuseMine} only swaps in the trophy.</li>
 *   <li>{@code SCCreativeModeTabs} — SC's mine tab is registered as
 *       {@code securitycraft:mine}; its display list is built only from SC's
 *       own item groups, so appending via
 *       {@link BuildCreativeModeTabContentsEvent} cannot duplicate.</li>
 * </ul>
 * Version-range note: verified v1.10.1; re-verify the four overrides on any
 * SecurityCraft bump.
 *
 * <p>Null-BE consequences (bytecode-checked in v1.10.1): with no block entity,
 * {@code entityInside} no-ops (stepping on a decoy is harmless — the trap is
 * MINING what looks like ore), {@code onDestroyedByPlayer} always takes the
 * non-owner branch (survival mining detonates; creative never does unless SC's
 * own config says so), and {@code OwnableBlock.getDestroyProgress} falls back
 * to vanilla speed. All intended.
 *
 * <p>Only ever classloaded when the securitycraft mod is present — the main mod
 * class invokes {@link #init(IEventBus)} reflectively. Blocks mimic
 * {@code orespawn:ore_ruby}, {@code orespawn:ore_titanium},
 * {@code orespawn:tigers_eye_ore}, {@code orespawn:ore_uranium} (ids verified
 * against ORESPAWN-IDS.json; orespawn is a required dep ordered BEFORE us, so
 * its blocks are registered by the time our block suppliers run). Recipes and
 * tags gate on {@code neoforge:mod_loaded} + the Thread-4
 * {@code orespawn_integrations:thread_enabled "big_game"} condition; block
 * self-drop loot tables stay ungated so a mid-world config flip never voids
 * drops (north star: never punish).
 */
public final class DecoyOreMinesCompat {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(OreSpawnIntegrations.MODID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(OreSpawnIntegrations.MODID);

    /** SC's "Block Mines" creative tab, referenced by id — no compile dep needed. */
    private static final ResourceKey<CreativeModeTab> SC_MINE_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath("securitycraft", "mine"));

    private static final String TOOLTIP_KEY = "item.orespawn_integrations.decoy_mine.tooltip";

    // Strengths mirror the mimicked ores (orespawn-mirror ModBlocks.java) so the
    // decoys mine EXACTLY like the real thing until the last half-heart of progress.
    public static final DeferredBlock<Block> RUBY_ORE_MINE =
            registerMine("ruby_ore_mine", "ore_ruby", 10.0F, 4.0F);
    public static final DeferredBlock<Block> TITANIUM_ORE_MINE =
            registerMine("titanium_ore_mine", "ore_titanium", 15.0F, 5.0F);
    public static final DeferredBlock<Block> TIGERS_EYE_ORE_MINE =
            registerMine("tigers_eye_ore_mine", "tigers_eye_ore", 15.0F, 60.0F);
    public static final DeferredBlock<Block> URANIUM_ORE_MINE =
            registerMine("uranium_ore_mine", "ore_uranium", 10.0F, 1.0F);

    /**
     * The defusal trophy: proof somebody tried to bait you and you out-played
     * them. Plain decorative block, stone-ish stats, hand-breakable (no tool
     * gate — a trophy that could vanish to a wrong tool would be a punishment).
     */
    public static final DeferredBlock<Block> SUSPICIOUS_ORE = BLOCKS.register("suspicious_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.STONE)));

    static {
        ITEMS.registerSimpleBlockItem("suspicious_ore", SUSPICIOUS_ORE);
    }

    private DecoyOreMinesCompat() {}

    /** Invoked reflectively by the main mod class when securitycraft is loaded. */
    public static void init(IEventBus modBus) {
        // orespawn is a required dep of the whole addon; belt-and-braces only.
        if (!ModList.get().isLoaded("orespawn")) {
            OreSpawnIntegrations.LOGGER.warn("Decoy ore mines skipped - orespawn not loaded");
            return;
        }
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(DecoyOreMinesCompat::addToCreativeTabs);
    }

    private static DeferredBlock<Block> registerMine(
            String name, String orespawnPath, float hardness, float resistance) {
        DeferredBlock<Block> block = BLOCKS.register(name, () -> new DecoyOreMineBlock(
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.STONE)
                        .strength(hardness, resistance)
                        .requiresCorrectToolForDrops(),
                mimicBlock(orespawnPath)));
        ITEMS.register(name, () -> new DecoyMineBlockItem(block.get(), new Item.Properties()));
        return block;
    }

    /**
     * Resolves the OreSpawn block to disguise as. Our block suppliers run during
     * the BLOCK RegisterEvent after orespawn's (mods.toml orders us AFTER it).
     * A missing id degrades to plain stone with a warning — never a crash.
     */
    private static Block mimicBlock(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("orespawn", path);
        return BuiltInRegistries.BLOCK.getOptional(id).orElseGet(() -> {
            OreSpawnIntegrations.LOGGER.warn(
                    "OreSpawn block {} not found - decoy mine will mimic stone", id);
            return Blocks.STONE;
        });
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == SC_MINE_TAB) {
            event.accept(RUBY_ORE_MINE);
            event.accept(TITANIUM_ORE_MINE);
            event.accept(TIGERS_EYE_ORE_MINE);
            event.accept(URANIUM_ORE_MINE);
            event.accept(SUSPICIOUS_ORE);
        }
    }

    /**
     * The four verified overrides on SecurityCraft's full-block mine (see the
     * class header for the exact upstream behavior each one replaces).
     */
    public static final class DecoyOreMineBlock extends BaseFullMineBlock {

        /** TNT is 4.0F; a scary flash with a shallow crater, per the approved tuning. */
        private static final float EXPLOSION_STRENGTH = 2.8F;

        public DecoyOreMineBlock(BlockBehaviour.Properties properties, Block blockDisguisedAs) {
            super(properties, blockDisguisedAs);
        }

        /**
         * (a) The fairness tell. {@code animateTick} is client-only (called from
         * {@code Level.doAnimateTick}) and hits a given nearby block roughly
         * 0.4-0.5 times a second, so three face attempts per call yields the
         * design's ~1-2 visible end-rod motes a second, plus a faint comparator
         * tick every ~8 seconds. Motes only emit from faces open to air, so a
         * buried mine stays quiet until someone digs close — exactly when the
         * warning matters.
         */
        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            for (int i = 0; i < 3; i++) {
                Direction dir = Direction.getRandom(random);
                BlockPos neighbor = pos.relative(dir);
                if (level.getBlockState(neighbor).isSolidRender(level, neighbor)) {
                    continue;
                }
                double px = pos.getX() + 0.5D + (dir.getAxis() == Direction.Axis.X
                        ? dir.getStepX() * 0.56D : (random.nextDouble() - 0.5D) * 0.8D);
                double py = pos.getY() + 0.5D + (dir.getAxis() == Direction.Axis.Y
                        ? dir.getStepY() * 0.56D : (random.nextDouble() - 0.5D) * 0.8D);
                double pz = pos.getZ() + 0.5D + (dir.getAxis() == Direction.Axis.Z
                        ? dir.getStepZ() * 0.56D : (random.nextDouble() - 0.5D) * 0.8D);
                level.addParticle(ParticleTypes.END_ROD, px, py, pz, 0.0D, 0.008D, 0.0D);
            }
            if (random.nextInt(4) == 0) {
                level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.06F, 1.9F, false);
            }
        }

        /** (b) Re-enable the inherited wire-cutters path (stubbed false upstream). */
        @Override
        public boolean isDefusable() {
            return true;
        }

        /**
         * (b) Defusing CONVERTS the mine into the Suspicious Ore trophy in place.
         * The inherited {@code ExplosiveBlock.useItemOn} handles cutter durability
         * and the shear sound whenever this returns true (on both sides).
         */
        @Override
        public boolean defuseMine(Level level, BlockPos pos) {
            if (!level.isClientSide) {
                level.setBlockAndUpdate(pos, SUSPICIOUS_ORE.get().defaultBlockState());
            }
            return true;
        }

        /**
         * (c) Upstream hardcodes 2.5F/5.0F from SC config; same shape as the
         * upstream body ({@code destroyBlock} first so drops are skipped and
         * {@code wasExploded} chains cannot recurse into this position), tuned
         * strength, never any fire.
         */
        @Override
        public void explode(Level level, BlockPos pos) {
            if (level.isClientSide) {
                return;
            }
            level.destroyBlock(pos, false);
            level.explode(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    EXPLOSION_STRENGTH, Level.ExplosionInteraction.BLOCK);
        }

        /**
         * (d) No block entity: upstream's OwnableBlockEntity type would be
         * rejected for our block by 1.21.1 chunk BE validation. Ownerless is
         * also the design — defusal-first is the counterplay, not owner checks.
         */
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return null;
        }
    }

    /**
     * BlockItem that carries the honesty tooltip. The tooltip is for the CRAFTER
     * (inventory/JEI); the placed block keeps the disguise — SC's overlay hooks
     * show it as the mimicked ore, and the shimmer is the victim's tell.
     */
    static final class DecoyMineBlockItem extends BlockItem {

        DecoyMineBlockItem(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context,
                List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, context, tooltip, flag);
            tooltip.add(Component.translatable(TOOLTIP_KEY)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
