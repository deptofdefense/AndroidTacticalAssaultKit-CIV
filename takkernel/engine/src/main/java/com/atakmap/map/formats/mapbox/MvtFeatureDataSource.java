package com.atakmap.map.formats.mapbox;

import com.atakmap.interop.Pointer;
import com.atakmap.map.layer.feature.NativeFeatureDataSource;

public final class MvtFeatureDataSource extends NativeFeatureDataSource {

    public MvtFeatureDataSource() {
        super(create());
    }

    static native Pointer create();
}

