package app.hearth.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.function.IntConsumer;

/**
 * A rotary selector with four labelled positions. When focused, Left/Right turn
 * it (with a short animated sweep); Up/Down move focus away as usual.
 */
final class KnobView extends View {

    // Clockwise from top-left, like the dial on a hi-fi.
    private static final float[] ANGLES = {-130f, -50f, 50f, 130f};

    private final String[] labels;
    private final Palette p;
    private final float dp;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final IntConsumer onTurn;

    private int position;
    private float angle;

    KnobView(Context context, Palette palette, String[] labels, int position, IntConsumer onTurn) {
        super(context);
        this.p = palette;
        this.labels = labels;
        this.position = position;
        this.angle = ANGLES[position];
        this.onTurn = onTurn;
        this.dp = context.getResources().getDisplayMetrics().density;
        setFocusable(true);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            turnTo((position + 1) % 4);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            turnTo((position + 3) % 4);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void turnTo(int next) {
        float from = angle, to = ANGLES[next];
        // Always sweep the short way round.
        if (to - from > 180) to -= 360;
        if (from - to > 180) to += 360;
        position = next;
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(260);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(v -> {
            angle = (float) v.getAnimatedValue();
            invalidate();
        });
        a.start();
        onTurn.accept(next);
    }

    @Override
    protected void onFocusChanged(boolean gain, int direction, Rect previous) {
        super.onFocusChanged(gain, direction, previous);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float outer = 56 * dp, body = 45 * dp, cap = 39 * dp;
        boolean f = isFocused();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(f ? 0xFFE9D6CC : 0xFFC9C5BE);
        canvas.drawCircle(cx, cy, outer, paint);
        paint.setColor(0xFF242321);
        canvas.drawCircle(cx, cy, body, paint);
        paint.setColor(0xFF34332F);
        canvas.drawCircle(cx, cy, cap, paint);

        // Pointer.
        double a = Math.toRadians(angle);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(4 * dp);
        paint.setColor(Palette.ORANGE);
        canvas.drawLine(cx + (float) Math.cos(a) * 4 * dp, cy + (float) Math.sin(a) * 4 * dp,
                cx + (float) Math.cos(a) * cap * 0.92f, cy + (float) Math.sin(a) * cap * 0.92f, paint);

        // Position dots and labels.
        paint.setStyle(Paint.Style.FILL);
        text.setTypeface(Fonts.mono(getContext(), 700));
        text.setTextSize(11 * dp);
        text.setLetterSpacing(0.04f);
        for (int i = 0; i < 4; i++) {
            double t = Math.toRadians(ANGLES[i]);
            float dx = (float) Math.cos(t), dy = (float) Math.sin(t);
            boolean on = i == position;
            paint.setColor(on ? Palette.ORANGE : 0xFF77746F);
            canvas.drawCircle(cx + dx * (outer + 10 * dp), cy + dy * (outer + 10 * dp),
                    (on ? 4.5f : 3.5f) * dp, paint);
            text.setColor(on ? p.ink : p.label);
            text.setTextAlign(dx < 0 ? Paint.Align.RIGHT : Paint.Align.LEFT);
            float lx = cx + dx * (outer + 22 * dp), ly = cy + dy * (outer + 30 * dp);
            canvas.drawText(labels[i], lx, ly + 4 * dp, text);
        }
    }
}
