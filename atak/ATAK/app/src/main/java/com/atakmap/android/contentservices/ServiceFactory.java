
package com.atakmap.android.contentservices;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.atakmap.spi.PrioritizedStrategyServiceProviderRegistry2;
import com.atakmap.spi.ServiceProvider;
import com.atakmap.util.Visitor;

public final class ServiceFactory {
    private final static PrioritizedStrategyServiceProviderRegistry2<ServiceListing, String, Spi, String> registry = new PrioritizedStrategyServiceProviderRegistry2<>();
    private final static Map<ServiceQuery, Spi> spis = new IdentityHashMap<>();

    private ServiceFactory() {
    }

    /**
     * Queries the content services available for a url
     * @param url the url to be used when querying for possible services
     * @return the ServiceListing that is a result of the query.
     */
    public static ServiceListing queryServices(String url) {
        return registry.create(url);
    }

    public static Set<ServiceListing> queryServices(final String url,
            final boolean firstOnly) {
        final Set<ServiceListing> result = new HashSet<>();
        registry.visitProviders(new Visitor<Iterator<Spi>>() {
            @Override
            public void visit(Iterator<Spi> iter) {
                while (iter.hasNext()) {
                    ServiceListing services = iter.next().create(url);
                    if (services != null) {
                        result.add(services);
                        if (firstOnly)
                            break;
                    }
                }
            }
        });
        return result;
    }

    public static void registerServiceQuery(ServiceQuery query) {
        Spi spi;
        synchronized (spis) {
            if (spis.containsKey(query))
                return;
            spi = new Spi(query);
        }
        registry.register(spi, spi.impl.getName(), spi.impl.getPriority());
    }

    public static void unregisterServiceQuery(ServiceQuery query) {
        Spi spi;
        synchronized (spis) {
            spi = spis.remove(query);
            if (spi == null)
                return;
        }
        registry.unregister(spi);
    }

    private static class Spi
            implements ServiceProvider<ServiceListing, String> {
        private final ServiceQuery impl;

        Spi(ServiceQuery impl) {
            this.impl = impl;
        }

        @Override
        public ServiceListing create(String url) {
            return impl.queryServices(url);
        }
    }
}
