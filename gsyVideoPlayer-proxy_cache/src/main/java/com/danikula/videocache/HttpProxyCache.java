package com.danikula.videocache;

import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;

import com.danikula.videocache.file.FileCache;
import com.danikula.videocache.utils.ConstantsUtil;
import com.danikula.videocache.utils.LogUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Locale;

import static com.danikula.videocache.ProxyCacheUtils.DEFAULT_BUFFER_SIZE;

/**
 * {@link ProxyCache} that read http url and writes data to {@link Socket}
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
class HttpProxyCache extends ProxyCache {
    private String TAG = HttpProxyCache.class.getSimpleName();

    private final HttpUrlSource source;
    public final FileCache cache;
    private CacheListener listener;

    public HttpProxyCache(HttpUrlSource source, FileCache cache) {
        super(source, cache);
        this.cache = cache;
        this.source = source;
    }

    public void registerCacheListener(CacheListener cacheListener) {
        this.listener = cacheListener;
    }

    public void processRequest(GetRequest request, Socket socket) throws IOException, ProxyCacheException {
        OutputStream out = new BufferedOutputStream(socket.getOutputStream());
        String responseHeaders = newResponseHeaders(request);
        out.write(responseHeaders.getBytes("UTF-8"));
        LogUtil.i(TAG, "processRequest responseHeaders:" + responseHeaders);
        long offset = request.rangeOffset;

        //head 请求获取，获取文件大小 ContentLength
        if (source.length() <= 0) {
            source.newFetchContentInfo();
        }
        long length = source.length();
        LogUtil.i(TAG, "length::" + length / 1024 + "KB");

        //当获取文件的起始位置超过文件的长度，则return;
        if (offset >= length) {
            if (isUseCache(request)) {
                tryComplete();
            }
            return;
        }

        LogUtil.i(TAG, "异步下载文件 offset::" + offset);
        if (isUseCache(request)) {
            responseWithCache(out, offset);
        } else {
            responseWithoutCache(out, offset);
        }
    }

    private boolean isUseCache(GetRequest request) throws ProxyCacheException {
        long sourceLength = source.length();
        boolean sourceLengthKnown = sourceLength > 0;
        long cacheAvailable = cache.available();
        // do not use cache for partial requests which too far from available cache. It seems user seek video.

        //避免seek时转圈太多
        float noCacheBarrier;
        if (sourceLength < ConstantsUtil.NO_CACHE_LENGTH_BARRIER) {
            noCacheBarrier = ConstantsUtil.NO_CACHE_BARRIER_FIRST;
        } else if (sourceLength < ConstantsUtil.NO_CACHE_LENGTH_BARRIER_SECOND) {
            noCacheBarrier = ConstantsUtil.NO_CACHE_BARRIER_SECOND;
        } else {
            noCacheBarrier = ConstantsUtil.NO_CACHE_BARRIER_THIRD;
        }

        // 在原来的基础上增加一个限制，手机可用空间必须大于350M，否则不会使用缓存
        LogUtil.i("AvailableInternal", "userable : " + getAvailableInternalMemorySize());
        return getAvailableInternalMemorySize() > 350 * 1024 * 1024 && (!sourceLengthKnown || !request.partial || request.rangeOffset <= cacheAvailable + sourceLength * noCacheBarrier);
    }

    public static long getAvailableInternalMemorySize() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        long availableBytes = stat.getAvailableBytes();
        return availableBytes;
    }

    private String newResponseHeaders(GetRequest request) throws IOException, ProxyCacheException {
        String mime = source.getMime();
        boolean mimeKnown = !TextUtils.isEmpty(mime);
        long length = cache.isCompleted() ? cache.available() : source.length();
        boolean lengthKnown = length >= 0;
        long contentLength = request.partial ? length - request.rangeOffset : length;
        boolean addRange = lengthKnown && request.partial;

        return new StringBuilder()
                .append(request.partial ? "HTTP/1.1 206 PARTIAL CONTENT\n" : "HTTP/1.1 200 OK\n")
                .append("Accept-Ranges: bytes\n")
                .append(lengthKnown ? format("Content-Length: %d\n", contentLength) : "")
                .append(addRange ? format("Content-Range: bytes %d-%d/%d\n", request.rangeOffset, length - 1, length) : "")
                .append(mimeKnown ? format("Content-Type: %s\n", mime) : "")
                .append("\n") // headers end
                .toString();
    }


    //如何判断播放器从socket中读取了
    private void responseWithCache(OutputStream out, long offset) throws ProxyCacheException, IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int readBytes;
        //使用while会将数据全部读完；将本地的文件全部写入out中，当本地无法满足的时候异步请求
        while ((readBytes = read(buffer, offset, buffer.length)) != -1) {
            out.write(buffer, 0, readBytes);
            offset += readBytes;

            if (offset >= ConstantsUtil.getInstance().getPreCacheLength() && shutdownAfterPrecache) {
                shutdownAfterPrecache = false;
                onCachePercentsAvailableChanged(percentsAvailable);
                break;
            }

            if (shutdownPreCache) {
                shutdownPreCache=false;
                LogUtil.i("调用顺序","提前停止预缓存 url:"+source.getUrl());
                onCachePercentsAvailableChanged(percentsAvailable);
                break;
            }
        }
        out.flush();
    }


    /**
     * 响应，不缓存在本地，只获取500K的数据完成以后，就finishProcessRequest();如何不让他finish
     */
    private void responseWithoutCache(OutputStream out, long offset) throws ProxyCacheException, IOException {

        HttpUrlSource newSourceNoCache = new HttpUrlSource(this.source);
        try {
            while (offset < source.length()) {
                LogUtil.i(TAG,"offset：："+offset/1024+",,length：："+source.length());
                newSourceNoCache.open((int) offset);
                byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                int readBytes;
                //将请求回来的数据全部都写入out中
                while ((readBytes = newSourceNoCache.read(buffer)) != -1) {
                    out.write(buffer, 0, readBytes);
                    offset += readBytes;
                }
            }
            out.flush();
        } finally {
            newSourceNoCache.close();
        }
    }

    private String format(String pattern, Object... args) {
        return String.format(Locale.US, pattern, args);
    }

    @Override
    protected void onCachePercentsAvailableChanged(int percents) {
        if (listener != null) {
            listener.onCacheAvailable(cache.file, source.getUrl(), percents);
        }
    }
}
