
package com.atakmap.android.drawing;

import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.os.Bundle;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.metrics.activity.MetricListActivity;

/**
 * (KYLE) I had to hack-up this thing to no end because it was extending the KmlListActivity which
 * we deprecated. It probably won't work anymore and I don't know what it was trying to do even when
 * it did work.
 */
public class KmlImportListActivity extends MetricListActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Make this work even though KmlListActivity is now disabled
        // TODO: maybe replace this with a file browser that defaults to the kml directory?
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(
                "com.atakmap.android.maps.kml.app.KML_FILE_LIST",
                "Intent to select from a kml list. Requires a String ArrayList extra for the list of kml, and an String array extra for the selected.");

        Intent intent = new Intent();
        intent.setAction("com.atakmap.android.maps.kml.KML_RQST_FILE_LIST");
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

}
