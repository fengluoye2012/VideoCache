package com.fly.videocache.cache;

import com.danikula.videocache.ProxyCacheUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * NanoHTTPD是一个免费、轻量级的(只有一个Java文件) HTTP服务器,可以很好地嵌入到Java程序中。
 * 支持 GET, POST, PUT, HEAD 和 DELETE 请求，支持文件上传，占用内存很小。
 * <p>
 * 可以在该手机浏览器上访问
 */
public class FileServer extends NanoHTTPD {

    public static final String PROXY_HOST = "127.0.0.1";
    public static final int PROXY_PORT = 8080;

    //使用父类的构造方法就够了
    public FileServer(int port) {
        super(port);
    }

    public FileServer(String hostname, int port) {
        super(hostname, port);
    }

    //重写serve方法，该方法在每次请求时调用
    //session相当于请求对象，里面包含获取uri，文件头，查询字符串等的方法
    @Override
    public Response serve(IHTTPSession session) {
        //获取请求的uri
        String uri = session.getUri();
        /*将请求uri转化为本地文件的地址
         *读取文件内容，保存到字符串或者字节数组中，这里不给出详细代码了
         */
        Map<String, String> headers = session.getHeaders();
        Method method = session.getMethod();



        //将文件转化的字符串或者数组作为响应内容返回
        return NanoHTTPD.newFixedLengthResponse("风落叶");
        //或者return  Response.newFixedLengthResponse(状态码，mime类型，字节数组)
    }

    public static String getProxyUrl(String url) {
        try {
            return String.format(Locale.US, "http://%s:%d/%s", PROXY_HOST, PROXY_PORT,  URLEncoder.encode(url, "utf-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "";
        }
    }
}
