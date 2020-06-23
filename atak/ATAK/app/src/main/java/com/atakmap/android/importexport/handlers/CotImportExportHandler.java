
package com.atakmap.android.importexport.handlers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.os.Bundle;
import android.os.Handler;

import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.comms.CotDispatcher;
import com.atakmap.comms.DispatchFlags;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.comms.CotServiceRemote.ConnectionListener;
import com.atakmap.comms.CotServiceRemote.CotEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Bridges the CoT event bus (events from StateSaver, CoT events coming in over the network, etc)
 * with the import/export handlers managed by ImportExportComponent and ITEM_PERSIST MapEvents.
 */
public class CotImportExportHandler implements ConnectionListener,
        CotEventListener,
        MapEventDispatcher.MapEventDispatchListener {

    public static final String TAG = "CotImportExportHandler";

    private final CotServiceRemote _cotService = new CotServiceRemote();
    private final CotDispatcher disp_ = new CotDispatcher();

    private final Map<String, MapGroup> groupMap = new HashMap<>();
    private final MapView mapView;
    private final Context context;

    // Used to add MapItems to MapGroups in the GUI thread.
    private final Handler handler;

    public CotImportExportHandler(MapView mapView, Handler handler) {
        this.mapView = mapView;
        this.context = mapView.getContext();

        this.handler = handler; // not sure what thread cot stuff runs in, might not need a
                                // handler...? probably does though, but we could create it here

        onCreateConnections(context, _cotService);
        mapView.getMapEventDispatcher().addMapEventListener(this);

        // initialize a way to dynamically specify which cot types belong to what groups
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction("com.atakmap.android.statesaver.ADD_CLASSIFICATION");
        AtakBroadcast.getInstance().registerReceiver(
                classificationBroadcastReceiver, filter);
    }

    public void shutdown() {
        AtakBroadcast.getInstance().unregisterReceiver(
                classificationBroadcastReceiver);
        _cotService.disconnect();
    }

    protected void onCreateConnections(Context context,
            CotServiceRemote cotService) {
        cotService.setCotEventListener(this);
        cotService.connect(this);

        for (MapGroup mg : groupMap.values())
            mg.clearGroups();
        groupMap.clear();
    }

    @Override
    public void onCotServiceConnected(Bundle fullServiceState) {
        Bundle meta = new Bundle();
        meta.putString("description", "route_management");
        meta.putString("management", "internal");
        _cotService.addInput("0.0.0.0:8087:udp", meta);

        disp_.setDispatchFlags(DispatchFlags.INTERNAL);

    }

    @Override
    public void onCotServiceDisconnected() {
    }

    @Override
    public void onCotEvent(final CotEvent event, Bundle extra) {
    }

    @Override
    public void onMapEvent(MapEvent event) {
        switch (event.getType()) {
            case MapEvent.ITEM_SHARED:
                // dispatch CoT for this item if it's exportable to CoT!
                dispatchCot(event);
                break;
            case MapEvent.ITEM_REMOVED:
                // StateSaver handles this case for deleting CoT from filesystem; we don't need to do
                // anything.

                // If this item is in a shared survey though, we need to tell collaborators that it's
                // been deleted
                break;
        }

    }

    private void dispatchCot(MapEvent event) {
        MapItem item = event.getItem();
        if (item instanceof Exportable) {
            Exportable exportable = (Exportable) item;

            if (exportable.isSupported(CotEvent.class)) {

                int dispatch = DispatchFlags.INTERNAL;

                if (event.getExtras() != null
                        && !event.getExtras().getBoolean("internal", false)) {
                    dispatch = DispatchFlags.INTERNAL | DispatchFlags.EXTERNAL;
                }

                CotEvent itemEvent = CotEventFactory.createCotEvent(item);
                if (itemEvent == null) {
                    return; // Item didn't want to be saved!
                }

                disp_.setDispatchFlags(dispatch);

                disp_.dispatch(itemEvent, event.getExtras());

            }
        }
    }

    public void addMapItemsToGroup(MapView mapView, MapItem[] items,
            MapGroup group) {
        // synchronized(grp){
        for (MapItem item : items) {
            group.addItem(item);
            item.refresh(mapView.getMapEventDispatcher(), null,
                    this.getClass());
        }
        // }
    }

    /**
     * Listens for new CoT types that components wish to be saved / reloaded.
     */
    private final BroadcastReceiver classificationBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String cotType = intent.getStringExtra("cotType");
            String groupName = intent.getStringExtra("groupName");

            final MapGroup rootGroup = mapView.getRootGroup();
            if (rootGroup == null)
                return;

            MapGroup group = rootGroup.findMapGroup(groupName);
            if (group == null) {
                group = new DefaultMapGroup(groupName);
                mapView.getMapOverlayManager().addOtherOverlay(
                        new DefaultMapGroupOverlay(mapView, group));
            }

            groupMap.put(cotType, group);
        }
    };

    /**
     * Register an type to be saved by StateSaver. Convenience method for the broadcast to send.
     * 
     * @param context the context to use
     * @param cotType the CoT type to register with the state saver
     * @param folderName the folder name to use when saving.
     */
    public static void registerWithStateSaver(Context context,
            String cotType, String folderName, String groupName,
            int queryOrder) {
        Intent intent = new Intent();
        intent.setAction("com.atakmap.android.statesaver.ADD_CLASSIFICATION");
        intent.putExtra("cotType", cotType);
        intent.putExtra("folderName", folderName);
        intent.putExtra("groupName", groupName);
        intent.putExtra("queryOrder", queryOrder);
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }

}
