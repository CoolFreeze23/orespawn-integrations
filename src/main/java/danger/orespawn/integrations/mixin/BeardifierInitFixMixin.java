package danger.orespawn.integrations.mixin;

import com.yungnickyoung.minecraft.yungsapi.world.structure.terrainadaptation.beardifier.EnhancedBeardifierData;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.levelgen.Beardifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Crash guard for YUNG's API enhanced terrain adaptation.
 *
 * YUNG's API only initializes its enhancedPieces/enhancedJunctions lists inside
 * Beardifier.forStructuresInChunk(). Some mods (Marvel Superheroes' BeardifierMixin
 * cancels that method and returns its own instance) construct Beardifiers directly,
 * leaving those fields null. YUNG's compute hook then NPEs during chunk generation
 * ("this.enhancedPieces is null").
 *
 * Initializing the lists to empty at construction makes bypassed instances behave
 * exactly like vanilla: YUNG's helper iterates nothing and returns the unchanged
 * density. Instances created through forStructuresInChunk still get the real lists,
 * since YUNG's injection overwrites these after construction.
 */
@Mixin(Beardifier.class)
public class BeardifierInitFixMixin {
    @SuppressWarnings("ConstantConditions")
    @Inject(method = "<init>", at = @At("TAIL"))
    private void orespawn_integrations$initEnhancedData(CallbackInfo ci) {
        EnhancedBeardifierData data = (EnhancedBeardifierData) (Object) this;
        data.yungsapi_setEnhancedPieces(new ObjectArrayList<>());
        data.yungsapi_setEnhancedJunctions(new ObjectArrayList<>());
    }
}
