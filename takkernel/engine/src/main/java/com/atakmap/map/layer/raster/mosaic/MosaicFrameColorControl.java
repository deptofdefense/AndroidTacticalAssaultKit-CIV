package com.atakmap.map.layer.raster.mosaic;

import com.atakmap.map.MapControl;
import com.atakmap.util.Filter;

public interface MosaicFrameColorControl extends MapControl {
    public void addFilter(Filter<MosaicDatabase2.Frame> filter, int color);
    public void removeFilter(Filter<MosaicDatabase2.Frame> filter);
    public int getColor(MosaicDatabase2.Frame frame);
}
