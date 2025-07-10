package android.zero.studio.uidesigner.editor.palette.layouts;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import android.zero.studio.uidesigner.utils.Constants;
import android.zero.studio.uidesigner.utils.Utils;

/**
 * A generic wrapper for any ViewGroup that needs to be displayed in the editor.
 * This wrapper handles the drawing of editor-specific UI like strokes and handles,
 * while the actual target ViewGroup is added as a child.
 */
public class ViewGroupDesign extends FrameLayout {

    private boolean drawStrokeEnabled;
    private boolean isBlueprint;
    private View wrappedView;

    public ViewGroupDesign(@NonNull Context context, @NonNull View wrappedView) {
        super(context);
        this.wrappedView = wrappedView;
        
        // Add the actual view as a child, matching the wrapper's size
        addView(wrappedView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public View getWrappedView() {
        return wrappedView;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (drawStrokeEnabled) {
            Utils.drawDashPathStroke(
                this, canvas, isBlueprint ? Constants.BLUEPRINT_DASH_COLOR : Constants.DESIGN_DASH_COLOR);
        }
    }

    public void setStrokeEnabled(boolean enabled) {
        drawStrokeEnabled = enabled;
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        if (isBlueprint) {
            Utils.drawDashPathStroke(this, canvas, Constants.BLUEPRINT_DASH_COLOR);
        }
        super.draw(canvas);
    }

    public void setBlueprint(boolean isBlueprint) {
        this.isBlueprint = isBlueprint;
        invalidate();
        
        // Pass the blueprint state to children if they are also Design wrappers
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            try {
                // Use reflection to call setBlueprint on any child that has it
                child.getClass().getMethod("setBlueprint", boolean.class).invoke(child, isBlueprint);
            } catch (Exception e) {
                // Child doesn't have this method, which is fine.
            }
        }
    }
}