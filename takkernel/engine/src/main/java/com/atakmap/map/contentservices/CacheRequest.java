package com.atakmap.map.contentservices;

import java.io.File;

import com.atakmap.map.layer.feature.geometry.Geometry;

public class CacheRequest {
    public enum CacheMode {
        Create,
        Append,
    }

    public double minResolution;
    public double maxResolution;
    public Geometry region;
    public long timespanStart;
    public long timespanEnd;
    public File cacheFile;
    public CacheMode mode;
    public boolean canceled;
    public boolean countOnly;
    public int maxThreads;
    public long expirationOffset;
    public String preferredContainerProvider;
}
