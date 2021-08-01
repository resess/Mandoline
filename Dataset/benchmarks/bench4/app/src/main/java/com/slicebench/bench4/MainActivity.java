package com.slicebench.bench4;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    Student bob;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        int grade = 80;
        this.bob = new Student();
        if (grade > 6) {
            this.bob.setPass(true);
        }
        boolean pass = this.bob.getPass();
        Log.i("Bench4", "The student passed? " + String.valueOf(pass));
    }
}

class Student {
    boolean pass = false;
    void setPass(boolean b) {
        this.pass = b;
    }
    boolean getPass(){
        return this.pass;
    }
}