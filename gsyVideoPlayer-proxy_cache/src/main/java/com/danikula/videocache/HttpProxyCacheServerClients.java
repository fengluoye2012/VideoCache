package com.danikula.videocache;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.danikula.videocache.file.FileCache;
import com.danikula.videocache.utils.LogUtil;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static com.danikula.videocache.Preconditions.checkNotNull;

/**
 * Client for {@link HttpProxyCacheServer}
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
public final class HttpProxyCacheServerClients {
    private String TAG = HttpProxyCacheServerClients.class.getSimpleName();
    private final AtomicInteger clientsCount = new AtomicInteger(0);
    private final String url;
    private volatile HttpProxyCache proxyCache;
    private final List<CacheListener> listeners = new CopyOnWriteArrayList<>();
    private final CacheListener uiCacheListener;
    private final Config config;

    /**
     * 缓存异常的监听
     */
    private OnVideoCacheErrorListener cacheErrorListener;

    public HttpProxyCacheServerClients(String url, Config config) {
        this.url = checkNotNull(url);
        this.config = checkNotNull(config);
        this.cacheErrorListener = config.cacheErrorListener;
        this.uiCacheListener = new UiListenerHandler(url, listeners);
    }

    public void processRequest(GetRequest request, Socket socket) throws ProxyCacheException, IOException {
        LogUtil.i(TAG, "startProcessRequest");
        startProcessRequest();
        try {
            clientsCount.incrementAndGet();
            proxyCache.processRequest(request, socket);
        } catch (SocketException e) {
            LogUtil.i(TAG, "SocketException");
            // socket exception基本是视频播放器和代理直接连接中断的异常，属于正常现象不做处理
        } catch (Exception e) {
            LogUtil.i(TAG, Log.getStackTraceString(e));
            // 在这里将一路传出来的异常回调出去
            if (cacheErrorListener != null) {
                // 获取到当前url对应的本地文件信息一并传回
                StringBuilder sb = new StringBuilder();
                File file = proxyCache.cache.getFile();
                if (file != null && file.exists()) {
                    sb.append("local file size :" + file.length() + " -- file is complete : " + proxyCache.cache.isCompleted());
                } else {
                    sb.append("local file not exists");
                }
                cacheErrorListener.onError(e, sb.toString());
            }
        } finally {
            LogUtil.i(TAG, "finishProcessRequest");
            finishProcessRequest();
        }
    }

    private synchronized void startProcessRequest() throws ProxyCacheException {
        proxyCache = proxyCache == null ? newHttpProxyCache() : proxyCache;
    }

    private synchronized void finishProcessRequest() {
        if (clientsCount.decrementAndGet() <= 0) {
            proxyCache.shutdown();
            proxyCache = null;
        }
    }

    public void registerCacheListener(CacheListener cacheListener) {
        listeners.add(cacheListener);
    }

    public void unregisterCacheListener(CacheListener cacheListener) {
        listeners.remove(cacheListener);
    }

    public void shutdown() {
        listeners.clear();
        if (proxyCache != null) {
            proxyCache.registerCacheListener(null);
            proxyCache.shutdown();
            proxyCache = null;
        }
        clientsCount.set(0);
    }

    public int getClientsCount() {
        return clientsCount.get();
    }

    private HttpProxyCache newHttpProxyCache() throws ProxyCacheException {
        HttpUrlSource source = new HttpUrlSource(url, config.sourceInfoStorage, config.headerInjector);
        FileCache cache = new FileCache(config.generateCacheFile(url), config.diskUsage);
        HttpProxyCache httpProxyCache = new HttpProxyCache(source, cache);
        httpProxyCache.registerCacheListener(uiCacheListener);
        return httpProxyCache;
    }

    private static final class UiListenerHandler extends Handler implements CacheListener {

        private final String url;
        private final List<CacheListener> listeners;

        public UiListenerHandler(String url, List<CacheListener> listeners) {
            super(Looper.getMainLooper());
            this.url = url;
            this.listeners = listeners;
        }

        @Override
        public void onCacheAvailable(File file, String url, int percentsAvailable) {
            Message message = obtainMessage();
            message.arg1 = percentsAvailable;
            message.obj = file;
            sendMessage(message);
        }

        @Override
        public void handleMessage(Message msg) {
            for (CacheListener cacheListener : listeners) {
                cacheListener.onCacheAvailable((File) msg.obj, url, msg.arg1);
            }
        }
    }

    public void setPause(boolean pause){
        if(proxyCache != null){
            proxyCache.setPause(pause);
        }
    }

    /**
     * 预缓存预定数据后shutdown
     * @param shutdownAfterPrecache
     */
    public void setShutdownAfterPrecache(boolean shutdownAfterPrecache){
        if(proxyCache != null){
            proxyCache.setShutdownAfterPrecache(shutdownAfterPrecache);
        }
    }

    /**
     * 设置错误异常监听
     * @param cacheErrorListener
     */
    public void setCacheErrorListener(OnVideoCacheErrorListener cacheErrorListener) {
        this.cacheErrorListener = cacheErrorListener;
    }

    /**
     * 停止预缓存
     * @param shutdownPreCache
     */
    public void setShutdownCache(boolean shutdownPreCache) {
        if (proxyCache != null) {
            proxyCache.setShutdownCache(shutdownPreCache);
        }
    }
}
