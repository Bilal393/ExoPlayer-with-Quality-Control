package com.adaptive.exoplayer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

public class Start extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent Main  = new Intent(Start.this,OnlinePlayerActivity.class);
                Main.putExtra("Url","http://ipflix.click:8080/oembw0jpzp/I89V35Gs21U/5435");
                startActivity(Main);
                finish();
            }
        },1000);
    }
}