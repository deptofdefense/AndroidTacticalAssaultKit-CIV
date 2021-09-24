package com.atakmap.spi;

import java.util.Iterator;

import com.atakmap.util.Visitor;

public class InteractivePrioritizedStrategyServiceProviderRegistry2<T, V, S extends InteractiveServiceProvider<T, V>, U> extends ServiceProviderRegistryBase<T, V, S, U> {

    public InteractivePrioritizedStrategyServiceProviderRegistry2() {
        this(false);
    }

    public InteractivePrioritizedStrategyServiceProviderRegistry2(boolean allowDuplicateTypeSpis) {
        super(allowDuplicateTypeSpis);
    }

    public void register(S provider, U strategy, int priority) {
        super.register(provider, strategy, false, priority);
    }
    
    @Override
    public void register(S provider, U strategy, boolean strategyOnly, int priority) {
        super.register(provider, strategy, strategyOnly, priority);
    }

    public T create(V object, U hint, InteractiveServiceProvider.Callback callback) {
        return super.createInteractive(this, object, hint, callback);
    }

    public boolean isSupported(V object, int limit) {
        return this.isSupported(object, null, limit);
    }

    public boolean isSupported(V object, U hint, int limit) {
        return isSupported(this, object, hint, limit);
    }

    @Override
    public void visitProviders(Visitor<Iterator<S>> visitor, U hint) {
        super.visitProviders(visitor, hint);
    }
}