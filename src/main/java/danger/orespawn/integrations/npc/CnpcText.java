package danger.orespawn.integrations.npc;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the text fields of a CustomNPCs quest or dialog file (its lenient JSON, raw newlines allowed) with no
 * Minecraft or CustomNPCs classes, so the installers' "still the shipped file?" test runs under JUnit: two files with
 * the same titles and texts, in order, are the same as far as an operator's edit goes.
 */
public final class CnpcText {

    private static final char BACKSLASH = 92;
    private static final String[] TEXT_FIELDS = {"Title", "Text", "CompleteText", "DialogTitle", "DialogText"};

    private CnpcText() {
    }

    /** Every text field's value, field by field in file order. */
    public static List<String> fields(String file) {
        List<String> out = new ArrayList<>();
        if (file == null) {
            return out;
        }
        for (String name : TEXT_FIELDS) {
            String tag = "\"" + name + "\": \"";
            int i = file.indexOf(tag);
            while (i >= 0) {
                int j = i + tag.length();
                int k = j;
                while (k < file.length() && file.charAt(k) != '"') {
                    k += file.charAt(k) == BACKSLASH ? 2 : 1;
                }
                out.add(name + "=" + file.substring(j, Math.min(k, file.length())).replace("\r", ""));
                i = file.indexOf(tag, k);
            }
        }
        return out;
    }
}
