package com.atakmap.spi;

import java.util.Iterator;

import com.atakmap.util.Visitor;

public class StrategyServiceProviderRegistry2<T, V, U, S extends ServiceProvider<T, V>> extends ServiceProviderRegistryBase<T, V, S, U> {

    public StrategyServiceProviderRegistry2() {
        this(false);
    }

    public StrategyServiceProviderRegistry2(boolean allowDuplicateTypeSpis) {
        super(allowDuplicateTypeSpis);
    }

    public void register(S provider, U strategy) {
        super.register(provider, strategy, false);
    }
    
    @Override
    public void register(S provider, U strategy, boolean strategyOnly) {
        super.register(provider, strategy, strategyOnly);
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
