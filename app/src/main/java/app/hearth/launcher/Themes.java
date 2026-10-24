package app.hearth.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/** Which home screen is active: the Fire TV-style default or the console. */
final class Themes {

    static final String CONSOLE = "console";
    static final String DEFAULT = "default";

    private Themes() {
    }

    static String current(Context context) {
        return context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
                .getString("theme", CONSOLE);
    }

    static Class<? extends Activity> homeClass(Context context) {
        String t = current(context);
        if (DEFAULT.equals(t)) return DefaultActivity.class;
        return MainActivity.class;
    }

    static Intent homeIntent(Context context) {
        return new Intent(context, homeClass(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    /** Saves the choice and swaps the running home screen for the new one. */
    static void switchTo(Activity from, String theme) {
        from.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit()
                .putString("theme", theme).apply();
        from.startActivity(homeIntent(from));
        from.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        from.finish();
    }

    /**
     * Opens Fire TV's main Settings screen directly. The generic ACTION_SETTINGS
     * route passes through Amazon's home screen, which the Home guard may catch.
     */
    static void openFireTvSettings(Activity activity) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory("android.intent.category.LEANBACK_SETTINGS")
                    .setClassName("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.MainSettingsActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            activity.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    /**
     * If another theme is active, opens it and returns true; the caller should
     * then stop building its own screen.
     */
    static boolean redirectIfNeeded(Activity activity) {
        if (homeClass(activity) == activity.getClass()) return false;
        activity.startActivity(homeIntent(activity));
        activity.overridePendingTransition(0, 0);
        activity.finish();
        return true;
    }
}
