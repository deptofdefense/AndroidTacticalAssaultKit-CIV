
package com.atakmap.android.user;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;

import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class ManualPointEntryReceiver extends BroadcastReceiver {

    private final MapView mapView;

    public ManualPointEntryReceiver(MapView mapView) {
        this.mapView = mapView;
    }

    @Override
    public void onReceive(final Context ignoreCtx, final Intent intent) {
        final Context context = mapView.getContext();

        MapItem item = this.mapView.getMapItem(intent.getStringExtra("uid"));

        if (!(item instanceof PointMapItem))
            return; // Shouldn't happen!

        final PointMapItem pointItem = (PointMapItem) item;

        final GeoPointMetaData point = pointItem.getGeoPointMetaData();

        AlertDialog.Builder b = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);

        final CoordDialogView coordView = (CoordDialogView) inflater.inflate(
                R.layout.draper_coord_dialog, null);
        b.setView(coordView);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);
        coordView.setParameters(point, this.mapView.getPoint());

        // Overrides setPositive button onClick to keep the window open when the input is invalid.
        final AlertDialog locDialog = b.create();
        locDialog.show();
        locDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        GeoPointMetaData gpm = coordView.getPoint();
                        if (gpm == null)
                            return;

                        GeoPoint newPoint = gpm.get();
                        CoordDialogView.Result result = coordView.getResult();
                        if (result == CoordDialogView.Result.INVALID)
                            return;
                        if (result == CoordDialogView.Result.VALID_CHANGED) {
                            String type;
                            type = pointItem.getType();
                            switch (type) {
                                case "corner_u-d-r": {
                                    Intent intent = new Intent(
                                            "com.atakmap.android.maps.MANUAL_POINT_RECTANGLE_EDIT");
                                    intent.putExtra("type", "corner_u-d-r");
                                    intent.putExtra("uid", pointItem.getUID());
                                    intent.putExtra("lat",
                                            String.valueOf(coordView
                                                    .getPoint().get()
                                                    .getLatitude()));
                                    intent.putExtra("lon",
                                            String.valueOf(coordView
                                                    .getPoint().get()
                                                    .getLongitude()));
                                    intent.putExtra("alt",
                                            String.valueOf(coordView
                                                    .getPoint().get()
                                                    .getAltitude()));
                                    intent.putExtra("from", "manualPointEntry");
                                    AtakBroadcast.getInstance().sendBroadcast(
                                            intent);
                                    break;
                                }
                                case "side_u-d-r": {
                                    Intent intent = new Intent(
                                            "com.atakmap.android.maps.MANUAL_POINT_RECTANGLE_EDIT");
                                    intent.putExtra("type", "side_u-d-r");
                                    intent.putExtra("uid", pointItem.getUID());
                                    intent.putExtra("lat",
                                            String.valueOf(coordView
                                                    .getPoint().get()
                                                    .getLatitude()));
                                    intent.putExtra("lon",
                                            String.valueOf(coordView
                                                    .getPoint().get()
                                                    .getLongitude()));
                                    intent.putExtra("alt",
                                            String.valueOf(coordView
                                                    .getPoint().get()
                                                    .getAltitude()));
                                    intent.putExtra("from", "manualPointEntry");
                                    AtakBroadcast.getInstance().sendBroadcast(
                                            intent);
                                    break;
                                }
                                default:
                                    pointItem.setPoint(newPoint);
                                    break;
                            }
                        }
                        locDialog.dismiss();
                    }
                });
    }

    // Start the tool off with MGRS and save the latest used style as the user goes along
    // TODO persist this setting on restart?

}
