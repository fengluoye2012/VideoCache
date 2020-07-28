package com.fly.videocache;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

import com.fly.videocache.cache.FileServer;
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;

public class VideoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        StandardGSYVideoPlayer player = findViewById(R.id.player);
        String originUrl ="https://file.yyuehd.com/3YMFGz-lZxvmSH99SNqRz3mtU2s=/lr7ck81foalsFrahNnOW90-TrfrJ?flag=ecc22ee1-aab4-42a5-9448-1d47b2de12d6";
        player.setUp(FileServer.getProxyUrl(originUrl),false,"");
        player.startPlayLogic();
    }
}
