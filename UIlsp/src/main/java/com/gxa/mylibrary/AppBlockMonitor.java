package com.gxa.mylibrary;

import android.os.Looper;
import android.util.Log;

public class AppBlockMonitor {

    private static final String TAG = AppBlockMonitor.class.getSimpleName();
    private static LogMonitor mLogMonitor;
    private static AppBlockMonitor mInstance;

    private AppBlockMonitor(){
        mLogMonitor = new LogMonitor();
    }

    public static AppBlockMonitor getInstance() {
        if (mInstance == null) {
            synchronized (AppBlockMonitor.class) {
                if (mInstance == null) {
                    mInstance = new AppBlockMonitor();
                }
            }
        }
        return mInstance;
    }

    /**
     * 开启监控
     */
    public void install() {
        Looper.getMainLooper().setMessageLogging(mLogMonitor);
    }

    /**
     * 设置监控message处理时间，默认3000ms
     *
     * @param blockThresholdMillis
     */
    public void setBlockThresholdMillis(long blockThresholdMillis) {
        if (blockThresholdMillis > 5000 || blockThresholdMillis < 0) {
            Log.e(TAG, "please set the threshold time between 0 to 5000ms.");
            return;
        }
        mLogMonitor.setBlockThresholdMillis(blockThresholdMillis);
    }
}
