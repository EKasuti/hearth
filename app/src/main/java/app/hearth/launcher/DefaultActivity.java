package app.hearth.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * The "Default" theme: the familiar Fire TV layout (tabs, a big featured
 * banner, rows of apps) without any ads or sponsored rows.
 */
public final class DefaultActivity extends Activity {

    private static final long ROTATE_MS = 8000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppRepo repo;
    private float dp;
    private FrameLayout root;
    private LinearLayout content;
    private HeroView hero;
    private TextView clock;
    private View firstFavorite, firstMore;
    private FrameLayout picker;
    private List<AppEntry> featured = new ArrayList<>();
    private int featuredIndex;
    private String focusPkg;
    private int homeTabId;

    private final Runnable rotate = new Runnable() {
        @Override
        public void run() {
            // Don't change what OK will open while the banner is focused.
            if (!featured.isEmpty() && !hero.isFocused()) {
                featuredIndex = (featuredIndex + 1) % featured.size();
                showFeatured();
            }
            handler.postDelayed(this, ROTATE_MS);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (clock != null) {
                clock.setText(DateFormat.format(DateFormat.is24HourFormat(DefaultActivity.this)
                        ? "HH:mm" : "h:mm a", Calendar.getInstance()));
            }
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000 + 50);
        }
    };

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            repo.reload();
            buildUi();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Themes.redirectIfNeeded(this)) return;
        dp = getResources().getDisplayMetrics().density;
        repo = new AppRepo(this);
        repo.reload();
        buildUi();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packagesChanged, filter);
    }

    @Override
    protected void onDestroy() {
        if (repo != null) unregisterReceiver(packagesChanged);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repo == null) return;
        handler.post(tick);
        handler.postDelayed(rotate, ROTATE_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        handler.removeCallbacks(rotate);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Themes.redirectIfNeeded(this)) return;
        closePicker();
        if (hero != null) hero.requestFocus();
    }

    @Override
    public void onBackPressed() {
        closePicker();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && picker == null && getCurrentFocus() instanceof FireTile) {
            AppEntry app = ((FireTile) getCurrentFocus()).app;
            if (!app.isAction()) {
                showOptions(app);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ------------------------------------------------------------------ layout

    private void buildUi() {
        picker = null; // a rebuild replaces every view, including an open picker
        if (getCurrentFocus() instanceof FireTile) focusPkg = ((FireTile) getCurrentFocus()).app.pkg;
        root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF131C26, 0xFF0B1016}));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(48), px(22), px(48), px(14));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        content.addView(topBar());

        hero = new HeroView(this);
        hero.setId(View.generateViewId());
        hero.setOnClickListener(v -> {
            if (hero.app() != null) launch(hero.app().pkg);
        });
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, px(194));
        hl.topMargin = px(14);
        content.addView(hero, hl);

        hero.setNextFocusUpId(homeTabId); // like Fire TV: Up from the banner lands on Home
        List<AppEntry> favs = repo.favorites();
        featured = favs.isEmpty() ? repo.others() : favs;
        featuredIndex = Math.min(featuredIndex, Math.max(0, featured.size() - 1));
        showFeatured();

        content.addView(label("Your apps"));
        firstFavorite = row(favs, 144, 81);
        List<AppEntry> more = new ArrayList<>(repo.others());
        more.add(new AppEntry("settings", "Settings", null,
                repo.iconFor("com.amazon.tv.settings.v2"), () -> Themes.openFireTvSettings(this)));
        content.addView(label("More apps"));
        firstMore = row(more, 120, 68);

        setContentView(root);
        View target = hero;
        if (focusPkg != null) {
            View v = findTile(root, focusPkg);
            if (v != null) target = v;
        }
        target.requestFocus();
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView home = tab("Home", true, () -> hero.requestFocus());
        home.setId(View.generateViewId());
        homeTabId = home.getId();
        bar.addView(home);
        bar.addView(tab("Apps", false, () -> {
            if (firstMore != null) firstMore.requestFocus();
        }));
        bar.addView(tab("Settings", false, () -> Themes.openFireTvSettings(this)));
        bar.addView(tab("Theme", false, this::showPicker));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        clock = new TextView(this);
        clock.setTextColor(0xFFE6EAF0);
        clock.setTextSize(16);
        clock.setTypeface(Fonts.inter(this, 500));
        bar.addView(clock);
        return bar;
    }

    /** A Fire TV menu tab: a pill that turns white when focused. */
    private TextView tab(String label, boolean selected, Runnable action) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(15);
        t.setTypeface(Fonts.inter(this, 600));
        t.setPadding(px(16), px(7), px(16), px(7));
        t.setFocusable(true);
        t.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(18));
        t.setBackground(bg);
        Runnable paint = () -> {
            boolean f = t.isFocused();
            bg.setColor(f ? 0xFFFFFFFF : selected ? 0x26FFFFFF : 0x00000000);
            t.setTextColor(f ? 0xFF0E141B : selected ? 0xFFFFFFFF : 0xB3FFFFFF);
        };
        paint.run();
        t.setOnFocusChangeListener((v, has) -> paint.run());
        t.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = px(6);
        t.setLayoutParams(lp);
        return t;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(0xFFE6EAF0);
        t.setTextSize(15);
        t.setTypeface(Fonts.inter(this, 600));
        t.setPadding(px(2), px(14), 0, px(6));
        return t;
    }

    /** A horizontally scrolling row of tiles; returns the first tile. */
    private View row(List<AppEntry> apps, int w, int h) {
        LinearLayout r = new LinearLayout(this);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        r.setPadding(px(10), px(8), px(60), px(8));
        View first = null;
        for (AppEntry e : apps) {
            FireTile t = new FireTile(this, e);
            t.setOnClickListener(v -> {
                if (e.isAction()) e.action.run();
                else launch(e.pkg);
            });
            if (!e.isAction()) {
                t.setOnLongClickListener(v -> {
                    showOptions(e);
                    return true;
                });
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(w), px(h));
            lp.rightMargin = px(16);
            r.addView(t, lp);
            if (first == null) first = t;
        }
        HorizontalScrollView s = new HorizontalScrollView(this);
        s.setHorizontalScrollBarEnabled(false);
        s.setOverScrollMode(View.OVER_SCROLL_NEVER);
        s.setClipChildren(false);
        s.setClipToPadding(false);
        s.addView(r);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.leftMargin = -px(10);
        sl.rightMargin = -px(48);
        content.addView(s, sl);
        return first;
    }

    private View findTile(View v, String pkg) {
        if (v instanceof FireTile && ((FireTile) v).app.pkg.equals(pkg)) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = findTile(g.getChildAt(i), pkg);
                if (f != null) return f;
            }
        }
        return null;
    }

    private void showFeatured() {
        if (featured.isEmpty()) return;
        hero.show(featured.get(featuredIndex), String.format(Locale.US, "Featured · %d of %d",
                featuredIndex + 1, featured.size()));
    }

    // ------------------------------------------------------------------ actions

    private void launch(String pkg) {
        Intent i = repo.launchIntent(pkg);
        if (i == null) {
            Toast.makeText(this, "Not installed", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(i);
    }

    private void showOptions(AppEntry app) {
        boolean fav = repo.isFavorite(app.pkg);
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (fav) {
            items.add("Move left");
            actions.add(() -> repo.moveFavorite(app.pkg, -1));
            items.add("Move right");
            actions.add(() -> repo.moveFavorite(app.pkg, 1));
            items.add("Remove from Your apps");
            actions.add(() -> repo.toggleFavorite(app.pkg));
        } else {
            items.add("Add to Your apps");
            actions.add(() -> repo.toggleFavorite(app.pkg));
        }
        items.add("Hide");
        actions.add(() -> repo.hide(app.pkg));
        int hiddenCount = repo.userHiddenCount();
        if (hiddenCount > 0) {
            items.add("Show hidden apps (" + hiddenCount + ")");
            actions.add(repo::unhideAll);
        }
        items.add("App info");
        actions.add(() -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", app.pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(app.label)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    actions.get(which).run();
                    if (which < items.size() - 1) {
                        focusPkg = app.pkg;
                        buildUi();
                    }
                })
                .show();
    }

    /** Three preview tiles, one per theme. */
    private void showPicker() {
        picker = new FrameLayout(this);
        picker.setBackgroundColor(0xF20B1016);
        picker.setClickable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(px(48), px(90), px(48), 0);
        TextView title = new TextView(this);
        title.setText("Choose a home screen");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(26);
        title.setTypeface(Fonts.inter(this, 700));
        col.addView(title);
        TextView sub = new TextView(this);
        sub.setText("OK to switch · Back to cancel");
        sub.setTextColor(0x99FFFFFF);
        sub.setTextSize(14);
        sub.setTypeface(Fonts.inter(this, 500));
        sub.setPadding(0, px(6), 0, px(34));
        col.addView(sub);

        LinearLayout cards = new LinearLayout(this);
        cards.setClipChildren(false);
        String[][] themes = {{Themes.DEFAULT, "Default"}, {Themes.CONSOLE, "Console"}};
        int[] previews = {R.drawable.preview_default, R.drawable.preview_console};
        View first = null;
        for (int i = 0; i < themes.length; i++) {
            String id = themes[i][0];
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setClipChildren(false);
            FireTile t = new FireTile(this, new AppEntry("theme:" + id, themes[i][1],
                    getDrawable(previews[i]), null, null));
            t.setOnClickListener(v -> {
                if (id.equals(Themes.current(this))) closePicker();
                else Themes.switchTo(this, id);
            });
            c.addView(t, new LinearLayout.LayoutParams(px(240), px(135)));
            TextView name = new TextView(this);
            name.setText(themes[i][1] + (id.equals(Themes.current(this)) ? "  ·  current" : ""));
            name.setTextColor(0xFFE6EAF0);
            name.setTextSize(14);
            name.setTypeface(Fonts.inter(this, 600));
            name.setPadding(0, px(14), 0, 0);
            c.addView(name);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(px(14), 0, px(14), 0);
            cards.addView(c, lp);
            if (first == null) first = t;
        }
        col.addView(cards);
        picker.addView(col, new FrameLayout.LayoutParams(-1, -1));
        root.addView(picker, new FrameLayout.LayoutParams(-1, -1));
        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        first.requestFocus();
    }

    private void closePicker() {
        if (picker == null) return;
        root.removeView(picker);
        picker = null;
        content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        hero.requestFocus();
    }

    private int px(float v) {
        return Math.round(v * dp);
    }
}
