package com.hesc.promise;

/**
 * Created by hesc on 16/6/13.
 */
public interface Function<T, R> {
    R call(T t);
}
