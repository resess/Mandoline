package com.slicebench.bench6;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import org.tinylog.Logger;

public class MainActivity extends AppCompatActivity {
    Student bob;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EditText editText = findViewById(R.id.editTextNumber);
        String str = editText.getText().toString();
        int grade = 100;
        if (!str.equals("")){
            grade = Integer.valueOf(str);
        }
        this.bob = new Student();
        if (grade > 6) {
            this.bob.setPass(true);
        }
        Button button = findViewById(R.id.button);
        MyClick myClick = new MyClick(bob);
        button.setOnClickListener(myClick);
    }
}


class MyClick implements View.OnClickListener {
    Student student;
    MyClick(Student s){
        this.student = s;
    }
    @Override
    public void onClick(View view) {
        boolean pass = this.student.getPass();
        Log.i("Bench6", String.valueOf(pass));
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
