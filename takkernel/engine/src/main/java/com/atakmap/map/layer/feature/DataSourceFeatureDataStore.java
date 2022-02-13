package com.atakmap.map.layer.feature;

import java.io.File;
import java.io.IOException;

import com.atakmap.database.RowIterator;

public interface DataSourceFeatureDataStore extends FeatureDataStore {
    /**
     * 
     * @param file  A file
     * 
     * @return  <code>true</code> if the layers for the specified file are in
     *          the data store, <code>false</code> otherwise.
     */
    public boolean contains(File file);

    /**
     * Returns the file associated with the specified layer in the data store.
     * 
     * @param info  A layer descriptor
     * 
     * @return  The file associated with the specified layer or
     *          <code>null</code> if the data store does not contain the layer. 
     */
    public File getFile(FeatureSet info);

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
    public boolean add(File file) throws IOException;
    
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
    public boolean add(File file, String hint) throws IOException;
    
    /**
     * Removes all layers derived from the specified file from the data store. 
     */
    public void remove(File file);

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
    public boolean update(File file) throws IOException;
    
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
    public boolean update(FeatureSet featureSet) throws IOException;

    /**
     * Returns all of the files with content currently managed by the data
     * store.
     * 
     * @return  The files with content currently managed by the data store.
     */
    public abstract FileCursor queryFiles();

    /**************************************************************************/

    public static interface FileCursor extends RowIterator {
        public final static FileCursor EMPTY = new FileCursor() {
            @Override
            public File getFile() { return null; }
            @Override
            public boolean moveToNext() { return false; }
            @Override
            public void close() {}
            @Override
            public boolean isClosed() { return false; }
        };

        public File getFile();
    }
} // DataSourceFeatureDataStore
