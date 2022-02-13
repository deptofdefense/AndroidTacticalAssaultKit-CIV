package com.atakmap.map.layer.raster.service;

import com.atakmap.map.layer.Layer2.Extension;
import com.atakmap.map.layer.raster.RasterLayer2;

public interface SelectionOptionsCallbackExtension extends Extension {
    public static interface OnSelectionOptionsChangedListener {
        public void onSelectionOptionsChanged(RasterLayer2 subject);
    }
    
    public void addOnSelectionOptionsChangedListener(OnSelectionOptionsChangedListener l);
    public void removeOnSelectionOptionsChangedListener(OnSelectionOptionsChangedListener l);
}
