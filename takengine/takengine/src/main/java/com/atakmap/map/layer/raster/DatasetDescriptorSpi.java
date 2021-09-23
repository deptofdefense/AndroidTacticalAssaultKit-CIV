
package com.atakmap.map.layer.raster;

import java.util.Set;

import com.atakmap.spi.InteractiveServiceProvider;
import com.atakmap.spi.PriorityServiceProvider;
import com.atakmap.spi.StrategyServiceProvider;

/**
 * Service provider interface for creating {@link DatasetDescriptor} instances. 
 * 
 * @author Developer
 * 
 * @see com.atakmap.map.layer.raster.DatasetDescriptor
 * @see com.atakmap.map.layer.raster.DatasetDescriptorFactory
 */
public interface DatasetDescriptorSpi extends InteractiveServiceProvider<Set<DatasetDescriptor>, DatasetDescriptorSpiArgs>,
                                              PriorityServiceProvider<Set<DatasetDescriptor>, DatasetDescriptorSpiArgs>,
                                              StrategyServiceProvider<Set<DatasetDescriptor>, DatasetDescriptorSpiArgs, String> {

    /**
     * Returns the parse version associated with this SPI. The parse version
     * should be an unsigned 16-bit value. The parse version should be
     * incremented every time the service provider implementation is modified
     * to produce different results.
     * 
     * <P>Version number <code>0</code> is reserved and should never be
     * returned by this method.
     * 
     * @return  The 16-bit unsigned parse version
     */
    public int parseVersion();

} // DatasetDescriptorSpi
