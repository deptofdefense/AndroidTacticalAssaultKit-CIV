package gov.tak.platform.utils;

import com.atakmap.util.Collections2;
import com.atakmap.util.TransmuteIterator;
import com.atakmap.util.Visitor;

import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

class ServiceProviderRegistryBase<T, V, S extends ServiceProvider<T, V>, U> {

    protected Map<U, Collection<SpiWrapper<T, V, S, U>>> providers;
    protected Map<S, SpiWrapper<T, V, S, U>> registered;
    
    private int insert;
    
    private final boolean allowDuplicateStrategies;

    ServiceProviderRegistryBase() {
        this(false);
    }
    
    ServiceProviderRegistryBase(boolean allowDuplicateStrategies) {
        this.providers = new HashMap<U, Collection<SpiWrapper<T, V, S, U>>>(); 
        this.registered = new IdentityHashMap<S, SpiWrapper<T, V, S, U>>();
        this.allowDuplicateStrategies = allowDuplicateStrategies;
    }
    
    protected T create(V object) {
        return this.create(object, null);
    }

    protected synchronized T create(V object, U strategy) {
        Collection<SpiWrapper<T, V, S, U>> providers = this.providers.get(strategy);
        if(providers == null)
            return null;
        return this.createNoSync(object, providers, new DefaultCreate<T, V, S, U>());
    }

    private T createNoSync(V object, Collection<SpiWrapper<T, V, S, U>> providers, CreateImpl<T, V, S, U> fn) {
        T retval = null;
        for(SpiWrapper<T, V, S, U> wrapper : providers) {
            retval = fn.create(wrapper.spi, object);
            if(retval != null)
                break;

            // XXX - 
            //if(callback != null && callback.isCanceled())
            //    break;
        }
        return retval;
    }

    protected static <T, V, S extends InteractiveServiceProvider<T, V>, U> T createInteractive(ServiceProviderRegistryBase<T, V, S, U> registry, V object, InteractiveServiceProvider.Callback callback) {
        return createInteractive(registry, object, null, callback);
    }

    protected static <T, V, S extends InteractiveServiceProvider<T, V>, U> T createInteractive(ServiceProviderRegistryBase<T, V, S, U> registry, V object, U strategy, InteractiveServiceProvider.Callback callback) {
        synchronized(registry) {
            Collection<SpiWrapper<T, V, S, U>> providers = registry.providers.get(strategy);
            if(providers == null)
                return null;
            return registry.createNoSync(object, providers, new InteractiveCreate<T, V, S, U>(callback));
        }
    }
    
    protected static <T, V, S extends InteractiveServiceProvider<T, V>, U> boolean isSupported(ServiceProviderRegistryBase<T, V, S, U> registry, V object, U hint, int probeLimit) {
        ProbeOnlyCallback probe = new ProbeOnlyCallback(probeLimit);
        synchronized(registry) {
            Collection<SpiWrapper<T, V, S, U>> providers = registry.providers.get(hint);
            if(providers == null)
                return false;
            for(SpiWrapper<T, V, S, U> wrapper : providers) {
                wrapper.spi.create(object, probe);
                if(probe.probeSuccess)
                    break;
            }
        }
        return probe.probeSuccess;
    }
    
    protected void register(S provider) {
        this.register(provider, null, false, 0);
    }
    
    protected void register(S provider, int priority) {
        this.register(provider, null, false, priority);
    }
    
    protected void register(S provider, U strategy, boolean strategyOnly) {
        this.register(provider, strategy, false, 0);
    }

    protected synchronized void register(S provider, U strategy, boolean strategyOnly, int priority) {
        if(this.registered.containsKey(provider))
            return;
        SpiWrapper<T, V, S, U> wrap = new SpiWrapper<T, V, S, U>(provider, strategy, priority, this.insert++);
        this.registerImpl(wrap, strategy, strategyOnly);
    }
    
    private void registerImpl(SpiWrapper<T, V, S, U> wrap, U strategy, boolean strategyOnly) {
        this.registered.put(wrap.spi, wrap);
        do {
            Collection<SpiWrapper<T, V, S, U>> providers = this.providers.get(strategy);
            if(providers == null)
               this.providers.put(strategy, providers=new TreeSet<SpiWrapper<T, V, S, U>>());
            if(!this.allowDuplicateStrategies && strategy != null)
                providers.clear();
            providers.add(wrap);
            if(strategy == null || strategyOnly)
                break;
            // if a strategy was specified, reset and insert into global list
            strategy = null;
        } while(true);
    }
    
