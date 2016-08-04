package com.hesc.promise;

/**
 * Created by hesc on 16/6/14.
 * <p>promise执行处理接口</p>
 */
public interface PromiseHandler<T> {
    /**
     * 执行该方法,把promise变为resolved状态
     * @param t
     */
    void resolve(T t);

    /**
     * 执行该方法,把promise变为resolved状态
     * @param promise
     */
    void resolvePromise(Promise<T> promise);

    /**
     * 执行该方法,把promise变为rejected状态
     * @param e
     */
    void reject(Throwable e);
}
