package danger.orespawn.integrations.config;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config infrastructure: one COMMON config with a boolean per cross-mod
 * content thread. Every piece of thread data (recipes, GLMs, advancements,
 * datapack-registry entries) carries the matching
 * {@code {"type":"orespawn_integrations:thread_enabled","thread":"<id>"}}
 * condition (see {@link ThreadEnabledCondition}), so flipping a toggle and
 * running {@code /reload} adds/removes that thread's content live; recipe
 * and loot-modifier JSONs are re-parsed through NeoForge's conditional codec
 * machinery on every reload.
 *
 * <p>Verified against NeoForge 21.1.223
 * ({@code net.neoforged.neoforge.common.ModConfigSpec}: Builder.comment/
 * push/define(String,boolean)/pop/build and {@code isLoaded()} checked via
 * javap; {@code LootModifierManager} + {@code RecipeManager} re-parse
 * conditions each reload, LootModifierManager.java:76-81). Pack runtime is
 * NeoForge 21.1.248, and this API is stable across the 21.1.x line; re-verify on
 * a NeoForge major bump.
 */
public final class IntegrationsConfig {

    /** THREAD 4 "Big Game" (combat feel). */
    public static final ModConfigSpec.BooleanValue threadBigGame;
    /** THREAD 1 "It Was Always Uranium" (power). */
    public static final ModConfigSpec.BooleanValue threadUranium;
    /** THREAD 2 "The Royal Court" (boss economy). */
    public static final ModConfigSpec.BooleanValue threadRoyalCourt;
    /** THREAD 3 "Her Side of the Story" (Girlfriend arc). */
    public static final ModConfigSpec.BooleanValue threadGirlfriend;
    /** THREAD 5 "The World Remembers" (atmosphere). */
    public static final ModConfigSpec.BooleanValue threadWorldRemembers;
    public static final ModConfigSpec.BooleanValue threadAliveWorld;
    /** THREAD 6 "Brasil" (the Brazilian mods). */
    public static final ModConfigSpec.BooleanValue threadBrazil;
    /** Client compat, not a content thread: hats on the OreSpawn rigs' head bones (compat/hats). */
    public static final ModConfigSpec.BooleanValue hatsOnRigs;
    public static final ModConfigSpec.BooleanValue playerModelBipeds;
    public static final ModConfigSpec.BooleanValue betterCombatBipeds;
    public static final ModConfigSpec.BooleanValue betterCombatOnPlayers;

    /** The First Hour (0.10.0): the [story], [sightings], [alive], [world] and [bigame] switches. */
    public static final ModConfigSpec.BooleanValue storyHatchingGround;
    public static final ModConfigSpec.BooleanValue storyHatchlingShell;
    public static final ModConfigSpec.BooleanValue storyGuideOnJoin;
    public static final ModConfigSpec.BooleanValue storyFirstHourTicket;
    public static final ModConfigSpec.BooleanValue storyPrincesLetter;
    public static final ModConfigSpec.BooleanValue storyScraps;
    public static final ModConfigSpec.BooleanValue storyFieldCorrection;
    public static final ModConfigSpec.BooleanValue storyCorrections;
    public static final ModConfigSpec.BooleanValue storyScaledCaskets;
    public static final ModConfigSpec.BooleanValue storyRoyalSeal;
    public static final ModConfigSpec.BooleanValue storyEtiqueta;
    public static final ModConfigSpec.BooleanValue storySightings;
    public static final ModConfigSpec.BooleanValue storyRegistry;
    public static final ModConfigSpec.BooleanValue storyAntechamber;
    public static final ModConfigSpec.BooleanValue storyHbmExemptions;
    public static final ModConfigSpec.IntValue sightingsMinDistance;
    public static final ModConfigSpec.IntValue sightingsScoutDistance;
    public static final ModConfigSpec.IntValue sightingsAutoPins;
    public static final ModConfigSpec.BooleanValue aliveFirstFall;
    public static final ModConfigSpec.BooleanValue aliveFirstFallExistingPlayers;
    public static final ModConfigSpec.BooleanValue aliveMeteorGrief;
    public static final ModConfigSpec.BooleanValue worldRegionNames;
    public static final ModConfigSpec.BooleanValue bigameBossCageRefusal;

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