    public final synchronized void unregister(S provider) {
        SpiWrapper<T, V, S, U> wrap = this.registered.remove(provider);
        if(wrap != null) {
            U strategy = wrap.strategy;
            do {
                Collection<SpiWrapper<T, V, S, U>> providers = this.providers.get(strategy);
                if(providers != null) {
                    providers.remove(wrap);
                    if(providers.isEmpty())
                        this.providers.remove(strategy);
                }
                if(strategy == null)
                    break;
                strategy = null;
            } while(true);
        }
    }
    public void visitProviders(Visitor<Iterator<S>> visitor) {
        this.visitProviders(visitor, null);
    }
    
    protected synchronized void visitProviders(Visitor<Iterator<S>> visitor, U hint) {
        Collection<SpiWrapper<T, V, S, U>> providers = this.providers.get(hint);
        if(providers != null)
            visitor.visit(new TransmuteIterator<SpiWrapper<T, V, S, U>, S>(providers.iterator()) {
                @Override
                protected S transmute(SpiWrapper<T, V, S, U> arg) {
                    return arg.spi;
                }
            });
        else
            visitor.visit(Collections2.<S>emptyIterator());
    }

    /**************************************************************************/

    protected static class SpiWrapper<T, V, S extends ServiceProvider<T, V>, U> implements Comparable<SpiWrapper<T, V, S, U>> {
        public final S spi;
        public final U strategy;
        public final int priority;
        public final int insert;
        
        public SpiWrapper(S spi, U strategy, int priority, int insert) {
            this.spi = spi;
            this.strategy = strategy;
            this.priority = priority;
            this.insert = insert;
        }

        @Override
        public int compareTo(SpiWrapper<T, V, S, U> other) {
            int retval = (other.priority - this.priority);
            if(retval == 0)
                retval = (this.insert-other.insert);
            return retval;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SpiWrapper<?, ?, ?, ?> that = (SpiWrapper<?, ?, ?, ?>) o;

            if (priority != that.priority) return false;
            if (insert != that.insert) return false;
            if (spi != null ? !spi.equals(that.spi) : that.spi != null) return false;
            return strategy != null ? strategy.equals(that.strategy) : that.strategy == null;
        }

        @Override
        public int hashCode() {
            int result = spi != null ? spi.hashCode() : 0;
            result = 31 * result + (strategy != null ? strategy.hashCode() : 0);
            result = 31 * result + priority;
            result = 31 * result + insert;
            return result;
        }

        @Override
        public String toString() {
            return "SpiWrapper {spi=" + spi + ",strategy=" + strategy + ",priority=" + priority + ",insert=" + insert + "}";
        }
    }
    
    protected static interface CreateImpl<T, V, S extends ServiceProvider<T, V>, U> {
        public T create(S provider, V arg);
    }
    
    protected static class DefaultCreate<T, V, S extends ServiceProvider<T, V>, U> implements CreateImpl<T, V, S, U> {
        @Override
        public T create(S provider, V arg) {
            return provider.create(arg);
        }
    }
    
    protected static class InteractiveCreate<T, V, S extends InteractiveServiceProvider<T, V>, U> implements CreateImpl<T, V, S, U> {
        private final InteractiveServiceProvider.Callback callback;
        
        public InteractiveCreate(InteractiveServiceProvider.Callback callback) {
            this.callback = callback;
        }

        @Override
        public T create(S provider, V arg) {
            return provider.create(arg, this.callback);
        }
    }
    
    protected final static class ProbeOnlyCallback implements InteractiveServiceProvider.Callback {
        boolean probeSuccess;
        private final int limit;
        
        ProbeOnlyCallback(int limit) {
            this.limit = limit;
            this.probeSuccess = false;
        }
        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public boolean isProbeOnly() {
            return true;
        }

        @Override
        public int getProbeLimit() {
            return this.limit;
        }

        @Override
        public void setProbeMatch(boolean match) {
            this.probeSuccess = match;
        }

        @Override
        public void errorOccurred(String msg, Throwable t) {}

        @Override
        public void progress(int progress) {}
    }
}
