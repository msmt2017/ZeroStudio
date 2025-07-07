package android.zero.studio.uidesigner.editor.palette.layouts;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import android.zero.studio.uidesigner.R;
import android.zero.studio.uidesigner.utils.Constants;
import android.zero.studio.uidesigner.utils.Utils;

public class ConstraintLayoutDesign extends ConstraintLayout {

    private boolean drawStrokeEnabled;
    private boolean isBlueprint;
    private final Paint constraintLinePaint;
    private final Paint handlePaint;
    private final Paint dragLinePaint;
    private final Paint baselinePaint;
    private final Drawable baselineHandleIcon;

    /**
     * Enum to represent different handles on a view that can be dragged.
     */
    public enum Handle {
        TOP, BOTTOM, LEFT, RIGHT,
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        BASELINE, // New handle for baseline constraint
        NONE
    }

    /**
     * Holds information about an active drag operation of a handle.
     */
    public static class DraggingInfo {
        public View view;
        public Handle handle;
        public float startX, startY;
        public float currentX, currentY;

        public DraggingInfo(View view, Handle handle, float startX, float startY) {
            this.view = view;
            this.handle = handle;
            this.startX = startX;
            this.startY = startY;
            this.currentX = startX;
            this.currentY = startY;
        }
    }

    private DraggingInfo draggingInfo = null;

