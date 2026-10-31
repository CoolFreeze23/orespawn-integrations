package danger.orespawn.integrations.story.week;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import danger.orespawn.integrations.config.IntegrationsConfig;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * {@code {"type":"orespawn_integrations:week_switch","switch":"boteco"}}: keeps a data file only while the named
 * 0.10.1 switch is on. Known switches: {@code boteco}, {@code lands_week}, {@code dusk_cry}, {@code almanac},
 * {@code gazette}. Fails open like the other switch conditions: with the config not loaded yet, or an unknown name,
 * the file loads.
 */
public record WeekSwitchCondition(String name) implements ICondition {

    public static final MapCodec<WeekSwitchCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("switch").forGetter(WeekSwitchCondition::name)
            ).apply(instance, WeekSwitchCondition::new));

    public static boolean enabled(String name) {
        if (!IntegrationsConfig.SPEC.isLoaded()) {
            return true;
        }
        return switch (name) {
            case "boteco" -> IntegrationsConfig.storyBoteco.getAsBoolean();
            case "lands_week" -> IntegrationsConfig.storyLandsWeek.getAsBoolean();
            case "dusk_cry" -> IntegrationsConfig.storyDuskCry.getAsBoolean();
            case "almanac" -> IntegrationsConfig.aliveAlmanac.getAsBoolean();
            case "gazette" -> IntegrationsConfig.aliveGazette.getAsBoolean();
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
        return "week_switch(\"" + name + "\")";
    }
}
