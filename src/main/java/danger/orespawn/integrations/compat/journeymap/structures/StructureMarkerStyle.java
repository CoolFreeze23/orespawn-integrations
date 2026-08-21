package danger.orespawn.integrations.compat.journeymap.structures;

import danger.orespawn.integrations.OreSpawnIntegrations;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;

/**
 * How one tracked structure is drawn on the map: which 24x24 icon texture
 * (shipped under {@code assets/orespawn_integrations/textures/journeymap/}),
 * an ARGB-less RGB tint for the icon (0xFFFFFF = untinted), the lang key for
 * the marker label, and an english fallback so the marker still reads
 * sensibly if the lang fragment was not merged.
 *
 * @param texture      full resource path of the icon png, extension included
 *                     (JourneyMap resolves MapImage locations through the
 *                     vanilla resource manager)
 * @param iconColor    RGB tint multiplied over the texture by JourneyMap
 * @param langKey      translation key for the on-map label
 * @param fallbackName label used when the key is missing at runtime
 */
public record StructureMarkerStyle(ResourceLocation texture, int iconColor, String langKey, String fallbackName) {

    /** Icons are authored at exactly this size; MapImage is told the same. */
    public static final int ICON_SIZE = 24;

    private static final String LANG_PREFIX = "journeymap." + OreSpawnIntegrations.MODID + ".structure.";
    private static final String TEXTURE_PREFIX = "textures/journeymap/";

    /**
     * Convenience factory: icon name + tint + structure path; lang key becomes
     * {@code journeymap.orespawn_integrations.structure.<structurePath>}.
     */
    static StructureMarkerStyle of(String iconName, int iconColor, String structurePath, String fallbackName) {
        return new StructureMarkerStyle(
                ResourceLocation.fromNamespaceAndPath(OreSpawnIntegrations.MODID,
                        TEXTURE_PREFIX + iconName + ".png"),
                iconColor,
                LANG_PREFIX + structurePath,
                fallbackName);
    }

    /**
     * Resolved on the client thread at marker-creation time so the label
     * follows the loaded language. Falls back to the baked english name when
     * the key is absent (e.g. fragment not merged into the lang file yet).
     */
    public String localizedName() {
        try {
            return I18n.exists(langKey) ? I18n.get(langKey) : fallbackName;
        } catch (Throwable t) {
            return fallbackName;
        }
    }
}
