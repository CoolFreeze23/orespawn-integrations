package danger.orespawn.integrations.story.registry;

import danger.orespawn.integrations.config.IntegrationsConfig;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The Corrections Table's JEI pages ({@code system-corrections}): each row's line on its input, and the two written
 * refusals, the rubies (R1) and hero titanium (R2), each ending in "No correction for that." Ids are looked up by
 * name, so a missing partner mod simply drops its page. Off with {@code [story] corrections}.
 */
@JeiPlugin
public class CorrectionsJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("orespawn_integrations", "corrections");
    private static final String K = "orespawn_integrations.correction.jei.";
    private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots", "sword", "pickaxe", "axe", "shovel", "hoe"};

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!IntegrationsConfig.on(IntegrationsConfig.storyCorrections)) {
            return;
        }
        page(registration, List.of("hbmsntm:ingot_uranium"), K + "row_1");
        page(registration, List.of("biomesoplenty:spanish_moss"), K + "row_2");
        page(registration, List.of("naturalist:cooked_crab_meat"), K + "row_3");
        List<String> pieces = new ArrayList<>();
        for (String piece : PIECES) {
            pieces.add("distantlandsmc:emerald_" + piece);
            pieces.add("orespawn:emerald_" + piece);
        }
        page(registration, pieces, K + "row_4", K + "row_4_novo");
        page(registration, List.of("hbmsntm:ingot_titanium"), K + "row_7");
        page(registration, List.of("orespawn:ruby", "distantlandsmc:ruby"), K + "r1");
        page(registration, List.of("marvel:titanium_ingot"), K + "r2");
    }

    private static void page(IRecipeRegistration registration, List<String> ids, String... keys) {
        List<ItemStack> stacks = new ArrayList<>();
        for (String id : ids) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item != Items.AIR) {
                stacks.add(new ItemStack(item));
            }
        }
        if (stacks.isEmpty()) {
            return;
        }
        Component[] lines = new Component[keys.length];
        for (int i = 0; i < keys.length; i++) {
            lines[i] = Component.translatable(keys[i]);
        }
        registration.addItemStackInfo(stacks, lines);
    }
}
