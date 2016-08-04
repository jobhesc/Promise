package com.hesc.promise;

import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Created by hesc on 16/6/13.
 * <p>promise来源于js里的异步编程框架,翻译为"承诺"。<br/>
 * 所谓promise，简单说就是一个容器，里面保存着某个未来才会结束的事件（通常是一个异步操作）的结果。</p>
 * <p>promise对象有以下两个特点。</p>
 * <ul>
 * <li>对象的状态不受外界影响。promise对象代表一个异步操作，有三种状态：pending（进行中）、
 * resolved（已完成，又称fulfilled）和rejected（已失败）。只有异步操作的结果，可以决定当前是哪一种状态，
 * 任何其他操作都无法改变这个状态。这也是promise这个名字的由来，它的英语意思就是“承诺”，表示其他手段无法改变。</li>
 * <li>一旦状态改变，就不会再变，任何时候都可以得到这个结果。promise对象的状态改变，
 * 只有两种可能：从pending变为resolved和从pending变为rejected。只要这两种情况发生，
 * 状态就凝固了，不会再变了，会一直保持这个结果。就算改变已经发生了，你再对promise对象添加回调函数，
 * 也会立即得到这个结果。这与事件（event）完全不同，事件的特点是，如果你错过了它，再去监听，是得不到结果的。</li>
 * </ul>
 * <p>有了promise对象，就可以将异步操作以同步操作的流程表达出来，避免了层层嵌套的回调函数。此外，promise对象提供统一的接口，使得控制异步操作更加容易。</p>
 *
 */
public final class Promise<T> {
    //状态:未执行
    static final int STATE_PENDING = 0;
    //状态:执行成功
    static final int STATE_RESOLVED = 1;
    //状态:执行失败
    static final int STATE_REJECTED = 2;

    //promise状态
    private int mState = STATE_PENDING;
    //回调处理类
    private InternalPromiseHandler<T> mPromiseHandler;
    //毁约类
    private Broken mBroken;
    //执行类
    private Executor mExecutor;
    //promise的名称
    private String mName;

    private Promise(Broken broken, Executor executor, String name){
        mExecutor = executor;
        mPromiseHandler = new InternalPromiseHandler<>(this, executor);
        mBroken = broken;
        mName = name;
    }

    /**
     * promise构造函数接受一个{@link Action}接口作为参数,在该接口的{@link Action#call(Object)}方法中,又传入
     * {@link PromiseHandler}接口作为参数。<br/>
     * PromiseHandler提供resolve方法和reject方法, <br/>
     * resolve方法的作用是，将promise对象的状态从“未完成”变为“成功”（即从pending变为resolved），
     * 在异步操作成功时调用，并将异步操作的结果，作为参数传递出去；<br/>
     * reject方法的作用是，将Promise对象的状态从“未完成”变为“失败”（即从pending变为rejected），
     * 在异步操作失败时调用，并将异步操作报出的错误，作为参数传递出去。<br/>
     * promise实例生成以后，可以用then方法分别指定resolved状态和rejected状态的回调函数
     * @param onAction
     * @param executor
     * @param name
     */
    public Promise(Action<PromiseHandler<T>> onAction, Executor executor, String name){
        this(new DefaultBroken(), executor, name);

        try {
            onAction.call(mPromiseHandler);
        } catch (Throwable e){
            e.printStackTrace();
            //状态未做变化,需要切换状态从pending到rejected,否则把异常抛出
            if(getState() == STATE_PENDING){
                mPromiseHandler.reject(e);
            } else {
                throw e;
            }
        }
    }

    public Promise(Action<PromiseHandler<T>> onAction, Executor executor){
        this(onAction, executor, null);
    }

    public Promise(Action<PromiseHandler<T>> onAction){
        this(onAction, createDefaultExecutor());
    }

    private static Executor createDefaultExecutor(){
        return PromiseExecutors.currentThread();
    }

