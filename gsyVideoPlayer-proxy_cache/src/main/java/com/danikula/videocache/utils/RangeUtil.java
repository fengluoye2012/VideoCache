package com.danikula.videocache.utils;

public class RangeUtil {
    public static long getRangeEnd(long offset, long length) {
        //客户端每次请求只下载500K
        long end = offset + ConstantsUtil.getInstance().getDownLoadLength();
        return Math.min(end, length-1);
    }
}
