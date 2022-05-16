package com.example.exo;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class Start extends AppCompatActivity {
    private static String URL;
    EditText editText;
    Button go;

    public String getURL() {
        return URL;
    }

    public void setURL(String URL) {
        this.URL = URL;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        editText = findViewById(R.id.ed);
        go = findViewById(R.id.go);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                URL = editText.getText().toString();
                startActivity(new Intent(Start.this, PlayerActivity.class));
            }
        });
    }

    public static String URL() {
        String url = URL;
        return url;
    }
}