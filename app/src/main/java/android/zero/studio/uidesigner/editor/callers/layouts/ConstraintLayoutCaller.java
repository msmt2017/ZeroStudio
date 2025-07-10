package android.zero.studio.uidesigner.editor.callers.layouts;

import android.content.Context;
import android.view.View;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import android.zero.studio.uidesigner.managers.IdManager;
import android.zero.studio.uidesigner.utils.DimensionUtil;

/**
 * A utility class to programmatically apply ConstraintLayout attributes to a View.
 * This class acts as a bridge between the attribute editor dialogs and the actual view manipulation.
 *
 * Note: Many methods in this class create a new ConstraintSet, clone the layout,
 * and apply changes. For performance-critical operations (like real-time dragging),
 * 
 */
public class ConstraintLayoutCaller {

    // --- ConstraintSet Constants ---
    private static final int PARENT_ID = ConstraintSet.PARENT_ID;
    private static final int LEFT = ConstraintSet.LEFT;
    private static final int RIGHT = ConstraintSet.RIGHT;
    private static final int TOP = ConstraintSet.TOP;
    private static final int BOTTOM = ConstraintSet.BOTTOM;
    private static final int BASELINE = ConstraintSet.BASELINE;
    private static final int START = ConstraintSet.START;
    private static final int END = ConstraintSet.END;

    private static final int CHAIN_SPREAD = ConstraintSet.CHAIN_SPREAD;
    private static final int CHAIN_SPREAD_INSIDE = ConstraintSet.CHAIN_SPREAD_INSIDE;
    private static final int CHAIN_PACKED = ConstraintSet.CHAIN_PACKED;

    /**
     * Ensures that a view has a valid ID, generating one if it doesn't.
     * @param view The view to check.
     */
    private static void ensureViewId(View view) {
        if (view != null && view.getId() == View.NO_ID) {
            view.setId(View.generateViewId());
        }
    }

    /**
     * A helper to safely get the parent ConstraintLayout.
     * @param target The view whose parent is needed.
     * @return The parent ConstraintLayout, or null if the parent is not a ConstraintLayout.
     */
    private static ConstraintLayout getParentLayout(View target) {
        if (target != null && target.getParent() instanceof ConstraintLayout) {
            return (ConstraintLayout) target.getParent();
        }
        return null;
    }

    /**
     * Connects one side of a view to another's side.
     * @param target The view to apply the constraint on.
     * @param value The ID name of the target anchor (e.g., "button1", "parent").
     * @param startSide The side of the 'target' view to connect from.
     * @param endSide The side of the anchor view to connect to.
     */
    private static void setConstraint(View target, String value, int startSide, int endSide) {
        ConstraintLayout layout = getParentLayout(target);
        if (layout == null || value == null) return;
        
        ensureViewId(target);

        String targetIdName = value.startsWith("@id/") ? value.substring(4) : value;
        int targetId = "parent".equals(targetIdName) ? PARENT_ID : IdManager.getViewId(targetIdName);

        // Even if the target view exists, it might not have an ID yet in the IdManager
        // This is a defensive check.
        if (targetId == View.NO_ID && !"parent".equals(targetIdName)) {
            // Target view might not be in the map yet, try to find it
            // This part can be made more robust if needed.
            return;
        }

        ConstraintSet set = new ConstraintSet();
        set.clone(layout);
        set.connect(target.getId(), startSide, targetId, endSide);
        set.applyTo(layout);
    }
    
    /**
     * Sets a margin for a specific side of a view.
     * @param target The view to set the margin on.
     * @param side The side to apply the margin to (e.g., ConstraintSet.START).
     * @param value The margin value as a string (e.g., "16dp").
     */
    private static void setMargin(View target, int side, String value) {
        ConstraintLayout layout = getParentLayout(target);
        if (layout == null) return;
        
        ensureViewId(target);
        int margin = (int) DimensionUtil.parse(value, target.getContext());
        
        ConstraintSet set = new ConstraintSet();
        set.clone(layout);
        set.setMargin(target.getId(), side, margin);
        set.applyTo(layout);
    }
    
    /**
     * Sets a "gone" margin for a specific side of a view.
     * @param target The view to set the gone margin on.
     * @param side The side to apply the gone margin to.
     * @param value The margin value as a string.
     */
    private static void setGoneMargin(View target, int side, String value) {
        ConstraintLayout layout = getParentLayout(target);
        if (layout == null) return;
        
        ensureViewId(target);
        int margin = (int) DimensionUtil.parse(value, target.getContext());

        ConstraintSet set = new ConstraintSet();
        set.clone(layout);
        set.setGoneMargin(target.getId(), side, margin);
        set.applyTo(layout);
    }

    // --- Public API for Constraint Connections ---
    public static void setLeftToLeft(View target, String value, Context context) { setConstraint(target, value, LEFT, LEFT); }
    public static void setLeftToRight(View target, String value, Context context) { setConstraint(target, value, LEFT, RIGHT); }
    public static void setRightToLeft(View target, String value, Context context) { setConstraint(target, value, RIGHT, LEFT); }
    public static void setRightToRight(View target, String value, Context context) { setConstraint(target, value, RIGHT, RIGHT); }
    public static void setTopToTop(View target, String value, Context context) { setConstraint(target, value, TOP, TOP); }
    public static void setTopToBottom(View target, String value, Context context) { setConstraint(target, value, TOP, BOTTOM); }
    public static void setBottomToTop(View target, String value, Context context) { setConstraint(target, value, BOTTOM, TOP); }
    public static void setBottomToBottom(View target, String value, Context context) { setConstraint(target, value, BOTTOM, BOTTOM); }
    public static void setBaselineToBaseline(View target, String value, Context context) { setConstraint(target, value, BASELINE, BASELINE); }
    public static void setStartToStart(View target, String value, Context context) { setConstraint(target, value, START, START); }
    public static void setStartToEnd(View target, String value, Context context) { setConstraint(target, value, START, END); }
    public static void setEndToStart(View target, String value, Context context) { setConstraint(target, value, END, START); }
    public static void setEndToEnd(View target, String value, Context context) { setConstraint(target, value, END, END); }
    
