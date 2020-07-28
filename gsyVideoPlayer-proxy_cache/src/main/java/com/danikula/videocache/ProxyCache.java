package com.danikula.videocache;


import com.danikula.videocache.utils.LogUtil;

import java.util.concurrent.atomic.AtomicInteger;

import static com.danikula.videocache.Preconditions.checkNotNull;

/**
 * Proxy for {@link Source} with caching support ({@link Cache}).
 * <p/>
 * Can be used only for sources with persistent data (that doesn't change with time).
 * Method {@link #read(byte[], long, int)} will be blocked while fetching data from source.
 * Useful for streaming something with caching e.g. streaming video/audio etc.
 *
 * @author Alexey Danilov (danikula@gmail.com).
 */
class ProxyCache {

    private String TAG = ProxyCache.class.getSimpleName();
    private static final int MAX_READ_SOURCE_ATTEMPTS = 1;

    private final Source source;
    private final Cache cache;
    private final Object wc = new Object();
    private final Object stopLock = new Object();
    private final AtomicInteger readSourceErrorsCount;

    /**
     * 最后一次发生的异常
     */
    private Exception lastReadException;

    /**
     * 是否已经清除过缓存了
     */
    private boolean hasClearCache;

    private volatile Thread sourceReaderThread;
    private volatile boolean stopped;
    protected volatile int percentsAvailable = -1;

    /**
     * 标识是否处于暂停状态
     */
    private boolean pause;


    /**
     * 是否预缓存之后结束
     */
    protected boolean shutdownAfterPrecache;

    /**
     * 是否提前停止预缓存
     */
    protected boolean shutdownPreCache;


    public ProxyCache(Source source, Cache cache) {
        this.source = checkNotNull(source);
        this.cache = checkNotNull(cache);
        this.readSourceErrorsCount = new AtomicInteger();
    }

    public int read(byte[] buffer, long offset, int length) throws ProxyCacheException {
        ProxyCacheUtils.assertBuffer(buffer, offset, length);
        LogUtil.i(TAG, "read offset::" + offset / 1024 + "K" + "，，已缓存：" + cache.available() / 1024 + "K");
        //当文件没有缓存完成，已经缓存的文件大小不满足读取的offset，异步读取数据；
        while (!cache.isCompleted() && cache.available() < (offset + length) && !stopped) {
            readSourceAsync();
            waitForSourceData();
            checkReadSourceErrorsCount();
        }
        int read = cache.read(buffer, offset, length);
        if (cache.isCompleted() && percentsAvailable != 100) {
            percentsAvailable = 100;
            onCachePercentsAvailableChanged(100);
        }
        return read;
    }


    private void checkReadSourceErrorsCount() throws ProxyCacheException {
        int errorsCount = readSourceErrorsCount.get();
        if (errorsCount > MAX_READ_SOURCE_ATTEMPTS) {
            readSourceErrorsCount.set(0);
            if(lastReadException == null){
                throw new ProxyCacheException("Error reading source " + errorsCount + " times" );
            } else {
                throw new ProxyCacheException("Error reading source " + errorsCount + " times" + lastReadException.toString() , lastReadException);
            }
        } else if(errorsCount == 1 && !hasClearCache){
            // 发生了一次错误，那么清空缓存
            hasClearCache = true;
            cache.reset();
        }
    }

    public void shutdown() {
        synchronized (stopLock) {
            try {
                stopped = true;
                if (sourceReaderThread != null) {
                    sourceReaderThread.interrupt();
                }
                cache.close();
            } catch (ProxyCacheException e) {
                onError(e);
            }
        }
    }

    private synchronized void readSourceAsync() throws ProxyCacheException {
        boolean readingInProgress = sourceReaderThread != null && sourceReaderThread.getState() != Thread.State.TERMINATED;
        if (!stopped && !cache.isCompleted() && !readingInProgress) {
            LogUtil.i(TAG, "调用readSourceAsync，开启子线程");
            sourceReaderThread = new Thread(new SourceReaderRunnable(), "Source reader for " + source);
            sourceReaderThread.start();
        }
    }

