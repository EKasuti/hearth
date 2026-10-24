package app.hearth.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.View;

/**
 * The big featured banner: the app's artwork on the right fading into a glow of
 * its brand colour, with the name and an "Open" pill on the left.
 */
final class HeroView extends View {

    private final float dp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private AppEntry app;
    private int color = 0xFF3B4A5C;
    private String eyebrow = "";

    HeroView(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        setFocusable(true);
        setClickable(true);
    }

    AppEntry app() {
        return app;
    }

    void show(AppEntry next, String eyebrow) {
        if (next == null) return;
        this.eyebrow = eyebrow;
        if (app == null) {
            app = next;
            color = signatureColor(next.banner != null ? next.banner : next.icon);
            setContentDescription(next.label);
            invalidate();
            return;
        }
        animate().alpha(0f).setDuration(220).withEndAction(() -> {
            app = next;
            color = signatureColor(next.banner != null ? next.banner : next.icon);
            setContentDescription(next.label);
            invalidate();
            animate().alpha(1f).setDuration(320).start();
        }).start();
    }

    @Override
    protected void onFocusChanged(boolean gain, int direction, Rect previous) {
        super.onFocusChanged(gain, direction, previous);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, 14 * dp, 14 * dp, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (app == null) return;
        int w = getWidth(), h = getHeight();
        canvas.save();
        canvas.clipPath(clip);

        // Brand-colour glow on a deep base.
        canvas.drawColor(0xFF0E141B);
        int rgb = color & 0x00FFFFFF;
        paint.setShader(new RadialGradient(w * 0.72f, h * 0.5f, w * 0.6f,
                new int[] {0x99000000 | rgb, 0x33000000 | rgb, 0x00000000}, new float[] {0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // Artwork on the right, fading into the left.
        float artH = h * 0.78f, artW = artH * 16f / 9f;
        float ax = w - artW - h * 0.11f, ay = (h - artH) / 2f;
        Drawable art = app.banner != null ? app.banner : app.icon;
        if (art != null) {
            RectF ar = new RectF(ax, ay, ax + artW, ay + artH);
            Path ac = new Path();
            ac.addRoundRect(ar, 10 * dp, 10 * dp, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(ac);
            if (app.banner == null) {
                canvas.drawColor(0xFF232B35);
                float s = artH * 0.5f;
                art.setBounds((int) (ar.centerX() - s / 2), (int) (ar.centerY() - s / 2),
                        (int) (ar.centerX() + s / 2), (int) (ar.centerY() + s / 2));
            } else {
                art.setBounds((int) ar.left, (int) ar.top, (int) ar.right, (int) ar.bottom);
            }
            art.draw(canvas);
            canvas.restore();
        }
        paint.setShader(new LinearGradient(0, 0, w * 0.62f, 0, 0xF20E141B, 0x000E141B, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // Text on the left.
        float x = 32 * dp;
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(Fonts.inter(getContext(), 700));
        text.setTextSize(11 * dp);
        text.setLetterSpacing(0.12f);
        text.setColor(0xFF8FB8FF);
        canvas.drawText(eyebrow.toUpperCase(), x, h * 0.34f, text);
        text.setTextSize(34 * dp);
        text.setLetterSpacing(-0.02f);
        text.setColor(0xFFFFFFFF);
        canvas.drawText(app.label, x, h * 0.34f + 40 * dp, text);

        // "Open" pill: white when the banner is focused, like Fire TV's buttons.
        boolean f = isFocused();
        float py = h * 0.34f + 62 * dp, pw = 96 * dp, ph = 34 * dp;
        paint.setColor(f ? 0xFFFFFFFF : 0x33FFFFFF);
        canvas.drawRoundRect(x, py, x + pw, py + ph, ph / 2, ph / 2, paint);
        text.setTextSize(13.5f * dp);
        text.setLetterSpacing(0f);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(f ? 0xFF0E141B : 0xFFFFFFFF);
        canvas.drawText("Open", x + pw / 2, py + ph / 2 + 5 * dp, text);
        canvas.restore();

        if (f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3 * dp);
            paint.setColor(0xFFFFFFFF);
            RectF r = new RectF(1.5f * dp, 1.5f * dp, w - 1.5f * dp, h - 1.5f * dp);
            canvas.drawRoundRect(r, 13 * dp, 13 * dp, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    /** The artwork's most vivid colour, ignoring white, black and grey. */
    private static int signatureColor(Drawable d) {
        if (d == null) return 0xFF3B4A5C;
        Bitmap bmp = Bitmap.createBitmap(24, 14, Bitmap.Config.ARGB_8888);
        Rect old = new Rect(d.getBounds());
        d.setBounds(0, 0, 24, 14);
        d.draw(new Canvas(bmp));
        d.setBounds(old);
        long r = 0, g = 0, b = 0, n = 0;
        float[] hsv = new float[3];
        for (int y = 0; y < 14; y++) {
            for (int x = 0; x < 24; x++) {
                int p = bmp.getPixel(x, y);
                Color.colorToHSV(p, hsv);
                if (Color.alpha(p) < 128 || hsv[1] < 0.35f || hsv[2] < 0.25f) continue;
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                n++;
            }
        }
        bmp.recycle();
        if (n == 0) return 0xFF3B4A5C;
        Color.colorToHSV(Color.rgb((int) (r / n), (int) (g / n), (int) (b / n)), hsv);
        hsv[1] = Math.max(hsv[1], 0.7f);
        hsv[2] = 0.9f;
        return Color.HSVToColor(hsv);
    }
}
