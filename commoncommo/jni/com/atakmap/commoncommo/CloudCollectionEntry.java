package com.atakmap.commoncommo;

/**
 * An entry in a cloud collection listing.
 *
 */
public class CloudCollectionEntry {
    /**
     * The type of entry
     */
    public enum Type {
        /**
         * A remote file resource
         */
        FILE(0),
        /**
         * A remote collection (folder)
         */
        COLLECTION(1);

        private final int id;
        
        private Type(int id) {
            this.id = id;
        }
        
        int getNativeVal() {
            return id;
        }
    }
    
    /**
     * The full path to the listed entry, from the
     * root of the cloud resource.  It will be URL encoded if needed.
     */
    public final String path;
    /**
     * The type of the listed entry
     */
    public final Type type;
    /**
     * The size of the resource, in bytes, if known.
     * Only valid if > 0, negative if unknown
     */
    public final long size;
    
    
    CloudCollectionEntry(Type t, String path, long size) {
        this.path = path;
        type = t;
        this.size = size;
    }

}
