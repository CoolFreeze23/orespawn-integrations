package danger.orespawn.integrations.compat.journeymap.structures;

/**
 * Toggle for the JourneyMap structure-marker feature.
 *
 * Deliberately not a real config file: this is a tiny cosmetic feature and
 * the pack has no config screen budget for it. The switch is a plain static
 * boolean (flippable at runtime from a debugger, KubeJS startup script, or
 * any other mod that wants to reach in) whose default falls back to a JVM
 * system property, so players can ship {@code
 * -Dorespawn_integrations.jm.structureMarkers=false} in their JVM args to
 * opt out at launch.
 */
public final class StructureMarkersConfig {

    /** Launch-time fallback: {@code -Dorespawn_integrations.jm.structureMarkers=false} disables. */
    public static final String SYSTEM_PROPERTY = "orespawn_integrations.jm.structureMarkers";

    /**
     * Master switch, checked on every hot path (chunk scan + marker drain),
     * so flipping it mid-session stops new markers immediately. Markers
     * already on the map stay until mapping stops.
     */
    public static volatile boolean structureMarkersEnabled = readSystemPropertyDefault();

    public static boolean isEnabled() {
        return structureMarkersEnabled;
    }

    /** Default is ON; only an explicit non-"true" property value disables. */
    private static boolean readSystemPropertyDefault() {
        try {
            String value = System.getProperty(SYSTEM_PROPERTY);
            return value == null || Boolean.parseBoolean(value);
        } catch (Throwable t) {
            // SecurityManager or malformed property - never let the toggle
            // itself take the feature down.
            return true;
        }
    }

    private StructureMarkersConfig() {
    }
}
