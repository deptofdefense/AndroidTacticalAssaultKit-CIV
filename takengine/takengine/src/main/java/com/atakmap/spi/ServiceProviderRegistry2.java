package com.atakmap.spi;

public class ServiceProviderRegistry2<T, V, S extends ServiceProvider<T, V>> extends ServiceProviderRegistryBase<T, V, S, Boolean> {

    @Override
    public T create(V object) {
        return super.create(object);
    }

    @Override
    public void register(S provider) {
        super.register(provider);
    }
}
