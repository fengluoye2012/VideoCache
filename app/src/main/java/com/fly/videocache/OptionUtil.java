package com.fly.videocache;

import com.shuyu.gsyvideoplayer.model.VideoOptionModel;

import java.util.ArrayList;
import java.util.List;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public class OptionUtil {

    private static OptionUtil instance;
    private List<VideoOptionModel> optionModels;

    //最大缓冲大小
    private int maxBufferSize;

    private OptionUtil() {
        optionModels = new ArrayList<>();
        initOption();
    }

    public static OptionUtil getInstance() {
        if (instance == null) {
            synchronized (OptionUtil.class) {
                if (instance == null) {
                    instance = new OptionUtil();

                }
            }
        }
        return instance;
    }

    public List<VideoOptionModel> getOptionList(){
        return optionModels;
    }

    /**
     * 设置Option
     */
    private void initOption() {
        //最大缓冲大小,默认值15*1024*1024即15M
        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", getMaxBufferSize()));
        //最大缓存时长
        //optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max_cached_duration", 3));

        //设置播放前的探测时间 1,达到首屏秒开效果
        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1));
        //播放前的探测Size，默认是1M, 改小一点会出画面更快，最小值为2048，最大值为1 << 20；
        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 5 * 1024));


        //根据IjkPlayer起播速度优化：https://cloud.tencent.com/developer/article/1357997 添加
        //跳过循环滤波; 0 开启，画面质量高，解码开销大; 48 关闭， 画面质量差，解码开销小
        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_CODEC,"skip_loop_filter",48));
        //设置最长分析时长
        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzemaxduration", 100));
        //通过立即清理数据包来减少等待时长
        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1));

        //暂停输出直到停止后读取足够的数据包，buffering方法不再执行
        //optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0));

        //网络不好的情况下进行丢包
        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1));
        //不查询stream_info，直接使用
        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER,"find_stream_info", 0));
        //等待开始之后才绘制
        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "render-wait-start", 1));


        //设置停止预读取的最小帧数，范围2～50000，默认值50000
        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "min-frames", 20));

//        //设置第一次唤醒read_thread线程的时间(毫秒)，范围100～5000，默认值100
//        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "first-high-water-mark-ms", 100));
//        //设置下一次唤醒read_thread线程的时间(毫秒)，范围100～5000，默认值1000
//        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "next-high-water-mark-ms", 1000));
//        //设置最后一次唤醒read_thread线程的时间(毫秒)，范围100～5000，默认值5000
//        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "last-high-water-mark-ms", 5000));

        //支持分片下载
        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "http-detect-range-support", 1));

        //SeekTo设置优化
        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1));
        //seek 默认超时时间5*1000 ms
        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "accurate-seek-timeout", 500));

        //清空DNS,有时会造成因为DNS的问题而报10000问题的
        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1));

//        //重连模式，如果中途服务器断开了连接，让它重新连接
//        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "reconnect", 5));
//        //链接超时时间
//        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "timeout", 20*1000*1000));

        //解决视频倍速播放部分机型无效
        optionModels.add(new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch",  1));
    }

    public int getMaxBufferSize() {
        return maxBufferSize > 0 ? maxBufferSize : 500 * 1024;
    }

    public void setMaxBufferSize(int maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
    }
}
