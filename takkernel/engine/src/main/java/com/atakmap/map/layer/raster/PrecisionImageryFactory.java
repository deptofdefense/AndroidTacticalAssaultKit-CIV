package com.atakmap.map.layer.raster;

import java.util.Iterator;

import com.atakmap.spi.PrioritizedStrategyServiceProviderRegistry2;
import com.atakmap.util.Visitor;

public final class PrecisionImageryFactory {
    private final static PrioritizedStrategyServiceProviderRegistry2<PrecisionImagery, String, PrecisionImagerySpi, String> registry = new PrioritizedStrategyServiceProviderRegistry2<PrecisionImagery, String, PrecisionImagerySpi, String>();

    private PrecisionImageryFactory() {}
    
    public static void register(PrecisionImagerySpi spi) {
        registry.register(spi, spi.getType(), spi.getPriority());
    }
    
    public static void unregister(PrecisionImagerySpi spi) {
        registry.unregister(spi);
    }
    
    public static PrecisionImagery create(String uri) {
        return create(uri, null);
    }

    public static PrecisionImagery create(String uri, String hint) {
        return registry.create(uri, hint);
    }
    
    public static boolean isSupported(String uri) {
        ProbeVisitor probe = new ProbeVisitor(uri);
        registry.visitProviders(probe);
        return probe.isSupported;
    }
    
    public static boolean isSupported(String uri, String hint) {
        ProbeVisitor probe = new ProbeVisitor(uri);
        registry.visitProviders(probe, hint);
        return probe.isSupported;
    }
    
    /**************************************************************************/

    private final static class ProbeVisitor implements Visitor<Iterator<PrecisionImagerySpi>> {

        private final String uri;
        public boolean isSupported;
        
        public ProbeVisitor(String uri) {
            this.uri = uri;

            this.isSupported = false;
        }

        @Override
        public void visit(Iterator<PrecisionImagerySpi> iter) {
            while(iter.hasNext()) {
                if(iter.next().isSupported(this.uri)) {
                    this.isSupported = true;
                    break;
                }
            }
        }        
    }
}
