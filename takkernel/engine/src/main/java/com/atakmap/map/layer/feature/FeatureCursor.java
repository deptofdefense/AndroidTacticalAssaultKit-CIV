package com.atakmap.map.layer.feature;

import com.atakmap.database.RowIterator;

/**
 * {@link CursorWrapper} subclass that provides direct access to the
 * {@link Feature} object described by the results.
 * 
 * @author Developer
 */
public interface FeatureCursor extends RowIterator, FeatureDefinition {
    
    public final static FeatureCursor EMPTY = new FeatureCursor() {
        @Override
        public boolean moveToNext() { return false; }
        @Override
        public void close() {}
        @Override
        public boolean isClosed() { return false; }
        @Override
        public Object getRawGeometry() { return null; }
        @Override
        public int getGeomCoding() { return 0; } 
        @Override
        public String getName() { return null; }
        @Override
        public int getStyleCoding() { return 0; }
        @Override
        public Object getRawStyle() { return null; }
        @Override
        public AttributeSet getAttributes() { return null; }
        @Override
        public Feature get() { return null; }
        @Override
        public long getId() { return 0; }
        @Override
        public long getVersion() { return 0; }
        @Override
        public long getFsid() { return 0; }
    };

    /**
     * Returns the {@link Feature} corresponding to the current row.
     * 
     * @return  The {@link Feature} corresponding to the current row.
     */
    @Override
    public abstract Feature get();
    
    public abstract long getId();
    public abstract long getVersion();
    public abstract long getFsid();

} // FeatureCursor
