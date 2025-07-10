package android.zero.studio.uidesigner.editor.callers.layouts;

import android.content.Context;
import android.view.View;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import android.zero.studio.uidesigner.managers.IdManager;
import android.zero.studio.uidesigner.utils.DimensionUtil;

public class ConstraintUpdater {
    private final ConstraintSet set;
    private final ConstraintLayout layout;
    private final Context context;

    public ConstraintUpdater(ConstraintLayout layout) {
        this.layout = layout;
        this.context = layout.getContext();
        this.set = new ConstraintSet();
        this.set.clone(layout);
    }

    private int getTargetId(String value) {
        if (value == null) return ConstraintSet.UNSET;
        if ("parent".equals(value)) {
            return ConstraintSet.PARENT_ID;
        }
        String idName = value.startsWith("@+id/") ? value.substring(5) :
                        value.startsWith("@id/") ? value.substring(4) : value;
        return IdManager.getViewId(idName);
    }

    public ConstraintUpdater connect(int startId, int startSide, int endId, int endSide) {
        set.connect(startId, startSide, endId, endSide);
        return this;
    }

    public ConstraintUpdater clear(int viewId, int side) {
        set.clear(viewId, side);
        return this;
    }

    public ConstraintUpdater setMargin(int viewId, int side, String value) {
        int margin = (int) DimensionUtil.parse(value, this.context);
        set.setMargin(viewId, side, margin);
        return this;
    }
    
    public ConstraintUpdater setHorizontalBias(int viewId, float bias) {
        set.setHorizontalBias(viewId, bias);
        return this;
    }

    public ConstraintUpdater setVerticalBias(int viewId, float bias) {
        set.setVerticalBias(viewId, bias);
        return this;
    }

    public ConstraintUpdater setWidth(int viewId, int width) {
        set.constrainWidth(viewId, width);
        return this;
    }

    public ConstraintUpdater setHeight(int viewId, int height) {
        set.constrainHeight(viewId, height);
        return this;
    }

    public ConstraintUpdater setDimensionRatio(int viewId, String ratio) {
        set.setDimensionRatio(viewId, ratio);
        return this;
    }

    public ConstraintUpdater setHorizontalChainStyle(int viewId, int chainStyle) {
        set.setHorizontalChainStyle(viewId, chainStyle);
        return this;
    }

    public ConstraintUpdater setVerticalChainStyle(int viewId, int chainStyle) {
        set.setVerticalChainStyle(viewId, chainStyle);
        return this;
    }
    
    public void apply() {
        set.applyTo(layout);
    }
}