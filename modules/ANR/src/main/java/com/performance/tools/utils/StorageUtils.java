package com.performance.tools.utils;

import android.os.Environment;


public class StorageUtils {

    private StorageUtils() {

    }

    /**
     * SD卡是否挂载
     *
     * @return
     */
    public static boolean isMounted() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

}