        threadBrazil = builder.comment(
                "THREAD 6 \"Brasil\" - the Brazilian mods: Brasil e Coisas critters become",
                "Domestication Innovation pets (capivara, quero-quero, urutau) through the",
                "domesticationinnovation:taming datapack registry.")
                .define("brazil", true);

        builder.pop();

        builder.comment(
                "Cross-mod client compat that is not a content thread.")
                .push("compat");
        hatsOnRigs = builder.comment(
                "Hats Renewed: draw a mob's hat on the OreSpawn rig's own head bone (the top-centre",
                "of the head, upright, sized to the head) instead of the mod's guess. Hats Renewed's",
                "per-entity placement files still apply on top. false = the mod's own placement.")
                .define("hats_on_rigs", true);
        playerModelBipeds = builder.comment(
                "With Entity Model Features or Better Combat installed, draw the Girlfriend and the Boyfriend",
                "with the vanilla player model (slim arms for her) instead of OreSpawn's own rig, so a player",
                "animation pack such as Fresh Animations: Player Extension animates them as it animates you.",
                "Their skins are converted to the player layout as they load. Takes effect on restart.")
                .define("player_model_bipeds", true);
        betterCombatBipeds = builder.comment(
                "With Better Combat installed, the Girlfriend and the Boyfriend swing a weapon Better Combat",
                "knows with that weapon's own attack animation, the way you do (needs player_model_bipeds).",
                "With a player animation pack, the swing is laid over the pack's pose of the torso and arms.")
                .define("better_combat_bipeds", true);
        betterCombatOnPlayers = builder.comment(
                "With Better Combat and Entity Model Features installed, keep Better Combat's attacks and",
                "weapon-holding poses on players when a player animation pack (such as Fresh Animations:",
                "Player Extension) animates the player model; without this the pack draws over them.",
                "false = the pack's animation wins, as it does without this mod.")
                .define("better_combat_on_players", true);
        builder.pop();

        // The First Hour (0.10.0). Every switch below turns one system off and gives back what v1.4.0 did there;
        // each one's comment says what "off" means.
        builder.comment(
                "The road: the first hour, its papers and its scenes. Each switch turns one system off;",
                "the comment says what you get instead. Changes apply on restart unless noted.")
                .push("story");
        storyHatchingGround = builder.comment(
                "Shelf 9, the Hatching Ground: placed at world spawn when a NEW world is created. On an existing",
                "world it is only placed by an operator (/cc hatching_ground place). false = no placement, and",
                "everything that points at Shelf 9 points at world spawn instead.")
                .define("hatching_ground", true);
        storyHatchlingShell = builder.comment(
                "A new player's first join gives the Eggshell (the starter kit). false = the join gives the",
                "shipped Field Guide, as v1.4.0 did.")
                .define("hatchling_shell", true);
        storyGuideOnJoin = builder.comment(
                "true = also hand out the shipped Field Guide on first join (the Eggshell carries pages 1-2 itself).")
                .define("guide_on_join", false);
        storyFirstHourTicket = builder.comment(
                "The Priority Ticket: the first hour hands over one new paper or verb at a time (P00-P10), never",
                "two within 90 seconds. false = every guide entry open at once, and the Prince's Letter and the",
                "First Fall come on their old timers.")
                .define("first_hour_ticket", true);
        storyPrincesLetter = builder.comment(
                "The Prince's first flyover is forced low over a new player and drops the Letter. false = no",
                "forced flyover; the Registry still issues the Summons.")
                .define("princes_letter", true);
        storyScraps = builder.comment(
                "Ledger Scraps drop from their sources. false = no scrap sources.")
                .define("scraps", true);
        storyFieldCorrection = builder.comment(
                "A Ledger Scrap used on a hatted creature takes the hat into your Wardrobe. false = the scrap",
                "does nothing on creatures.")
                .define("field_correction", true);
        storyCorrections = builder.comment(
                "The Registry desk's Correction options and the recipe-book advancements. false = those are off;",
                "the Correction recipes always load.")
                .define("corrections", true);
        storyScaledCaskets = builder.comment(
                "Lucky blocks and caskets roll by court rank, so the first hour can't end a run. false = the",
                "lucky blocks' own branches, as shipped.")
                .define("scaled_caskets", true);
        storyRoyalSeal = builder.comment(
                "Royal eggs from the Larder only hatch for a player of the right rank. false = royal eggs hatch",
                "at any rank.")
                .define("royal_seal", true);
        storyEtiqueta = builder.comment(
                "The Egg's Label: your origin and class become tags and scores the world reacts to. false = no",
                "tags or scores, and every line that reacts to them uses its default.")
                .define("etiqueta", true);
        storySightings = builder.comment(
                "Looking at a structure through a spyglass stamps a sighting and can pin it. false = the spyglass",
                "is vanilla.")
                .define("sightings", true);
        storyRegistry = builder.comment(
                "The Registry desks and the Odetes' installer. false = no desks and no installer.")
                .define("registry", true);
        storyAntechamber = builder.comment(
                "Below Squire, a butterfly trip to Chaos lands on the Antechamber's ledge. false = the shipped",
                "butterfly trip.")
                .define("antechamber", true);
        storyHbmExemptions = builder.comment(
                "HBM's hazards skip a short list of vanilla items (green and white wool, coal, gold ingot and a few",
                "more, tag orespawn_integrations:hbm_hazard_exempt). false = the call is skipped.")
                .define("hbm_exemptions", true);
        builder.pop();

