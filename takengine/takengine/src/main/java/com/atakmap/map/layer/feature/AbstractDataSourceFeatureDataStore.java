package com.atakmap.map.layer.feature;

import java.io.File;
import java.io.IOException;

import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.style.Style;

public abstract class AbstractDataSourceFeatureDataStore extends AbstractFeatureDataStore implements DataSourceFeatureDataStore {

    private final static int VISIBILITY_FLAGS = VISIBILITY_SETTINGS_FEATURE |
                                                VISIBILITY_SETTINGS_FEATURESET;

    private final static int MODIFICATION_FLAGS = MODIFY_BULK_MODIFICATIONS |
                                                  MODIFY_FEATURESET_DELETE |
                                                  MODIFY_FEATURESET_UPDATE |
                                                      MODIFY_FEATURESET_DISPLAY_THRESHOLDS;
    

    protected AbstractDataSourceFeatureDataStore() {
        super(MODIFICATION_FLAGS, VISIBILITY_FLAGS);
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
    @Override
    public synchronized boolean contains(File file) {
        return this.containsImpl(file);
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
    @Override
    public synchronized File getFile(FeatureSet info) {
        return this.getFileNoSync(info.getId());
    }
    
    /**
     * Implements {@link #getFile(FeatureSet)}.
     * 
     * <P>Invocation of this method should always be externally synchronized on
     * <code>this</code>.
     * 
     * @param fsid  The feature set ID
     * 
     * @return  The file associated with the specified feature set or
     *          <code>null</code> if the data store does not contain the layer.
     */
    protected abstract File getFileNoSync(long fsid);

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
    @Override
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
    @Override
    public boolean add(File file, String hint) throws IOException {
        return this.addNoSync(file, hint, true);
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
     * @throws IOException when there is an error modifying the data source
     */
    protected boolean addNoSync(File file, String hint, boolean notify) throws IOException {
        FeatureDataSource.Content content = null;
        try {
            content = FeatureDataSourceContentFactory.parse(file, hint);
            //System.out.println("ADD " + file.getName() + " (" + hint + ") -> " + content);
            if(content == null)
                return false;

            synchronized(this) {
                if(this.contains(file))
                    throw new IllegalArgumentException("An entry for the specified file already exists");
                this.addImpl(file, content);
                if(notify)
                    this.dispatchDataStoreContentChangedNoSync();
            }
            return true;
        } finally {
            if(content != null)
                content.close();
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
    protected abstract void addImpl(File file, FeatureDataSource.Content content) throws IOException;
    
    /**
     * Removes all layers derived from the specified file from the data store. 
     */
    @Override
    public synchronized void remove(File file) {
        this.removeNoSync(file, true);
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
    @Override
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
    @Override
    public synchronized boolean update(FeatureSet featureSet) throws IOException {
        final File toUpdate = this.getFile(featureSet);
        if(toUpdate == null)
            return false;
        return this.updateImpl(toUpdate);
    }
    
    protected boolean updateImpl(File file) throws IOException {
        this.removeNoSync(file, false);
        
        // XXX - specify provider from existing feature sets?
        return this.addNoSync(file, null, true);
    }

    /**
     * Returns all of the files with content currently managed by the data
     * store.
     * 
     * @return  The files with content currently managed by the data store.
     */
    @Override
    public abstract FileCursor queryFiles();

    /**************************************************************************/
    // Abstract Feature Data Store

    @Override
    protected FeatureSet insertFeatureSetImpl(String provider, String type, String name, double minResolution,
            double maxResolution, boolean returnRef) {

        throw new UnsupportedOperationException();
    }


    @Override
    protected void updateFeatureSetImpl(long fsid, String name) {
        throw new UnsupportedOperationException();
    }


    @Override
    protected void updateFeatureSetImpl(long fsid, double minResolution, double maxResolution) {
        throw new UnsupportedOperationException();
    }


    @Override
    protected void updateFeatureSetImpl(long fsid, String name, double minResolution,
            double maxResolution) {
        throw new UnsupportedOperationException();
    }


    @Override
    protected void deleteFeatureSetImpl(long fsid) {
        this.remove(this.getFileNoSync(fsid));
    }


    @Override
    protected Feature insertFeatureImpl(long fsid, String name, Geometry geom, Style style,
            AttributeSet attributes, boolean returnRef) {

        throw new UnsupportedOperationException();
    }


    @Override
    protected void updateFeatureImpl(long fid, String name) {
        throw new UnsupportedOperationException();
    }


    @Override
    protected void updateFeatureImpl(long fid, Geometry geom) {
        throw new UnsupportedOperationException();
    }


    @Override
    protected void updateFeatureImpl(long fid, Style style) {
        throw new UnsupportedOperationException();
    }


    @Override
    protected void updateFeatureImpl(long fid, AttributeSet attributes) {
        throw new UnsupportedOperationException();
    }


    @Override
    protected void updateFeatureImpl(long fid, String name, Geometry geom, Style style,
            AttributeSet attributes) {

        throw new UnsupportedOperationException();
    }


    @Override
    protected void deleteFeatureImpl(long fsid) {
        throw new UnsupportedOperationException();
    }


    @Override
    protected void deleteAllFeaturesImpl(long fsid) {
        throw new UnsupportedOperationException();
    }

} // DataSourceFeatureDataStore
