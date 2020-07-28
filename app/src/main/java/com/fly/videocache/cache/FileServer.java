package com.fly.videocache.cache;

import fi.iki.elonen.NanoHTTPD;

public class FileServer extends NanoHTTPD {

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

        //将文件转化的字符串或者数组作为响应内容返回
        return NanoHTTPD.newFixedLengthResponse("");
        //或者return  Response.newFixedLengthResponse(状态码，mime类型，字节数组)
    }
}
