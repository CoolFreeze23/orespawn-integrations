package danger.orespawn.integrations.compat.enchanted;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.favouriteless.enchanted.api.circle_magic.RiteFactory;
import net.favouriteless.enchanted.api.circle_magic.RiteFactoryRegistry;
import net.favouriteless.enchanted.common.enchanted.circle_magic.rites.CommandRite;
import net.favouriteless.enchanted.common.enchanted.circle_magic.rites.Rite;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.IEventBus;

/**
 * Thread 5 "The World Remembers" — Summoning Rites: registers the
 * {@code orespawn_integrations:summon_in_biome} rite factory, a biome-gated
 * wrapper around Enchanted's command rite. The Rite of the Deep (data JSON in
 * {@code data/orespawn_integrations/enchanted/circle_magic/rite/}) uses it to
 * demand an ocean biome for the Kraken; on a mismatch every consumed offering
 * is refunded on the spot and the caster gets a friendly actionbar hint —
 * north star: the world says "not here", never "too bad".
 *
 * <p>Only classloaded when the "enchanted" mod is present —
 * {@link danger.orespawn.integrations.OreSpawnIntegrations} invokes
 * {@link #init(IEventBus)} reflectively after a ModList check, so referencing
 * {@code net.favouriteless.enchanted.*} directly from this class is safe.
 * No client code lives here (rites are pure server logic; JEI/book display of
 * our rites is handled by Enchanted itself), so there is no
 * {@code FMLEnvironment.dist} guard to need.
 *
 * <p>POLICY 4 — light-code integration, verified against
 * {@code enchanted-neoforge-1.21.1-4.2.7.jar} (pack mods folder, javap +
 * bytecode disassembly on the shipped classes; compileOnly via
 * {@code libs/enchanted-neoforge-1.21.1-4.2.7.jar}):
 * <ul>
 *   <li>{@code net.favouriteless.enchanted.api.circle_magic.RiteFactoryRegistry}
 *       — javap-confirmed {@code static RiteFactoryRegistry get()} (bytecode:
 *       plain {@code getstatic RiteFactoryRegistryImpl.INSTANCE}, an eager
 *       static singleton with no event/registry machinery, so calling it at
 *       mod-construction time is safe regardless of Enchanted's own init
 *       order) and {@code void register(ResourceLocation,
 *       MapCodec<? extends RiteFactory>)}. Rite JSONs are decoded at datapack
 *       load (datapack registry {@code enchanted:circle_magic/rite}), long
 *       after every mod constructor has run.</li>
 *   <li>{@code net.favouriteless.enchanted.api.circle_magic.RiteFactory} —
 *       javap-confirmed contract {@code Rite create(Rite.BaseRiteParams,
 *       Rite.RiteParams)}, {@code ResourceLocation id()}, default
 *       {@code List<ItemStack> getOutputs()}.</li>
 *   <li>{@code ...common.enchanted.circle_magic.rites.CommandRite} —
 *       javap-confirmed public ctor {@code (Rite.BaseRiteParams,
 *       Rite.RiteParams, List<List<String>>, int)}. onStart bytecode: builds a
 *       {@code CommandSourceStack} at {@code Vec3.atCenterOf(pos)} (circle
 *       center, so {@code ~ ~ ~} in commands is the circle), permission level
 *       2 ({@code iconst_2} — enough for /summon and /weather), named
 *       "Command Rite"; with {@code delay == 0} every command batch runs
 *       inside onStart and the rite finishes immediately (one-shot).</li>
 *   <li>{@code ...common.enchanted.circle_magic.rites.Rite} — protected fields
 *       {@code level}/{@code pos}; {@code start()} bytecode: an onStart
 *       returning false calls {@code stop()} then
 *       {@code RiteManager.removeRite} — i.e. returning false is a clean
 *       abort/finish, safe for the refusal path. {@code cancel()} bytecode:
 *       plays NOTE_BLOCK_SNARE at the circle and respawns EVERY
 *       {@code RiteParams.consumedItems} stack as an ItemEntity at
 *       pos + 0.5 — this is the refund contract the north star leans on.
 *       {@code Rite.RiteParams} exposes {@code public final UUID caster}.</li>
 * </ul>
 * Version-range note: the api package ({@code net.favouriteless.enchanted.api})
 * is stable within [4.2,5.0); {@code CommandRite}/{@code Rite} live in
 * {@code common} and are an internal surface verified against 4.2.7 only — on
 * any Enchanted bump, re-verify the ctor and the start()/cancel() flow. If a
 * future build breaks them, {@link #init} dies into its log-once catch, the
 * unresolved factory type makes our rite JSONs fail decode, and NeoForge's
 * datapack-registry loader skips those entries with a log line — the pack
 * keeps running, the two rites simply sit out.
 *
 * <p>Thread gating: the rite JSONs carry the full
 * {@code neoforge:conditions} set (mod_loaded enchanted + orespawn,
 * thread_enabled world_remembers) — verified honored for datapack registry
 * entries by the NeoForge 21.1.223 {@code RegistryDataLoader} patch
 * (ConditionalOps-wrapped element decode). Registering the factory itself is
 * NOT config-gated: with the thread off it is just an unused codec in a map.
 */
