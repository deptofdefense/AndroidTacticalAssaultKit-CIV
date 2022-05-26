package com.atakmap.map.layer.raster;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;

public class RuntimeLocalRasterDataStore extends LocalRasterDataStore {

    private RuntimeRasterDataStore impl;
    private Map<File, Set<DatasetDescriptor>> fileToDesc;
    private Map<DatasetDescriptor, File> descToFile;

    protected RuntimeLocalRasterDataStore(File workingDir) {
        super(workingDir);
        
        this.impl = new RuntimeRasterDataStore();
        this.fileToDesc = new HashMap<File, Set<DatasetDescriptor>>();
        this.descToFile = new IdentityHashMap<DatasetDescriptor, File>();
    }

    @Override
    public DatasetDescriptorCursor queryDatasets(DatasetQueryParameters params) {
        return this.impl.queryDatasets();
    }

    @Override
    public int queryDatasetsCount(DatasetQueryParameters params) {
        return this.impl.queryDatasetsCount(params);
    }

    @Override
    public Collection<String> getDatasetNames() {
        return this.impl.getDatasetNames();
    }

    @Override
    public Collection<String> getImageryTypes() {
        return this.impl.getImageryTypes();
    }

    @Override
    public Collection<String> getDatasetTypes() {
        return this.impl.getDatasetTypes();
    }

    @Override
    public Collection<String> getProviders() {
        return this.impl.getProviders();
    }

    @Override
    public double getMinimumResolution(String dataset, String type) {
        return this.impl.getMinimumResolution(dataset, type);
    }

    @Override
    public double getMaximumResolution(String dataset, String type) {
        return this.impl.getMaximumResolution(dataset, type);
    }

    @Override
    public void refresh() {
        // XXX - 
    }

    @Override
    public boolean isAvailable() {
        return IOProviderFactory.exists(this.workingDir);
    }

    @Override
    public void dispose() {
        FileSystemUtils.deleteDirectory(workingDir, false);
    }

    @Override
    protected boolean containsImpl(File file) {
        return this.fileToDesc.containsKey(file);
    }

    @Override
    protected File getFileNoSync(DatasetDescriptor info) {
        return this.descToFile.get(info);
    }

    @Override
    protected void addImpl(File file, Set<DatasetDescriptor> layers, File layerDir)
            throws IOException {
        
        for(DatasetDescriptor desc : layers) {
            desc = this.impl.add(desc);
            this.descToFile.put(desc,  file);
            Set<DatasetDescriptor> descs = this.fileToDesc.get(file);
            if(descs == null)
                this.fileToDesc.put(file, descs=Collections.newSetFromMap(new IdentityHashMap<DatasetDescriptor, Boolean>()));
            descs.add(desc);
        }
    }

    @Override
    protected void removeImpl(File file) {
        Set<DatasetDescriptor> descs = this.fileToDesc.remove(file);
        if(descs == null)
            return;
        
        for(DatasetDescriptor desc : descs) {
            this.descToFile.remove(desc);
            this.impl.remove(desc, true);
        }
    }

    @Override
    protected void clearImpl() {
        this.impl.clear(true);
    }

    @Override
    protected boolean isModifiable() {
        return true;
    }

    @Override
    public FileCursor queryFiles() {
        //return new FileCursorImpl((new HashSet<File>(this.fileDescIdx.keySet())).iterator());
        throw new UnsupportedOperationException();
    }

    /**************************************************************************/
}
