package gov.tak.platform.utils;

public final class PriorityServiceProviderRegistry<T, V, S extends ServiceProvider<T, V>> extends ServiceProviderRegistryBase<T, V, S, Boolean> {

    public PriorityServiceProviderRegistry() {
        super();
    }

    @Override
    public void register(S provider, int priority) {
        super.register(provider, priority);
    }
    
    @Override
    public T create(V object) {
        return super.create(object);
    }
}
