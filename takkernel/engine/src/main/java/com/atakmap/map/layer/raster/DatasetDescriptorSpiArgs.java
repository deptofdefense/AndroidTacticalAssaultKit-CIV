package com.atakmap.map.layer.raster;

import java.io.File;

public final class DatasetDescriptorSpiArgs {
    public final File file;
    public final File workingDir;
    
    public DatasetDescriptorSpiArgs(File file, File workingDir) {
        this.file = file;
        this.workingDir = workingDir;
    }
    
    @Override
    public String toString() {
        return "DatasetDescriptorSpi {file=" + file.getAbsolutePath() + "}";
    }
}
