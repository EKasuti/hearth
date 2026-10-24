package app.hearth.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.FrameLayout;

/** The console chassis: flat finish with a screw in each corner. */
final class ConsoleView extends FrameLayout {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Palette p;
    private final float dp;

    ConsoleView(Context context, Palette palette) {
        super(context);
        this.p = palette;
        this.dp = context.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(p.background);
        float m = 15 * dp;
        screw(canvas, m, m);
        screw(canvas, getWidth() - m, m);
        screw(canvas, m, getHeight() - m);
        screw(canvas, getWidth() - m, getHeight() - m);
    }

    private void screw(Canvas canvas, float x, float y) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(p.screw);
        canvas.drawCircle(x, y, 4.5f * dp, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp);
        paint.setColor(0x33000000);
        canvas.drawCircle(x, y, 4.5f * dp, paint);
    }
}
