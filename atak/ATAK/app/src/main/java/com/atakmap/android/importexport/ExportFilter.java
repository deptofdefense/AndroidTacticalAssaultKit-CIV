
package com.atakmap.android.importexport;

import com.atakmap.android.maps.MapItem;

/**
 * Provide a late binding filter capability e.g. to filter items within a 
 * <code>MapOverlay</code> when the entire MapOverlay was selected for export
 * 
 * 
 */
public interface ExportFilter {
    boolean filter(MapItem item);
}
