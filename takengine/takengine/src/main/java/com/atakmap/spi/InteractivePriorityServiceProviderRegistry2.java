package com.atakmap.spi;

public class InteractivePriorityServiceProviderRegistry2<T, V, S extends InteractiveServiceProvider<T, V>> extends ServiceProviderRegistryBase<T, V, S, Boolean> {

    @Override
    public void register(S provider, int priority) {
        super.register(provider, priority);
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
    
    public boolean isSupported(V object, int limit) {
        return isSupported(this, object, null, limit);
    }
}
