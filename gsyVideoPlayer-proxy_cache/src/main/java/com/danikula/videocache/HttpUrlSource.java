package com.danikula.videocache;

import android.text.TextUtils;
import android.util.Log;

import com.danikula.videocache.headers.EmptyHeadersInjector;
import com.danikula.videocache.headers.HeaderInjector;
import com.danikula.videocache.sourcestorage.SourceInfoStorage;
import com.danikula.videocache.sourcestorage.SourceInfoStorageFactory;
import com.danikula.videocache.utils.ConstantsUtil;
import com.danikula.videocache.utils.HttpsUtils;
import com.danikula.videocache.utils.LogUtil;
import com.danikula.videocache.utils.RangeUtil;


import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import static com.danikula.videocache.Preconditions.checkNotNull;
import static com.danikula.videocache.ProxyCacheUtils.DEFAULT_BUFFER_SIZE;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PARTIAL;
import static java.net.HttpURLConnection.HTTP_SEE_OTHER;

/**
 * {@link Source} that uses http resource as source for {@link ProxyCache}.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
public class HttpUrlSource implements Source {

    private static final int MAX_REDIRECTS = 5;
    private final SourceInfoStorage sourceInfoStorage;
    private final HeaderInjector headerInjector;
    private SourceInfo sourceInfo;
    private HttpURLConnection connection;
    private InputStream inputStream;

    private String TAG= HttpUrlSource.class.getSimpleName();

    public HttpUrlSource(String url) {
        this(url, SourceInfoStorageFactory.newEmptySourceInfoStorage());
    }

    public HttpUrlSource(String url, SourceInfoStorage sourceInfoStorage) {
        this(url, sourceInfoStorage, new EmptyHeadersInjector());
    }

    public HttpUrlSource(String url, SourceInfoStorage sourceInfoStorage, HeaderInjector headerInjector) {
        this.sourceInfoStorage = checkNotNull(sourceInfoStorage);
        this.headerInjector = checkNotNull(headerInjector);
        SourceInfo sourceInfo = sourceInfoStorage.get(url);
        this.sourceInfo = sourceInfo != null ? sourceInfo :
                new SourceInfo(url, Integer.MIN_VALUE, ProxyCacheUtils.getSupposablyMime(url));
    }

    public HttpUrlSource(HttpUrlSource source) {
        this.sourceInfo = source.sourceInfo;
        this.sourceInfoStorage = source.sourceInfoStorage;
        this.headerInjector = source.headerInjector;
    }

    @Override
    public synchronized long length() throws ProxyCacheException {
        if (sourceInfo.length == Integer.MIN_VALUE) {
            //fetchContentInfo();
            newFetchContentInfo();
        }
        return sourceInfo.length;
    }

    @Override
    public void open(long offset) throws ProxyCacheException {
        try {
            int timeout = offset == ConstantsUtil.PING_SERVER_OFFSET ? ConstantsUtil.SYSTEM_OUT_TIME : ConstantsUtil.CUS_OUT_TIME;
            connection = openConnection(offset, timeout);
            String mime = connection.getContentType();
            inputStream = new BufferedInputStream(connection.getInputStream(), DEFAULT_BUFFER_SIZE);
            //long length = readSourceAvailableBytes(connection, offset, connection.getResponseCode());
            this.sourceInfo = new SourceInfo(sourceInfo.url, sourceInfo.length, mime);
            this.sourceInfoStorage.put(sourceInfo.url, sourceInfo);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ProxyCacheException("Error opening connection for " + sourceInfo.url + " with offset " + offset + "\n raw reason:" + e.toString(), e);
        }
    }

    private long readSourceAvailableBytes(HttpURLConnection connection, long offset, int responseCode) throws IOException {
        long contentLength = getContentInfo(connection);
        return responseCode == HTTP_OK ? contentLength
                : responseCode == HTTP_PARTIAL ? contentLength + offset : sourceInfo.length;
    }

    private long getContentInfo(HttpURLConnection connection) {
        String contentLengthValue = connection.getHeaderField("Content-Length");
        return contentLengthValue == null ? -1 : Long.parseLong(contentLengthValue);
    }

    @Override
    public void close() throws ProxyCacheException {
        if (connection != null) {
            try {
                connection.disconnect();
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
            throw new ProxyCacheException("Error reading data from " + sourceInfo.url  + "--rawReason : " + e.toString(), e);
        }
    }

    //只是用来获取getContentType 和 getContentType 没有必要使用get 请求
//    private void fetchContentInfo() throws ProxyCacheException {
//        HttpURLConnection urlConnection = null;
//        InputStream inputStream = null;
//        try {
//            urlConnection = openConnection(0, ConstantsUtil.CUS_OUT_TIME);
//            long length = getContentInfo(urlConnection);
//            String mime = urlConnection.getContentType();
//            inputStream = urlConnection.getInputStream();
//            this.sourceInfo = new SourceInfo(sourceInfo.url, length, mime);
//            this.sourceInfoStorage.put(sourceInfo.url, sourceInfo);
//        } catch (IOException e) {
//            HttpProxyCacheDebuger.printfError("Error fetching info from " + sourceInfo.url, e);
//        } finally {
//            ProxyCacheUtils.close(inputStream);
//            if (urlConnection != null) {
//                urlConnection.disconnect();
//            }
//        }
//    }

    /**
     * 通过Head 请求获取ContentLength
     *
     * @return
     */
    public void newFetchContentInfo() throws ProxyCacheException {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(ConstantsUtil.HEAD_OFFSET, ConstantsUtil.SYSTEM_OUT_TIME);
            int contentLength = connection.getContentLength();
            String mime = connection.getContentType();
            this.sourceInfo = new SourceInfo(sourceInfo.url, contentLength, mime);
            this.sourceInfoStorage.put(sourceInfo.url, sourceInfo);

            LogUtil.i(TAG, "contentLength::" + contentLength);

        } catch (IOException e) {
            HttpProxyCacheDebuger.printfError("Error fetching info from " + sourceInfo.url, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection openConnection(long offset, int timeout) throws IOException, ProxyCacheException {
        HttpURLConnection connection;
        boolean redirected;
        int redirectCount = 0;
        String originUrl = this.sourceInfo.url;
        LogUtil.i(TAG, "originUrl::" + originUrl);
        do {
            URL url = new URL(originUrl);
            //HttpDns
            if (HttpDnsUtil.getInstance().getHttpDns() == null) {
                LogUtil.i(TAG, "HttpDns 为null");
            }

            //如果dns为空或者originUrl是代理服务url,则不获取ip
            if (HttpDnsUtil.getInstance().getHttpDns() != null && !HttpProxyCacheServer.PROXY_HOST.equals(url.getHost())) {
                LogUtil.i(TAG, "异步接口获取IP");
                // 异步接口获取IP
                String ip = HttpDnsUtil.getInstance().getHttpDns().getIpByHostAsync(url.getHost());

                if (ip != null) {
                    // 通过HTTPDNS获取IP成功，进行URL替换和HOST头设置
                    LogUtil.i(TAG, "Get IP: " + ip + " for host: " + url.getHost() + " from HTTPDNS successfully!");
                    String newUrl = originUrl.replaceFirst(url.getHost(), ip);
                    LogUtil.i(TAG, "newUrl::" + newUrl);
                    connection = (HttpURLConnection) new URL(newUrl).openConnection();
                    //设置HTTP请求头Host域
                    connection.setRequestProperty("Host", url.getHost());

                    hostnameVerifier(connection);
                } else {
                    LogUtil.i(TAG, "无法获取IP");
                    connection = (HttpURLConnection) url.openConnection();
                    cusHostNameVerifier(connection);
                }
            } else {
                connection = (HttpURLConnection) url.openConnection();
                cusHostNameVerifier(connection);
            }

            injectCustomHeaders(connection, originUrl);

            //关于Range
            //不指定Range，code为200，不是206，起码一次性会返回20M的内容
            //只指定start,不指定end,则一次性下载完所有的数据；
            //connection.setRequestProperty("Range", "bytes=" + start + "-");
            //指定start,end 一次性只下载（end-start）的数据量
            //connection.setRequestProperty("Range", "bytes=" + start + "-"+end);

            if (offset != ConstantsUtil.PING_SERVER_OFFSET && offset != ConstantsUtil.HEAD_OFFSET) {
                connection.setRequestProperty("Range", "bytes=" + offset + "-" + RangeUtil.getRangeEnd(offset,sourceInfo.length));
            }

            if (offset == ConstantsUtil.HEAD_OFFSET) {
                connection.setRequestMethod("HEAD");
            } else {
                connection.setRequestMethod("GET");
            }

            //链接超时时间，系统默认的超时时间为8s;
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);

            int code = connection.getResponseCode();
            redirected = code == HTTP_MOVED_PERM || code == HTTP_MOVED_TEMP || code == HTTP_SEE_OTHER;
            if (redirected) {
                originUrl = connection.getHeaderField("Location");
                redirectCount++;
                connection.disconnect();
            }
            if (redirectCount > MAX_REDIRECTS) {
                throw new ProxyCacheException("Too many redirects: " + redirectCount);
            }
        } while (redirected);
        return connection;
    }

    /**
     * 处理证书不匹配问题，同时处理 HTTPS IP直连（SNI）,定制SSLSocketFactory，在createSocket时替换为HTTPDNS的IP，并进行SNI/HostNameVerify配置。
     *
     * @param connection
     */
    private void hostnameVerifier(HttpURLConnection connection) {
        //如果是https
        if (!(connection instanceof HttpsURLConnection)){
            return;
        }

        Log.i(TAG,"处理证书不匹配问题");
        final HttpsURLConnection finalConn = (HttpsURLConnection) connection;
        HttpDnsTLSSniSocketFactory sslSocketFactory = new HttpDnsTLSSniSocketFactory(finalConn);
        finalConn.setSSLSocketFactory(sslSocketFactory);

        finalConn.setHostnameVerifier(new HostnameVerifier() {
            /*
             * 关于这个接口的说明，官方有文档描述：
             * This is an extended verification option that implementers can provide.
             * It is to be used during a handshake if the URL's hostname does not match the
             * peer's identification hostname.
             *
             * 使用HTTPDNS后URL里设置的hostname不是远程的主机名(如:m.taobao.com)，与证书颁发的域不匹配，
             * Android HttpsURLConnection提供了回调接口让用户来处理这种定制化场景。
             * 在确认HTTPDNS返回的源站IP与Session携带的IP信息一致后，您可以在回调方法中将待验证域名替换为原来的真实域名进行验证。
             *
             */
            @Override
            public boolean verify(String hostname, SSLSession session) {
                String host = finalConn.getRequestProperty("Host");
                if (null == host) {
                    host = finalConn.getURL().getHost();
                }
                return HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session);
            }
        });
    }


    /**
     * 自处理Https证书问题
     *
     * @param connection
     */
    private void cusHostNameVerifier(HttpURLConnection connection) {
        if (!(connection instanceof HttpsURLConnection)) {
            return;
        }

        final HttpsURLConnection finalConn = (HttpsURLConnection) connection;
        HttpsUtils.SSLParams sslParams = HttpsUtils.getSslSocketFactory(null, null, null);
        finalConn.setSSLSocketFactory(sslParams.sSLSocketFactory);
        finalConn.setHostnameVerifier(new NullHostNameVerifier());

    }

    private void injectCustomHeaders(HttpURLConnection connection, String url) {
        Map<String, String> extraHeaders = headerInjector.addHeaders(url);
        if (extraHeaders == null) {
            return;
        }
        HttpProxyCacheDebuger.printfError("****** injectCustomHeaders ****** :" + extraHeaders.size());
        for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
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
        return "HttpUrlSource{sourceInfo='" + sourceInfo + "}";
    }

}
