package com.atakmap.map.elevation;

import java.util.Collection;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public final class ElevationSourceManager {
    @DontObfuscate
    public static interface OnSourcesChangedListener {
        public void onSourceAttached(ElevationSource src);
        public void onSourceDetached(ElevationSource src);
    }

    private ElevationSourceManager() {}

    public static native void addOnSourcesChangedListener(OnSourcesChangedListener listener);
    public static native void removeOnSourcesChangedListener(OnSourcesChangedListener listener);
    public static native void attach(ElevationSource source);
    public static native void detach(ElevationSource source);
    public static native ElevationSource findSource(String name);
    public static native void getSources(Collection<ElevationSource> s);
}