public final class OceanRiteCompat {

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    /**
     * Factory type id. Rite JSONs reference it as
     * <pre>"factory": { "type": "orespawn_integrations:summon_in_biome", ... }</pre>
     */
    public static final ResourceLocation SUMMON_IN_BIOME_ID =
            ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "summon_in_biome");

    private OceanRiteCompat() {
    }

    public static void init(IEventBus modBus) {
        try {
            RiteFactoryRegistry.get().register(SUMMON_IN_BIOME_ID, SummonInBiomeFactory.CODEC);
            OreSpawnIntegrations.LOGGER.info(
                    "[enchanted] rite factory '{}' registered (Thread 5 summoning rites)", SUMMON_IN_BIOME_ID);
        } catch (Throwable t) {
            logOnce("register_factory", t);
        }
    }

    static void logOnce(String key, Throwable t) {
        if (LOGGED.add(key)) {
            OreSpawnIntegrations.LOGGER.error(
                    "[enchanted] '{}' failed; muting further reports of this failure", key, t);
        }
    }

    /**
     * Data-driven biome-gated command rite factory. Field-compatible with
     * {@code enchanted:command} ({@code commands} = list of command batches,
     * {@code delay} = ticks between batches) plus:
     * <ul>
     *   <li>{@code biome} — biome tag id (no leading '#'), e.g.
     *       {@code "minecraft:is_ocean"}; the circle-center biome must carry
     *       the tag or the rite refuses.</li>
     *   <li>{@code failure_message} — translation key for the actionbar hint
     *       shown to the caster on refusal (optional; defaults to the generic
     *       wrong-biome line).</li>
     * </ul>
     */
    public record SummonInBiomeFactory(List<List<String>> commands, int delay, TagKey<Biome> biome,
                                       String failureMessage) implements RiteFactory {

        public static final MapCodec<SummonInBiomeFactory> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Codec.STRING.listOf().listOf().fieldOf("commands")
                                .forGetter(SummonInBiomeFactory::commands),
                        Codec.INT.optionalFieldOf("delay", 0)
                                .forGetter(SummonInBiomeFactory::delay),
                        TagKey.codec(Registries.BIOME).fieldOf("biome")
                                .forGetter(SummonInBiomeFactory::biome),
                        Codec.STRING.optionalFieldOf("failure_message",
                                        "compat.orespawn_integrations.rite.wrong_biome")
                                .forGetter(SummonInBiomeFactory::failureMessage)
                ).apply(instance, SummonInBiomeFactory::new));

        @Override
        public Rite create(Rite.BaseRiteParams baseParams, Rite.RiteParams params) {
            return new BiomeGatedCommandRite(baseParams, params, this);
        }

        @Override
        public ResourceLocation id() {
            return SUMMON_IN_BIOME_ID;
        }
    }

    /**
     * The rite itself: checks the circle-center biome before handing over to
     * {@link CommandRite}. On the wrong biome it {@code cancel()}s (full item
     * refund + snare-drum tell, both base-class behavior) and tells the caster
     * where to go — then returns false, which {@code Rite.start()} turns into
     * a clean removal. Nothing is ever lost to a mislaid circle.
     */
    static final class BiomeGatedCommandRite extends CommandRite {

        private final SummonInBiomeFactory factory;

        BiomeGatedCommandRite(Rite.BaseRiteParams baseParams, Rite.RiteParams params,
                              SummonInBiomeFactory factory) {
            super(baseParams, params, factory.commands(), factory.delay());
            this.factory = factory;
        }

        @Override
        protected boolean onStart(Rite.RiteParams params) {
            try {
                if (!level.getBiome(pos).is(factory.biome())) {
                    refuse(params);
                    return false;
                }
                return super.onStart(params);
            } catch (Throwable t) {
                logOnce("rite_start", t);
                try {
                    cancel(); // even a broken start refunds the offerings
                } catch (Throwable inner) {
                    logOnce("rite_refund", inner);
                }
                return false;
            }
        }

        /** Refund everything, then a friendly actionbar nudge — never punish. */
        private void refuse(Rite.RiteParams params) {
            cancel();
            if (params.caster == null) {
                return;
            }
            if (level.getPlayerByUUID(params.caster) instanceof ServerPlayer caster) {
                caster.displayClientMessage(Component.translatable(factory.failureMessage()), true);
            }
        }
    }
}