    private void waitForSourceData() throws ProxyCacheException {
        synchronized (wc) {
            try {
                wc.wait(1000);
            } catch (InterruptedException e) {
                throw new ProxyCacheException("Waiting source data is interrupted!", e);
            }
        }
    }

    private void notifyNewCacheDataAvailable(long cacheAvailable, long sourceAvailable) {
        onCacheAvailable(cacheAvailable, sourceAvailable);

        synchronized (wc) {
            wc.notifyAll();
        }
    }

    protected void onCacheAvailable(long cacheAvailable, long sourceLength) {
        boolean zeroLengthSource = sourceLength == 0;
        int percents = zeroLengthSource ? 100 : (int) (cacheAvailable*1.0f / sourceLength * 100);
        boolean percentsChanged = percents != percentsAvailable;
        boolean sourceLengthKnown = sourceLength >= 0;
        if (sourceLengthKnown && percentsChanged) {
            onCachePercentsAvailableChanged(percents);
        }
        percentsAvailable = percents;
    }

    protected void onCachePercentsAvailableChanged(int percentsAvailable) {
    }

    private boolean hasError;
    private void readSource() {
        long sourceAvailable = -1;
        long offset = 0;
        try {
            offset = cache.available();
            source.open(offset);
            sourceAvailable = source.length();
            byte[] buffer = new byte[ProxyCacheUtils.DEFAULT_BUFFER_SIZE];
            int readBytes;
            while ((readBytes = source.read(buffer)) != -1) {
                synchronized (stopLock) {
                    if (isStopped()) {
                        return;
                    }

                    cache.append(buffer, readBytes);
                    if (cache.available() == source.length()) {
                        tryComplete();
                    }
                    offset += readBytes;
                    notifyNewCacheDataAvailable(offset, sourceAvailable);
                }
            }
            tryComplete();
            onSourceRead();
        } catch (Exception e) {
            e.printStackTrace();
            lastReadException = e;
            readSourceErrorsCount.incrementAndGet();
            onError(e);
        } finally {
            closeSource();
            notifyNewCacheDataAvailable(offset, sourceAvailable);
        }
    }

    private void onSourceRead() {
        //LogUtil.e(TAG,"onSourceRead");
        // guaranteed notify listeners after source read and cache completed
        //percentsAvailable = 100;
        onCachePercentsAvailableChanged(percentsAvailable);
    }

    protected void tryComplete() throws ProxyCacheException {
        synchronized (stopLock) {
            LogUtil.i(TAG, "available::" + cache.available()/1024+"KB" + ",,length::" + source.length()/1024+"KB");
            if (!isStopped() && cache.available() == source.length()) {
                cache.complete();
            }
        }
    }

    private boolean isStopped() {
        return Thread.currentThread().isInterrupted() || stopped;
    }

    private void closeSource() {
        try {
            source.close();
        } catch (ProxyCacheException e) {
            onError(new ProxyCacheException("Error closing source " + source, e));
        }
    }

    protected final void onError(final Throwable e) {
        boolean interruption = e instanceof InterruptedProxyCacheException;
        if (interruption) {
            HttpProxyCacheDebuger.printfLog("ProxyCache is interrupted");
        } else {
            HttpProxyCacheDebuger.printfError("ProxyCache error", e.getMessage());
        }
    }

    private class SourceReaderRunnable implements Runnable {

        @Override
        public void run() {
            readSource();
        }
    }

    public void setPause(boolean pause) {
        this.pause = pause;
    }

    public void setShutdownAfterPrecache(boolean shutdownAfterPrecache) {
        this.shutdownAfterPrecache = shutdownAfterPrecache;
    }

    public void setShutdownCache(boolean shutdownPreCache) {
        this.shutdownPreCache = shutdownPreCache;
    }
}
