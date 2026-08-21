package danger.orespawn.integrations.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * POLICY 2 infrastructure: the datapack condition that gates a JSON on its
 * thread's config toggle. Exact JSON shape (inside the standard top-level
 * {@code "neoforge:conditions"} array — ConditionalOps.java:49,
 * {@code DEFAULT_CONDITIONS_KEY}):
 *
 * <pre>{"type": "orespawn_integrations:thread_enabled", "thread": "big_game"}</pre>
 *
 * <p>The {@code "type"} field is dispatch handled by {@code ICondition.CODEC}
 * against the condition-codec registry ({@link ConfigInit} registers this
 * record's {@link #CODEC} there as {@code thread_enabled}); this MapCodec only
 * owns the {@code "thread"} field.
 *
 * <p>{@link #test} delegates to
 * {@link IntegrationsConfig#isThreadEnabled(String)}, which fails OPEN
 * (returns true) while the config is not yet loaded — a datapack parse that
 * races ahead of config load can never hide content permanently; the next
 * /reload re-evaluates with real values.
 *
 * <p>Verified against (policy 4): NeoForge 21.1.223,
 * {@code net.neoforged.neoforge.common.conditions.ICondition} — contract
 * {@code boolean test(ICondition.IContext)} +
 * {@code MapCodec<? extends ICondition> codec()} confirmed via javap. Pack
 * runtime is NeoForge 21.1.248; API stable across 21.1.x.
 */
public record ThreadEnabledCondition(String thread) implements ICondition {

    public static final MapCodec<ThreadEnabledCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("thread").forGetter(ThreadEnabledCondition::thread)
            ).apply(instance, ThreadEnabledCondition::new));

    @Override
    public boolean test(ICondition.IContext context) {
        return IntegrationsConfig.isThreadEnabled(thread);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "thread_enabled(\"" + thread + "\")";
    }
}
