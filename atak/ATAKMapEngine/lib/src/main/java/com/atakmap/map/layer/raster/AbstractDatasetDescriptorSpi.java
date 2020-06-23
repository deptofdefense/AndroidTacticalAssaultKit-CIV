package com.atakmap.map.layer.raster;

import java.io.File;
import java.util.Set;

import com.atakmap.spi.InteractiveServiceProvider;

public abstract class AbstractDatasetDescriptorSpi implements DatasetDescriptorSpi {

    protected final String type;
    protected final int priority;
    
    protected AbstractDatasetDescriptorSpi(String type, int priority) {
        this.type = type;
        this.priority = priority;
    }

    @Override
    public final Set<DatasetDescriptor> create(DatasetDescriptorSpiArgs object, InteractiveServiceProvider.Callback callback) {
        if(callback != null && callback.isProbeOnly()) {
            final boolean success = this.probe(object.file, callback);
            callback.setProbeMatch(success);
            return null;
        } else {
            return this.create(object.file, object.workingDir, callback);
        }
    }

    @Override
    public final Set<DatasetDescriptor> create(DatasetDescriptorSpiArgs object) {
        return this.create(object, null);
    }

    @Override
    public final int getPriority() {
        return this.priority;
    }

    @Override
    public final String getType() {
        return this.type;
    }
    
    /**
     * Returns the dataset descriptors parsed from the specified file, or
     * <code>null</code> if this spi failed to parse any datasets from the
     * specified file.
     * 
     * <P>This method should not throw.
     * 
     * @param file          The dataset file
     * @param workingDir    A working directory that will be associated with any
     *                      descriptors derived from the file and is guaranteed
     *                      to persist for the lifetime of the descriptor,
     *                      including through multiple runtimes of the end user
     *                      application if the descriptor is serialized. 
     * @param callback      A callback, may be <code>null</code>
     * 
     * @return  The parsed descriptors or <code>null</code> if this spi is
     *          unable to derive any descriptors for the specified file
     */
    protected abstract Set<DatasetDescriptor> create(File file, File workingDir, InteractiveServiceProvider.Callback callback);
    /**
     * Returns a flag indicating whether or not this spi can derive any datasets
     * from the specified file.
     * 
     * @param file      A dataset file
     * @param callback  A callback, may be <code>null</code>
     * 
     * @return  <code>true</code> if this spi can derive datasets from the
     *          specified file, <code>false</code> otherwise.
     */
    protected abstract boolean probe(File file, InteractiveServiceProvider.Callback callback);
}
