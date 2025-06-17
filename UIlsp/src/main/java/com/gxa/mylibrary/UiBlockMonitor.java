package com.gxa.mylibrary;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.Choreographer;

public class UiBlockMonitor {

    private static String TAG = UiBlockMonitor.class.getSimpleName();
    private long mLastFrameTimeNanos = 0;
    private static UiBlockMonitor mInstance;
    private Choreographer.FrameCallback mFrameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            //上次回调时间
            if (mLastFrameTimeNanos == 0) {
                mLastFrameTimeNanos = frameTimeNanos;
                Choreographer.getInstance().postFrameCallback(this);
                return;
            }
            long diff = (frameTimeNanos - mLastFrameTimeNanos) / 1_000_000;
            if (diff > 16.6f) {
                //掉帧数
                int droppedCount = (int) (diff / 16.6);
                Log.e(TAG, "it has drop " + droppedCount + " frames.");
            }
            mLastFrameTimeNanos = frameTimeNanos;
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private UiBlockMonitor() {
    }

    public static UiBlockMonitor getInstance() {
        if (mInstance == null) {
            synchronized (UiBlockMonitor.class) {
                if (mInstance == null) {
                    mInstance = new UiBlockMonitor();
                }
            }
        }
        return mInstance;
    }


    /**
     * 开启监控丢帧
     */
    public void start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            Choreographer.getInstance().postFrameCallback(mFrameCallback);
        }
    }

    /**
     * 取消监控丢帧
     */
    public void stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            Choreographer.getInstance().removeFrameCallback(mFrameCallback);
            mFrameCallback = null;
        }
    }
}
