
package com.atakmap.content;

import java.util.HashMap;
import java.util.Map;

public final class CatalogCurrencyRegistry {
    private Map<String, CatalogCurrency> registeredInstances;

    public CatalogCurrencyRegistry() {
        this.registeredInstances = new HashMap<>();
    }

    public synchronized void register(CatalogCurrency instance) {
        this.registeredInstances.put(instance.getName(), instance);
    }

    public synchronized void deregister(CatalogCurrency instance) {
        this.registeredInstances.values().remove(instance);
    }

    public synchronized void deregister(String name) {
        this.registeredInstances.remove(name);
    }

    public synchronized CatalogCurrency get(String name) {
        return this.registeredInstances.get(name);
    }
}
