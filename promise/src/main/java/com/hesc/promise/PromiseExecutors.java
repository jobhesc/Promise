package com.hesc.promise;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by hesc on 16/8/4.
 */
public class PromiseExecutors {

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    private static final ExecutorService ioExecutor = Executors.newCachedThreadPool();
    private static final ExecutorService newThreadExecutor = Executors.newSingleThreadExecutor();
    private static final ExecutorService computationExecutor = Executors.newFixedThreadPool(CPU_COUNT);

    public static Executor io(){
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                ioExecutor.execute(command);
            }
        };
    }

    public static Executor immediate(){
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }

    public static Executor computation(){
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                computationExecutor.execute(command);
            }
        };
    }

    public static Executor newThread(){
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                newThreadExecutor.execute(command);
            }
        };
    }

    public static Executor mainThread(){
        return new Executor() {
            private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
            @Override
            public void execute(Runnable command) {
                mainThreadHandler.post(command);
            }
        };
    }

    public static Executor currentThread(){
        return new Executor() {
            private final Handler mHandler = new Handler();
            @Override
            public void execute(Runnable command) {
                mHandler.post(command);
            }
        };
    }
}
