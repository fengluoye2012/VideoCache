package com.danikula.videocache.utils;

public class ConstantsUtil {
    private String TAG=ConstantsUtil.class.getSimpleName();

    //ping Service的offset
    public static final int PING_SERVER_OFFSET = -1;

    //Head 请求的offset;
    public static final int HEAD_OFFSET= -2;

    //系统的默认超时时间
    public static final int SYSTEM_OUT_TIME = 8 * 1000;

    //自定义超时时间
    public static final int CUS_OUT_TIME = 30 * 1000;

    //每次默认下载500K文件
    private static final int DOWN_LOAD_LENGTH = 500 * 1024;

    //预缓存的最大长度
    private static final int PRE_CACHE_LENGTH = 500*1024;


    public static final int NO_CACHE_LENGTH_BARRIER = 15 * 1024 * 1024;

    public static final int NO_CACHE_LENGTH_BARRIER_SECOND = 30 * 1024 * 1024;

    //文件长度小于15M,seek临界值为15%
    public static final float NO_CACHE_BARRIER_FIRST = 0.15f;

    //文件长度大于15M,seek临界值为10%
    public static final float NO_CACHE_BARRIER_SECOND = 0.1f;

    //文件长度大于15M,seek临界值为5%
    public static final float NO_CACHE_BARRIER_THIRD = 0.05f;

    //预缓存的最大长度
    private int preCacheLength;


    //文件每次下载长度
    private int downLoadLength;

    private static ConstantsUtil instance;

    private ConstantsUtil(){}

    public static ConstantsUtil getInstance(){
        if (instance == null){
            synchronized (ConstantsUtil.class){
                if (instance == null){
                    instance = new ConstantsUtil();
                }
            }
        }
        return instance;
    }

    public int getPreCacheLength() {
        LogUtil.i(TAG, "preCacheLength::" + preCacheLength);
        return preCacheLength > 0 ? preCacheLength : PRE_CACHE_LENGTH;
    }

    public void setPreCacheLength(int preCacheLength) {
        this.preCacheLength = preCacheLength;
    }


    public int getDownLoadLength() {
        return downLoadLength > 0 ? downLoadLength : DOWN_LOAD_LENGTH;
    }

    public void setDownLoadLength(int downLoadLength) {
        this.downLoadLength = downLoadLength;
    }

}
