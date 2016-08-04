package com.hesc.promise;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class PromiseUnitTest {

    private static class MockThreadFactory implements ThreadFactory {
        private volatile int threadCount = 0;
        private List<String> mLockedThreads = new Vector<>();

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "thread-" + (++threadCount));
        }

        public void acquire(){
            mLockedThreads.add(Thread.currentThread().getName());
        }

        public void release(){
            mLockedThreads.remove(Thread.currentThread().getName());
        }

        public void join() throws InterruptedException {
            while(true){
                if(!mLockedThreads.isEmpty()){
                    Thread.sleep(10);
                } else {
                    break;
                }
            }
        }

    }

    private MockThreadFactory mThreadFactory = new MockThreadFactory();

    private Executor mMockExecutor =  createExecutor();

    private Executor createExecutor(){
        return new Executor() {
            @Override
            public void execute(final Runnable command) {
                mThreadFactory.newThread(new Runnable() {
                    @Override
                    public void run() {
                        mThreadFactory.acquire();
                        try {
                            command.run();
                        } finally {
                            mThreadFactory.release();
                        }
                    }
                }).run();
            }
        };
    }

    @Test
    public void normalPromise() throws Exception{
        Promise<Integer> promise = new Promise<>(new Action<PromiseHandler<Integer>>() {
            @Override
            public void call(PromiseHandler<Integer> handler) {
                handler.resolve(100);
            }
        }, mMockExecutor, "normalPromise");
        promise.then(new Function<Integer, Integer>() {
            @Override
            public Integer call(Integer integer) {
                assertEquals((long) integer, 100);
                return 100;
            }
        }).exception(new Action<Throwable>() {
            @Override
            public void call(Throwable throwable) {
            }
        }).then(new Function<Object, Void>() {
            @Override
            public Void call(Object o) {
                assertEquals((long)(Integer) o, 100);
                return null;
            }
        });

        mThreadFactory.join();
    }

    @Test
    public void normalPromise_1() throws Exception{
        Promise<Integer> promise = new Promise<>(new Action<PromiseHandler<Integer>>() {
            @Override
            public void call(PromiseHandler<Integer> handler) {
                handler.resolve(100);
            }
        }, mMockExecutor, "normalPromise");
        promise.exception(new Action<Throwable>() {
            @Override
            public void call(Throwable throwable) {

            }
        }).then(new Function<Object, Void>() {
            @Override
            public Void call(Object o) {
                assertEquals((long)(Integer) o, 100);
                return null;
            }
        });

        mThreadFactory.join();
    }

    @Test
    public void normalPromiseOnChain() throws Exception{
        Promise<Integer> promise = new Promise<>(new Action<PromiseHandler<Integer>>() {
            @Override
            public void call(PromiseHandler<Integer> handler) {
                handler.resolve(100);
            }
        }, mMockExecutor, "normalPromiseOnChain");
        promise.then(new Function<Integer, Integer>() {
            @Override
            public Integer call(Integer integer) {
                assertEquals((long)integer, 100L);
                return 60;
            }
        }).then(new Function<Integer, Void>() {
            @Override
            public Void call(Integer integer) {
                assertEquals((long)integer, 60);
                return null;
            }
        }).thenPromise(new Function<Void, Promise<Integer>>() {
            @Override
            public Promise<Integer> call(Void aVoid) {
                return new Promise<Integer>(new Action<PromiseHandler<Integer>>() {
                    @Override
                    public void call(PromiseHandler<Integer> handler) {
                        handler.resolve(200);
                    }
                },mMockExecutor, "normalPromiseOnChain-1");
            }
        }).then(new Function<Integer, Void>() {
            @Override
            public Void call(Integer integer) {
                assertEquals((long)integer, 200);
                return null;
            }
        });
        mThreadFactory.join();
    }

    @Test
    public void normalPromiseOnChain_1() throws Exception{
        Promise<Integer> promise = new Promise<>(new Action<PromiseHandler<Integer>>() {
            @Override
            public void call(PromiseHandler<Integer> handler) {
                handler.resolve(100);
            }
        }, mMockExecutor, "normalPromiseOnChain");

        Promise<Integer> newPromise;
        for(int i=0; i<1000; i++){
            newPromise = promise;
            for(int j=0; j<10; j++) {
                newPromise = newPromise.then(new Function<Integer, Integer>() {
                    @Override
                    public Integer call(Integer integer) {
                        assertEquals((long) integer, 100L);
                        return integer;
                    }
                });
            }
        }
        mThreadFactory.join();
    }

    @Test
    public void normalPromiseOnMoreTime() throws Exception{
        Promise<Integer> promise = new Promise<>(new Action<PromiseHandler<Integer>>() {
            @Override
            public void call(PromiseHandler<Integer> handler) {
                handler.resolve(100);
            }
        }, mMockExecutor, "normalPromiseOnMoreTime");

        for(int i =0; i<1000; i++) {
            promise.then(new Function<Integer, Void>() {
                @Override
                public Void call(Integer integer) {
                    assertEquals((long) integer, 100L);
                    return null;
                }
            });
        }
        mThreadFactory.join();
    }

    @Test
    public void normalPromiseOnThread() throws Exception{
        Promise<Integer> promise = new Promise<>(new Action<PromiseHandler<Integer>>() {
            @Override
            public void call(final PromiseHandler<Integer> handler) {
                Executors.newSingleThreadExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        handler.resolve(100);
                    }
                });
            }
        }, mMockExecutor, "normalPromiseOnThread");

        for(int i=0; i<1000; i++) {
            promise.then(new Function<Integer, Void>() {
                @Override
                public Void call(Integer integer) {
                    assertEquals((long) integer, 100L);
                    return null;
                }
            });
        }
        mThreadFactory.join();
    }

    @Test
    public void normalPromiseOnNesting() throws Exception{
        Executor executor = mMockExecutor;

        final Promise<Integer> nestedPromise = new Promise<Integer>(new Action<PromiseHandler<Integer>>() {
            @Override
            public void call(PromiseHandler<Integer> handler) {
                handler.resolve(100);
            }
        }, executor, "normalPromiseOnNesting-1");

        Promise<Integer> promise = new Promise<>(new Action<PromiseHandler<Integer>>() {
            @Override
            public void call(PromiseHandler<Integer> handler) {
                handler.resolvePromise(nestedPromise);
            }
        }, executor, "normalPromiseOnNesting");

        for(int i=0; i<1000; i++) {
            promise.then(new Function<Integer, Void>() {
                @Override
                public Void call(Integer integer) {
                    assertEquals((long) integer, 100);
                    return null;
                }
            }).exception(new Action<Throwable>() {
                @Override
                public void call(Throwable throwable) {
                    throwable.printStackTrace();
                    assertTrue(true);
                }
            });
        }
        mThreadFactory.join();
    }

    @Test
    public void allPromise() throws Exception {
        Promise<Integer>[] promises = new Promise[10];
        final Executor executor = createExecutor();
        Executor mockExecutor = mMockExecutor;

        int[] sleepTimes = new int[]{
                100,
                200,
                500,
                600,
                400,
                300,
                250,
                50,
                700,
                900,
        };

        for(int i=0; i<10; i++){
            final long sleepTime = sleepTimes[i];
            final int value = (i+1)*10;

            promises[i] = new Promise<>(new Action<PromiseHandler<Integer>>() {
                @Override
                public void call(final PromiseHandler<Integer> handler) {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(sleepTime);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            handler.resolve(value);
                        }
                    });
                }
            }, mockExecutor, "allPromise" + i);
        }

        Promise<List<Integer>> newPromise = Promise.all(mockExecutor, "allPromise_new", promises);
        newPromise.then(new Function<List<Integer>, Void>() {
            @Override
            public Void call(List<Integer> integers) {
                assertEquals(10, integers.size());
                return null;
            }
        });
        mThreadFactory.join();
    }

    @Test
    public void racePromise() throws Exception {
        Promise<Integer>[] promises = new Promise[10];
        final Executor executor = createExecutor();
        Executor mockExecutor = mMockExecutor;

        int[] sleepTimes = new int[]{
                100,
                200,
                500,
                600,
                400,
                300,
                250,
                50,
                700,
                900,
        };

        for(int i=0; i<10; i++){
            final long sleepTime = sleepTimes[i];
            final int value = (i+1)*10;

            promises[i] = new Promise<>(new Action<PromiseHandler<Integer>>() {
                @Override
                public void call(final PromiseHandler<Integer> handler) {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(sleepTime);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            handler.resolve(value);
                        }
                    });
                }
            }, mockExecutor, "racePromise" + i);
        }

        Promise<Integer> newPromise = Promise.race(mockExecutor, "racePromise_new", promises);
        newPromise.then(new Function<Integer, Object>() {
            @Override
            public Object call(Integer integer) {
                assertEquals(80, (long)integer);
                return null;
            }
        });
        mThreadFactory.join();
    }
}