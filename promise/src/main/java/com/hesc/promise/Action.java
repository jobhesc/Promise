package com.hesc.promise;

/**
 * Created by hesc on 16/6/13.
 */
public interface Action<T> {
    void call(T t);
}
