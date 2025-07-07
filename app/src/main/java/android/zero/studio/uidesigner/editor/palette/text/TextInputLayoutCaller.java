package android.zero.studio.uidesigner.editor.callers.text;

import android.content.Context;
import android.view.View;
import com.google.android.material.textfield.TextInputLayout;
import android.zero.studio.uidesigner.ProjectFile;
import android.zero.studio.uidesigner.managers.DrawableManager;
import android.zero.studio.uidesigner.managers.ProjectManager;
import android.zero.studio.uidesigner.managers.ValuesManager;
import android.zero.studio.uidesigner.tools.ValuesResourceParser;

public class TextInputLayoutCaller {

    public static void setHint(View target, String value, Context context) {
        if (value.startsWith("@string/")) {
            ProjectFile project = ProjectManager.getInstance().getOpenedProject();
            String resValue = ValuesManager.getValueFromResources(
                ValuesResourceParser.TAG_STRING, value, project.getStringsPath());
            ((TextInputLayout) target).setHint(resValue);
        } else {
            ((TextInputLayout) target).setHint(value);
        }
    }

    public static void setEndIconMode(View target, String value, Context context) {
        int mode = TextInputLayout.END_ICON_NONE;
        switch(value) {
            case "clear_text":
                mode = TextInputLayout.END_ICON_CLEAR_TEXT;
                break;
            case "password_toggle":
                mode = TextInputLayout.END_ICON_PASSWORD_TOGGLE;
                break;
            case "dropdown":
                mode = TextInputLayout.END_ICON_DROPDOWN_MENU;
                break;
            case "custom":
                mode = TextInputLayout.END_ICON_CUSTOM;
                break;
        }
        ((TextInputLayout) target).setEndIconMode(mode);
    }

     public static void setStartIconDrawable(View target, String value, Context context) {
        String name = value.replace("@drawable/", "");
        if (DrawableManager.contains(name)) {
            ((TextInputLayout) target).setStartIconDrawable(DrawableManager.getDrawable(context, name));
        }
    }
}