package com.danikula.videocache;

import android.util.Log;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

public class NullHostNameVerifier implements HostnameVerifier {

    private String TAG = NullHostNameVerifier.class.getSimpleName();

    @Override
    public boolean verify(String hostname, SSLSession session) {
        Log.i(TAG, "hostname::" + hostname);
        return true;
    }
}
