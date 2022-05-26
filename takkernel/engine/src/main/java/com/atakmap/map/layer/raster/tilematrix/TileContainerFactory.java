package com.atakmap.map.layer.raster.tilematrix;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Collections;
import java.util.Set;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.util.Visitor;

public final class TileContainerFactory {
    private final static Set<TileContainerSpi> spis = Collections.newSetFromMap(new IdentityHashMap<TileContainerSpi, Boolean>());

    private TileContainerFactory() {}
    
    /**
     * Opens or creates a {@link TileContainer} at the specified location that
     * will be able to 
     * 
     * @param path  The path of the target tile container
     * @param spec  Describes the layout of the target tile container, may not
     *              be <code>null</code>
     * @param hint  The desired <I>provider</I>, or <code>null</code> to select
     *              any compatible container provider
     * 
     * @return  A new {@link TileContainer} capable of storing tile data
     *          described by the specified matrix, or <code>null</code> if no
     *          such container could be created.
     */
    public static TileContainer openOrCreateCompatibleContainer(String path, TileMatrix spec, String hint) {
        if(path == null)
            return null;

        boolean create = !IOProviderFactory.exists(new File(path));
        for(TileContainerSpi spi : spis) {
            if(hint != null && !spi.getName().equals(hint))
                continue;
            if(!spi.isCompatible(spec))
                continue;
            final TileContainer retval;
            if(create)
                retval = spi.create(spec.getName(), path, spec);
            else
                retval = spi.open(path, spec, false);
            if(retval != null)
                return retval;
        }
        return null;
    }
    
    /**
     * Opens an already existing tile container at the specified location.
     * 
     * @param path      The path
     * @param readOnly  <code>true</code> to open as read-only,
     *                  <code>false</code> to allow read-write.
     * @param hint      The desired <I>provider</I>, or <code>null</code> to
     *                  select any container provider that can open the file at
     *                  specified location.
     *                  
     * @return  A new {@link TileContainer} instance providing access to the
     *          tile content at the specified location or <code>null</code> if
     *          no such container could be opened.
     */
    public synchronized static TileContainer open(String path, boolean readOnly, String hint) {
        if(path == null)
            return null;

        for(TileContainerSpi spi : spis) {
            if(hint != null && !spi.getName().equals(hint))
                continue;
            final TileContainer retval = spi.open(path, null, readOnly);
            if(retval != null)
                return retval;
        }
        return null;
    }
    
    /**
     * Registers the specified spi.
     * @param spi
     */
    public synchronized static void registerSpi(TileContainerSpi spi) {
        spis.add(spi);
    }
    
    /**
     * Unregisters the specified spi.
     * @param spi
     */
    public synchronized static void unregisterSpi(TileContainerSpi spi) {
        spis.remove(spi);
    }
    
    /**
     * Visits all registered spis.
     * 
     * @param visitor   The callback that will be invoked when visiting the
     *                  registered spis.
     */
    public synchronized static void visitSpis(Visitor<Collection<TileContainerSpi>> visitor) {
        visitor.visit(spis);
    }
    
    /**
     * Visits all registered spis compatible with the specified tile matrix.
     * 
     * @param visitor   The callback that will be invoked when visiting the
     *                  compatible registered spis.
     */
    public synchronized static void visitCompatibleSpis(Visitor<Collection<TileContainerSpi>> visitor, TileMatrix spec) {
        Collection<TileContainerSpi> compatible = new ArrayList<TileContainerSpi>();
        for(TileContainerSpi spi : spis)
            if(spi.isCompatible(spec))
                compatible.add(spi);
        visitor.visit(compatible);
    }
}
