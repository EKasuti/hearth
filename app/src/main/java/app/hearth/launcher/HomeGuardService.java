package app.hearth.launcher;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS doesn't let users pick another default home app. When Amazon's home
 * screen comes to the front, bring Hearth up instead.
 *
 * Fire OS always shows Amazon's home first, so the redirect fires quickly to
 * keep that flash short. Two cases must not be redirected:
 * - Amazon's Settings shortcut passes through its home screen on the way
 *   (Settings handler -> HomeActivity -> MainSettingsActivity).
 * - Any other app coming to the front cancels a pending redirect, so we never
 *   jump on top of an app the user just opened.
 *
 * Amazon's home can also end up in front while this service isn't running
 * (Android killed the process to free memory). Pressing Home on a screen that
 * is already showing produces no window change, so focus events from Amazon's
 * home (any button press there) also trigger the redirect.
 */
public final class HomeGuardService extends AccessibilityService {

    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_SETTINGS = "com.amazon.tv.settings.v2";
    private static final long SETTLE_MS = 150;
    private static final long SETTINGS_WINDOW_MS = 2000;
    private static final long BOOT_WINDOW_MS = 5 * 60 * 1000L;

    /** What's in front, as far as this service knows. */
    private enum Front { UNKNOWN, AMAZON_HOME, AMAZON_OTHER, OTHER }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable redirect = this::redirect;
    private long settingsSeenAt = -SETTINGS_WINDOW_MS;
    private Front front = Front.UNKNOWN;

    @Override
    protected void onServiceConnected() {
        // Right after boot Amazon's home is already showing and won't change on its own.
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) {
            redirect();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        String p = pkg.toString();

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                onWindowChanged(p, event.getClassName());
                break;
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                // Focus moving on Amazon's home: the user is on it and pressing buttons.
                // UNKNOWN covers the case where it came to the front while we weren't running.
                if (AMAZON_LAUNCHER.equals(p) && (front == Front.AMAZON_HOME || front == Front.UNKNOWN)) {
                    handler.removeCallbacks(redirect);
                    handler.postDelayed(redirect, SETTLE_MS);
                }
                break;
            default:
                break;
        }
    }

    private void onWindowChanged(String p, CharSequence cls) {
        if (cls == null) return;
        if (AMAZON_SETTINGS.equals(p)) settingsSeenAt = SystemClock.uptimeMillis();
        if (p.equals("com.android.systemui")) return; // toasts, volume bar: not a new screen

        handler.removeCallbacks(redirect);
        boolean home = AMAZON_LAUNCHER.equals(p) && cls.toString().contains("HomeActivity");
        front = home ? Front.AMAZON_HOME : AMAZON_LAUNCHER.equals(p) ? Front.AMAZON_OTHER : Front.OTHER;
        if (home && SystemClock.uptimeMillis() - settingsSeenAt > SETTINGS_WINDOW_MS) {
            handler.postDelayed(redirect, SETTLE_MS);
        }
    }

    private void redirect() {
        startActivity(Themes.homeIntent(this));
    }

    @Override
    public void onInterrupt() {
    }
}
