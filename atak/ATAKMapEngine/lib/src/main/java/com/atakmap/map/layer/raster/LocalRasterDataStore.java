package com.atakmap.map.layer.raster;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.database.CursorIface;
import com.atakmap.database.CursorWrapper;
import com.atakmap.spi.InteractiveServiceProvider;

/**
 * A {@link RasterDataStore} on the local file system. If modifiable, supports
 * add, remove and update operations making use of {@link DatasetDescriptorFactory}.
 * Implementations are responsible for the data structure managing the datasets.
 * 
 * @author Developer
 */
public abstract class LocalRasterDataStore extends AbstractRasterDataStore implements RasterDataStore {

    protected final File workingDir; 

    protected boolean inBatch;
    protected boolean batchDispatchNotify;

    private Set<File> ingesting;

    /**
     * Creates a new instance. The specified working directory will contain
     * the working directories for all layers created and managed by the data
     * store. Consistent with the general contract for {@link DatasetDescriptor}, any
     * content created in the working directory must be guaranteed to persist
     * so long as the associated {@link DatasetDescriptor} object persists.
     * 
     * @param workingDir    The working directory
     */
    protected LocalRasterDataStore(File workingDir) {
        this.workingDir = workingDir;
        
        this.ingesting = new HashSet<File>();
    }

    /**
     * Begins a batch operation on the data store. Dispatch of content changed
     * notifications will be deferred until the batch is signaled to be complete
     * via {@link #endBatch()}.
     */
    public synchronized void beginBatch() {
        this.inBatch = true;
        this.batchDispatchNotify = false;
    }

    /**
     * Ends a batch operation on the data store. Dispatch of content changed
     * notifications will occur if the content in the data store has changed
     * since the previous invocation of {@link #beginBatch()}.
     */
    public synchronized void endBatch() {
        this.inBatch = false;
        if(this.batchDispatchNotify) {
            this.dispatchDataStoreContentChangedNoSync();
            this.batchDispatchNotify = false;
        }
    }
    /**
     * Returns <code>true</code> if the layers for the specified file are in the
     * data store, <code>false</code> otherwise.
     * 
     * @param file  A file
     * 
     * @return  <code>true</code> if the layers for the specified file are in
     *          the data store, <code>false</code> otherwise.
     */
    public synchronized boolean contains(File file) {
        return (this.ingesting.contains(file) || this.containsImpl(file));
    }

    /**
     * Implements {@link #contains(File)}. This method should always be
     * externally synchronized on <code>this</code>.
     * 
     * @param file  A file
     * 
     * @return  <code>true</code> if the layers for the specified file are in
     *          the data store, <code>false</code> otherwise.
     */
    protected abstract boolean containsImpl(File file);

    /**
     * Returns the file associated with the specified layer in the data store.
     * 
     * @param info  A layer descriptor
     * 
     * @return  The file associated with the specified layer or
     *          <code>null</code> if the data store does not contain the layer. 
     */
    public synchronized File getFile(DatasetDescriptor info) {
        return this.getFileNoSync(info);
    }
    
    /**
     * Implements {@link #getFile(DatasetDescriptor)}.
     * 
     * <P>Invocation of this method should always be externally synchronized on
     * <code>this</code>.
     * 
     * @param info  A layer descriptor
     * @return  The file associated with the specified layer or
     *          <code>null</code> if the data store does not contain the layer.
     */
    protected abstract File getFileNoSync(DatasetDescriptor info);

    /**
     * Adds the layers for the specified file to the data store.
     * 
     * @param file  A file
     * 
     * @return  <code>true</code> if the layers for the file were added,
     *          <code>false</code> if no layers could be derived from the file.
     * 
     * @throws IllegalArgumentException If the data store already contains the
     *                                  layers for the specified file.
     */
    public boolean add(File file) throws IOException {
        return this.add(file, null);
    }
    
    /**
     * Adds the layers for the specified file to the data store.
     * 
     * @param file  A file
     * @param hint  The name of the preferred provider to create the layers, if
     *              <code>null</code> any compatible provider will be used.
     * 
     * @return  <code>true</code> if the layers for the file were added,
     *          <code>false</code> if no layers could be derived from the file.
     * 
     * @throws IllegalArgumentException If the data store already contains the
     *                                  layers for the specified file.
     */
    public boolean add(File file, String hint) throws IOException {
        return this.addNoSync(file, hint, null, true);
    }
    
    public boolean add(File file, String hint, InteractiveServiceProvider.Callback callback) throws IOException {
        return this.addNoSync(file, hint, callback, true);
    }
    
    /**
     * Implementation for {@link #add(File, String)}. Returns <code>true</code>
     * on success.
     * 
     * <P>Modification of the data store is internally synchronized on
     * <code>this</code>. It is recommended not to invoke this method when
     * externally synchronized on <code>this</code> as creation of the dataset
     * descriptors may take significant time.
     * 
     * @param file      A file
     * @param hint      The provider hint
     * @param notify    If <code>true</code>,
     *                  {@link #dispatchDataStoreContentChangedNoSync()} will be
     *                  invoked prior to this method returning successfully
     *
     * @return  <code>true</code> if the layers for the file were added,
     *          <code>false</code> if no layers could be derived from the file.
     *          
     * @throws IOException
     */
    protected boolean addNoSync(File file, String hint, InteractiveServiceProvider.Callback callback, boolean notify) throws IOException {
        if(!this.isModifiable())
            throw new UnsupportedOperationException();

        synchronized(this) {
            if(!this.ingesting.add(file))
                return true;
        }

        File layerDir = null;
        boolean success = false;
        try {
            layerDir = FileSystemUtils.createTempDir("layer", "priv", this.workingDir);
            Set<DatasetDescriptor> layers = DatasetDescriptorFactory2.create(file, layerDir, hint, callback);
            if(layers == null || layers.size() < 1)
                return false;

            synchronized(this) {
                if(this.containsImpl(file))
                    return true;
                this.addImpl(file, layers, layerDir);
                success = true;
                if(notify)
                    this.dispatchDataStoreContentChangedNoSync();
            }
            return true;
        } finally {
            if(!success && layerDir != null)
                FileSystemUtils.delete(layerDir);
            synchronized(this) {
                this.ingesting.remove(file);
            }
        }
    }
    
