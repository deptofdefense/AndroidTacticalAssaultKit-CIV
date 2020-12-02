
package com.atakmap.android.geofence.data;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.atakmap.android.geofence.component.GeoFenceReceiver;
import com.atakmap.android.importexport.AbstractCotEventImporter;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.comms.CommsMapComponent;

import java.io.IOException;
import java.io.InputStream;

/**
 * @deprecated
 */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public class GeoFenceImporter extends AbstractCotEventImporter {

    private final static String TAG = "GeoFenceImporter";

    public GeoFenceImporter(Context context) {
        super(context, GeoFenceCotEventMarshal.CONTENT_TYPE);

    }

    @Override
    public CommsMapComponent.ImportResult importData(CotEvent event,
            Bundle extra) {
        if (!event.getType().equals(GeoFence.COT_TYPE))
            return CommsMapComponent.ImportResult.FAILURE;

        // Ignore any route that's internal, it's already in the system
        if (extra.getString("from").equals("internal"))
            return CommsMapComponent.ImportResult.FAILURE;

        //simply kick the event over to GeoFenceReceiver
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(GeoFenceReceiver.ADD).putExtra("cotevent", event));

        return CommsMapComponent.ImportResult.SUCCESS;
    }

    @Override
    protected CommsMapComponent.ImportResult importNonCotData(
            InputStream source,
            String mime)
            throws IOException {
        return CommsMapComponent.ImportResult.FAILURE;
    }

}
