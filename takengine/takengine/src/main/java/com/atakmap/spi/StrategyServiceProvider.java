package com.atakmap.spi;

public interface StrategyServiceProvider<T, V, U> extends ServiceProvider<T, V> {

    public U getType();
}
