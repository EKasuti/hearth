package app.hearth.launcher;

/**
 * The console's colours: a warm-grey finish by day and a graphite one after
 * sunset (see MainActivity).
 */
final class Palette {

    static final Palette LIGHT = new Palette("LIGHT",
            0xFFD0CCC5, 0xFFDAD7D1, 0xFFBDB9B2, 0xFFA9A59F,
            0xFFF1EFEB, 0xFFB9B5AE, 0xFF1E1D1B, 0xFF55524D, 0xFFB3AFA8,
            0xFF2B2A27, 0xFFF1EFEB, 0xFF3A3935, 0xFF6E6B66, 0xFF9E9A94, 0xFFE2DFD9);

    static final Palette DARK = new Palette("DARK",
            0xFF1C1C1E, 0xFF252527, 0xFF333336, 0xFF46464A,
            0xFF2F2F32, 0xFF141416, 0xFFEDEBE6, 0xFF9C9993, 0xFF5A5A5E,
            0xFFE9E6E0, 0xFF1E1D1B, 0xFFC9C6C0, 0xFF8A8782, 0xFF4A4A4E, 0xFF232326);

    static final int ORANGE = 0xFFFF5A1F;
    static final int LCD = 0xFFB5C29D;
    static final int LCD_EDGE = 0xFF97A47F;
    static final int LCD_INK = 0xFF1F2618;

    final String name;
    final int background, panel, panelEdge, screw;
    final int key, keyShadow, ink, inkSoft, keyDot;
    final int darkKey, darkKeyInk, label, labelSoft, rule;
    /** Background behind an icon on a favorite that has no banner. */
    final int plate;

    private Palette(String name, int background, int panel, int panelEdge, int screw,
                    int key, int keyShadow, int ink, int inkSoft, int keyDot,
                    int darkKey, int darkKeyInk, int label, int labelSoft, int rule, int plate) {
        this.name = name;
        this.background = background;
        this.panel = panel;
        this.panelEdge = panelEdge;
        this.screw = screw;
        this.key = key;
        this.keyShadow = keyShadow;
        this.ink = ink;
        this.inkSoft = inkSoft;
        this.keyDot = keyDot;
        this.darkKey = darkKey;
        this.darkKeyInk = darkKeyInk;
        this.label = label;
        this.labelSoft = labelSoft;
        this.rule = rule;
        this.plate = plate;
    }
}