        builder.comment("Sightings (spyglass) tuning.").push("sightings");
        sightingsMinDistance = builder.comment("Closest a structure can be and still count as a sighting, in blocks.")
                .defineInRange("min_distance", 32, 8, 256);
        sightingsScoutDistance = builder.comment(
                "Within this many blocks of a sighted structure you count as having scouted it.")
                .defineInRange("scout_distance", 24, 4, 128);
        sightingsAutoPins = builder.comment("Most map pins sightings place per player.")
                .defineInRange("auto_pins", 64, 0, 512);
        builder.pop();

        builder.comment("The living world's events.").push("alive");
        aliveFirstFall = builder.comment(
                "The First Fall: a new player's first golden meteor lands 60-100 blocks ahead in daylight, with a",
                "crate addressed to them and the Field Odete. false = no First Fall.")
                .define("first_fall", true);
        aliveFirstFallExistingPlayers = builder.comment(
                "Players who joined before this version get their own Back-Dated Fall the first time they log in.",
                "false = no Back-Dated Fall.")
                .define("first_fall_existing_players", true);
        aliveMeteorGrief = builder.comment(
                "true = meteors crater the ground as before. false = meteors land in the wild, never near a bed,",
                "a Parish Stone or a home, and break no blocks.")
                .define("meteor_grief", false);
        builder.pop();

        builder.comment("Places and their names.").push("world");
        worldRegionNames = builder.comment(
                "Crossing into a region shows its name, and the first visit gives a scrap. false = no titles and no",
                "first-visit scraps.")
                .define("region_names", true);
        builder.pop();

        builder.comment("Bosses and the big monsters.").push("bigame");
        bigameBossCageRefusal = builder.comment(
                "OreSpawn's cage refuses bosses (Mobzilla, the Wither, the Kraken and the rest). false = the cage",
                "takes any mob.")
                .define("boss_cage_refusal", true);
        builder.pop();

        SPEC = builder.build();
    }

    /**
     * Reads a switch, falling back to its default while the config hasn't loaded yet (an event that fires before
     * the COMMON config lands gets the shipped default, never a crash).
     */
    public static boolean on(ModConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.getAsBoolean() : value.getDefault();
    }

    public static int get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.getAsInt() : value.getDefault();
    }

    private IntegrationsConfig() {}

    /**
     * The single lookup every {@link ThreadEnabledCondition} (and any Java-side
     * thread gate) goes through.
     *
     * <p>Known ids: {@code big_game}, {@code uranium}, {@code royal_court},
     * {@code girlfriend}, {@code world_remembers}, {@code alive_world}, {@code brazil}.
     *
     * <p>Fails OPEN by design: if the config has not
     * loaded yet when a datapack parse asks, or a condition JSON carries an
     * unknown thread id, the answer is {@code true}, so content is never
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
            case "brazil" -> threadBrazil.getAsBoolean();
            default -> {
                OreSpawnIntegrations.LOGGER.warn(
                        "thread_enabled condition references unknown thread id '{}' - treating as enabled",
                        threadId);
                yield true;
            }
        };
    }
}
