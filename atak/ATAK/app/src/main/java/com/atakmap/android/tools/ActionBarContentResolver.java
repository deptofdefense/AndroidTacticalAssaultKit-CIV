
package com.atakmap.android.tools;

import com.atakmap.android.data.FileContentHandler;
import com.atakmap.android.data.FileContentResolver;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.tools.menu.AtakActionBarListData;
import com.atakmap.android.tools.menu.AtakActionBarMenuData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves URI based on action bar .xml files
 */
public class ActionBarContentResolver extends FileContentResolver {

    public ActionBarContentResolver() {
        super(Collections.singleton("xml"));
    }

    /**
     * Add/remove content handlers based on action bar list data
     * @param list List data
     */
    public void loadActionBars(AtakActionBarListData list) {

        MapView mv = MapView.getMapView();

        Map<String, FileContentHandler> removed;
        synchronized (this) {
            removed = new HashMap<>(_handlers);
        }

        // Add new handlers
        for (AtakActionBarMenuData data : list.getActionBars()) {
            String path = data.getFile().getAbsolutePath();
            if (removed.containsKey(path))
                removed.remove(path);
            else
                addHandler(new ActionBarContentHandler(mv, data));
        }

        // Remove handlers that no longer exist
        for (FileContentHandler h : removed.values())
            removeHandler(h.getFile());
    }
}
