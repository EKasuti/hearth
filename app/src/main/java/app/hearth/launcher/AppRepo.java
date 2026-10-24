package app.hearth.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds installed apps and stores the user's favorites order and hidden apps. */
final class AppRepo {

    private static final List<String> DEFAULT_FAVORITES = Arrays.asList(
            "com.netflix.ninja",
            "com.amazon.firetv.youtube",
            "com.amazon.firebat",          // Prime Video
            "com.disney.disneyplus",
            "com.hulu.plus",
            "com.wbd.stream",              // Max
            "com.plexapp.android",
            "com.spotify.tv.android");

    // Amazon filler and launcher plumbing that shouldn't clutter the home screen.
    private static final Set<String> DEFAULT_HIDDEN = new HashSet<>(Arrays.asList(
            "com.amazon.tv.launcher",
            "com.amazon.ftv.screensaver",
            "com.amazon.ftv.profilepicker",
            "com.amazon.tv.ftvambient",
            "com.amazon.tv.quicksettings.ui",
            "com.amazon.ssm",
            "com.amazon.smarthomemapviewapp",
            "com.amazon.spiderpork",       // Luna
            "com.amazon.minitv.android.app",
            "com.amazon.tahoe",            // Amazon Kids
            "com.amazon.bueller.photos",
            "com.amazon.tv.localgallery",
            "com.amazon.hedwig",           // News
            "com.mediatek.calman"));

    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_HIDDEN = "hidden";

    private final Context context;
    private final SharedPreferences prefs;
    private final Map<String, AppEntry> apps = new LinkedHashMap<>();

    AppRepo(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
    }

    void reload() {
        apps.clear();
        PackageManager pm = context.getPackageManager();
        // TV apps first so their banner-capable activity wins over a phone-style one.
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER);
        collect(pm, Intent.CATEGORY_LAUNCHER);
    }

    private void collect(PackageManager pm, String category) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (apps.containsKey(ai.packageName) || ai.packageName.equals(context.getPackageName())) {
                continue;
            }
            Drawable banner = ai.loadBanner(pm);
            if (banner == null) {
                banner = ai.applicationInfo.loadBanner(pm);
            }
            String label = ri.loadLabel(pm).toString();
            apps.put(ai.packageName, new AppEntry(ai.packageName, label, banner, ri.loadIcon(pm)));
        }
    }

    List<AppEntry> favorites() {
        List<AppEntry> out = new ArrayList<>();
        for (String pkg : favoriteOrder()) {
            AppEntry e = apps.get(pkg);
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    List<AppEntry> others() {
        List<String> favs = favoriteOrder();
        Set<String> hidden = hidden();
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : apps.values()) {
            if (!favs.contains(e.pkg) && !hidden.contains(e.pkg)) {
                out.add(e);
            }
        }
        out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    boolean isFavorite(String pkg) {
        return favoriteOrder().contains(pkg);
    }

    void toggleFavorite(String pkg) {
        List<String> favs = favoriteOrder();
        if (!favs.remove(pkg)) {
            favs.add(pkg);
        }
        saveFavorites(favs);
    }

    /** Keeps apps that stay ticked in their current order; newly ticked ones go last. */
    void setFavorites(List<String> picked) {
        List<String> next = new ArrayList<>();
        for (String pkg : favoriteOrder()) if (picked.contains(pkg)) next.add(pkg);
        for (String pkg : picked) if (!next.contains(pkg)) next.add(pkg);
        saveFavorites(next);
    }

    /** Swaps with the neighbouring favourite that is actually installed (and so on screen). */
    void moveFavorite(String pkg, int delta) {
        List<String> favs = favoriteOrder();
        List<String> shown = new ArrayList<>();
        for (String f : favs) if (apps.containsKey(f)) shown.add(f);
        int from = shown.indexOf(pkg);
        int to = from + delta;
        if (from < 0 || to < 0 || to >= shown.size()) {
            return;
        }
        Collections.swap(favs, favs.indexOf(pkg), favs.indexOf(shown.get(to)));
        saveFavorites(favs);
    }

    /** How many apps the user has hidden (not counting the built-in Amazon filler). */
    int userHiddenCount() {
        Set<String> h = hidden();
        h.removeAll(DEFAULT_HIDDEN);
        return h.size();
    }

    void unhideAll() {
        prefs.edit().putStringSet(KEY_HIDDEN, new HashSet<>(DEFAULT_HIDDEN)).apply();
    }

    void hide(String pkg) {
        Set<String> hidden = hidden();
        hidden.add(pkg);
        List<String> favs = favoriteOrder();
        favs.remove(pkg);
        saveFavorites(favs);
        prefs.edit().putStringSet(KEY_HIDDEN, hidden).apply();
    }

    /** The app's icon, or null when it isn't installed. */
    Drawable iconFor(String pkg) {
        try {
            return context.getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    Intent launchIntent(String pkg) {
        PackageManager pm = context.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) {
            i = pm.getLaunchIntentForPackage(pkg);
        }
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return i;
    }

    private List<String> favoriteOrder() {
        String raw = prefs.getString(KEY_FAVORITES, null);
        if (raw == null) {
            return new ArrayList<>(DEFAULT_FAVORITES);
        }
        List<String> out = new ArrayList<>();
        for (String s : raw.split(",")) {
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private void saveFavorites(List<String> favs) {
        prefs.edit().putString(KEY_FAVORITES, TextUtils.join(",", favs)).apply();
    }

    private Set<String> hidden() {
        // Copy: the set returned by SharedPreferences must not be modified in place.
        return new HashSet<>(prefs.getStringSet(KEY_HIDDEN, DEFAULT_HIDDEN));
    }
}
