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
import androidx.constraintlayout.widget.Guideline;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import android.zero.studio.uidesigner.R;
import android.zero.studio.uidesigner.managers.IdManager;


import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.HORIZONTAL;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;

public class ConstraintLayoutDesign extends ConstraintLayout {

    private boolean drawStrokeEnabled;
    private boolean isBlueprint;
    private final Paint constraintLinePaint;
    private final Paint handlePaint;
    private final Paint handleStrokePaint;
    private final Paint dragLinePaint;
    private final Paint baselinePaint;
    private final Paint alignmentLinePaint;
    private final Paint errorPaint;
    private final Paint blueprintTextPaint;
    private final Paint guidelinePaint;
    private final Drawable baselineHandleIcon;

    public enum Handle {
        TOP, BOTTOM, LEFT, RIGHT,
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        BASELINE,
        NONE
    }

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

    public static class AlignmentLine {
        public float startX, startY, endX, endY;
        public AlignmentLine(float startX, float startY, float endX, float endY) {
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
        }
    }

    private DraggingInfo draggingInfo = null;
    private List<AlignmentLine> alignmentLines = new ArrayList<>();
    private List<View> errorViews = new ArrayList<>();

    public ConstraintLayoutDesign(Context context) {
        super(context);
        float density = getContext().getResources().getDisplayMetrics().density;

        constraintLinePaint = new Paint();
        constraintLinePaint.setColor(Color.parseColor("#03A9F4"));
        constraintLinePaint.setStrokeWidth(1.5f * density);
        constraintLinePaint.setAntiAlias(true);
        constraintLinePaint.setStyle(Paint.Style.STROKE);

        baselinePaint = new Paint(constraintLinePaint);
        baselinePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{4 * density, 4 * density}, 0));

        handlePaint = new Paint();
        handlePaint.setAntiAlias(true);

        handleStrokePaint = new Paint();
        handleStrokePaint.setAntiAlias(true);
        handleStrokePaint.setStyle(Paint.Style.STROKE);
        handleStrokePaint.setStrokeWidth(1 * density);
        handleStrokePaint.setColor(Color.WHITE);

        dragLinePaint = new Paint();
        dragLinePaint.setColor(Color.parseColor("#FFD700"));
        dragLinePaint.setStrokeWidth(2 * density);
        dragLinePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10 * density, 5 * density}, 0));
        dragLinePaint.setAntiAlias(true);

        alignmentLinePaint = new Paint();
        alignmentLinePaint.setColor(Color.parseColor("#F44336"));
        alignmentLinePaint.setStrokeWidth(1 * density);
        alignmentLinePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{4 * density, 4 * density}, 0));
        alignmentLinePaint.setAntiAlias(true);
        alignmentLinePaint.setStyle(Paint.Style.STROKE);

        errorPaint = new Paint();
        errorPaint.setColor(Color.RED);
        errorPaint.setStrokeWidth(2 * density);
        errorPaint.setStyle(Paint.Style.STROKE);
        
        blueprintTextPaint = new Paint();
        blueprintTextPaint.setColor(Color.WHITE);
        blueprintTextPaint.setTextSize(12 * density);
        blueprintTextPaint.setAntiAlias(true);
        blueprintTextPaint.setTextAlign(Paint.Align.CENTER);

        guidelinePaint = new Paint();
        guidelinePaint.setColor(Color.parseColor("#E91E63")); // Pink
        guidelinePaint.setStrokeWidth(density); // 1dp
        guidelinePaint.setStyle(Paint.Style.STROKE);
        guidelinePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{4 * density, 4 * density}, 0));

        baselineHandleIcon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_handle);

        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
    }
    

    public void setDraggingInfo(DraggingInfo info) { this.draggingInfo = info; invalidate(); }
    public void clearDraggingInfo() { this.draggingInfo = null; invalidate(); }
    public void setAlignmentLines(List<AlignmentLine> lines) { this.alignmentLines = lines; invalidate(); }
    public void clearAlignmentLines() { if (!this.alignmentLines.isEmpty()) { this.alignmentLines.clear(); invalidate(); } }
    public void setBlueprint(boolean isBlueprint) { this.isBlueprint = isBlueprint; invalidate(); }
    public void setStrokeEnabled(boolean enabled) { this.drawStrokeEnabled = enabled; invalidate(); }
    public void setErrorViews(List<View> views) { this.errorViews.clear(); this.errorViews.addAll(views); invalidate(); }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isBlueprint) {
            canvas.drawColor(Color.parseColor("#235C6F"));
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Draw background elements first
        if (drawStrokeEnabled || isBlueprint) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof Guideline) {
                    drawGuideline(canvas, (Guideline) child);
                }
            }
        }
        

        super.dispatchDraw(canvas);
        

        if (drawStrokeEnabled || isBlueprint) {
            if (isBlueprint) {
                drawBlueprintLayer(canvas);
            }
            
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child == null || child instanceof Guideline) continue; // Skip Guidelines here
                drawExistingConstraints(canvas, child);
                drawHandles(canvas, child);
            }

            for (AlignmentLine line : alignmentLines) {
                canvas.drawLine(line.startX, line.startY, line.endX, line.endY, alignmentLinePaint);
            }

            for (View errorView : errorViews) {
                canvas.drawRect(errorView.getLeft(), errorView.getTop(), errorView.getRight(), errorView.getBottom(), errorPaint);
            }

            if (draggingInfo != null) {
                canvas.drawLine(draggingInfo.startX, draggingInfo.startY, draggingInfo.currentX, draggingInfo.currentY, dragLinePaint);
            }
        }
    }

    private void drawBlueprintLayer(Canvas canvas) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null || child instanceof Guideline) continue;

            handleStrokePaint.setColor(Color.WHITE);
            canvas.drawRect(child.getLeft(), child.getTop(), child.getRight(), child.getBottom(), handleStrokePaint);

            String viewId = IdManager.getIdMap().get(child);
            if (viewId == null || viewId.isEmpty()) {
                viewId = child.getClass().getSimpleName().replace("Design", "");
            }
            // Draw text in the center of the view
            float textX = child.getX() + child.getWidth() / 2f;
            float textY = child.getY() + child.getHeight() / 2f + (blueprintTextPaint.getTextSize() / 3);
            canvas.drawText(viewId, textX, textY, blueprintTextPaint);
        }
    }

    private void drawGuideline(Canvas canvas, Guideline guideline) {
        LayoutParams params = (LayoutParams) guideline.getLayoutParams();
        
        int position;
        if (params.guideBegin != -1) {
            position = getLeft() + params.guideBegin; // Position is relative to the parent's padding
        } else if (params.guideEnd != -1) {
            int parentDim = (params.orientation == HORIZONTAL ? getHeight() : getWidth());
            position = parentDim - params.guideEnd;
        } else {
            int parentDim = (params.orientation == HORIZONTAL ? getHeight() : getWidth());
            position = (int) (parentDim * params.guidePercent);
        }
        
        if (params.orientation == HORIZONTAL) {
            canvas.drawLine(0, position, getWidth(), position, guidelinePaint);
        } else { // VERTICAL
            canvas.drawLine(position, 0, position, getHeight(), guidelinePaint);
        }
    }

    private void drawHandles(Canvas canvas, View child) {
        float density = getContext().getResources().getDisplayMetrics().density;
        handlePaint.setColor(isBlueprint ? Color.WHITE : Color.parseColor("#1689F6"));
        handleStrokePaint.setColor(isBlueprint ? Color.DKGRAY : Color.WHITE);
        
        float handleRadius = 4 * density;
        canvas.drawCircle(child.getX() + child.getWidth() / 2f, child.getY(), handleRadius, handlePaint);
        canvas.drawCircle(child.getX() + child.getWidth() / 2f, child.getY(), handleRadius, handleStrokePaint);
        
        canvas.drawCircle(child.getX() + child.getWidth() / 2f, child.getY() + child.getHeight(), handleRadius, handlePaint);
        canvas.drawCircle(child.getX() + child.getWidth() / 2f, child.getY() + child.getHeight(), handleRadius, handleStrokePaint);
        
        canvas.drawCircle(child.getX(), child.getY() + child.getHeight() / 2f, handleRadius, handlePaint);
        canvas.drawCircle(child.getX(), child.getY() + child.getHeight() / 2f, handleRadius, handleStrokePaint);
        
        canvas.drawCircle(child.getX() + child.getWidth(), child.getY() + child.getHeight() / 2f, handleRadius, handlePaint);
        canvas.drawCircle(child.getX() + child.getWidth(), child.getY() + child.getHeight() / 2f, handleRadius, handleStrokePaint);
        
        float resizeHandleSize = 8 * density;
        float offset = resizeHandleSize / 2;
        canvas.drawRect(child.getX() - offset, child.getY() - offset, child.getX() + offset, child.getY() + offset, handlePaint);
        canvas.drawRect(child.getX() + child.getWidth() - offset, child.getY() - offset, child.getX() + child.getWidth() + offset, child.getY() + offset, handlePaint);
        canvas.drawRect(child.getX() - offset, child.getY() + child.getHeight() - offset, child.getX() + offset, child.getY() + child.getHeight() + offset, handlePaint);
        canvas.drawRect(child.getX() + child.getWidth() - offset, child.getY() + child.getHeight() - offset, child.getX() + child.getWidth() + offset, child.getY() + child.getHeight() + offset, handlePaint);

        if (child instanceof TextView && ((TextView) child).getLineCount() > 0) {
            int baselineY = child.getBaseline();
            if(baselineY > 0) {
                 int iconSize = (int) (16 * density);
                 int left = (int)(child.getX() + child.getWidth() / 2f - iconSize / 2);
                 int top = (int)(child.getY() + baselineY - iconSize / 2);
                 baselineHandleIcon.setBounds(left, top, left + iconSize, top + iconSize);
                 baselineHandleIcon.draw(canvas);
            }
        }
    }

    private void drawExistingConstraints(Canvas canvas, View child) {
        if (!(child.getLayoutParams() instanceof LayoutParams)) return;
        LayoutParams params = (LayoutParams) child.getLayoutParams();

        if (params.topToTop != -1 || params.topToBottom != -1) {
            float startX = child.getX() + child.getWidth() / 2f;
            float startY = child.getY();
            View target = findViewById(params.topToTop != -1 ? params.topToTop : params.topToBottom);
            if (target != null) {
                float endY = (params.topToTop != -1 ? target.getTop() : target.getBottom());
                drawWavyLine(canvas, startX, startY, startX, endY);
            } else if (params.topToTop == PARENT_ID) {
                drawWavyLine(canvas, startX, startY, startX, 0);
            }
        }
        if (params.bottomToBottom != -1 || params.bottomToTop != -1) {
            float startX = child.getX() + child.getWidth() / 2f;
            float startY = child.getY() + child.getHeight();
            View target = findViewById(params.bottomToBottom != -1 ? params.bottomToBottom : params.bottomToTop);
            if (target != null) {
                 float endY = (params.bottomToBottom != -1 ? target.getBottom() : target.getTop());
                 drawWavyLine(canvas, startX, startY, startX, endY);
            } else if (params.bottomToBottom == PARENT_ID) {
                drawWavyLine(canvas, startX, startY, startX, getHeight());
            }
        }
        if (params.startToStart != -1 || params.startToEnd != -1 || params.leftToLeft != -1 || params.leftToRight != -1) {
            float startX = child.getX();
            float startY = child.getY() + child.getHeight() / 2f;
            int targetId = getTargetId(params, Handle.LEFT);
            View target = findViewById(targetId);
            if (target != null) {
                float endX = isStartSide(params, Handle.LEFT) ? target.getLeft() : target.getRight();
                drawWavyLine(canvas, startX, startY, endX, startY);
            } else if (targetId == PARENT_ID) {
                drawWavyLine(canvas, startX, startY, 0, startY);
            }
        }
        if (params.endToEnd != -1 || params.endToStart != -1 || params.rightToRight != -1 || params.rightToLeft != -1) {
            float startX = child.getX() + child.getWidth();
            float startY = child.getY() + child.getHeight() / 2f;
            int targetId = getTargetId(params, Handle.RIGHT);
            View target = findViewById(targetId);
            if (target != null) {
                float endX = isEndSide(params, Handle.RIGHT) ? target.getRight() : target.getLeft();
                drawWavyLine(canvas, startX, startY, endX, startY);
            } else if (targetId == PARENT_ID) {
                drawWavyLine(canvas, startX, startY, getWidth(), startY);
            }
        }
        if (params.baselineToBaseline != -1) {
            if (!(child instanceof TextView) || ((TextView) child).getLineCount() == 0) return;
            float startX = child.getX() + child.getWidth() / 2f;
            float startY = child.getY() + child.getBaseline();
            View target = findViewById(params.baselineToBaseline);
            if (target instanceof TextView && ((TextView) target).getLineCount() > 0) {
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
    
    private boolean isEndSide(LayoutParams params, Handle handle) {
        switch(handle) {
            case LEFT:
                 return params.startToEnd != -1 || params.leftToRight != -1;
            case RIGHT:
                 return params.endToEnd != -1 || params.rightToRight != -1;
            default: return false;
        }
    }

    private void drawWavyLine(Canvas canvas, float startX, float startY, float endX, float endY) {
        Path path = new Path();
        path.moveTo(startX, startY);
        float midX = (startX + endX) / 2;
        float midY = (startY + endY) / 2;
        float controlPointOffset = 10 * getContext().getResources().getDisplayMetrics().density;

        if (Math.abs(startX - endX) < Math.abs(startY - endY)) {
            path.cubicTo(
                startX + controlPointOffset, (startY * 0.66f) + (midY * 0.33f),
                startX - controlPointOffset, (startY * 0.33f) + (midY * 0.66f),
                midX, midY
            );
             path.cubicTo(
                midX + controlPointOffset, (midY * 0.66f) + (endY * 0.33f),
                midX - controlPointOffset, (midY * 0.33f) + (endY * 0.66f),
                endX, endY
            );
        } else {
            path.cubicTo(
                 (startX * 0.66f) + (midX * 0.33f), startY + controlPointOffset,
                 (startX * 0.33f) + (midX * 0.66f), startY - controlPointOffset,
                 midX, midY
            );
             path.cubicTo(
                 (midX * 0.66f) + (endX * 0.33f), midY + controlPointOffset,
                 (midX * 0.33f) + (endX * 0.66f), midY - controlPointOffset,
                 endX, endY
            );
        }
        canvas.drawPath(path, constraintLinePaint);
    }
}