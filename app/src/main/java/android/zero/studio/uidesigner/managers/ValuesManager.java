package android.zero.studio.uidesigner.managers;

import android.zero.studio.uidesigner.adapters.models.ValuesItem;
import android.zero.studio.uidesigner.tools.ValuesResourceParser;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class ValuesManager {

    public static String getValueFromResources(String tag, String value, String path) {
        String resValueName = value.substring(value.indexOf("/") + 1);
        String result = null;
        try {
            ValuesResourceParser parser = new ValuesResourceParser(new FileInputStream(path), tag);

            for (ValuesItem item : parser.getValuesList()) {
                if (item.name.equals(resValueName)) {
                    result = item.value;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return result;
    }
}
