package android.zero.studio.uidesigner.editor.callers;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.constraintlayout.widget.ConstraintLayout;

import android.zero.studio.uidesigner.managers.DrawableManager;
import android.zero.studio.uidesigner.managers.IdManager;
import android.zero.studio.uidesigner.utils.ArgumentUtil;
import android.zero.studio.uidesigner.utils.Constants;
import android.zero.studio.uidesigner.utils.DimensionUtil;

public class ViewCaller {
    public static void setId(View target, String value, Context context) {
        IdManager.addNewId(target, value);
    }

    public static void setLayoutWidth(View target, String value, Context context) {
        ViewGroup.LayoutParams params = target.getLayoutParams();
        if (params != null) {
            params.width = (int) DimensionUtil.parse(value, context);
            target.setLayoutParams(params);
        }
    }

    public static void setLayoutHeight(View target, String value, Context context) {
        ViewGroup.LayoutParams params = target.getLayoutParams();
        if (params != null) {
            params.height = (int) DimensionUtil.parse(value, context);
            target.setLayoutParams(params);
        }
    }

    public static void setBackground(View target, String value, Context context) {
        if (value == null) return;
        if (ArgumentUtil.parseType(value, new String[]{"color", "drawable"}).equals(ArgumentUtil.COLOR)) {
            try {
                target.setBackgroundColor(Color.parseColor(value));
            } catch (IllegalArgumentException e) {
                // Invalid color string
            }
        } else {
            String name = value.replace("@drawable/", "");
            target.setBackground(DrawableManager.getDrawable(context, name));
        }
    }

    public static void setForeground(View target, String value, Context context) {
        if (value == null) return;
        if (ArgumentUtil.parseType(value, new String[]{"color", "drawable"}).equals(ArgumentUtil.COLOR)) {
            try {
                target.setForeground(new ColorDrawable(Color.parseColor(value)));
            } catch (IllegalArgumentException e) {
                // Invalid color string
            }
        } else {
            String name = value.replace("@drawable/", "");
            target.setForeground(DrawableManager.getDrawable(context, name));
        }
    }

    public static void setElevation(View target, String value, Context context) {
        target.setElevation(DimensionUtil.parse(value, context));
    }

    public static void setAlpha(View target, String value, Context context) {
        try {
            target.setAlpha(Float.parseFloat(value));
        } catch (NumberFormatException e) {}
    }

    public static void setRotation(View target, String value, Context context) {
        try {
            target.setRotation(Float.parseFloat(value));
        } catch (NumberFormatException e) {}
    }

    public static void setRotationX(View target, String value, Context context) {
        try {
            target.setRotationX(Float.parseFloat(value));
        } catch (NumberFormatException e) {}
    }

    public static void setRotationY(View target, String value, Context context) {
        try {
            target.setRotationY(Float.parseFloat(value));
        } catch (NumberFormatException e) {}
    }

    public static void setTranslationX(View target, String value, Context context) {
        target.setTranslationX(DimensionUtil.parse(value, context));
    }

    public static void setTranslationY(View target, String value, Context context) {
        target.setTranslationY(DimensionUtil.parse(value, context));
    }

    public static void setTranslationZ(View target, String value, Context context) {
        target.setTranslationZ(DimensionUtil.parse(value, context));
    }

    public static void setScaleX(View target, String value, Context context) {
        try {
            target.setScaleX(Float.parseFloat(value));
        } catch (NumberFormatException e) {}
    }

    public static void setScaleY(View target, String value, Context context) {
        try {
            target.setScaleY(Float.parseFloat(value));
        } catch (NumberFormatException e) {}
    }

    public static void setPadding(View target, String value, Context context) {
        int pad = (int) DimensionUtil.parse(value, context);
        target.setPadding(pad, pad, pad, pad);
    }

    public static void setPaddingLeft(View target, String value, Context context) {
        int pad = (int) DimensionUtil.parse(value, context);
        target.setPadding(pad, target.getPaddingTop(), target.getPaddingRight(), target.getPaddingBottom());
    }

    public static void setPaddingRight(View target, String value, Context context) {
        int pad = (int) DimensionUtil.parse(value, context);
        target.setPadding(target.getPaddingLeft(), target.getPaddingTop(), pad, target.getPaddingBottom());
    }

    public static void setPaddingTop(View target, String value, Context context) {
        int pad = (int) DimensionUtil.parse(value, context);
        target.setPadding(target.getPaddingLeft(), pad, target.getPaddingRight(), target.getPaddingBottom());
    }

    public static void setPaddingBottom(View target, String value, Context context) {
        int pad = (int) DimensionUtil.parse(value, context);
        target.setPadding(target.getPaddingLeft(), target.getPaddingTop(), target.getPaddingRight(), pad);
    }

    public static void setEnabled(View target, String value, Context context) {
        target.setEnabled("true".equals(value));
    }

    public static void setVisibility(View target, String value, Context context) {
        if (value != null && Constants.visibilityMap.containsKey(value)) {
            target.setVisibility(Constants.visibilityMap.get(value));
        }
    }

    // New method for Guideline's orientation and LinearLayout's orientation
    public static void setOrientation(View target, String value, Context context) {
        if (target.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) target.getLayoutParams();
            if ("horizontal".equals(value)) {
                params.orientation = ConstraintLayout.LayoutParams.HORIZONTAL;
            } else {
                params.orientation = ConstraintLayout.LayoutParams.VERTICAL;
            }
            target.setLayoutParams(params);
        } else if (target instanceof android.widget.LinearLayout) {
            ((android.widget.LinearLayout) target).setOrientation("horizontal".equals(value) ?
                    android.widget.LinearLayout.HORIZONTAL : android.widget.LinearLayout.VERTICAL);
        }
    }
}