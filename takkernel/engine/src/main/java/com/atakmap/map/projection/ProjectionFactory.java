
package com.atakmap.map.projection;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import com.atakmap.interop.Pointer;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public final class ProjectionFactory {

    private final static Map<ProjectionSpi, Pointer> spiToWrapper = new IdentityHashMap<>();

    static {
        MapProjectionDisplayModel.registerModel(EquirectangularMapProjection.DISPLAY_MODEL);
        //MapProjectionDisplayModel.registerModel(WebMercatorProjection.DISPLAY_MODEL);
        MapProjectionDisplayModel.registerModel(ECEFProjection.DISPLAY_MODEL);
    }
    
    private final static Map<Integer, WeakReference<Projection>> cache = new HashMap<>();

    private ProjectionFactory() {}

    /**
     * Set whether or not the library provided SPI should be preferred over any
     * registered by the client application. The library provided SPI is likely
     * to perform significantly better than generic coordinate transformations.
     * 
     * <P>The default behavior is to prefer the library provided SPI.
     * 
     * @param v <code>true</code> to prefer the library provided SPI,
     *          <code>false</code> to prefer registered SPIs.
     */
    public static native void setPreferLibrarySpi(boolean v);

    public static Projection getProjection(int srid) {
        Projection retval = null;
        synchronized(spiToWrapper) {
            WeakReference<Projection> ref = cache.get(srid);
            do {
                if(ref != null)
                    retval = ref.get();
                if(retval != null)
                    break;
                retval = getProjectionImpl(srid);
                cache.put(srid, new WeakReference<>(retval));
            } while(false);
        }
        return retval;
    }
    
    public static void registerSpi(ProjectionSpi spi) {
        registerSpi(spi, 0);
    }

    /**
     * Register the specified SPI. SPIs registered with higher priorities are
     * evaluated before SPIs with lower priorities; SPIs with the same priority
     * are evaluated in LIFO order.
     * 
     * @param spi       The SPI
     * @param priority  The priority
     */
    public static void registerSpi(ProjectionSpi spi, int priority) {
        if(spi == null)
            throw new NullPointerException();
        synchronized(spiToWrapper) {
            if(spiToWrapper.containsKey(spi))
                return;
            cache.clear();
            Pointer wrapper = registerSpiImpl(spi, priority);
            spiToWrapper.put(spi, wrapper);
        }
    }
    
    public static void unregisterSpi(ProjectionSpi spi) {
        Pointer wrapper;
        synchronized(spiToWrapper) {
            wrapper = spiToWrapper.remove(spi);
            cache.clear();
        }
        if(wrapper == null)
            return;
        unregisterSpiImpl(wrapper);
    }

    /**************************************************************************/
    
    private static class LibraryProjectionSpi implements ProjectionSpi {
        @Override
        public Projection create(int srid) {
            if(srid == EquirectangularMapProjection.INSTANCE.getSpatialReferenceID())
                return EquirectangularMapProjection.INSTANCE;
            else if(srid == WebMercatorProjection.INSTANCE.getSpatialReferenceID())
                return WebMercatorProjection.INSTANCE;
            else if(srid == ECEFProjection.INSTANCE.getSpatialReferenceID())
                return ECEFProjection.INSTANCE;
            return null;
        }        
    }

    static native Pointer registerSpiImpl(ProjectionSpi spi, int priority);

    /**
     * Unregisters the native Spi and frees the memory associated with the pointer
     * @param pointer
     */
    static native void unregisterSpiImpl(Pointer pointer);

    static native Projection getProjectionImpl(int srid);
}