    /**
     * Adds the parsed layers to the underlying storage.
     *
     * <P>Invocation of this method should always be externally synchronized on
     * <code>this</code>.
     * 
     * @param file      The file that the layers were derived from
     * @param layers    The layers to be added to the storage
     */
    protected abstract void addImpl(File file, Set<DatasetDescriptor> layers, File layerDir) throws IOException;
    
    /**
     * Removes all layers derived from the specified file from the data store. 
     */
    public synchronized void remove(File file) {
        this.removeNoSync(file, true);
    }
    
    /**
     * Removes the specified layer from the data store. Note that any other
     * layers associated with the file that the specified layer was derived from
     * will also be removed.
     * 
     * @param info  The layer to remove.
     */
    public synchronized void remove(DatasetDescriptor info) {
        final File toRemove = this.getFile(info);
        if(toRemove != null)
            this.removeNoSync(toRemove, true);
    }

    /**
     * Removes the layers derived from the specified file from the data store.
     * 
     * <P>Invocation of this method should always be externally synchronized on
     * <code>this</code>.
     * 
     * @param file      The file
     * @param notify    If <code>true</code>,
     *                  {@link #dispatchDataStoreContentChangedNoSync()} will be
     *                  invoked prior to this method returning successfully
     */
    protected void removeNoSync(File file, boolean notify) {
        if(!this.isModifiable())
            throw new UnsupportedOperationException();
        if(!this.containsImpl(file))
            return;
        this.removeImpl(file);
        if(notify)
            this.dispatchDataStoreContentChangedNoSync();
    }
    
    /**
     * Implements {@link #removeNoSync(File, boolean)}.
     * 
     * <P>Invocation of this method should always be externally synchronized on
     * <code>this</code>.
     * 
     * @param file  The file
     */
    protected abstract void removeImpl(File file);
    
    /**
     * Updates the layers derived from the specified file.
     * 
     * @param file  The file
     * 
     * @return  <code>true</code> if the layers were successfully updated,
     *          <code>false</code> otherwise
     *          
     * @throws IOException
     */
    public synchronized boolean update(File file) throws IOException {
        if(!this.containsImpl(file))
            return false;
        return this.updateImpl(file);
    }
    
    /**
     * Updates the specified layer. Note that if other layers were derived from
     * the same file as the specified layer that they will be updated as well.
     * 
     * @param info  The layer descriptor
     * 
     * @return  <code>true</code> if the layers were successfully updated,
     *          <code>false</code> otherwise
     *          
     * @throws IOException
     */
    public synchronized boolean update(DatasetDescriptor info) throws IOException {
        final File toUpdate = this.getFile(info);
        if(toUpdate == null)
            return false;
        return this.updateImpl(toUpdate);
    }
    
    protected boolean updateImpl(File file) throws IOException {
        this.removeNoSync(file, false);
        return this.addNoSync(file, null, null, true);
    }

    /**
     * Update extra metadata for the specified layer.
     * By default this will perform a standard full update. Sub-classes are
     * expected to perform a more optimized update.
     *
     * @param info Layer descriptor
     * @return  <code>true</code> if the layers were successfully updated,
     *          <code>false</code> otherwise
     * @throws Exception Database error
     */
    public boolean updateExtraData(DatasetDescriptor info) throws Exception {
        return update(info);
    }
    
    /**
     * Removes all layers from this data store.
     */
    public synchronized void clear() {
        if(!this.isModifiable())
            throw new UnsupportedOperationException();
        
        this.clearImpl();
        
        this.dispatchDataStoreContentChangedNoSync();
    }

    /**
     * Implements {@link #clear()}. Removes all layers from this data store.
     * 
     * <P>Invocation of this method should always be externally synchronized on
     * <code>this</code>.
     */
    protected abstract void clearImpl();
    
    /**
     * Returns a flag indicating whether or not the data store is modifiable.
     * 
     * @return  <code>true</code> if the data store is modifiable,
     *          <code>false</code> otherwise.
     */
    protected abstract boolean isModifiable();

    /**
     * Returns all of the files with content currently managed by the data
     * store.
     * 
     * @return  The files with content currently managed by the data store.
     */
    public abstract FileCursor queryFiles();

    /**************************************************************************/
    // Abstract Raster Data Store
/*    
    @Override
    protected void dispatchDataStoreContentChangedNoSync() {
        if(!this.inBatch)
            super.dispatchDataStoreContentChangedNoSync();
        else
            this.batchDispatchNotify = true;
    }
*/
    /**************************************************************************/

    public static abstract class FileCursor extends CursorWrapper {

        protected FileCursor(CursorIface filter) {
            super(filter);
        }
        
        public abstract File getFile();
    }
}
