package android.zero.studio.uidesigner.editor.palette.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.view.TextureView;

import android.zero.studio.uidesigner.utils.Constants;
import android.zero.studio.uidesigner.utils.Utils;

public class TextureViewDesign extends TextureView {

    private boolean drawStrokeEnabled;
    private boolean isBlueprint;

    public TextureViewDesign(Context context) {
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

    public void setBlueprint(boolean isBlueprint) {
        this.isBlueprint = isBlueprint;
        invalidate();
    }
}
