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

/** A Fire TV-style app tile: the app's banner (or icon and name) with a white ring on focus. */
final class FireTile extends View {

    final AppEntry app;
    private final float dp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();

    FireTile(Context context, AppEntry app) {
        super(context);
        this.app = app;
        this.dp = context.getResources().getDisplayMetrics().density;
        setFocusable(true);
        setClickable(true);
        setContentDescription(app.label);
    }

    @Override
    protected void onFocusChanged(boolean gain, int direction, Rect previous) {
        super.onFocusChanged(gain, direction, previous);
        float s = gain ? 1.1f : 1f;
        animate().scaleX(s).scaleY(s).translationZ(gain ? 12 * dp : 0).setDuration(150).start();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, 8 * dp, 8 * dp, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawColor(0xFF232B35);
        if (app.banner != null) {
            app.banner.setBounds(0, 0, w, h);
            app.banner.draw(canvas);
        } else {
            Drawable icon = app.icon;
            float s = h * 0.42f;
            if (icon != null) {
                icon.setBounds((int) (w / 2f - s / 2), (int) (h * 0.14f), (int) (w / 2f + s / 2), (int) (h * 0.14f + s));
                icon.draw(canvas);
            }
            text.setTypeface(Fonts.inter(getContext(), 600));
            text.setTextSize(10.5f * dp);
            text.setColor(0xE6FFFFFF);
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(TextUtils.ellipsize(app.label, text, w - 10 * dp, TextUtils.TruncateAt.END).toString(),
                    w / 2f, h - 9 * dp, text);
        }
        canvas.restore();
        if (isFocused()) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3 * dp);
            paint.setColor(0xFFFFFFFF);
            rect.set(1.5f * dp, 1.5f * dp, w - 1.5f * dp, h - 1.5f * dp);
            canvas.drawRoundRect(rect, 7 * dp, 7 * dp, paint);
        }
    }
}