    public ConstraintLayoutDesign(Context context) {
        super(context);

        constraintLinePaint = new Paint();
        constraintLinePaint.setColor(Color.parseColor("#03A9F4"));
        constraintLinePaint.setStrokeWidth(Utils.pxToDp(getContext(), 1));
        constraintLinePaint.setAntiAlias(true);
        constraintLinePaint.setStyle(Paint.Style.STROKE);

        baselinePaint = new Paint(constraintLinePaint);
        baselinePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{4, 4}, 0));

        handlePaint = new Paint();
        handlePaint.setAntiAlias(true);

        dragLinePaint = new Paint();
        dragLinePaint.setColor(Color.parseColor("#FFD700")); // Gold color for visibility
        dragLinePaint.setStrokeWidth(Utils.pxToDp(getContext(), 2));
        dragLinePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10, 5}, 0));
        dragLinePaint.setAntiAlias(true);

        baselineHandleIcon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_handle);

        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setDraggingInfo(DraggingInfo info) {
        this.draggingInfo = info;
        invalidate();
    }

    public void clearDraggingInfo() {
        this.draggingInfo = null;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (drawStrokeEnabled) {
            Utils.drawDashPathStroke(
                    this, canvas, isBlueprint ? Constants.BLUEPRINT_DASH_COLOR : Constants.DESIGN_DASH_COLOR);

            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child == null) continue;

                drawExistingConstraints(canvas, child);
                drawHandles(canvas, child);
            }
        }

        if (draggingInfo != null) {
            canvas.drawLine(draggingInfo.startX, draggingInfo.startY, draggingInfo.currentX, draggingInfo.currentY, dragLinePaint);
        }
    }

    private void drawHandles(Canvas canvas, View child) {
        handlePaint.setColor(isBlueprint ? Color.WHITE : Color.parseColor("#1689F6"));

        // Regular constraint handles
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(Utils.pxToDp(getContext(), 1));
        float handleRadius = Utils.pxToDp(getContext(), 4);

        canvas.drawCircle(child.getX() + child.getWidth() / 2f, child.getY(), handleRadius, handlePaint);
        canvas.drawCircle(child.getX() + child.getWidth() / 2f, child.getY() + child.getHeight(), handleRadius, handlePaint);
        canvas.drawCircle(child.getX(), child.getY() + child.getHeight() / 2f, handleRadius, handlePaint);
        canvas.drawCircle(child.getX() + child.getWidth(), child.getY() + child.getHeight() / 2f, handleRadius, handlePaint);

        // Resize handles
        handlePaint.setStyle(Paint.Style.FILL);
        float resizeHandleSize = Utils.pxToDp(getContext(), 8);
        canvas.drawRect(child.getX() - resizeHandleSize / 2, child.getY() - resizeHandleSize / 2, child.getX() + resizeHandleSize / 2, child.getY() + resizeHandleSize / 2, handlePaint);
        canvas.drawRect(child.getX() + child.getWidth() - resizeHandleSize / 2, child.getY() - resizeHandleSize / 2, child.getX() + child.getWidth() + resizeHandleSize / 2, child.getY() + resizeHandleSize / 2, handlePaint);
        canvas.drawRect(child.getX() - resizeHandleSize / 2, child.getY() + child.getHeight() - resizeHandleSize / 2, child.getX() + resizeHandleSize / 2, child.getY() + child.getHeight() + resizeHandleSize / 2, handlePaint);
        canvas.drawRect(child.getX() + child.getWidth() - resizeHandleSize / 2, child.getY() + child.getHeight() - resizeHandleSize / 2, child.getX() + child.getWidth() + resizeHandleSize / 2, child.getY() + child.getHeight() + resizeHandleSize / 2, handlePaint);

        // Baseline handle
        if (child instanceof TextView) {
            int baselineY = child.getBaseline();
            if(baselineY > 0) {
                 int iconSize = Utils.pxToDp(getContext(), 16);
                 int left = (int)(child.getX() + child.getWidth() / 2f - iconSize / 2);
                 int top = (int)(child.getY() + baselineY - iconSize / 2);
                 baselineHandleIcon.setBounds(left, top, left + iconSize, top + iconSize);
                 baselineHandleIcon.draw(canvas);
            }
        }
    }

    private void drawExistingConstraints(Canvas canvas, View child) {
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) child.getLayoutParams();

        // Top constraint
        if (params.topToTop != -1 || params.topToBottom != -1) {
            float startX = child.getX() + child.getWidth() / 2f;
            float startY = child.getY();
            View target = findViewById(params.topToTop != -1 ? params.topToTop : params.topToBottom);
            float endY = (target != null) ? (params.topToTop != -1 ? target.getTop() : target.getBottom()) : (params.topToTop == LayoutParams.PARENT_ID ? 0 : startY);
            drawWavyLine(canvas, startX, startY, startX, endY);
        }
        // Bottom constraint
        if (params.bottomToBottom != -1 || params.bottomToTop != -1) {
            float startX = child.getX() + child.getWidth() / 2f;
            float startY = child.getY() + child.getHeight();
            View target = findViewById(params.bottomToBottom != -1 ? params.bottomToBottom : params.bottomToTop);
            float endY = (target != null) ? (params.bottomToBottom != -1 ? target.getBottom() : target.getTop()) : (params.bottomToBottom == LayoutParams.PARENT_ID ? getHeight() : startY);
            drawWavyLine(canvas, startX, startY, startX, endY);
        }
        // Left / Start constraint
        if (params.startToStart != -1 || params.startToEnd != -1 || params.leftToLeft != -1 || params.leftToRight != -1) {
            float startX = child.getX();
            float startY = child.getY() + child.getHeight() / 2f;
            int targetId = getTargetId(params, Handle.LEFT);
            View target = findViewById(targetId);
            float endX = (target != null) ? (isStartSide(params, Handle.LEFT) ? target.getLeft() : target.getRight()) : (targetId == LayoutParams.PARENT_ID ? 0 : startX);
            drawWavyLine(canvas, startX, startY, endX, startY);
        }
        // Right / End constraint
        if (params.endToEnd != -1 || params.endToStart != -1 || params.rightToRight != -1 || params.rightToLeft != -1) {
            float startX = child.getX() + child.getWidth();
            float startY = child.getY() + child.getHeight() / 2f;
            int targetId = getTargetId(params, Handle.RIGHT);
            View target = findViewById(targetId);
            float endX = (target != null) ? (isStartSide(params, Handle.RIGHT) ? target.getLeft() : target.getRight()) : (targetId == LayoutParams.PARENT_ID ? getWidth() : startX);
            drawWavyLine(canvas, startX, startY, endX, startY);
        }
        // Baseline constraint
        if (params.baselineToBaseline != -1) {
            float startX = child.getX() + child.getWidth() / 2f;
            float startY = child.getY() + child.getBaseline();
            View target = findViewById(params.baselineToBaseline);
            if (target != null) {
                float endX = target.getX() + target.getWidth() / 2f;
                float endY = target.getY() + target.getBaseline();
                canvas.drawLine(startX, startY, endX, endY, baselinePaint);
            }
        }
    }

    private int getTargetId(LayoutParams params, Handle handle) {
        switch(handle) {
            case LEFT:
                if(params.startToStart != -1) return params.startToStart;
                if(params.startToEnd != -1) return params.startToEnd;
                if(params.leftToLeft != -1) return params.leftToLeft;
                return params.leftToRight;
            case RIGHT:
                if(params.endToEnd != -1) return params.endToEnd;
                if(params.endToStart != -1) return params.endToStart;
                if(params.rightToRight != -1) return params.rightToRight;
                return params.rightToLeft;
            default: return -1;
        }
    }
    
    private boolean isStartSide(LayoutParams params, Handle handle) {
        switch(handle) {
            case LEFT:
                return params.startToStart != -1 || params.leftToLeft != -1;
            case RIGHT:
                return params.endToStart != -1 || params.rightToLeft != -1;
            default: return false;
        }
    }

    private void drawWavyLine(Canvas canvas, float startX, float startY, float endX, float endY) {
        Path path = new Path();
        path.moveTo(startX, startY);
        float midX = (startX + endX) / 2;
        float midY = (startY + endY) / 2;
        float amplitude = 10f;

        if (Math.abs(startX - endX) < Math.abs(startY - endY)) { // Vertical-ish line
            path.cubicTo(startX, startY + (midY - startY) / 2, midX, midY - (midY - startY) / 2, midX, midY);
            path.cubicTo(midX, midY + (endY-midY)/2, endX, endY - (endY-midY)/2, endX, endY);
        } else { // Horizontal-ish line
             path.cubicTo(startX + (midX-startX)/2, startY, midX - (midX-startX)/2, midY, midX, midY);
             path.cubicTo(midX + (endX-midX)/2, midY, endX-(endX-midX)/2, endY, endX, endY);
        }
        canvas.drawPath(path, constraintLinePaint);
    }
    
    // ... other methods like draw, setBlueprint, setStrokeEnabled ...
    @Override
    public void draw(Canvas canvas) {
        if (isBlueprint) Utils.drawDashPathStroke(this, canvas, Constants.BLUEPRINT_DASH_COLOR);
        else super.draw(canvas);
    }

    public void setStrokeEnabled(boolean enabled) {
        drawStrokeEnabled = enabled;
        invalidate();
    }
}