package danger.orespawn.integrations.story.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import danger.orespawn.integrations.config.IntegrationsConfig;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * {@code {"type":"orespawn_integrations:registry_switch","switch":"registry"}}: keeps a data file (a Lithostitched
 * modifier, an advancement) only while the named Registry switch is on. Known switches: {@code registry},
 * {@code corrections}, {@code scraps}, {@code field_correction}. Fails open, like the thread condition: with the config
 * not loaded yet, or an unknown name, the file loads, so nothing is hidden by a load-order accident.
 */
public record RegistrySwitchCondition(String name) implements ICondition {

    public static final MapCodec<RegistrySwitchCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("switch").forGetter(RegistrySwitchCondition::name)
            ).apply(instance, RegistrySwitchCondition::new));

    public static boolean enabled(String name) {
        if (!IntegrationsConfig.SPEC.isLoaded()) {
            return true;
        }
        return switch (name) {
            case "registry" -> IntegrationsConfig.storyRegistry.getAsBoolean();
            case "corrections" -> IntegrationsConfig.storyCorrections.getAsBoolean();
            case "scraps" -> IntegrationsConfig.storyScraps.getAsBoolean();
            case "field_correction" -> IntegrationsConfig.storyFieldCorrection.getAsBoolean();
            default -> true;
        };
    }

    @Override
    public boolean test(ICondition.IContext context) {
        return enabled(name);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "registry_switch(\"" + name + "\")";
    }
}
