package com.atakmap.spi;

import java.util.Iterator;

import com.atakmap.util.Visitor;

public class PrioritizedStrategyServiceProviderRegistry2<T, V, S extends ServiceProvider<T, V>, U> extends ServiceProviderRegistryBase<T, V, S, U> {

    public PrioritizedStrategyServiceProviderRegistry2() {
        this(false);
    }

    public PrioritizedStrategyServiceProviderRegistry2(boolean allowDuplicateTypeSpis) {
        super(allowDuplicateTypeSpis);
    }

    public void register(S provider, U hint, int priority) {
        this.register(provider, hint, false, priority);
    }

    @Override
    public void register(S provider, U hint, boolean strategyOnly, int priority) {
        super.register(provider, hint, strategyOnly, priority);
    }
    
    @Override
    public T create(V object) {
        return super.create(object);
    }
    
    @Override
    public T create(V object, U hint) {
        return super.create(object, hint);
    }

    @Override
    public void visitProviders(Visitor<Iterator<S>> visitor, U hint) {
        super.visitProviders(visitor, hint);
    }
}
