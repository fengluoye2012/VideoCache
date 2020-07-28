package com.danikula.videocache.utils;

import com.danikula.videocache.OkHttpDns;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class OkHttpUtil {
    private static OkHttpUtil instance;
    private OkHttpClient okHttpClient;

    private OkHttpUtil() {
        OkHttpsUtils.SSLParams sslParams = OkHttpsUtils.getSslSocketFactory(null, null, null);

        okHttpClient = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(false)//禁制OkHttp的重定向操作，我们自己处理重定向
                .dns(OkHttpDns.getInstance())
                .sslSocketFactory(sslParams.sSLSocketFactory , sslParams.trustManager)
                .build();
    }

    public static OkHttpUtil getInstance() {
        if (instance == null) {
            synchronized (OkHttpUtil.class) {
                if (instance == null) {
                    instance = new OkHttpUtil();
                }
            }
        }
        return instance;
    }

    public OkHttpClient getClient() {
        return okHttpClient;
    }
}
