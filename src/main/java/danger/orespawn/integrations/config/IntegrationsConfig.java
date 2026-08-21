package danger.orespawn.integrations.config;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * POLICY 2 infrastructure: one COMMON config with a boolean per cross-mod
 * content thread. Every piece of thread data (recipes, GLMs, advancements,
 * datapack-registry entries) carries the matching
 * {@code {"type":"orespawn_integrations:thread_enabled","thread":"<id>"}}
 * condition (see {@link ThreadEnabledCondition}), so flipping a toggle and
 * running {@code /reload} adds/removes that thread's content live — recipe
 * and loot-modifier JSONs are re-parsed through NeoForge's conditional codec
 * machinery on every reload.
 *
 * <p>Verified against (policy 4): NeoForge 21.1.223
 * ({@code net.neoforged.neoforge.common.ModConfigSpec} — Builder.comment/
 * push/define(String,boolean)/pop/build and {@code isLoaded()} checked via
 * javap; {@code LootModifierManager} + {@code RecipeManager} re-parse
 * conditions each reload, LootModifierManager.java:76-81). Pack runtime is
 * NeoForge 21.1.248 — this API is stable across the 21.1.x line; re-verify on
 * a NeoForge major bump.
 */
public final class IntegrationsConfig {

    /** THREAD 4 — "Big Game" (combat feel). */
    public static final ModConfigSpec.BooleanValue threadBigGame;
    /** THREAD 1 — "It Was Always Uranium" (power). */
    public static final ModConfigSpec.BooleanValue threadUranium;
    /** THREAD 2 — "The Royal Court" (boss economy). */
    public static final ModConfigSpec.BooleanValue threadRoyalCourt;
    /** THREAD 3 — "Her Side of the Story" (Girlfriend arc). */
    public static final ModConfigSpec.BooleanValue threadGirlfriend;
    /** THREAD 5 — "The World Remembers" (atmosphere). */
    public static final ModConfigSpec.BooleanValue threadWorldRemembers;
    public static final ModConfigSpec.BooleanValue threadAliveWorld;

    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
                "Cross-mod content threads. Each toggle gates that thread's recipes,",
                "loot modifiers and advancements. Flip a toggle, then run /reload",
                "(no restart needed) - the content appears or vanishes on the spot.",
                "NOT gateable on NeoForge 21.1 (they ignore conditions; all are inert",
                "when their partner mod is absent): tag merges, block self-drop loot,",
                "and Better Combat weapon move-sets.")
                .push("threads");

        threadBigGame = builder.comment(
                "THREAD 4 \"Big Game\" - combat feel: Mobzilla plating, boss-hide",
                "backpacks, Emperor's Chitin Band, Angel insurance tags, decoy ore mines.",
                "EXCEPTION: Better Combat weapon move-sets load via Better Combat's own",
                "scanner and ignore this toggle; remove Better Combat to remove them.")
                .define("big_game", true);

        threadUranium = builder.comment(
                "THREAD 1 \"It Was Always Uranium\" - power: geiger prospecting,",
                "radioactive enchanting, uranium collectors, arc reactor, bottled",
                "uranium, A-10 ammo.")
                .define("uranium", true);

        threadRoyalCourt = builder.comment(
                "THREAD 2 \"The Royal Court\" - boss economy: witherite tempering, boss-",
                "school scrolls, dragon egg, soul shards, upgrade orbs, uncrafting,",
                "queen tribute.")
                .define("royal_court", true);

        threadGirlfriend = builder.comment(
                "THREAD 3 \"Her Side of the Story\" - Girlfriend arc: diary, tome,",
                "familiar, Date Night gift chain.")
                .define("girlfriend", true);

        threadWorldRemembers = builder.comment(
                "THREAD 5 \"The World Remembers\" - atmosphere: summoning rites, Mobzilla",
                "statue, crystalline transmutation, rail works.")
                .define("world_remembers", true);

        threadAliveWorld = builder.comment(
                "ALIVE-WORLD WAVE integrations - OreSpawn hooks into the structure/",
                "seasons/creature mod wave (YUNG loot, seasonal crops, End Remastered",
                "eyes, pirate fleets, guard salutes, dog backflips, and friends).")
                .define("alive_world", true);

        builder.pop();
        SPEC = builder.build();
    }

    private IntegrationsConfig() {}

    /**
     * The single lookup every {@link ThreadEnabledCondition} (and any Java-side
     * thread gate) goes through.
     *
     * <p>Known ids: {@code big_game}, {@code uranium}, {@code royal_court},
     * {@code girlfriend}, {@code world_remembers}.
     *
     * <p>Fails OPEN by design (north star: never punish): if the config has not
     * loaded yet when a datapack parse asks — or a condition JSON carries an
     * unknown thread id — the answer is {@code true}, so content is never
     * hidden permanently by config/load-order accidents. The next /reload after
     * the config lands re-evaluates with the real values.
     */
    public static boolean isThreadEnabled(String threadId) {
        if (!SPEC.isLoaded()) {
            return true;
        }
        return switch (threadId) {
            case "big_game" -> threadBigGame.getAsBoolean();
            case "uranium" -> threadUranium.getAsBoolean();
            case "royal_court" -> threadRoyalCourt.getAsBoolean();
            case "girlfriend" -> threadGirlfriend.getAsBoolean();
            case "world_remembers" -> threadWorldRemembers.getAsBoolean();
            case "alive_world" -> threadAliveWorld.getAsBoolean();
            default -> {
                OreSpawnIntegrations.LOGGER.warn(
                        "thread_enabled condition references unknown thread id '{}' - treating as enabled",
                        threadId);
                yield true;
            }
        };
    }
}
