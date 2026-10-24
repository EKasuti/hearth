package app.hearth.launcher;

import android.graphics.drawable.Drawable;

/** One launchable app, or a built-in action tile (e.g. Settings) when {@code action} is set. */
final class AppEntry {
    final String pkg;
    final String label;
    final Drawable banner;
    final Drawable icon;
    final Runnable action;

    AppEntry(String pkg, String label, Drawable banner, Drawable icon) {
        this(pkg, label, banner, icon, null);
    }

    AppEntry(String pkg, String label, Drawable banner, Drawable icon, Runnable action) {
        this.pkg = pkg;
        this.label = label;
        this.banner = banner;
        this.icon = icon;
        this.action = action;
    }

    boolean isAction() {
        return action != null;
    }
}
