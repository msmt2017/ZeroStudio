package android.zero.studio.uidesigner.editor.palette.text;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.AutoCompleteTextView;

import android.zero.studio.uidesigner.utils.Constants;
import android.zero.studio.uidesigner.utils.Utils;

public class AutoCompleteTextViewDesign extends AutoCompleteTextView {

    private boolean drawStrokeEnabled;
    private boolean isBlueprint;

    public AutoCompleteTextViewDesign(Context context) {
        super(context);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (drawStrokeEnabled)
            Utils.drawDashPathStroke(
                this, canvas, isBlueprint ? Constants.BLUEPRINT_DASH_COLOR : Constants.DESIGN_DASH_COLOR);
    }

    public void setStrokeEnabled(boolean enabled) {
        drawStrokeEnabled = enabled;
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        if (isBlueprint) Utils.drawDashPathStroke(this, canvas, Constants.BLUEPRINT_DASH_COLOR);
        else super.draw(canvas);
    }

    public void setBlueprint(boolean isBlueprint) {
        this.isBlueprint = isBlueprint;
        invalidate();
    }
}
