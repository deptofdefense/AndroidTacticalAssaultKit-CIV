package com.atakmap.map.formats.dted;

import com.atakmap.map.elevation.ElevationSource;

public final class DtedElevationSource {
    public static native ElevationSource create(String dir);
}
