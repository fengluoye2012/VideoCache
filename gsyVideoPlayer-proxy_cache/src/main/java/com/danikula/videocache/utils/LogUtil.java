package com.danikula.videocache.utils;

import android.util.Log;

public class LogUtil {

    private static boolean debug = true;

    public static void setDebug(boolean isDebug) {
        debug = isDebug;
    }

    public static void i(String TAG, String msg) {
        if (!debug) {
            return;
        }
        Log.i(TAG, msg);
    }

    public static void e(String TAG, String msg) {
        if (!debug) {
            return;
        }
        Log.e(TAG, msg);
    }

}
