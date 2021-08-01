package com.slicebench.bench2;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    String f1 = "";
    String f2 = "";
    String f3 = "";
    String f4 = "";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        f1 = "Hello";
        foo();
    }
    public void foo(){
        bar();
    }
    public void bar(){
        f4 = f1;
        if (f1 != null) {
            f4 = f2;
        } else {
            f4 = f3;
        }
        Log.i("Bench1", f4);
    }
}