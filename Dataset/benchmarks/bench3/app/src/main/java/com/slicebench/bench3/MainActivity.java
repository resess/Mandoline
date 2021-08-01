package com.slicebench.bench3;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {
    EditText editText;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editText = findViewById(R.id.textIn);
        Click click  = new Click(this);
        click.onClick();
    }
}

class Click {
    MainActivity field;
    Click(MainActivity activity) {
        field = activity;
    }
    public void onClick(){
        Log.d("Bench3", field.editText.getText().toString());
    }
}