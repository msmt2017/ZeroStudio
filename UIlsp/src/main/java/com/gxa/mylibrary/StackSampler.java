package com.gxa.mylibrary;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class StackSampler {
    public static final String SEPARATOR = "\r\n";
    public static final SimpleDateFormat TIME_FORMATTER =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS");


    private Handler mHandler;
    private Map<Long, String> mStackMap = new LinkedHashMap<>();
    private int mMaxCount = 100;
    private long mSampleInterval;
    //是否需要采样
    protected AtomicBoolean mShouldSample = new AtomicBoolean(false);

    public StackSampler(long sampleInterval) {
        mSampleInterval = sampleInterval;
        HandlerThread handlerThread = new HandlerThread("block-canary-sampler");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());
    }

    /**
     * 开始采样 执行堆栈
     */
    public void startDump() {
        //避免重复开始
        if (mShouldSample.get()) {
            return;
        }
        mShouldSample.set(true);

        mHandler.removeCallbacks(mRunnable);
        mHandler.postDelayed(mRunnable, mSampleInterval);
    }

    public void stopDump() {
        if (!mShouldSample.get()) {
            return;
        }
        mShouldSample.set(false);

        mHandler.removeCallbacks(mRunnable);
    }


    public List<String> getStacks(long startTime, long endTime) {
        ArrayList<String> result = new ArrayList<>();
        synchronized (mStackMap) {
            for (Long entryTime : mStackMap.keySet()) {
                if (startTime < entryTime && entryTime < endTime) {
                    result.add(TIME_FORMATTER.format(entryTime)
                            + SEPARATOR
                            + SEPARATOR
                            + mStackMap.get(entryTime));
                }
            }
        }
        return result;
    }

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            StringBuilder sb = new StringBuilder();
            StackTraceElement[] stackTrace = Looper.getMainLooper().getThread().getStackTrace();
            for (StackTraceElement s : stackTrace) {
                sb.append(s.toString()).append("\n");
            }
            synchronized (mStackMap) {
                //最多保存100条堆栈信息
                if (mStackMap.size() == mMaxCount) {
                    mStackMap.remove(mStackMap.keySet().iterator().next());
                }
                mStackMap.put(System.currentTimeMillis(), sb.toString());
            }

            if (mShouldSample.get()) {
                mHandler.postDelayed(mRunnable, mSampleInterval);
            }
        }
    };

}