    // --- Public API for Margins ---
    public static void setLayoutMarginStart(View target, String value, Context context) { setMargin(target, START, value); }
    public static void setLayoutMarginEnd(View target, String value, Context context) { setMargin(target, END, value); }
    public static void setLayoutMarginLeft(View target, String value, Context context) { setMargin(target, LEFT, value); }
    public static void setLayoutMarginTop(View target, String value, Context context) { setMargin(target, TOP, value); }
    public static void setLayoutMarginRight(View target, String value, Context context) { setMargin(target, RIGHT, value); }
    public static void setLayoutMarginBottom(View target, String value, Context context) { setMargin(target, BOTTOM, value); }
    public static void setLayoutMarginBaseline(View target, String value, Context context) { setMargin(target, BASELINE, value); }

    // --- Public API for Gone Margins ---
    public static void setLayoutGoneMarginStart(View target, String value, Context context) { setGoneMargin(target, START, value); }
    public static void setLayoutGoneMarginEnd(View target, String value, Context context) { setGoneMargin(target, END, value); }
    public static void setLayoutGoneMarginLeft(View target, String value, Context context) { setGoneMargin(target, LEFT, value); }
    public static void setLayoutGoneMarginTop(View target, String value, Context context) { setGoneMargin(target, TOP, value); }
    public static void setLayoutGoneMarginRight(View target, String value, Context context) { setGoneMargin(target, RIGHT, value); }
    public static void setLayoutGoneMarginBottom(View target, String value, Context context) { setGoneMargin(target, BOTTOM, value); }
    public static void setLayoutGoneMarginBaseline(View target, String value, Context context) { setGoneMargin(target, BASELINE, value); }

    // --- Public API for Bias (Optimized to not use ConstraintSet) ---
    public static void setHorizontalBias(View target, String value, Context context) {
        if (target != null && target.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) target.getLayoutParams();
            try {
                float bias = Float.parseFloat(value);
                params.horizontalBias = Math.max(0.0f, Math.min(1.0f, bias));
                target.setLayoutParams(params);
            } catch (NumberFormatException e) {
                // Handle invalid float string if necessary
            }
        }
    }

    public static void setVerticalBias(View target, String value, Context context) {
        if (target != null && target.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) target.getLayoutParams();
            try {
                float bias = Float.parseFloat(value);
                params.verticalBias = Math.max(0.0f, Math.min(1.0f, bias));
                target.setLayoutParams(params);
            } catch (NumberFormatException e) {
                // Handle invalid float string
            }
        }
    }
    

    public static void setGuideBegin(View target, String value, Context context) {
        if (target != null && target.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) target.getLayoutParams();
            params.guideBegin = (int) DimensionUtil.parse(value, context);
            params.guideEnd = ConstraintLayout.LayoutParams.UNSET;
            params.guidePercent = -1f;
            target.setLayoutParams(params);
        }
    }
    
    public static void setGuideEnd(View target, String value, Context context) {
        if (target != null && target.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) target.getLayoutParams();
            params.guideEnd = (int) DimensionUtil.parse(value, context);
            params.guideBegin = ConstraintLayout.LayoutParams.UNSET;
            params.guidePercent = -1f;
            target.setLayoutParams(params);
        }
    }
    
    public static void setGuidePercent(View target, String value, Context context) {
        if (target != null && target.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) target.getLayoutParams();
            try {
                params.guidePercent = Float.parseFloat(value);
                params.guideBegin = ConstraintLayout.LayoutParams.UNSET;
                params.guideEnd = ConstraintLayout.LayoutParams.UNSET;
                target.setLayoutParams(params);
            } catch (NumberFormatException e) {
                // Handle invalid float string
            }
        }
    }
    
    
    public static void setHorizontalChainStyle(View target, String value, Context context) {
        ConstraintLayout layout = getParentLayout(target);
        if (layout == null) return;
        
        ensureViewId(target);
        int style = CHAIN_SPREAD; // default
        if ("spread_inside".equals(value)) style = CHAIN_SPREAD_INSIDE;
        if ("packed".equals(value)) style = CHAIN_PACKED;
        
        ConstraintSet set = new ConstraintSet();
        set.clone(layout);
        set.setHorizontalChainStyle(target.getId(), style);
        set.applyTo(layout);
    }

    public static void setVerticalChainStyle(View target, String value, Context context) {
        ConstraintLayout layout = getParentLayout(target);
        if (layout == null) return;

        ensureViewId(target);
        int style = CHAIN_SPREAD; // default
        if ("spread_inside".equals(value)) style = CHAIN_SPREAD_INSIDE;
        if ("packed".equals(value)) style = CHAIN_PACKED;

        ConstraintSet set = new ConstraintSet();
        set.clone(layout);
        set.setVerticalChainStyle(target.getId(), style);
        set.applyTo(layout);
    }
}