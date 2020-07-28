package com.danikula.videocache;

import com.alibaba.sdk.android.httpdns.HttpDnsService;
import com.danikula.videocache.utils.LogUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import okhttp3.Dns;

public class OkHttpDns implements Dns {

    private static OkHttpDns instance = null;
    private final HttpDnsService httpDns;
    private String TAG = OkHttpDns.class.getSimpleName();

    private OkHttpDns() {
        httpDns = HttpDnsUtil.getInstance().getHttpDns();
    }

    public static OkHttpDns getInstance() {
        if (instance == null) {
            synchronized (OkHttpDns.class) {
                if (instance == null) {
                    instance = new OkHttpDns();
                }
            }
        }
        return instance;
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        LogUtil.i(TAG,"hostname="+hostname);
        //如果dns为空或者originUrl是代理服务url,则不获取ip
        if (httpDns != null && !HttpProxyCacheServer.PROXY_HOST.equals(hostname)) {
            //通过异步解析接口获取ip
            String ip = httpDns.getIpByHostAsync(hostname);
            if (ip != null) {
                //如果ip不为null，直接使用该ip进行网络请求
                List<InetAddress> inetAddresses = Arrays.asList(InetAddress.getAllByName(ip));
                LogUtil.i("OkHttpDns", "inetAddresses:" + inetAddresses);
                return inetAddresses;
            }
        }
        //如果返回null，走系统DNS服务解析域名
        return Dns.SYSTEM.lookup(hostname);
    }
}
