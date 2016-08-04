package com.hesc.promise.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.hesc.promise.Function;
import com.hesc.promise.Promise;
import com.hesc.promise.PromiseExecutors;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void doTest(View view){
        Promise<Integer>[] promises = new Promise[100];

        Random random = new Random();
        for(int i=0; i<100; i++){
            final long sleepTime = random.nextInt(1000);
            final int value = (i+1)*10;

            promises[i] = Promise.delay(sleepTime).then(new Function<Object, Integer>() {
                @Override
                public Integer call(Object obj) {
                    return value;
                }
            });
        }

        Promise<List<Integer>> newPromise = Promise.all(promises);
        newPromise.then(new Function<List<Integer>, Void>() {
            @Override
            public Void call(List<Integer> integers) {
                String result = Arrays.toString(integers.toArray(new Integer[100]));
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
                Log.d("kkk", result);
                return null;
            }
        });

//        Promise<Integer> newPromise = Promise.race(PromiseExecutors.currentThread(), "newPromise",promises);
//        newPromise.then(new Function<Integer, Void>() {
//            @Override
//            public Void call(Integer integer) {
//                Toast.makeText(MainActivity.this, integer+"", Toast.LENGTH_LONG).show();
//                return null;
//            }
//        });
    }
}
