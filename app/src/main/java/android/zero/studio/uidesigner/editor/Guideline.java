package android.zero.studio.uidesigner.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import androidx.constraintlayout.widget.ConstraintLayout;

public class Guideline extends View {
    private Paint paint;
    private int orientation; // 0 for horizontal, 1 for vertical

    public Guideline(Context context, int orientation) {
        super(context);
        this.orientation = orientation;
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.parseColor("#FFC107")); // A bright color for visibility
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.STROKE);
        paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10, 5}, 0));
    }

    public int getOrientation() {
        return orientation;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (orientation == 0) { // Horizontal
            canvas.drawLine(0, getHeight() / 2f, getWidth(), getHeight() / 2f, paint);
        } else { // Vertical
            canvas.drawLine(getWidth() / 2f, 0, getWidth() / 2f, getHeight(), paint);
        }
    }
}