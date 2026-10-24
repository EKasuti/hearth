package app.hearth.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/**
 * A raised hardware key carrying an app's real artwork. Favorites are big keys
 * with the app's TV banner on a recessed plate; other apps are small keys with
 * their icon. Focus lights the key orange and lifts it.
 */
final class KeyCard extends View {

    enum Style { FAVORITE, SMALL, BUTTON, PRIMARY }

    final AppEntry app;
    final String key; // stable id for restoring focus

    private final Style style;
    private final String number;
    private final Palette p;
    private final float dp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF art = new RectF();
    private final Path clip = new Path();
    private int slot; // 1-6 when ticked in the favorites editor, 0 otherwise

    KeyCard(Context context, Palette palette, Style style, AppEntry app, String number) {
        super(context);
        this.p = palette;
        this.style = style;
        this.app = app;
        this.key = app.pkg;
        this.number = number;
        setContentDescription(app.label);
        this.dp = context.getResources().getDisplayMetrics().density;
        setFocusable(true);
        setClickable(true);
    }

    /** Shows a favorites slot number in place of the LED dot (0 hides it). */
    void setSlot(int slot) {
        this.slot = slot;
        invalidate();
    }

    @Override
    protected void onFocusChanged(boolean gain, int direction, Rect previous) {
        super.onFocusChanged(gain, direction, previous);
        if (style == Style.FAVORITE || style == Style.SMALL) { // buttons just light up
            float s = gain ? 1.05f : 1f;
            animate().scaleX(s).scaleY(s).translationY(gain ? -2 * dp : 0).setDuration(160).start();
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean f = isFocused();
        float w = getWidth(), h = getHeight(), r = 12 * dp, lift = 4 * dp;

        // Soft two-step shadow, then the key face.
        paint.setColor(f ? 0x55C2410C : 0x22000000);
        rect.set(2 * dp, lift + 2 * dp, w - 2 * dp, h);
        canvas.drawRoundRect(rect, r, r, paint);
        paint.setColor(f ? 0xFFD9480F : p.keyShadow);
        rect.set(0, lift, w, h - dp);
        canvas.drawRoundRect(rect, r, r, paint);
        paint.setColor(f ? Palette.ORANGE : style == Style.PRIMARY ? p.darkKey : p.key);
        rect.set(0, 0, w, h - lift);
        canvas.drawRoundRect(rect, r, r, paint);
        float faceH = h - lift;

        if (style == Style.FAVORITE) drawFavorite(canvas, f, w, faceH);
        else if (style == Style.SMALL) drawSmall(canvas, f, w, faceH);
        else drawButton(canvas, f, w, faceH);
    }

    private void drawButton(Canvas canvas, boolean f, float w, float faceH) {
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Fonts.mono(getContext(), 700));
        text.setTextSize(11 * dp);
        text.setLetterSpacing(0.2f);
        text.setColor(f ? 0xFF1E1D1B : style == Style.PRIMARY ? p.darkKeyInk : p.ink);
        canvas.drawText(app.label.toUpperCase(), w / 2f, faceH / 2f + 4 * dp, text);
    }

    private void drawFavorite(Canvas canvas, boolean f, float w, float faceH) {
        float in = 7 * dp, strip = 30 * dp;
        art.set(in, in, w - in, faceH - strip);
        float r = 7 * dp;
        clip.reset();
        clip.addRoundRect(art, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        Drawable d = app.banner;
        if (d != null) {
            d.setBounds((int) art.left, (int) art.top, (int) art.right, (int) art.bottom);
            d.draw(canvas);
        } else {
            canvas.drawColor(p.plate);
            drawIcon(canvas, art.centerX(), art.centerY(), art.height() * 0.5f);
        }
        canvas.restore();
        // Recessed plate edge.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp);
        paint.setColor(0x26000000);
        canvas.drawRoundRect(art, r, r, paint);
        paint.setStyle(Paint.Style.FILL);

        float base = faceH - 10.5f * dp;
        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(Fonts.mono(getContext(), 600));
        text.setTextSize(9.5f * dp);
        text.setLetterSpacing(0.04f);
        text.setColor(f ? 0xFF2A1A12 : p.inkSoft);
        canvas.drawText(number, in + 2 * dp, base, text);
        float nameX = in + 26 * dp;

        text.setTypeface(Fonts.inter(getContext(), 700));
        text.setTextSize(13.5f * dp);
        text.setLetterSpacing(-0.01f);
        text.setColor(f ? 0xFF1E1D1B : p.ink);
        float max = w - nameX - 22 * dp;
        canvas.drawText(TextUtils.ellipsize(app.label, text, max, TextUtils.TruncateAt.END).toString(),
                nameX, base, text);

        paint.setColor(f ? 0xFFFFFFFF : p.keyDot);
        canvas.drawCircle(w - in - 6 * dp, base - 4 * dp, 3 * dp, paint);
    }

    private void drawSmall(Canvas canvas, boolean f, float w, float faceH) {
        float in = 10 * dp, size = 30 * dp;
        drawIcon(canvas, in + size / 2f, in + size / 2f, size);

        if (slot > 0) {
            text.setTextAlign(Paint.Align.RIGHT);
            text.setTypeface(Fonts.mono(getContext(), 700));
            text.setTextSize(11 * dp);
            text.setLetterSpacing(0.04f);
            text.setColor(f ? 0xFFFFFFFF : Palette.ORANGE);
            canvas.drawText(String.format(java.util.Locale.US, "%02d", slot), w - in + 2 * dp, in + 9 * dp, text);
        } else {
            paint.setColor(f ? 0xFFFFFFFF : p.keyDot);
            canvas.drawCircle(w - in - 2 * dp, in + 4 * dp, 2.5f * dp, paint);
        }

        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(Fonts.inter(getContext(), 650));
        text.setTextSize(11.5f * dp);
        text.setLetterSpacing(-0.005f);
        text.setColor(f ? 0xFF1E1D1B : p.ink);
        canvas.drawText(TextUtils.ellipsize(app.label, text, w - 2 * in, TextUtils.TruncateAt.END)
                .toString(), in, faceH - 11 * dp, text);
    }

    private void drawIcon(Canvas canvas, float cx, float cy, float size) {
        Drawable icon = app.icon;
        if (icon == null) return;
        RectF b = new RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        Path c = new Path();
        c.addRoundRect(b, size * 0.24f, size * 0.24f, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(c);
        icon.setBounds((int) b.left, (int) b.top, (int) b.right, (int) b.bottom);
        icon.draw(canvas);
        canvas.restore();
    }
}
