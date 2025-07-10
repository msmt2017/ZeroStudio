package android.zero.studio.uidesigner.editor.callers.parentcallers;

import android.content.Context;
import android.view.View;
import androidx.percentlayout.widget.PercentLayoutHelper;

public class PercentLinearLayoutCaller {

    private static float parsePercent(String percent) {
        if (percent == null || !percent.endsWith("%")) {
            return -1f; // Invalid format
        }
        try {
            return Float.parseFloat(percent.substring(0, percent.length() - 1)) / 100f;
        } catch (NumberFormatException e) {
            return -1f;
        }
    }

    private static void setPercentInfo(View target, String value, Action action) {
        if (!(target.getLayoutParams() instanceof PercentLayoutHelper.PercentLayoutParams)) {
            return;
        }
        PercentLayoutHelper.PercentLayoutParams params = (PercentLayoutHelper.PercentLayoutParams) target.getLayoutParams();
        PercentLayoutHelper.PercentLayoutInfo info = params.getPercentLayoutInfo();
        if (info != null) {
            action.apply(info, parsePercent(value));
            target.requestLayout();
        }
    }

    public static void setLayoutWidthPercent(View target, String value, Context context) {
        setPercentInfo(target, value, (info, percent) -> info.widthPercent = percent);
    }

    public static void setLayoutHeightPercent(View target, String value, Context context) {
        setPercentInfo(target, value, (info, percent) -> info.heightPercent = percent);
    }

    public static void setLayoutMarginPercent(View target, String value, Context context) {
        setPercentInfo(target, value, (info, percent) -> {
            info.leftMarginPercent = percent;
            info.topMarginPercent = percent;
            info.rightMarginPercent = percent;
            info.bottomMarginPercent = percent;
        });
    }

    public static void setLayoutMarginStartPercent(View target, String value, Context context) {
        setPercentInfo(target, value, (info, percent) -> info.startMarginPercent = percent);
    }

    public static void setLayoutMarginEndPercent(View target, String value, Context context) {
        setPercentInfo(target, value, (info, percent) -> info.endMarginPercent = percent);
    }

    public static void setLayoutMarginTopPercent(View target, String value, Context context) {
        setPercentInfo(target, value, (info, percent) -> info.topMarginPercent = percent);
    }

    public static void setLayoutMarginBottomPercent(View target, String value, Context context) {
        setPercentInfo(target, value, (info, percent) -> info.bottomMarginPercent = percent);
    }

    public static void setLayoutAspectRatio(View target, String value, Context context) {
        if (!(target.getLayoutParams() instanceof PercentLayoutHelper.PercentLayoutParams)) {
            return;
        }
        PercentLayoutHelper.PercentLayoutParams params = (PercentLayoutHelper.PercentLayoutParams) target.getLayoutParams();
        PercentLayoutHelper.PercentLayoutInfo info = params.getPercentLayoutInfo();
        if (info != null) {
            try {
                info.aspectRatio = Float.parseFloat(value);
                target.requestLayout();
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private interface Action {
        void apply(PercentLayoutHelper.PercentLayoutInfo info, float percent);
    }
}