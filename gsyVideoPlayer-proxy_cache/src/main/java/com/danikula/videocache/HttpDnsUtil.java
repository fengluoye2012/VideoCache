package com.danikula.videocache;

import android.util.Log;

import com.alibaba.sdk.android.httpdns.HttpDnsService;

public class HttpDnsUtil {

    public static HttpDnsUtil instance;

    private HttpDnsService httpDns;

    private String TAG = HttpDnsUtil.class.getSimpleName();

    private HttpDnsUtil() {
    }

    public static HttpDnsUtil getInstance() {
        if (instance == null) {
            synchronized (HttpDnsUtil.class) {
                if (instance == null) {
                    instance = new HttpDnsUtil();
                }
            }
        }
        return instance;
    }

    public void setHttpDns(HttpDnsService httpDns) {
        Log.i(TAG,"setHttpDns");
        if (httpDns == null){
            Log.i(TAG,"setHttpDns 时 httpDns 为null");
        }
        this.httpDns = httpDns;
    }

    public HttpDnsService getHttpDns() {
        return httpDns;
    }
}