    /**
     * 用于将多个promise实例，包装成一个新的promise实例。<br/>
     * 新的promise实例的状态由传入参数的promise决定，分成两种情况。<br/>
     * <ul>
     *     <li>只有所有参数的promise的状态都变成resolved，新的promise实例的状态才会变成resolved，
     *     此时所有参数promise的返回值组成一个数组，传递给新的promise实例的回调函数。</li>
     *     <li>只要所有参数的promise之中有一个被rejected，新的promise实例的状态就变成rejected，
     *     此时第一个被reject的实例的返回值，会传递给新的promise实例的回调函数</li>
     * </ul>
     * @param executor
     * @param promises
     * @param name
     * @param <T>
     * @return
     */
    @SafeVarargs
    public static <T> Promise<List<T>> all(Executor executor, String name, final Promise<T>... promises){
        if(promises == null || promises.length==0) {
            throw new IllegalArgumentException("parameter promises is null");
        }

        final Promise<List<T>> newPromise = new Promise<>(new DefaultBroken(), executor, name);

        Function<T, Void> onResolved = new Function<T, Void>() {
            List<T> items = new Vector<>();

            @Override
            public Void call(T t) {
                if(newPromise.getState() == STATE_PENDING) {
                    items.add(t);
                    if (items.size() == promises.length) {
                        newPromise.mPromiseHandler.rawResolve(items);
                    }
                }
                return null;
            }
        };

        Promise<T> promise;
        for(int i = 0; i<promises.length; i++){
            promise = promises[i];
            promise.mPromiseHandler.setCallback(onResolved,
                    wrapRejectedFunction(newPromise, null));
        }
        return newPromise;
    }

    @SafeVarargs
    public static <T> Promise<List<T>> all(Executor executor, final Promise<T>... promises){
        return all(executor, null, promises);
    }

    @SafeVarargs
    public static <T> Promise<List<T>> all(final Promise<T>... promises){
        return all(createDefaultExecutor(), promises);
    }

    /**
     * 用于将多个promise实例，包装成一个新的promise实例。<br/>
     * 新的promise实例的状态由传入参数的promise决定，分成两种情况。<br/>
     * <ul>
     *     <li>只要所有参数的promise之中有一个被resolved，新的promise实例的状态就会变成resolved，
     *     此时第一个被resolved的实例的返回值，传递给新的promise实例的回调函数。</li>
     *     <li>只要所有参数的promise之中有一个被rejected，新的promise实例的状态就变成rejected，
     *     此时第一个被reject的实例的返回值，会传递给新的promise实例的回调函数</li>
     * </ul>
     * @param executor
     * @param promises
     * @param name
     * @param <T>
     * @return
     */
    @SafeVarargs
    public static <T> Promise<T> race(Executor executor, String name, final Promise<T>... promises){
        if(promises == null || promises.length==0) {
            throw new IllegalArgumentException("parameter promises is null");
        }

        final Promise<T> newPromise = new Promise<>(new DefaultBroken(), executor, name);

        Function<T, Void> onResolved = new Function<T, Void>() {

            @Override
            public Void call(T t) {
                if (newPromise.getState() == STATE_PENDING) {
                    newPromise.mPromiseHandler.rawResolve(t);
                }
                return null;
            }
        };

        Promise<T> promise;
        for(int i = 0; i<promises.length; i++){
            promise = promises[i];
            promise.mPromiseHandler.setCallback(onResolved,
                    wrapRejectedFunction(newPromise, null));
        }
        return newPromise;
    }

    @SafeVarargs
    public static <T> Promise<T> race(Executor executor, final Promise<T>... promises){
        return race(executor, null, promises);
    }

    @SafeVarargs
    public static <T> Promise<T> race(final Promise<T>... promises){
        return race(createDefaultExecutor(), promises);
    }

    public static <T> Promise<T> delay(final long milliseconds){
        return delay(milliseconds, createDefaultExecutor());
    }

