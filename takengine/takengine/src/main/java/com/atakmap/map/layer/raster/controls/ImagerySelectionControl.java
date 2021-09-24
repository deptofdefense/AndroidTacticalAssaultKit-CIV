package com.atakmap.map.layer.raster.controls;

import com.atakmap.map.MapControl;

import java.util.Set;

public interface ImagerySelectionControl extends MapControl {
    public enum Mode {
        /** Select imagery based on filter, whose minimum resolution exceeds map resolution */
        MinimumResolution,
        /** Select imagery based on filter, whose maximum resolution exceeds map resolution */
        MaximumResolution,
        /** Select imagery based on filter, completely ignoring resolution */
        IgnoreResolution,
    }

    public void setResolutionSelectMode(Mode mode);
    public Mode getResolutionSelectMode();
    public void setFilter(Set<String> filter);
}
