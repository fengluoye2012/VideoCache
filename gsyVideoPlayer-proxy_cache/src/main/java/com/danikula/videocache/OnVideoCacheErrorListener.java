package com.danikula.videocache;

/**
 * Created by hanli
 * date 2020-02-04.
 * ps: 视频缓存发生异常的时候的回调监听
 */
public interface OnVideoCacheErrorListener {

    /**
     * 发生了异常
     * @param e
     * @param expandInfo
     */
    void onError(Exception e, String expandInfo);
}