    public static <T> Promise<T> delay(final long milliseconds, Executor executor){
        return new Promise<>(new Action<PromiseHandler<T>>() {
            @Override
            public void call(final PromiseHandler<T> promiseHandler) {
                Executors.newSingleThreadExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(milliseconds);
                            promiseHandler.resolve(null);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }, executor);
    }

    /**
     * 把一个普通对象转成一个promise对象,状态为resolved
     * @param executor
     * @param name
     * @param a
     * @param <T>
     * @return
     */
    public static <T> Promise<T> resolve(final T a, Executor executor, String name){
        return new Promise<>(new Action<PromiseHandler<T>>() {
            @Override
            public void call(PromiseHandler<T> promiseHandler) {
                promiseHandler.resolve(a);
            }
        }, executor, name);
    }

    public static <T> Promise<T> resolve(final T a, String name){
        return resolve(a, createDefaultExecutor(), name);
    }

    /**
     * 生成一个空的promise实例, 状态为resolved
     * @param executor
     * @param <T>
     * @return
     */
    public static <T> Promise<T> resolve(Executor executor, String name){
        return resolve(null, executor, name);
    }

    public static <T> Promise<T> resolve(String name){
        return resolve(createDefaultExecutor(), name);
    }

    public static <T> Promise<T> resolve(){
        return resolve(null);
    }

    /**
     * 生成一个空的promise实例,状态为rejected
     * @param executor
     * @param name
     * @return
     */
    public static <T> Promise<T> reject(Executor executor, String name){
        return reject("", executor, name);
    }

    public static <T> Promise<T> reject(final String exception){
        return reject(exception, createDefaultExecutor(), null);
    }

    /**
     * 生成一个promise实例,状态为rejected,异常信息为exception
     * @param executor
     * @param name
     * @param exception
     * @return
     */
    public static <T> Promise<T> reject(final String exception, Executor executor, String name){
        return new Promise<>(new Action<PromiseHandler<T>>() {
            @Override
            public void call(PromiseHandler<T> promiseHandler) {
                promiseHandler.reject(new Exception(exception));
            }
        }, executor, name);
    }

    /**
     * 处理状态变化时,把构造函数调用{@link PromiseHandler#resolve(Object)}传入的值,交给参数onResolved回调函数处理,
     * onResolved回调函数需要返回一个新的promise实例
     * @param onResolved
     * @param <R>
     * @return
     */
    public <R> Promise<R> thenPromise(final Function<T, Promise<R>> onResolved){
        return thenPromise(onResolved, null);
    }

    /**
     * 处理状态变化时,把构造函数调用{@link PromiseHandler#resolve(Object)}传入的值,交给参数onResolved回调函数处理,
     * 且把{@link PromiseHandler#reject(Throwable)}传入的值,交给参数onRejected处理。
     * onResolved回调函数需要返回一个新的promise实例
     * @param onResolved
     * @param onRejected
     * @param <R>
     * @return
     */
    public <R> Promise<R> thenPromise(final Function<T, Promise<R>> onResolved, final Action<Throwable> onRejected){
        final Promise<R> promise = new Promise<>(mBroken, mExecutor,
                (mName == null || mName.length()==0)? null: mName + "-thenPromise");
        mPromiseHandler.setCallback(wrapResolvedPromiseFunction(promise, onResolved), wrapRejectedFunction(promise, onRejected));
        return promise;
    }

    /**
     * 处理状态变化时,把构造函数调用{@link PromiseHandler#resolve(Object)}传入的值,交给参数onResolved回调函数处理,
     * onResolved回调函数返回一个普通类型对象
     * @param onResolved
     * @param <R>
     * @return
     */
    public <R> Promise<R> then(final Function<T, R> onResolved){
        return then(onResolved, null);
    }

    /**
     * 处理状态变化时,把构造函数调用{@link PromiseHandler#resolve(Object)}传入的值,交给参数onResolved回调函数处理,
     * 且把{@link PromiseHandler#reject(Throwable)}传入的值,交给参数onRejected处理。
     * onResolved回调函数需要返回一个普通类型对象
     * @param onResolved
     * @param onRejected
     * @param <R>
     * @return
     */
    public <R> Promise<R> then(final Function<T, R> onResolved, final Action<Throwable> onRejected){
        final Promise<R> promise = new Promise<>(mBroken, mExecutor,
                (mName == null || mName.length()==0)? null: mName + "-then");
        mPromiseHandler.setCallback(wrapResolvedFunction(promise, onResolved), wrapRejectedFunction(promise, onRejected));
        return promise;
    }

    private static <T, R> Function<T, Void> wrapResolvedPromiseFunction(final Promise<R> promise, final Function<T, Promise<R>> onResolved){
        return new Function<T, Void>() {
            @Override
            public Void call(T t) {
                if (promise.getState() != STATE_PENDING) return null;
                if (promise.mBroken.isBroken()) return null;

                try {
                    if (onResolved != null) {
                        Promise<R> result = onResolved.call(t);
                        promise.mPromiseHandler.resolvePromise(result);
                    } else {
                        promise.mPromiseHandler.rawResolve((R) t);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    //状态未做变化,需要切换状态从pending到rejected,否则把异常抛出
                    if (promise.getState() == STATE_PENDING) {
                        promise.mPromiseHandler.rawReject(e);
                    } else {
                        throw e;
                    }
                }
                return null;
            }
        };
    }

    private static <T, R> Function<T, Void> wrapResolvedFunction(final Promise<R> promise, final Function<T, R> onResolved){
        return new Function<T, Void>() {
            @Override
            public Void call(T t) {
                if (promise.getState() != STATE_PENDING) return null;
                if (promise.mBroken.isBroken()) return null;

                try {
                    if (onResolved != null) {
                        R result = onResolved.call(t);
                        promise.mPromiseHandler.rawResolve(result);
                    } else {
                        promise.mPromiseHandler.rawResolve((R) t);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                    //状态未做变化,需要切换状态从pending到rejected,否则把异常抛出
                    if (promise.getState() == STATE_PENDING) {
                        promise.mPromiseHandler.rawReject(e);
                    } else {
                        throw e;
                    }
                }
                return null;
            }
        };
    }

    private static <R> Action<Throwable> wrapRejectedFunction(final Promise<R> promise, final Action<Throwable> onRejected){
        return new Action<Throwable>() {
            @Override
            public void call(Throwable e) {
                if (promise.getState() != STATE_PENDING) return;
                if (promise.mBroken.isBroken()) return;

                if (onRejected != null) {
                    onRejected.call(e);
                    //已经处理了异常,则不传到下个promise
                    //promise.mPromiseHandler.reject(e);
                } else {
                    promise.mPromiseHandler.rawReject(e);
                }
            }
        };
    }

    /**
     * 异常处理回调
     * @param onRejected
     * @param <R>
     * @return
     */
    public <R> Promise<R> exception(Action<Throwable> onRejected){
        return then(null, onRejected);
    }

    /**
     * 毁约,也即结束整个promise
     */
    public void broke(){
        mBroken.broke();
    }

    public String getName(){
        return mName;
    }

    public void setName(String name){
        mName = name;
    }

    private String getStateName(int state){
        switch (state){
            case STATE_PENDING:
                return "Pending";
            case STATE_RESOLVED:
                return "Resolved";
            case STATE_REJECTED:
                return "Rejected";
            default:
                return "";
        }
    }

    synchronized void setState(int state){

        if(mState != STATE_PENDING){
            throw new IllegalStateException(String.format("Promise[%s]状态变化:%s=>%s, 状态变化出现异常! " +
                    "Promise状态只能从Pending到Resolved,或者从Pending到Rejected",
                    this.mName, getStateName(mState), getStateName(state)));
        }
        mState = state;
    }

    synchronized int getState(){
        return mState;
    }

    private interface Broken{
        boolean isBroken();
        void broke();
    }

    private static class DefaultBroken implements Broken{
        private boolean mIsBroken = false;

        @Override
        public boolean isBroken() {
            return mIsBroken;
        }

        @Override
        public void broke() {
            mIsBroken = true;
        }
    }

    private static class InternalPromiseHandler<T> implements PromiseHandler<T> {
        private Promise<T> mPromise;
        private Function<T, Void> mOnResolved;
        private Action<Throwable> mOnRejected;
        private T mValue;
        private Throwable mThrowable;
        private Executor mExecutor;

        public InternalPromiseHandler(Promise<T> promise, Executor executor) {
            mPromise = promise;
            mExecutor = executor;
        }

        @Override
        public void resolve(final T t) {
            if (t instanceof Promise) {
                throw new IllegalArgumentException("参数类型不能是Promise, 请使用方法resolvePromise()");
            }

            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (mPromise) {
                        rawResolve(t);
                    }
                }
            });
        }

        @Override
        public void resolvePromise(Promise<T> promise) {
            promise.then(new Function<T, Void>() {
                @Override
                public Void call(T t) {
                    if (t instanceof Promise) {
                        resolvePromise((Promise) t);
                    } else {
                        resolve(t);
                    }
                    return null;
                }
            }).exception(new Action<Throwable>() {
                @Override
                public void call(Throwable throwable) {
                    reject(throwable);
                }
            });
        }

        @Override
        public void reject(final Throwable e) {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (mPromise) {
                        rawReject(e);
                    }
                }
            });
        }

        public void rawResolve(final T t){
            assert mPromise != null;

            mPromise.setState(STATE_RESOLVED);
            mValue = t;
            doCallback();
        }

        public void rawReject(final Throwable e){
            assert mPromise != null;

            mPromise.setState(STATE_REJECTED);
            mThrowable = e;
            doCallback();
        }

        public void setCallback(final Function<T, Void> onResolved, final Action<Throwable> onRejected) {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    synchronized (mPromise) {
                        mOnResolved = onResolved;
                        mOnRejected = onRejected;
                        doCallback();
                    }
                }
            });
        }

        private void doCallback() {
            if (mPromise.getState() == STATE_PENDING) return;

            if (mPromise.getState() == STATE_RESOLVED) {
                if (mOnResolved != null) {
                    mOnResolved.call(mValue);
                }
            } else if (mPromise.getState() == STATE_REJECTED) {
                if (mOnRejected != null) {
                    mOnRejected.call(mThrowable);
                }
            }
        }
    }
}
