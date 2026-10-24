package app.hearth.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
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
 * Hearth: a hardware-console home screen. A green LCD and a rotary
 * knob on the left; favorites as big keys with each app's real artwork, and
 * every other app as a small key with its icon.
 */
public final class MainActivity extends Activity {

    private static final String[] DIAL = {"TIME", "WEATHER", "AIR", "DATE"};
    private static final int MAX_FAVORITES = 6;
    private static final long WEATHER_EVERY_MS = 20 * 60 * 1000L;

    // Layout grid, in dp, matching a 960x540 dp screen.
    private static final int PANEL_X = 30, PANEL_Y = 30, PANEL_W = 280, PANEL_H = 480;
    private static final int RIGHT_X = 334, RIGHT_W = 596;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<KeyCard> keys = new ArrayList<>();

    private AppRepo repo;
    private WeatherClient weather;
    private SharedPreferences prefs;
    private Palette p;
    private float dp;
    private long weatherAt;
    private WeatherClient.Data wx;
    private String focusKey;

    private FrameLayout root;
    private LcdView lcd;
    private TextView hints;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateLcd();
            refreshWeather();
            if (paletteNow() != p) buildUi(); // sunrise or sunset just passed
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
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        repo = new AppRepo(this);
        repo.reload();
        weather = new WeatherClient();
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
        if (repo == null) return; // redirected to another theme
        handler.post(ticker);
        handler.post(meterTick);
    }

    /**
     * Fetches weather when it's missing or older than 20 minutes. Called every
     * minute, so a failed fetch (e.g. no network yet after boot) retries soon.
     */
    private void refreshWeather() {
        if (fetching || (weatherAt != 0 && SystemClock.elapsedRealtime() - weatherAt < WEATHER_EVERY_MS)) {
            return;
        }
        fetching = true;
        weather.fetch(d -> {
            fetching = false;
            if (d == null || isDestroyed()) return;
            weatherAt = SystemClock.elapsedRealtime();
            wx = d;
            if (paletteNow() != p) buildUi();
            else updateLcd();
        });
    }

    private boolean fetching;

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(meterTick);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Themes.redirectIfNeeded(this)) return;
        closeEditor();
        if (!keys.isEmpty()) {
            if (appsScroll != null) appsScroll.smoothScrollTo(0, 0);
            keys.get(0).requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        closeEditor(); // this is home: Back only closes the favorites editor
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && editor == null && getCurrentFocus() instanceof KeyCard) {
            AppEntry app = ((KeyCard) getCurrentFocus()).app;
            if (!app.isAction()) {
                showOptions(app);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ------------------------------------------------------------------ layout

    private void buildUi() {
        // A rebuild replaces every view, so an open editor or picker is discarded.
        editor = null;
        returnFocus = null;
        if (getCurrentFocus() instanceof KeyCard) focusKey = ((KeyCard) getCurrentFocus()).key;
        p = paletteNow();
        keys.clear();

        root = new ConsoleView(this, p);
        buildConsolePanel();
        KeyCard first = buildFavorites();
        buildApps();

        hints = mono(9.5f, p.label, 600, 0.12f);
        hints.setText("←→↑↓ MOVE    OK OPEN    ≡ OPTIONS");
        place(hints, RIGHT_X, 497, -2, -2);

        setContentView(root);
        updateLcd();

        KeyCard target = first;
        for (KeyCard k : keys) if (k.key.equals(focusKey)) target = k;
        if (target != null) target.requestFocus();
    }

    /** Warm grey by day, graphite between sunset and sunrise. */
    private Palette paletteNow() {
        Calendar now = Calendar.getInstance();
        int min = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int rise = wx != null ? wx.sunriseMin : 7 * 60, set = wx != null ? wx.sunsetMin : 19 * 60;
        return min >= rise && min < set ? Palette.LIGHT : Palette.DARK;
    }

    private void buildConsolePanel() {
        View panel = new View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(18 * dp);
        bg.setColor(p.panel);
        bg.setStroke(Math.round(dp), p.panelEdge);
        panel.setBackground(bg);
        place(panel, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

        lcd = new LcdView(this);
        place(lcd, PANEL_X + 16, PANEL_Y + 16, PANEL_W - 32, 138);

        int dial = prefs.getInt("dial", 0);
        KnobView knob = new KnobView(this, p, DIAL, dial, pos -> {
            prefs.edit().putInt("dial", pos).apply();
            updateLcd();
        });
        knob.setId(View.generateViewId());
        knob.setOnFocusChangeListener((v, has) -> hints.setText(has
                ? "←→ TURN DIAL    ↑↓ BACK TO APPS"
                : "←→↑↓ MOVE    OK OPEN    ≡ OPTIONS"));
        place(knob, PANEL_X, PANEL_Y + 160, PANEL_W, 220);

        // Two VU meters under the knob: free memory and Wi-Fi signal.
        int mw = (PANEL_W - 32 - 10) / 2;
        ramMeter = new MeterView(this, "RAM");
        wifiMeter = new MeterView(this, "WIFI");
        place(ramMeter, PANEL_X + 16, PANEL_Y + 372, mw, 58);
        place(wifiMeter, PANEL_X + 16 + mw + 10, PANEL_Y + 372, mw, 58);

        TextView footer = mono(9, p.labelSoft, 600, 0.3f);
        footer.setText("HEARTH");
        place(footer, PANEL_X + 16, PANEL_Y + PANEL_H - 32, -2, -2);
        knobRef = knob;
    }

    private KnobView knobRef;
    private MeterView ramMeter, wifiMeter;

    /** Reads free memory and Wi-Fi strength and swings the meters to them. */
    private void updateMeters() {
        if (ramMeter == null) return;
        android.app.ActivityManager am = getSystemService(android.app.ActivityManager.class);
        android.app.ActivityManager.MemoryInfo mem = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(mem);
        float free = mem.availMem / (float) mem.totalMem;
        ramMeter.set(free, String.format(Locale.US, "%.1fG", mem.availMem / 1073741824f));

        android.net.wifi.WifiManager wm = getApplicationContext().getSystemService(android.net.wifi.WifiManager.class);
        @SuppressWarnings("deprecation")
        int rssi = wm == null ? -127 : wm.getConnectionInfo().getRssi();
        if (rssi <= -127 || rssi >= 0) {
            wifiMeter.set(0f, "OFF");
        } else {
            // -90 dBm (barely usable) .. -30 dBm (right next to the router)
            wifiMeter.set((rssi + 90) / 60f, rssi + "dBm");
        }
    }

    private final Runnable meterTick = new Runnable() {
        @Override
        public void run() {
            updateMeters();
            handler.postDelayed(this, 3000);
        }
    };

    private HorizontalScrollView appsScroll;

    private KeyCard buildFavorites() {
        List<AppEntry> favs = repo.favorites();
        int shown = Math.min(favs.size(), MAX_FAVORITES);
        sectionHeader("FAVORITES", shown, 30, true);

        int w = 189, gap = 14, h = Math.round((w - 14) * 9f / 16f) + 7 + 30 + 4, top = 54;
        KeyCard first = null;
        for (int i = 0; i < shown; i++) {
            AppEntry e = favs.get(i);
            KeyCard k = key(KeyCard.Style.FAVORITE, e, idx(i));
            if (i % 3 == 0) k.setNextFocusLeftId(knobRef.getId());
            if (i < 3) {
                if (editRef.getId() == View.NO_ID) editRef.setId(View.generateViewId());
                k.setNextFocusUpId(editRef.getId());
            }
            place(k, RIGHT_X + (i % 3) * (w + gap), top + (i / 3) * (h + 12), w, h);
            if (first == null) first = k;
        }
        if (first != null) {
            first.setId(View.generateViewId());
            knobRef.setNextFocusUpId(first.getId());
            knobRef.setNextFocusDownId(first.getId());
            knobRef.setNextFocusRightId(first.getId());
        }
        return first;
    }

    private void buildApps() {
        List<AppEntry> favs = repo.favorites();
        List<AppEntry> apps = new ArrayList<>(favs.subList(Math.min(favs.size(), MAX_FAVORITES), favs.size()));
        apps.addAll(repo.others());
        apps.add(new AppEntry("settings", "Settings", null,
                repo.iconFor("com.amazon.tv.settings.v2"), this::openSettings));
        apps.add(new AppEntry("theme", "Theme", null,
                getDrawable(R.drawable.ic_launcher), this::showThemePicker));
        sectionHeader("APPS", apps.size(), 362, false);

        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(0, px(8), px(70), px(10)); // room so the last key isn't cut off
        for (int i = 0; i < apps.size(); i++) {
            KeyCard k = key(KeyCard.Style.SMALL, apps.get(i), idx(i));
            if (i == 0) k.setNextFocusLeftId(knobRef.getId());
            if (i == apps.size() - 1) { // → at the end of the row stays put
                k.setId(View.generateViewId());
                k.setNextFocusRightId(k.getId());
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(100), px(86));
            lp.rightMargin = px(12);
            row.addView(k, lp);
        }
        appsScroll = new HorizontalScrollView(this);
        appsScroll.setHorizontalScrollBarEnabled(false);
        appsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        appsScroll.setClipChildren(false);
        appsScroll.setClipToPadding(false);
        appsScroll.addView(row);
        // Runs to the screen edge so the row visibly continues.
        place(appsScroll, RIGHT_X, 378, 960 - RIGHT_X, 110);
    }

    private KeyCard key(KeyCard.Style style, AppEntry e, String number) {
        KeyCard k = new KeyCard(this, p, style, e, number);
        k.setOnClickListener(v -> {
            if (e.isAction()) e.action.run();
            else launch(e.pkg);
        });
        if (!e.isAction()) {
            k.setOnLongClickListener(v -> {
                showOptions(e);
                return true;
            });
        }
        keys.add(k);
        return k;
    }

    private void sectionHeader(String title, int count, int y, boolean editable) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = mono(10, p.label, 600, 0.3f);
        t.setText(title);
        row.addView(t);
        View rule = new View(this);
        rule.setBackgroundColor(p.rule);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.max(1, px(1)), 1f);
        lp.setMargins(px(10), 0, px(10), 0);
        row.addView(rule, lp);
        TextView n = mono(10, p.label, 600, 0.2f);
        n.setText(String.format(Locale.US, "%02d", count));
        row.addView(n);
        if (editable) row.addView(editButton());
        place(row, RIGHT_X, y, RIGHT_W, 20);
    }

    /** A small focusable "EDIT" tag that opens the favorites checklist. */
    private View editButton() {
        TextView b = mono(9, p.label, 700, 0.2f);
        b.setText("EDIT");
        b.setGravity(Gravity.CENTER);
        b.setPadding(px(9), px(3), px(9), px(3));
        b.setFocusable(true);
        b.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(10));
        bg.setStroke(Math.max(1, px(1)), p.rule);
        b.setBackground(bg);
        b.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? Palette.ORANGE : 0);
            bg.setStroke(Math.max(1, px(1)), has ? Palette.ORANGE : p.rule);
            b.setTextColor(has ? 0xFF1E1D1B : p.label);
        });
        b.setOnClickListener(v -> editFavorites());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = px(10);
        b.setLayoutParams(lp);
        editRef = b;
        return b;
    }

    private View editRef;

    private FrameLayout editor;
    private TextView editorCount, editorHint;
    private final List<String> picked = new ArrayList<>();
    private final List<KeyCard> pickKeys = new ArrayList<>();

    /**
     * The favorites editor: a console panel over the right side with every app as
     * a small key. OK ticks or unticks; ticked keys show their slot (01-06).
     */
    private View returnFocus;

    /** Two big keys with a preview of each launcher look; OK switches. */
    private void showThemePicker() {
        returnFocus = getCurrentFocus();
        editor = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(18 * dp);
        bg.setColor(p.panel);
        bg.setStroke(Math.round(dp), p.panelEdge);
        editor.setBackground(bg);
        editor.setClickable(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(20), px(16), px(20), px(16));
        col.setClipChildren(false);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = mono(10, p.label, 600, 0.3f);
        title.setText("THEME");
        head.addView(title);
        View rule = new View(this);
        rule.setBackgroundColor(p.rule);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, Math.max(1, px(1)), 1f);
        rl.setMargins(px(10), 0, 0, 0);
        head.addView(rule, rl);
        col.addView(head, new LinearLayout.LayoutParams(-1, px(20)));

        LinearLayout cards = new LinearLayout(this);
        cards.setClipChildren(false);
        cards.setPadding(px(4), px(24), px(4), 0);
        String[][] themes = {
                {Themes.DEFAULT, "Default", "01"},
                {Themes.CONSOLE, "Console", "02"},
        };
        int[] previews = {R.drawable.preview_default, R.drawable.preview_console};
        KeyCard first = null;
        for (int i = 0; i < themes.length; i++) {
            String id = themes[i][0];
            boolean active = id.equals(Themes.current(this));
            AppEntry entry = new AppEntry("theme:" + id, themes[i][1] + (active ? " · active" : ""),
                    getDrawable(previews[i]), null, () -> {
                        if (id.equals(Themes.current(this))) closeEditor();
                        else Themes.switchTo(this, id);
                    });
            KeyCard k = new KeyCard(this, p, KeyCard.Style.FAVORITE, entry, themes[i][2]);
            k.setOnClickListener(v -> entry.action.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(270), px(190));
            lp.rightMargin = px(14);
            cards.addView(k, lp);
            if (first == null || active) first = k;
        }
        col.addView(cards);
        col.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        TextView hint = mono(9.5f, p.label, 600, 0.12f);
        hint.setText("OK SWITCH · BACK CANCEL");
        col.addView(hint);

        editor.addView(col, new FrameLayout.LayoutParams(-1, -1));
        place(editor, RIGHT_X - 12, 22, RIGHT_W + 24, 470);
        for (KeyCard k : keys) k.setFocusable(false);
        knobRef.setFocusable(false);
        editRef.setFocusable(false);
        appsScroll.setVisibility(View.INVISIBLE);
        hints.setVisibility(View.INVISIBLE);
        first.requestFocus();
    }

    private void editFavorites() {
        returnFocus = null;
        picked.clear();
        List<AppEntry> favs = repo.favorites();
        for (int i = 0; i < Math.min(favs.size(), MAX_FAVORITES); i++) picked.add(favs.get(i).pkg);
        List<AppEntry> all = new ArrayList<>(favs);
        all.addAll(repo.others());

        editor = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(18 * dp);
        bg.setColor(p.panel);
        bg.setStroke(Math.round(dp), p.panelEdge);
        editor.setBackground(bg);
        editor.setClickable(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(20), px(16), px(20), px(16));
        col.setClipChildren(false);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = mono(10, p.label, 600, 0.3f);
        title.setText("EDIT FAVORITES");
        head.addView(title);
        View rule = new View(this);
        rule.setBackgroundColor(p.rule);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, Math.max(1, px(1)), 1f);
        rl.setMargins(px(10), 0, px(10), 0);
        head.addView(rule, rl);
        editorCount = mono(10, p.ink, 700, 0.2f);
        head.addView(editorCount);
        col.addView(head, new LinearLayout.LayoutParams(-1, px(20)));

        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(5);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(px(4), px(10), px(4), px(10));
        pickKeys.clear();
        KeyCard first = null;
        for (int i = 0; i < all.size(); i++) {
            AppEntry e = all.get(i);
            KeyCard k = new KeyCard(this, p, KeyCard.Style.SMALL, e, "");
            k.setOnClickListener(v -> toggle(e.pkg));
            android.widget.GridLayout.LayoutParams g = new android.widget.GridLayout.LayoutParams();
            g.width = px(100);
            g.height = px(86);
            g.setMargins(0, 0, i % 5 == 4 ? 0 : px(11), px(10));
            grid.addView(k, g);
            pickKeys.add(k);
            if (first == null) first = k;
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.addView(grid);
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setClipChildren(false);
        foot.setPadding(0, 0, px(6), 0); // room for the focused key to lift
        editorHint = mono(9.5f, p.label, 600, 0.12f);
        foot.addView(editorHint, new LinearLayout.LayoutParams(0, -2, 1f));
        foot.addView(button(KeyCard.Style.BUTTON, "Cancel", this::closeEditor));
        View spacer = new View(this);
        foot.addView(spacer, new LinearLayout.LayoutParams(px(12), 1));
        KeyCard save = button(KeyCard.Style.PRIMARY, "Save", this::saveEditor);
        save.setId(View.generateViewId());
        foot.addView(save);
        // ↓ from the bottom row goes straight to Save. A key listener is needed
        // because the ScrollView would otherwise take the key to scroll.
        for (int i = Math.max(0, pickKeys.size() - 5); i < pickKeys.size(); i++) {
            pickKeys.get(i).setOnKeyListener((v, code, ev) -> {
                if (code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                if (ev.getAction() == KeyEvent.ACTION_DOWN) save.requestFocus();
                return true;
            });
        }
        col.addView(foot, new LinearLayout.LayoutParams(-1, -2));

        editor.addView(col, new FrameLayout.LayoutParams(-1, -1));
        place(editor, RIGHT_X - 12, 22, RIGHT_W + 24, 470);

        // Only the editor is focusable while it's open.
        for (KeyCard k : keys) k.setFocusable(false);
        knobRef.setFocusable(false);
        editRef.setFocusable(false);
        appsScroll.setVisibility(View.INVISIBLE);
        hints.setVisibility(View.INVISIBLE);
        refreshEditor(null);
        if (first != null) first.requestFocus();
    }

    private KeyCard button(KeyCard.Style style, String label, Runnable action) {
        KeyCard b = new KeyCard(this, p, style, new AppEntry(label.toLowerCase(Locale.US), label,
                null, null, action), "");
        b.setOnClickListener(v -> action.run());
        b.setLayoutParams(new LinearLayout.LayoutParams(px(108), px(44)));
        return b;
    }

    private void toggle(String pkg) {
        if (picked.remove(pkg)) {
            refreshEditor(null);
        } else if (picked.size() >= MAX_FAVORITES) {
            refreshEditor("FULL · UNTICK ONE FIRST");
        } else {
            picked.add(pkg);
            refreshEditor(null);
        }
    }

    private void refreshEditor(String warning) {
        for (KeyCard k : pickKeys) k.setSlot(picked.indexOf(k.key) + 1);
        editorCount.setText(String.format(Locale.US, "%02d / %02d", picked.size(), MAX_FAVORITES));
        editorCount.setTextColor(warning != null ? Palette.ORANGE : p.ink);
        editorHint.setText(warning != null ? warning : "OK TICK · BACK CANCEL");
        editorHint.setTextColor(warning != null ? Palette.ORANGE : p.label);
    }

    private void saveEditor() {
        repo.setFavorites(new ArrayList<>(picked));
        focusKey = picked.isEmpty() ? null : picked.get(0);
        editor = null;
        buildUi();
    }

    /** Returns true if the editor was open. */
    private boolean closeEditor() {
        if (editor == null) return false;
        root.removeView(editor);
        editor = null;
        for (KeyCard k : keys) k.setFocusable(true);
        knobRef.setFocusable(true);
        editRef.setFocusable(true);
        appsScroll.setVisibility(View.VISIBLE);
        hints.setVisibility(View.VISIBLE);
        (returnFocus != null ? returnFocus : editRef).requestFocus();
        returnFocus = null;
        return true;
    }

    // ------------------------------------------------------------------ LCD

    private void updateLcd() {
        if (lcd == null) return;
        Calendar now = Calendar.getInstance();
        boolean h24 = DateFormat.is24HourFormat(this);
        String temp = wx == null ? "--°" : wx.temp + (wx.fahrenheit ? "°F" : "°C");
        String cond = wx == null ? "NO SIGNAL" : WeatherClient.describe(wx.code).toUpperCase(Locale.US);
        String city = wx == null || wx.city == null ? "" : wx.city.toUpperCase(Locale.US);
        String aqi = wx == null || wx.aqi < 0 ? "" : "AQI " + wx.aqi;

        switch (prefs.getInt("dial", 0)) {
            case 1: { // WEATHER
                StringBuilder hours = new StringBuilder();
                if (wx != null) {
                    hours.append("H").append(wx.high).append(" L").append(wx.low);
                    for (int i = 0; i < 3 && wx.hours[i] != null; i++) {
                        hours.append(" · ").append(wx.hours[i].label.replace(" PM", "P").replace(" AM", "A"))
                                .append(' ').append(wx.hours[i].temp);
                    }
                }
                String big = wx == null ? "--°" : wx.temp + "°";
                lcd.show(city, wx == null ? "" : "FEELS " + wx.feels + "°", big,
                        wx == null ? "WAITING FOR DATA" : cond + " · " + hours);
                break;
            }
            case 2: { // AIR
                String big = wx == null || wx.aqi < 0 ? "---" : String.valueOf(wx.aqi);
                lcd.show("AIR QUALITY", "US AQI", big,
                        wx == null || wx.aqi < 0 ? "WAITING FOR DATA"
                                : WeatherClient.aqiLabel(wx.aqi).toUpperCase(Locale.US) + " · " + city);
                break;
            }
            case 3: { // DATE
                String big = DateFormat.format("dd MMM", now).toString().toUpperCase(Locale.US);
                lcd.show(DateFormat.format("EEEE", now).toString().toUpperCase(Locale.US),
                        DateFormat.format("yyyy", now).toString(), big,
                        Holidays.next(now).toUpperCase(Locale.US));
                break;
            }
            default: { // TIME
                String big = DateFormat.format(h24 ? "HH:mm" : "h:mm", now).toString();
                StringBuilder line = new StringBuilder(temp).append(' ').append(cond);
                if (!city.isEmpty()) line.append(" · ").append(city);
                if (!aqi.isEmpty()) line.append(" · ").append(aqi);
                lcd.show(DateFormat.format("EEE dd MMM", now).toString().toUpperCase(Locale.US),
                        h24 ? "" : DateFormat.format("a", now).toString().toUpperCase(Locale.US),
                        big, line.toString());
            }
        }
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

    private void openSettings() {
        Themes.openFireTvSettings(this);
    }

    private void showOptions(AppEntry app) {
        boolean fav = repo.isFavorite(app.pkg);
        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (fav) {
            items.add("Move earlier");
            actions.add(() -> repo.moveFavorite(app.pkg, -1));
            items.add("Move later");
            actions.add(() -> repo.moveFavorite(app.pkg, 1));
            items.add("Remove from Favorites");
            actions.add(() -> repo.toggleFavorite(app.pkg));
        } else {
            items.add("Add to Favorites");
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
                        focusKey = app.pkg;
                        buildUi();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------ helpers

    private void place(View v, int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                w < 0 ? w : px(w), h < 0 ? h : px(h));
        lp.leftMargin = px(x);
        lp.topMargin = px(y);
        root.addView(v, lp);
    }

    private TextView mono(float sp, int color, int weight, float spacing) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Fonts.mono(this, weight));
        t.setLetterSpacing(spacing);
        t.setIncludeFontPadding(false);
        return t;
    }

    private static String idx(int i) {
        return String.format(Locale.US, "%02d", i + 1);
    }

    private int px(float v) {
        return Math.round(v * dp);
    }
}
