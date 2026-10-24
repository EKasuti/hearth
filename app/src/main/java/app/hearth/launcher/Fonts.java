package app.hearth.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.util.SparseArray;

/**
 * Bundled fonts (both SIL Open Font License): Inter for app names, as one
 * variable font, and IBM Plex Mono for labels and the LCD.
 */
final class Fonts {

    private static final SparseArray<Typeface> INTER = new SparseArray<>();
    private static final SparseArray<Typeface> MONO = new SparseArray<>();

    private Fonts() {
    }

    static Typeface inter(Context context, int weight) {
        Typeface t = INTER.get(weight);
        if (t == null) {
            t = new Typeface.Builder(context.getAssets(), "fonts/Inter.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .build();
            if (t == null) t = Typeface.DEFAULT;
            INTER.put(weight, t);
        }
        return t;
    }

    /** IBM Plex Mono at 500, 600 or 700. */
    static Typeface mono(Context context, int weight) {
        Typeface t = MONO.get(weight);
        if (t == null) {
            String file = weight >= 700 ? "Bold" : weight >= 600 ? "SemiBold" : "Medium";
            t = Typeface.createFromAsset(context.getAssets(), "fonts/IBMPlexMono-" + file + ".ttf");
            MONO.put(weight, t);
        }
        return t;
    }
}
