package com.atakmap.map.layer.feature;

import com.atakmap.database.RowIterator;

public interface FeatureSetCursor extends RowIterator {
    public final static FeatureSetCursor EMPTY = new FeatureSetCursor() {
        @Override
        public FeatureSet get() { return null; }
        @Override
        public long getId() { return FeatureDataStore2.FEATURESET_ID_NONE; }
        @Override
        public String getType() { return null; }
        @Override
        public String getProvider() { return null; }
        @Override
        public String getName() { return null; }
        @Override
        public double getMinResolution() { return Double.NaN; }
        @Override
        public double getMaxResolution() { return Double.NaN; }
        @Override
        public boolean moveToNext() { return false; }
        @Override
        public void close() { }
        @Override
        public boolean isClosed() { return false; }
    };

    public FeatureSet get();
    public long getId();
    public String getType();
    public String getProvider();
    public String getName();
    public double getMinResolution();
    public double getMaxResolution();
}
