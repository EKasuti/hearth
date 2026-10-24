package app.hearth.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** A green reflective LCD: two small corner readouts, one big readout and a status line. */
final class LcdView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float dp;

    private String topLeft = "", topRight = "", big = "", bottom = "";

    LcdView(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
    }

    void show(String topLeft, String topRight, String big, String bottom) {
        this.topLeft = topLeft;
        this.topRight = topRight;
        this.big = big;
        this.bottom = bottom;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // Inner shadow along the top edge, as if the glass sits below the bezel.
        shadow.setShader(new LinearGradient(0, 0, 0, 14 * dp, 0x33000000, 0x00000000,
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), r = 8 * dp;
        rect.set(0, 0, w, h);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Palette.LCD);
        canvas.drawRoundRect(rect, r, r, paint);
        canvas.drawRoundRect(rect, r, r, shadow);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * dp);
        paint.setColor(Palette.LCD_EDGE);
        rect.inset(dp, dp);
        canvas.drawRoundRect(rect, r, r, paint);

        float pad = 16 * dp;
        text.setTypeface(Fonts.mono(getContext(), 700));
        text.setTextSize(11 * dp);
        text.setLetterSpacing(0.04f);
        text.setColor(Palette.LCD_INK);
        text.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(topLeft, pad, pad + 9 * dp, text);
        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(topRight, w - pad, pad + 9 * dp, text);

        // Big readout, shrunk to fit the width.
        text.setTextAlign(Paint.Align.LEFT);
        text.setLetterSpacing(-0.04f);
        float size = 76 * dp;
        text.setTextSize(size);
        while (text.measureText(big) > w - 2 * pad && size > 20 * dp) {
            size -= dp;
            text.setTextSize(size);
        }
        float baseline = h / 2f + size * 0.36f;
        text.setColor(Palette.LCD_INK);
        canvas.drawText(big, pad - 4 * dp, baseline, text);

        text.setTextSize(11.5f * dp);
        text.setLetterSpacing(0.03f);
        while (text.measureText(bottom) > w - 2 * pad && text.getTextSize() > 8 * dp) {
            text.setTextSize(text.getTextSize() - 0.5f * dp);
        }
        canvas.drawText(bottom, pad, h - pad + 2 * dp, text);
    }
}
