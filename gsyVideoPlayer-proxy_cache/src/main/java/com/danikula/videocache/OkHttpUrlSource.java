package com.danikula.videocache;

import android.text.TextUtils;
import android.util.Log;

import com.danikula.videocache.headers.EmptyHeadersInjector;
import com.danikula.videocache.headers.HeaderInjector;
import com.danikula.videocache.sourcestorage.SourceInfoStorage;
import com.danikula.videocache.sourcestorage.SourceInfoStorageFactory;
import com.danikula.videocache.utils.ConstantsUtil;
import com.danikula.videocache.utils.LogUtil;
import com.danikula.videocache.utils.OkHttpUtil;
import com.danikula.videocache.utils.RangeUtil;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.danikula.videocache.Preconditions.checkNotNull;
import static com.danikula.videocache.ProxyCacheUtils.DEFAULT_BUFFER_SIZE;

//暂时不使用OkHttp 作为网络请求库，等待完善
public class OkHttpUrlSource implements Source {

    private final String TAG = OkHttpUrlSource.class.getSimpleName();
    private static final int MAX_REDIRECTS = 5;
    private final SourceInfoStorage sourceInfoStorage;
    private final HeaderInjector headerInjector;
    private SourceInfo sourceInfo;

    private Response response;
    private BufferedInputStream inputStream;

    public OkHttpUrlSource(String url) {
        this(url, SourceInfoStorageFactory.newEmptySourceInfoStorage());
    }

    public OkHttpUrlSource(String url, SourceInfoStorage sourceInfoStorage) {
        this(url, sourceInfoStorage, new EmptyHeadersInjector());
    }

    public OkHttpUrlSource(String url, SourceInfoStorage sourceInfoStorage, HeaderInjector headerInjector) {
        this.sourceInfoStorage = checkNotNull(sourceInfoStorage);
        this.headerInjector = checkNotNull(headerInjector);
        SourceInfo sourceInfo = sourceInfoStorage.get(url);
        this.sourceInfo = sourceInfo != null ? sourceInfo :
                new SourceInfo(url, Integer.MIN_VALUE, ProxyCacheUtils.getSupposablyMime(url));
    }

    public OkHttpUrlSource(OkHttpUrlSource source) {
        this.sourceInfo = source.sourceInfo;
        this.sourceInfoStorage = source.sourceInfoStorage;
        this.headerInjector = source.headerInjector;
    }


