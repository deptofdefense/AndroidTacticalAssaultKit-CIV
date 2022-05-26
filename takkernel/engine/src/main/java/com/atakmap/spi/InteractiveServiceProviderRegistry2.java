package com.atakmap.spi;

public class InteractiveServiceProviderRegistry2<T, V, S extends InteractiveServiceProvider<T, V>> extends ServiceProviderRegistryBase<T, V, S, Boolean> {

    @Override
    public void register(S provider) {
        super.register(provider);
    }

    @Override
    public T create(V object) {
        return super.create(object);
    }

    public T create(V object, InteractiveServiceProvider.Callback callback) {
        if(callback == null)
            return super.create(object);
        else
            return createInteractive(this, object, callback);
    }
    
    public synchronized boolean isSupported(V object, int limit) {
        return isSupported(this, object, null, limit);
    }
}
