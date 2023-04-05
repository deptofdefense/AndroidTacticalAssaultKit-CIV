
package com.atakmap.android.importfiles.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importfiles.resource.RemoteResource;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;

/**
 * Contains list of Remote Resources Also allows user to add a new Remote Resource
 * 
 * 
 */
public class ImportManagerDropdown extends DropDownReceiver {

    protected static final String TAG = "ImportManagerDropdown";
    public static final String ADD_RESOURCE = "com.atakmap.android.importfiles.ui.ADD_RESOURCE";
    public static final String DLOAD_RESOURCE = "com.atakmap.android.importfiles.ui.DLOAD_RESOURCE";
    public static final String UPDATE_RESOURCE = "com.atakmap.android.importfiles.ui.UPDATE_RESOURCE";

    private ImportManagerView _view;
    private ImportExportMapComponent _component;
    private final ImportManagerMapOverlay _overlay;
    private final int _notifyId = 24126; // replace any existing Resource notifications

    public ImportManagerDropdown(MapView mapView,
            ImportExportMapComponent importExportMapComponent,
            SharedPreferences prefs) {
        super(mapView);
        _component = importExportMapComponent;
        _view = new ImportManagerView(mapView);
        _overlay = ImportManagerMapOverlay.getOverlay(mapView);
    }

    @Override
    public void disposeImpl() {
        if (_view != null) {
            _view.dispose();
            _view = null;
        }
        _component = null;
    }

    @Override
    public void onReceive(Context arg0, Intent intent) {
        Log.d(TAG, "Processing action: " + intent.getAction());

        if (ADD_RESOURCE.equals(intent.getAction())) {
            String name = intent.getStringExtra("name");
            String url = intent.getStringExtra("url");
            String type = intent.getStringExtra("type");
            boolean deleteOnExit = Boolean.parseBoolean(intent
                    .getStringExtra("deleteOnExit"));
            long refreshSeconds = -1;

            try {
                refreshSeconds = Long.parseLong(intent
                        .getStringExtra("refreshSeconds"));
            } catch (NumberFormatException e) {
                Log.w(TAG, "Unable to parse ADD_RESOURCE refreshSeconds", e);
            }

            RemoteResource resource = new RemoteResource();
            resource.setName(name);
            resource.setUrl(url);
            resource.setType(type);
            resource.setDeleteOnExit(deleteOnExit);
            resource.setRefreshSeconds(refreshSeconds);

            add(resource);

        } else if (DLOAD_RESOURCE.equals(intent.getAction())) {
            RemoteResource resource = intent.getParcelableExtra("resource");
            _component.download(resource,
                    intent.getBooleanExtra("showNotifications", true));
        } else if (UPDATE_RESOURCE.equals(intent.getAction())) {
            if (_overlay != null) {
                RemoteResource resource = intent.getParcelableExtra("resource");

                // ONLY update the local path, MD5, type, etc.
                // Don't do a full replace of the resource because there's
                // probably info missing from the resource in this intent
                // TODO: Rewrite this entire system in 3.13 - it's terrible
                if (intent.getBooleanExtra("updateLocalPath", false)) {
                    for (RemoteResource r : _overlay.getResources()) {
                        if (resource != null
                                && r.getUrl().equals(resource.getUrl())) {
                            r.setLocalPath(resource.getLocalPath());
                            r.setMd5(resource.getMd5());
                            r.setType(resource.getType());
                            r.setLastRefreshed(resource.getLastRefreshed());
                            resource = r;
                            break;
                        }
                    }
                }

                _overlay.addResource(resource);
            }
        }
    }

    public void add(CotEvent event, Bundle extra) {
        // Ignore any route that's internal, it's already in the system
        String from = extra.getString("from");
        if ("internal".equals(from))
            return;

        // parse COT
        final RemoteResource resource = RemoteResource.fromCoT(event);
        if (resource == null || !resource.isValid()) {

            Log.e(TAG,
                    "Failed to handle CoT RemoteResource: "
                            + event.getUID());
            Log.e(TAG, event.toString());
            Log.e(TAG, "--------------");
            if (resource != null)
                Log.e(TAG, "" + resource.toCot("ERROR", null));
            return;
        }

        // initiate download file with auto-retry, and then send ACK
        Log.d(TAG, "New Remote Resource received: " + resource);
        getMapView().post(new Runnable() {
            @Override
            public void run() {
                add(resource);
            }
        });
    }

    void add(RemoteResource resource) {
        if (resource == null || !resource.isValid()) {
            Log.w(TAG,
                    "Discarding invalid Resource: "
                            + (resource == null ? "null"
                                    : resource.toString()));
            return;
        }

        if (_overlay != null) {
            _overlay.addResource(resource, false);

            // Bring user to Overlays > Remote Resources when notification is tapped
            ArrayList<String> overlayPaths = new ArrayList<>();
            overlayPaths.add(getMapView().getContext().getString(
                    R.string.importmgr_remote_resource_plural));
            Intent intent = new Intent(HierarchyListReceiver.MANAGE_HIERARCHY)
                    .putExtra("refresh", true)
                    .putStringArrayListExtra("list_item_paths", overlayPaths)
                    .putExtra("isRootList", true);

            // Notify user
            NotificationUtil
                    .getInstance()
                    .postNotification(
                            _notifyId,
                            resource.isKML()
                                    ? R.drawable.ic_kml_file_notification_icon
                                    : R.drawable.download_remote_file,
                            NotificationUtil.WHITE,
                            getMapView()
                                    .getContext()
                                    .getString(
                                            R.string.importmgr_remote_resource_configuration_received),
                            String.format(
                                    getMapView()
                                            .getContext()
                                            .getString(
                                                    R.string.download_available_via_import_manager),
                                    resource.getName()),
                            intent, true);
        } else {
            Log.d(TAG, "Not adding resource: " + resource);
        }
    }

    public void onShow() {
        _view.showDialog();
    }
}