    @Override
    public void open(long offset) throws ProxyCacheException {
        int timeout = offset == ConstantsUtil.PING_SERVER_OFFSET ? ConstantsUtil.SYSTEM_OUT_TIME : ConstantsUtil.CUS_OUT_TIME;
        try {
            response = openConnection(offset, timeout);
            if (!response.isSuccessful()) {
                throw new ProxyCacheException("response + code=" + response.code()+" for " + sourceInfo.url);
            }

            ResponseBody responseBody = response.body();
            if (responseBody != null && response.isSuccessful()) {
                inputStream = new BufferedInputStream(responseBody.byteStream(), DEFAULT_BUFFER_SIZE);
                String mine = response.header("content-type");
                LogUtil.i(TAG, "url:" + sourceInfo.url + "content-type=" + mine);
                this.sourceInfo = new SourceInfo(sourceInfo.url, sourceInfo.length, mine);
                this.sourceInfoStorage.put(sourceInfo.url, sourceInfo);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new ProxyCacheException("Error opening connection for " + sourceInfo.url + " with offset " + offset + "\n raw reason:" + e.toString(), e);
        }
    }

    @Override
    public long length() throws ProxyCacheException {
        if (sourceInfo.length == Integer.MIN_VALUE) {
            newFetchContentInfo();
        }
        return sourceInfo.length;
    }

    /**
     * 通过Head 请求获取ContentLength
     *
     * @return
     */
    public void newFetchContentInfo() throws ProxyCacheException {
        Response response = null;
        try {
            response = openConnection(ConstantsUtil.HEAD_OFFSET, ConstantsUtil.SYSTEM_OUT_TIME);

            if (!response.isSuccessful()) {
                throw new ProxyCacheException("response + code=" + response.code()+" for " + sourceInfo.url);
            }

            if (response != null && response.isSuccessful()) {
                int contentLength = Integer.MIN_VALUE;
                String contentLengthStr = response.header("content-length");
                if (contentLengthStr != null && !contentLengthStr.equals("")) {
                    contentLength = Integer.parseInt(contentLengthStr);
                }

                String mime = response.header("content-type");
                this.sourceInfo = new SourceInfo(sourceInfo.url, contentLength, mime);
                this.sourceInfoStorage.put(sourceInfo.url, sourceInfo);
                LogUtil.i(TAG, "contentLength::" + contentLength + ",,mime=" + mime);
            }
        } catch (IOException e) {
            LogUtil.e(TAG, "Error fetching info from " + sourceInfo.url + Log.getStackTraceString(e));
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public int read(byte[] buffer) throws ProxyCacheException {
        if (inputStream == null) {
            throw new ProxyCacheException("Error reading data from " + sourceInfo.url + ": connection is absent!");
        }
        try {
            return inputStream.read(buffer, 0, buffer.length);
        } catch (InterruptedIOException e) {
            throw new InterruptedProxyCacheException("Reading source " + sourceInfo.url + " is interrupted" + "--rawReason : " + e.toString(), e);
        } catch (IOException e) {
            throw new ProxyCacheException("Error reading data from " + sourceInfo.url + "--rawReason : " + e.toString(), e);
        }
    }

    @Override
    public void close() throws ProxyCacheException {
        if (response != null) {
            try {
                response.close();
            } catch (NullPointerException | IllegalArgumentException e) {
                String message = "Wait... but why? WTF!? " +
                        "Really shouldn't happen any more after fixing https://github.com/danikula/AndroidVideoCache/issues/43. " +
                        "If you read it on your device log, please, notify me danikula@gmail.com or create issue here " +
                        "https://github.com/danikula/AndroidVideoCache/issues.";
                throw new RuntimeException(message, e);
            } catch (ArrayIndexOutOfBoundsException e) {
                HttpProxyCacheDebuger.printfError("Error closing connection correctly. Should happen only on Android L. " +
                        "If anybody know how to fix it, please visit https://github.com/danikula/AndroidVideoCache/issues/88. " +
                        "Until good solution is not know, just ignore this issue :(", e);
            }
        }
    }

    private Response openConnection(long offset, int timeout) throws IOException, ProxyCacheException {
        Response response;
        boolean redirected;
        int redirectCount = 0;
        String originUrl = this.sourceInfo.url;
        LogUtil.i(TAG, "originUrl::" + originUrl);
        do {
            Request.Builder builder = new Request.Builder();
            builder.url(originUrl);
            builder.headers(injectCustomHeaders(offset, originUrl));
            if (offset == ConstantsUtil.HEAD_OFFSET) {
                builder.head();
            } else {
                builder.get();
            }
            Request request = builder.build();

            Call call = OkHttpUtil.getInstance().getClient().newCall(request);
            response = call.execute();

            redirected = response.isRedirect();
            if (redirected) {
                originUrl = response.headers().get("Location");
                redirectCount++;
            }
            if (redirectCount > MAX_REDIRECTS) {
                throw new ProxyCacheException("Too many redirects: " + redirectCount);
            }
        } while (redirected);
        return response;
    }

    private Headers injectCustomHeaders(long offset, String url) {
        Headers.Builder builder = new Headers.Builder();

        if (offset != ConstantsUtil.PING_SERVER_OFFSET && offset != ConstantsUtil.HEAD_OFFSET) {
            builder.set("Range", "bytes=" + offset + "-" + RangeUtil.getRangeEnd(offset, sourceInfo.length));
        }

        Map<String, String> extraHeaders = headerInjector.addHeaders(url);
        if (extraHeaders == null) {
            return builder.build();
        }

        HttpProxyCacheDebuger.printfError("****** injectCustomHeaders ****** :" + extraHeaders.size());
        for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
            builder.set(header.getKey(), header.getValue());
        }
        return builder.build();
    }

    public synchronized String getMime() throws ProxyCacheException {
        if (TextUtils.isEmpty(sourceInfo.mime)) {
            newFetchContentInfo();
        }
        return sourceInfo.mime;
    }

    public String getUrl() {
        return sourceInfo.url;
    }

    @Override
    public String toString() {
        return "OkHttpUrlSource{sourceInfo='" + sourceInfo + "}";
    }
}
