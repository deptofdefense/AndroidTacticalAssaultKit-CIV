
package com.atakmap.android.cot.importer;

import android.os.Bundle;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

/**
 * Importer for legacy markers that should not follow the default marker logic
 *
 * Currently a hack to allow for the Enterprise Sync tool.
 * please see the comment in StateSaverListener
 */
public class LegacyMarkerImporter extends CotEventTypeImporter {

    private static final String TAG = "LegacyMarkerImporter";

    private final MapView _mapView;

    public LegacyMarkerImporter(MapView mapView, String... types) {
        super(mapView, types);
        _mapView = mapView;
    }

    @Override
    public ImportResult importData(CotEvent event, Bundle extras) {
        String fromExtra = extras.getString("from");
        String serverFromExtra = extras.getString("serverFrom");

        Log.d(TAG, "@deprecated direct restoration of a CotEvent: "
                + event.getUID());
        Marker m = new Marker(event.getUID() + "-legacy");
        m.setType(event.getType());
        m.setMetaString("legacy_cot_event", event.toString());
        m.setMetaBoolean("nevercot", true);
        m.setMetaBoolean("virtual", true);
        m.setVisible(false);
        MapEvent.Builder b = new MapEvent.Builder(MapEvent.ITEM_ADDED);
        b.setItem(m);

        Bundle markerExtras = new Bundle();
        markerExtras.putString("from", fromExtra);
        if (!FileSystemUtils.isEmpty(serverFromExtra))
            markerExtras.putString("serverFrom", serverFromExtra);
        b.setExtras(markerExtras);

        _mapView.getMapEventDispatcher().dispatch(b.build());
        return ImportResult.SUCCESS;
    }
}
