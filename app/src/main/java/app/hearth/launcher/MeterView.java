package app.hearth.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * A small analogue VU meter: cream face, a 120° scale with an orange zone at the
 * top end, and a needle that swings to each new reading with a little overshoot.
 */
final class MeterView extends View {

    private static final float SWEEP = 120f; // degrees, centred on straight up

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint();
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final float dp;
    private final String title;

    private float value;      // 0..1, where the needle is drawn
    private String reading = "";
    private ValueAnimator anim;

    MeterView(Context context, String title) {
        super(context);
        this.title = title;
        this.dp = context.getResources().getDisplayMetrics().density;
    }

    /** Moves the needle to {@code target} (0..1) and shows {@code reading} on the face. */
    void set(float target, String reading) {
        this.reading = reading;
        target = Math.max(0f, Math.min(1f, target));
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(value, target);
        anim.setDuration(900);
        anim.setInterpolator(new OvershootInterpolator(2.2f));
        anim.addUpdateListener(a -> {
            value = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float r = 6 * dp;
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, r, r, Path.Direction.CW);
        // Glass shadow under the bezel's top edge.
        shade.setShader(new LinearGradient(0, 0, 0, 10 * dp, 0x2E000000, 0x00000000, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), r = 6 * dp;

        canvas.save();
        canvas.clipPath(clip);
        canvas.drawColor(0xFFF3EFE3);

        // Pivot sits below the face so only the top of the arc shows, like a real VU meter.
        float cx = w / 2f, cy = h + 6 * dp, radius = h * 0.95f;
        float start = -90 - SWEEP / 2;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * dp);
        paint.setColor(0xFF6E6B66);
        RectF arc = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(arc, start, SWEEP * 0.8f, false, paint);
        paint.setStrokeWidth(3 * dp);
        paint.setColor(Palette.ORANGE);
        canvas.drawArc(arc, start + SWEEP * 0.8f, SWEEP * 0.2f, false, paint);

        // Ticks: 11 of them, every other one longer.
        paint.setStrokeWidth(1.2f * dp);
        for (int i = 0; i <= 10; i++) {
            double a = Math.toRadians(start + SWEEP * i / 10f);
            float len = (i % 2 == 0 ? 7 : 4) * dp;
            paint.setColor(i >= 8 ? Palette.ORANGE : 0xFF3A3935);
            canvas.drawLine(cx + (float) Math.cos(a) * radius, cy + (float) Math.sin(a) * radius,
                    cx + (float) Math.cos(a) * (radius - len), cy + (float) Math.sin(a) * (radius - len), paint);
        }

        // Labels.
        text.setTypeface(Fonts.mono(getContext(), 700));
        text.setLetterSpacing(0.1f);
        text.setTextSize(8.5f * dp);
        text.setColor(0xFF3A3935);
        text.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(title, 7 * dp, h - 6 * dp, text);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(0xFF1E1D1B);
        canvas.drawText(reading, w - 7 * dp, h - 6 * dp, text);

        // Needle.
        double a = Math.toRadians(start + SWEEP * value);
        paint.setColor(0xFF1E1D1B);
        paint.setStrokeWidth(1.5f * dp);
        paint.setStrokeCap(Paint.Cap.ROUND);
        // Only the top of the needle shows; the pivot is hidden below the labels.
        float from = radius * 0.42f, to = radius - 2 * dp;
        canvas.drawLine(cx + (float) Math.cos(a) * from, cy + (float) Math.sin(a) * from,
                cx + (float) Math.cos(a) * to, cy + (float) Math.sin(a) * to, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);

        canvas.drawRect(0, 0, w, h, shade);
        canvas.restore();

        // Bezel.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * dp);
        paint.setColor(0xFFB9B5AE);
        rect.set(0.75f * dp, 0.75f * dp, w - 0.75f * dp, h - 0.75f * dp);
        canvas.drawRoundRect(rect, r, r, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
