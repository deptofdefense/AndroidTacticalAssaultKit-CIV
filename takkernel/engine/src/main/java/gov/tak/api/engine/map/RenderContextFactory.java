package gov.tak.api.engine.map;

import java.util.ArrayList;

public final class RenderContextFactory {
    static final ArrayList<IRenderContextSpi> registry = new ArrayList<>();

    private RenderContextFactory() {}

    public static RenderContext create(Object o) {
        if(o instanceof RenderContext)  return (RenderContext) o;
        synchronized (registry) {
            for (IRenderContextSpi renderContextSpi : registry) {

                RenderContext renderContext = renderContextSpi.create(o);
                if (renderContext != null)
                    return renderContext;
            }
            return null;
        }
    }

    public static void registerSpi(IRenderContextSpi spi) {
        synchronized (registry) {
            registry.add(spi);
        }
    }

    public static void unregisterSpi(IRenderContextSpi spi) {
        synchronized (registry) {
            registry.remove(spi);
        }
    }
}