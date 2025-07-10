
package android.zero.studio.uidesigner.editor.palette.layouts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import androidx.constraintlayout.widget.ConstraintLayout;

public class GuidelineDesign extends View {
    private Paint mPaint;
    private boolean isHorizontal;

    public GuidelineDesign(Context context) {
        super(context);
        init();
    }

    private void init() {
        mPaint = new Paint();
        mPaint.setColor(Color.parseColor("#E91E63")); // Pink color for guidelines
        mPaint.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{4, 4}, 0));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) getLayoutParams();
        // A guideline has 0 width or height, so we draw a line across its parent
        if (params.orientation == ConstraintLayout.LayoutParams.HORIZONTAL) { // This is a horizontal guideline
            canvas.drawLine(0, getHeight() / 2f, getRootView().getWidth(), getHeight() / 2f, mPaint);
        } else { // Vertical
            canvas.drawLine(getWidth() / 2f, 0, getWidth() / 2f, getRootView().getHeight(), mPaint);
        }
    }
    
    // We need a way to set blueprint mode, even if it does nothing, to avoid crashes
    public void setBlueprint(boolean isBlueprint) {
        // No visual change for guideline in blueprint mode, but method must exist
    }
    
    public void setStrokeEnabled(boolean enabled) {
        // Not applicable, but method must exist
    }
}