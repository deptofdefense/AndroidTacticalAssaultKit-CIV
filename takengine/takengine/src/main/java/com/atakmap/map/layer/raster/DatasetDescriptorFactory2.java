
package com.atakmap.map.layer.raster;

import java.util.Iterator;
import java.util.Set;

import com.atakmap.spi.InteractivePrioritizedStrategyServiceProviderRegistry2;
import com.atakmap.spi.InteractiveServiceProvider;
import com.atakmap.spi.StrategyOnlyServiceProvider;
import com.atakmap.util.Visitor;

/**
 * Static factory for {@link DatasetDescriptorSpi} objects.
 *  
 * @author Developer
 */
public final class DatasetDescriptorFactory2 {

    public static final String TAG = "DatasetDescriptorFactory";

    public final static int DEFAULT_PROBE = 500;
    
    private static InteractivePrioritizedStrategyServiceProviderRegistry2<Set<DatasetDescriptor>, DatasetDescriptorSpiArgs, DatasetDescriptorSpi, String> registry = new InteractivePrioritizedStrategyServiceProviderRegistry2<Set<DatasetDescriptor>, DatasetDescriptorSpiArgs, DatasetDescriptorSpi, String>(false);

    private DatasetDescriptorFactory2() {}

    public static void register(DatasetDescriptorSpi spi) {
        registry.register(spi, spi.getType(), (spi instanceof StrategyOnlyServiceProvider), spi.getPriority());
    }
    
    public static void unregister(DatasetDescriptorSpi spi) {
        registry.unregister(spi);
    }
    
    public static Set<DatasetDescriptor> create(java.io.File file, java.io.File workingDir, String hint, InteractiveServiceProvider.Callback callback) {
        return create(new DatasetDescriptorSpiArgs(file, workingDir), hint, callback);
    }
    
    public static Set<DatasetDescriptor> create(DatasetDescriptorSpiArgs args, String hint, InteractiveServiceProvider.Callback callback) {
        return registry.create(args, hint, callback);
    }

    /**
     * Checks if the specified {@link java.io.File File} may be supported by one
     * of the registered {@link DatasetDescriptorSpi} instances.
     *
     * <P>This method is equilivalent to invoking:
     * <code>isSupported(new DatasetDescriptorSpiArgs(file, null), DatasetDescriptorFactory.DEFAULT_PROBE)</code> 
     * @param file
     * @return
     */
    public static boolean isSupported(java.io.File file) {
        return registry.isSupported(new DatasetDescriptorSpiArgs(file, null), DEFAULT_PROBE);
    }

    /**
     * Returns the registered provider with the specified name.
     * 
     * @param type  The provider name
     * 
     * @return  The registered provider with the specified name or
     *          <code>null</code> if there is no registered provider with the
     *          specified name.
     */
    public static DatasetDescriptorSpi getRegisteredSpi(String type) {
        RegisteredSpiProbe probe = new RegisteredSpiProbe();
        registry.visitProviders(probe, type);
        return probe.spi;
    }
    
    /**************************************************************************/
    
    private static class RegisteredSpiProbe implements Visitor<Iterator<DatasetDescriptorSpi>> {

        public DatasetDescriptorSpi spi;

        @Override
        public void visit(Iterator<DatasetDescriptorSpi> iter) {
            this.spi = null;
            if(!iter.hasNext())
                return;
            this.spi = iter.next();
        }
    
    }
}
