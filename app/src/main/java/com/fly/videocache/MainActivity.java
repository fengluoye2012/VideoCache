package com.fly.videocache;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.fly.videocache.cache.FileServer;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private Activity act;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        act = this;
        setContentView(R.layout.activity_main);

        TextView textView = findViewById(R.id.textView);
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FileServer fileServer = new FileServer(FileServer.PROXY_HOST, FileServer.PROXY_PORT);
                try {
                    if (!fileServer.wasStarted()) {
                        fileServer.start();
                    }

                    startActivity(new Intent(act, VideoActivity.class));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
