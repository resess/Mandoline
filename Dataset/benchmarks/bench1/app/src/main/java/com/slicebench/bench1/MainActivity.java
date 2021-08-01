package com.slicebench.bench1;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    String tag ="Bench1";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        String x = tag;
        foo(x);
    }
    public void foo(String tag){
        Struct a = new Struct();
        Struct b = new Struct();
        a = b;
        bar(a);
        Log.i(tag, b.f);
    }
    public void bar(Struct x){
        x.f = "Data from bar";
    }

    @Override
    protected void onResume() {
        super.onResume();
        tag = "Bench1";
    }
}

class Struct{
    String f = "";
}