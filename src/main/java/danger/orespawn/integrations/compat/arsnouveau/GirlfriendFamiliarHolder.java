package danger.orespawn.integrations.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.familiar.AbstractFamiliarHolder;
import com.hollingsworth.arsnouveau.api.familiar.IFamiliar;

import danger.orespawn.entity.Girlfriend;
import danger.orespawn.integrations.OreSpawnIntegrations;
import danger.orespawn.integrations.config.IntegrationsConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Familiar holder {@code orespawn_integrations:girlfriend}, patterned on Ars
 * Nouveau's own {@code common.familiars.StarbuncleFamiliarHolder}
 * (ars_nouveau-1.21.1-5.13.0, javap): super ctor {@code (ResourceLocation,
 * Predicate<Entity>)} — the String overload is unusable for addons, its
 * bytecode routes through {@code ArsNouveau.prefix} and would namespace the id
 * {@code ars_nouveau:*} — plus {@code getSummonEntity(Level, CompoundTag)}
 * constructing the wrapper entity and forwarding {@code setTagData(tag)},
 * exactly as Starbuncle does.
 *
 * <p><b>THE MANDATORY PREDICATE (north-star law, thread3 verification Q4):</b>
 * AN's canon {@code common.ritual.RitualBinding} scans nearby entities against
 * every holder's {@code isEntity} predicate, drops {@code getOutputItem()} and
 * then calls {@code Entity.remove(RemovalReason)} on the matched entity
 * (bytecode-verified) — <b>the bound entity is deleted</b>. A naive
 * {@code instanceof Girlfriend} match would let the ritual consume a player's
 * tamed companion. The predicate therefore matches only <b>untamed</b>
 * girlfriends: port {@code danger.orespawn.entity.Girlfriend extends
 * TamableAnimal} (mirror Girlfriend.java:53), so {@code isTame()} is the
 * vanilla accessor — a tamed companion can never be consumed, while
 * ritual-binding a wild girlfriend is the legitimate discovery path (Ritual of
 * Binding is the standard unlock; Date Night completion is NOT required). The
 * lang-key surface ({@code <ns>.familiar_name.<path>} /
 * {@code <ns>.familiar_desc.<path>}, bytecode of
 * {@code AbstractFamiliarHolder.getLangName/getLangDescription}) is fed by
 * this addon's lang fragment.
 *
 * <p>The thread toggle is part of the predicate too, so disabling the
 * {@code girlfriend} thread in config live-disables ritual matching (config
 * fails open while unloaded, matching {@code ThreadEnabledCondition}).
 */
public class GirlfriendFamiliarHolder extends AbstractFamiliarHolder {

    public GirlfriendFamiliarHolder() {
        super(ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID, "girlfriend"),
                entity -> entity instanceof Girlfriend girlfriend
                        && !girlfriend.isTame()
                        && IntegrationsConfig.isThreadEnabled(ArsFamiliarCompat.THREAD_ID));
    }

    @Override
    public IFamiliar getSummonEntity(Level level, CompoundTag tag) {
        FamiliarGirlfriend familiar =
                new FamiliarGirlfriend(ArsFamiliarCompat.FAMILIAR_GIRLFRIEND.get(), level);
        familiar.setTagData(tag);
        return familiar;
    }

    @Override
    public String getBookName() {
        return "Girlfriend";
    }

    @Override
    public String getBookDescription() {
        return "A pocket-sized devoted companion. While she is summoned, any living attacker"
                + " that hurts her player is struck with Weakness II for 5 seconds - jealousy is"
                + " a powerful motivator. Obtained by performing the Ritual of Binding near a wild"
                + " Girlfriend; a tamed girlfriend is never taken by the ritual.";
    }
}
