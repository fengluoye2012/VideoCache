package com.fly.videocache;

import android.content.Context;
import android.util.AttributeSet;

import com.shuyu.gsyvideoplayer.video.ListGSYVideoPlayer;
import com.shuyu.gsyvideoplayer.video.base.GSYVideoViewBridge;

public class MyListGSYVideoPlayer extends ListGSYVideoPlayer {

    public MyListGSYVideoPlayer(Context context, Boolean fullFlag) {
        super(context, fullFlag);
    }

    public MyListGSYVideoPlayer(Context context) {
        super(context);
    }

    public MyListGSYVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }


    @Override
    public GSYVideoViewBridge getGSYVideoManager() {
        MyGSYVideoManager.instance().initContext(getContext().getApplicationContext());
        return  MyGSYVideoManager.instance();
    }
}
