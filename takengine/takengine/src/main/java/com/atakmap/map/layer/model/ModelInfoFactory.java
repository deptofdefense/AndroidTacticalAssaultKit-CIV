package com.atakmap.map.layer.model;

import android.net.Uri;

import com.atakmap.spi.PrioritizedStrategyServiceProviderRegistry2;
import com.atakmap.util.ReadWriteLock;
import com.atakmap.util.Visitor;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

public final class ModelInfoFactory {
    private final static PrioritizedStrategyServiceProviderRegistry2<Set<ModelInfo>, String, ModelInfoSpi, String> registry = new PrioritizedStrategyServiceProviderRegistry2<Set<ModelInfo>, String, ModelInfoSpi, String>();
    private final static Set<Georeferencer> georeferencers = Collections.<Georeferencer>newSetFromMap(new IdentityHashMap<Georeferencer, Boolean>());

    private final static ReadWriteLock rwlock = new ReadWriteLock();
    public static Set<ModelInfo> create(String path) {
        return create(path, null);
    }

    public static Set<ModelInfo> create(String path, String hint) {
        Set<ModelInfo> retval = registry.create(path, hint);
        if(retval == null)
            return null;
        for(ModelInfo info : retval) {
            if(info.srid != -1)
                continue;
            rwlock.acquireRead();
            try {
                for(Georeferencer georef : georeferencers) {
                    if (!georef.locate(info))
                        continue;
                    if (info.srid != -1)
                        break;
                }
            } finally {
                rwlock.releaseRead();
            }
        }

        return retval;
    }

    public static boolean isSupported(Uri uri) {
        return isSupported(uri.toString());
    }

    public static boolean isSupported(String uri) {
        return isSupported(uri, null);
    }

    public static boolean isSupported(final String uri, String hint) {
        final boolean[] supported = new boolean[] { false };
        registry.visitProviders(new Visitor<Iterator<ModelInfoSpi>>() {
            @Override
            public void visit(Iterator<ModelInfoSpi> object) {
                while(object.hasNext()) {
                    if(object.next().isSupported(uri)) {
                        supported[0] = true;
                        break;
                    }
                }
            }
        }, hint);
        return supported[0];
    }

    public static void registerSpi(ModelInfoSpi spi) {
        registry.register(spi, spi.getName(), spi.getPriority());
    }

    public static void unregisterSpi(ModelInfoSpi spi) {
        registry.unregister(spi);
    }

    public static void registerGeoreferencer(Georeferencer georef) {
        rwlock.acquireWrite();
        try {
            georeferencers.add(georef);
        } finally {
            rwlock.releaseWrite();
        }
    }

    public static void unregisterGeoreferencer(Georeferencer georef) {
        rwlock.acquireWrite();
        try {
            georeferencers.remove(georef);
        } finally {
            rwlock.releaseWrite();
        }
    }
}
