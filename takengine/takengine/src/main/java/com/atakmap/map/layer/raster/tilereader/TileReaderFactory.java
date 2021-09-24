package com.atakmap.map.layer.raster.tilereader;

import android.util.Pair;

import com.atakmap.spi.PrioritizedStrategyServiceProviderRegistry2;
import com.atakmap.spi.ServiceProvider;
import com.atakmap.util.Visitor;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

public final class TileReaderFactory {

    private static PrioritizedStrategyServiceProviderRegistry2<TileReader, Pair<String, Options>, Spi, String> registry = new PrioritizedStrategyServiceProviderRegistry2<>(true);
    private static Map<TileReaderSpi, Spi> spis = new IdentityHashMap<>();

    private TileReaderFactory() {}
    
    public static TileReader create(String uri) {
        return create(uri, null);
    }
    
    public static TileReader create(String uri, Options options) {

        Collection<TileReaderSpi> c;
        String hint = null;
        if(options != null)
            hint = options.preferredSpi;
        return registry.create(new Pair<String, Options>(uri, options), hint);
    }

    public static void registerSpi(TileReaderSpi spi) {
        Spi adapter;
        synchronized(spis) {
            if(spis.containsKey(spi))
                return;
            adapter = new Spi(spi);
            spis.put(spi, adapter);
        }
        registry.register(adapter, adapter.impl.getName(), (spi instanceof TileReaderSpi2) ? ((TileReaderSpi2)spi).getPriority() : 0);
    }
    
    public static void unregisterSpi(TileReaderSpi spi) {
        Spi adapter;
        synchronized(spis) {
            adapter = spis.remove(spi);
            if(adapter == null)
                return;
        }
        registry.unregister(adapter);
    }
    
    public static boolean isSupported(String uri) {
        return isSupported(uri, null);
    }
    
    public static boolean isSupported(final String uri, String hint) {
        final boolean[] retval = new boolean[1];
        registry.visitProviders(new Visitor<Iterator<Spi>>() {
            @Override
            public void visit(Iterator<Spi> object) {
                while(object.hasNext()) {
                    retval[0] = object.next().impl.isSupported(uri);
                    if(retval[0])
                        break;
                }
            }
        });
        return retval[0];
    }
    
    /**************************************************************************/
    
    public static class Options {
        public String preferredSpi;
        public int preferredTileWidth;
        public int preferredTileHeight;
        public String cacheUri;
        public TileReader.AsynchronousIO asyncIO;
        
        public Options() {
            this.preferredSpi = null;
            this.preferredTileWidth = 0;
            this.preferredTileHeight = 0;
            this.cacheUri = null;
            this.asyncIO = null;
        }
        
        public Options(Options other) {
            this.preferredSpi = other.preferredSpi;
            this.preferredTileWidth = other.preferredTileWidth;
            this.preferredTileHeight = other.preferredTileHeight;
            this.cacheUri = other.cacheUri;
            this.asyncIO = other.asyncIO;
        }
    }

    private final static class Spi implements ServiceProvider<TileReader, Pair<String, Options>> {

        final TileReaderSpi impl;

        Spi(TileReaderSpi impl) {
            this.impl = impl;
        }

        @Override
        public TileReader create(Pair<String, Options> object) {
            return this.impl.create(object.first, object.second);
        }
    }
}
