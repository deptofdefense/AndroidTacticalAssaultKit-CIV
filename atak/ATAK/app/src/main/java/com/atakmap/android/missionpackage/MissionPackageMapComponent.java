
package com.atakmap.android.missionpackage;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.data.ClearContentRegistry;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.filesharing.android.service.WebServer;
import com.atakmap.android.importexport.ExporterManager;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.DocumentedExtra;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.export.MissionPackageConnectorHandler;
import com.atakmap.android.missionpackage.export.MissionPackageExportMarshal;
import com.atakmap.android.missionpackage.file.MissionPackageFileIO;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.http.datamodel.MissionPackageQueryResult;
import com.atakmap.android.missionpackage.lasso.LassoContentProvider;
import com.atakmap.android.missionpackage.lasso.LassoSelectionReceiver;
import com.atakmap.android.missionpackage.ui.MissionPackageMapOverlay;
import com.atakmap.android.missionpackage.ui.MissionPackagePreferenceFragment;
import com.atakmap.android.widgets.AbstractWidgetMapComponent;
import com.atakmap.app.R;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.commoncommo.Commo;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.CotStreamListener;
import com.atakmap.comms.app.CotPortListActivity.CotPort;
import com.atakmap.comms.CotServiceRemote.CotEventListener;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;

import java.util.ArrayList;
import java.util.List;

/**
 * File Share Tool Map Component & CoT Listener.   This provides for all
 * capabilities to send and receive files within the tak systems.   
 * 
 * 
 */
public class MissionPackageMapComponent extends AbstractWidgetMapComponent
        implements CotEventListener, MapEventDispatchListener {

    public static final String TOOL_NAME = "com.atakmap.android.missionpackage.MissionPackageMapComponent";

    protected static final String TAG = "MissionPackageMapComponent";

    private static MissionPackageMapComponent _self;

    /**
     * Drop Down Receiver
     */
    private MissionPackageReceiver _receiver;

    /**
     * Overaly Manager support
     */
    private MissionPackageMapOverlay _overlay;

    /**
     * Watch directories for file IO activity
     */
    private MissionPackageFileIO _fileIO;

    /**
     * Tool to allow user to select an item from the map view
     */
    private MapItemSelectTool _mapViewSelectTool;

    /**
     * Listen for changes to relevant user settings
     */
    private MissionPackagePreferenceListener _prefListener;
    private SharedPreferences _prefs;

    private Context _context;
    private MapView _mapView;

    private boolean _enabled; // User can enable or disable via settings
    private boolean _stillStarting;

    /**
     * CoT & server connection support
     */
    private ServerListener _serverListener;

    private BroadcastReceiver toolreceiver;
    //private BroadcastReceiver compCreatedRec;

    private MissionPackageConnectorHandler _connectorHandler;
    private LassoContentProvider _lassoProvider;
    private LassoSelectionReceiver _lassoReceiver;

    public static MissionPackageMapComponent getInstance() {
        return _self;
    }

    @Override
    protected void onCreateWidgets(Context context, Intent intent,
            MapView mapView) {
        _self = this;
        _mapView = mapView;
        _context = context;
        _stillStarting = true;

        // get prefs
        _prefs = PreferenceManager
                .getDefaultSharedPreferences(mapView.getContext());
        _enabled = _prefs.getBoolean(
                MissionPackagePreferenceListener.filesharingEnabled,
                MissionPackagePreferenceListener.filesharingEnabledDefault);
        _prefListener = new MissionPackagePreferenceListener(context, this);

        _receiver = new MissionPackageReceiver(mapView, this);
        _fileIO = new MissionPackageFileIO(_receiver, context);

        // Tell comms how to route incoming MPs
        CommsMapComponent.getInstance()
                .setMissionPackageReceiveInitiator(_receiver);

        // set up CoT sending
        _serverListener = new ServerListener(_context);

        // allow receiver to handle File Share related intents
        DocumentedIntentFilter fileShareFilter = new DocumentedIntentFilter();
        fileShareFilter.addAction(MissionPackageReceiver.MISSIONPACKAGE,
                "Intent to display the Mission Package Tool UI");
        fileShareFilter.addAction(MissionPackageReceiver.MISSIONPACKAGE_LOG,
                "Intent to display the Mission Package Tool Log UI");
        fileShareFilter.addAction(MissionPackageReceiver.MISSIONPACKGE_DETAIL,
                "Intent to display the details of a Mission Package",
                new DocumentedExtra[] {
                        new DocumentedExtra("missionPackageUID",
                                "The UID of the Mission Package to show",
                                false, String.class)
                });
        fileShareFilter.addAction(
                MissionPackageReceiver.MISSIONPACKAGE_MAPSELECT,
                "Intent to select map items via Map View",
                new DocumentedExtra[] {
                        new DocumentedExtra("MissionPackageUID",
                                "The UID of the Mission Package to add items to",
                                false, String.class),
                        new DocumentedExtra("itemUID",
                                "The UID of the single map item to add",
                                true, String.class),
                        new DocumentedExtra("itemUIDs",
                                "Array of map item UIDs to add",
                                true, String[].class)
                });
        fileShareFilter.addAction(MissionPackageReceiver.MISSIONPACKAGE_SAVE,
                "Intent to save, and optionally send, a Mission Package",
                new DocumentedExtra[] {
                        new DocumentedExtra(
                                MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST,
                                "The MP manifest to save/send",
                                false, MissionPackageManifest.class),
                        new DocumentedExtra(
                                MissionPackageApi.INTENT_EXTRA_SAVEANDSEND,
                                "True to save and send, false to save only",
                                true, Boolean.class),
                        new DocumentedExtra(
                                MissionPackageApi.INTENT_EXTRA_SENDONLY,
                                "True to send the MP without saving",
                                true, Boolean.class),
                        new DocumentedExtra(
                                MissionPackageApi.INTENT_EXTRA_SENDERCALLBACKCLASSNAME,
                                "Class name of callback to invoke when MP has been sent (class must implement SaveAndSendCallback)",
                                true, String.class),
                        new DocumentedExtra(
                                MissionPackageApi.INTENT_EXTRA_SENDERCALLBACKPACKAGENAME,
                                "The package name of the send callback class",
                                true, String.class)
                });
        fileShareFilter.addAction(MissionPackageReceiver.MISSIONPACKAGE_SEND,
                "Callback intent sent after selecting send-to contacts from list",
                new DocumentedExtra[] {
                        new DocumentedExtra(
                                MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST,
                                "The MP manifest that is being sent",
                                false, MissionPackageManifest.class),
                        new DocumentedExtra(
                                MissionPackageApi.INTENT_EXTRA_SENDERCALLBACKCLASSNAME,
                                "Class name of callback to invoke when MP has been sent (class must implement SaveAndSendCallback)",
                                true, String.class),
                        new DocumentedExtra(
                                MissionPackageApi.INTENT_EXTRA_SENDONLY,
                                "True to send the MP without saving",
                                true, Boolean.class)
                });
        fileShareFilter.addAction(MissionPackageReceiver.MISSIONPACKAGE_UPDATE,
                "Intent to save changes to an existing Mission Package (without displaying UI/list)",
                new DocumentedExtra[] {
                        new DocumentedExtra(
                                MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST_UID,
                                "The UID of the Mission Package to update",
                                false, String.class),
                        new DocumentedExtra("save",
                                "True to save the MP, false to only update",
                                true, Boolean.class),
                        new DocumentedExtra("mapitems",
                                "Array of map item UIDs to add",
                                true, String[].class),
                        new DocumentedExtra("files",
                                "Array of file paths to add",
                                true, String[].class)
                });
        fileShareFilter.addAction(MissionPackageReceiver.MISSIONPACKAGE_DELETE,
                "Intent to delete a Mission Package by path or manifest",
                new DocumentedExtra[] {
                        new DocumentedExtra(MissionPackageApi.INTENT_EXTRA_PATH,
                                "The path of the Mission Package to delete",
                                true, String.class),
                        new DocumentedExtra(
                                MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST,
                                "The UID of the Mission Package to delete",
                                true, MissionPackageManifest.class)
                });
        fileShareFilter.addAction(MissionPackageReceiver.MISSIONPACKAGE_POST,
                "Intent to send a Mission Package to TAK Server",
                new DocumentedExtra[] {
                        new DocumentedExtra("manifest",
                                "The Mission Package manifest to send",
                                false, MissionPackageManifest.class),
                        new DocumentedExtra("filepath",
                                "The path to the deployed transfer package",
                                false, String.class),
                        new DocumentedExtra("serverConnectString",
                                "The TAK server connect net connect string",
                                true, String.class)
                });
        fileShareFilter.addAction(MissionPackageReceiver.MISSIONPACKAGE_QUERY,
                "Intent to query list of Mission Packages available on TAK Server",
                new DocumentedExtra[] {
                        new DocumentedExtra("serverConnectString",
                                "The TAK server connect net connect string",
                                true, String.class),
                        new DocumentedExtra("tool",
                                "The group to query packages from - \"public\" by default",
                                true, String.class)
                });
        fileShareFilter.addAction(
                MissionPackageReceiver.MISSIONPACKAGE_DOWNLOAD,
                "Intent to download a Mission Package", new DocumentedExtra[] {
                        new DocumentedExtra("package",
                                "The query result returned by MISSIONPACKAGE_QUERY",
                                true, MissionPackageQueryResult.class),
                        new DocumentedExtra("serverConnectString",
                                "The TAK server connect net connect string",
                                true, String.class),
                });
        fileShareFilter.addAction(
                MissionPackageReceiver.MISSIONPACKAGE_REMOVE_LASSO,
                "Intent to remove contents from a Data Package using a shape",
                new DocumentedExtra[] {
                        new DocumentedExtra(
                                MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST_UID,
                                "Data package UID", false),
                        new DocumentedExtra("uid", "The UID of the shape")
                });

        AtakBroadcast.getInstance()
                .registerReceiver(_receiver, fileShareFilter);

        ClearContentRegistry.getInstance().registerListener(_receiver.ccl);

        // allow receiver to be activated when selected from Tools list
        DocumentedIntentFilter toolFilter = new DocumentedIntentFilter();
        toolFilter.addCategory("com.atakmap.android.maps.INTEGRATION");
        toolFilter.addAction("com.atakmap.android.maps.TOOLSELECTOR_READY",
                "Intent to register the Mission Package Tool UI");
        AtakBroadcast.getInstance().registerReceiver(
                toolreceiver = new BroadcastReceiver() {

                    @Override
                    public void onReceive(Context context, Intent intent) {

                        // Register menu items in the tool selector
                        if ("com.atakmap.android.maps.TOOLSELECTOR_READY"
                                .equals(intent.getAction())) {

                            // intent to run when tool is selected
                            Intent launchTool = new Intent();
                            launchTool.setAction(
                                    MissionPackageReceiver.MISSIONPACKAGE);
                            // *need* a request code, or we'll overwrite other pending intents with the same
                            // action! Hopefully hash code is unique enough?
                            PendingIntent act = PendingIntent.getBroadcast(
                                    context,
                                    this.hashCode(),
                                    launchTool, 0);

                            // register with selector
                            Intent toolSelectorRegisterIntent = new Intent();
                            toolSelectorRegisterIntent
                                    .setAction(
                                            "com.atakmap.android.maps.TOOLSELECTION_NOTIFY");
                            toolSelectorRegisterIntent
                                    .addCategory(
                                            "com.atakmap.android.maps.INTEGRATION"); // what does
                                                                                                                            // the category
                                                                                                                            // do?
                            toolSelectorRegisterIntent.putExtra(
                                    "title",
                                    context.getString(
                                            R.string.mission_package_toolname));
                            toolSelectorRegisterIntent.putExtra("action", act);
                            AtakBroadcast.getInstance().sendBroadcast(
                                    toolSelectorRegisterIntent);
                        }
                    }
                }, toolFilter);
        //Order of operations OK b/c this component starts up after CotMapComponent, per component.xml
        _connectorHandler = new MissionPackageConnectorHandler(_context);
        CotMapComponent.getInstance().getContactConnectorMgr()
                .addContactHandler(_connectorHandler);

        _mapViewSelectTool = new MapItemSelectTool(mapView);

        _overlay = new MissionPackageMapOverlay(mapView, this);
        mapView.getMapOverlayManager().addOverlay(_overlay);

        //register Overlay Manager exporter
        ExporterManager.registerExporter(
                context.getString(R.string.mission_package_name),
                R.drawable.ic_menu_missionpackage,
                MissionPackageExportMarshal.class);

        mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_ADDED, this);

        ToolsPreferenceFragment
                .register(
                        new ToolsPreferenceFragment.ToolPreference(
                                context.getString(
                                        R.string.missionpackage_control_prefs),
                                context.getString(
                                        R.string.adjust_missionpackage_settings),
                                "missionpackagePreference",
                                context.getResources().getDrawable(
                                        R.drawable.ic_menu_missionpackage),
                                new MissionPackagePreferenceFragment()));

        // Bug 1801 on some devices, the first instance of HttpClient must be created on the main
        // thread...
        new DefaultHttpClient(new BasicHttpParams());

        // Lasso tool and content provider
        _lassoProvider = new LassoContentProvider(mapView);
        _lassoReceiver = new LassoSelectionReceiver(mapView, _lassoProvider);
    }

    private void finishStartup() {
        // now that components are ready, see if file sharing should commence based on user prefs
        if (_enabled) {
            Log.d(TAG, "Enabling File Sharing during startup...");
            _enabled = false;
            if (!enable())
                _prefListener.setEnabled(false);
        } else {
            Log.d(TAG, "Skipping File Sharing during startup...");
        }

        _fileIO.enableFileWatching();
        _stillStarting = false;
    }

    public MissionPackageReceiver getReceiver() {
        return _receiver;
    }

    public MissionPackageFileIO getFileIO() {
        return _fileIO;
    }

    boolean isEnabled() {
        return _enabled;
    }

    public boolean checkFileSharingEnabled() {
        if (!isEnabled()) {
            _mapView.post(new Runnable() {
                public void run() {
                    Toast.makeText(_context,
                            R.string.mission_package_file_sharing_is_disabled,
                            Toast.LENGTH_LONG).show();
                }
            });
            return false;
        }
        return true;
    }

    /**
     * Enable File Sharing
     * @return
     */
    boolean enable() {
        if (_enabled) {
            Log.d(TAG, "File Sharing already enabled");
            return true;
        }

        Log.i(TAG, "Enabling File Sharing");
        // start web server
        boolean success = enableCommsFileSharing();
        if (!success) {
            _mapView.post(new Runnable() {
                public void run() {
                    Toast.makeText(_context,
                            R.string.mission_package_failed_to_enable_file_sharing,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
        _enabled = success;
        return success;
    }

    /**
     * Disable File Sharing
     * 
     * @param notifyUser
     */
    void disable(boolean notifyUser) {
        if (!_enabled) {
            Log.d(TAG, "File Sharing already disabled");
            return;
        }

        Log.i(TAG, "Disabling File Sharing");
        disableCommsFileSharing();

        if (notifyUser) {
            _mapView.post(new Runnable() {
                public void run() {
                    Toast.makeText(_context,
                            R.string.mission_package_file_sharing_disabled,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
        _enabled = false;
    }

    /**
     * Restart File Sharing on specified port
     * 
     * @return
     */
    boolean restart() {
        disable(false);
        return enable();
    }

    private boolean enableCommsFileSharing() {
        int unsecurePort;
        int securePort;
        boolean legacyEnabled;
        try {
            legacyEnabled = _prefs.getBoolean(
                    WebServer.SERVER_LEGACY_HTTP_ENABLED_KEY,
                    false);
        } catch (Exception e) {
            legacyEnabled = false;
        }

        if (legacyEnabled) {
            try {
                unsecurePort = Integer
                        .parseInt(_prefs.getString(WebServer.SERVER_PORT_KEY,
                                String.valueOf(WebServer.DEFAULT_SERVER_PORT)));
                if (unsecurePort < 1)
                    unsecurePort = WebServer.DEFAULT_SERVER_PORT;
            } catch (Exception e) {
                unsecurePort = WebServer.DEFAULT_SERVER_PORT;
            }
        } else {
            unsecurePort = Commo.MPIO_LOCAL_PORT_DISABLE;
        }

        try {
            securePort = Integer
                    .parseInt(_prefs.getString(WebServer.SECURE_SERVER_PORT_KEY,
                            String.valueOf(
                                    WebServer.DEFAULT_SECURE_SERVER_PORT)));
            if (securePort < 1)
                securePort = WebServer.DEFAULT_SECURE_SERVER_PORT;
        } catch (Exception e) {
            securePort = WebServer.DEFAULT_SECURE_SERVER_PORT;
        }

        return CommsMapComponent.getInstance().setMissionPackageEnabled(true,
                unsecurePort, securePort);
    }

    private void disableCommsFileSharing() {
        CommsMapComponent.getInstance().setMissionPackageEnabled(false, 0, 0);
    }

    @Override
    protected void onDestroyWidgets(Context context, MapView view) {
        disable(false);
        _fileIO.disableFileWatching();

        if (_lassoProvider != null)
            _lassoProvider.dispose();

        if (_lassoReceiver != null)
            _lassoReceiver.dispose();

        AtakBroadcast.getInstance().unregisterReceiver(toolreceiver);
        //AtakBroadcast.getInstance().unregisterReceiver(compCreatedRec);

        if (_serverListener != null) {
            _serverListener.dispose();
            _serverListener = null;
        }

        if (_overlay != null)
            _overlay.dispose();

        ClearContentRegistry.getInstance().unregisterListener(_receiver.ccl);

        if (_receiver != null) {
            AtakBroadcast.getInstance().unregisterReceiver(_receiver);
            _receiver.dispose();
            _receiver = null;
        }

        if (_prefListener != null) {
            _prefListener.dispose();
            _prefListener = null;
        }
        _prefs = null;
        _self = null;
    }

    @Override
    public void onMapEvent(final MapEvent event) {
        try {
            final String eventType = event.getType();
            final MapItem item = event.getItem();
            final Bundle extras = event.getExtras();

            if (eventType.equals(MapEvent.ITEM_ADDED)
                    && item instanceof Marker) {
                String cot = item.getMetaString("legacy_cot_event", null);
                if (cot != null) {
                    Bundle b = new Bundle();
                    if (extras != null)
                        b.putString("from", extras.getString("from"));

                    //Log.d(TAG, "Marker: " + item.getUID() + " " +
                    //           cot + " FROM: " + b.getString("from"));
                    onCotEvent(CotEvent.parse(cot), b);
                }
            }
        } catch (Exception e) {
            Log.d(TAG,
                    "error occurred attempting to process a virtual CotEvent",
                    e);
        }
    }

    @Override
    public void onCotEvent(CotEvent event, Bundle extra) {

        if (event == null || (event.getType() == null)) {
            Log.w(TAG, "Unable to process empty CoT event");
            return;
        }

        if (_receiver == null) {
            Log.w(TAG, "Unable to process CoT event without receiver handler");
        }

        // see if its a CoT event type we care about
        //if (event.getType().equals(FileTransfer.COT_TYPE)) {
        // Handled by common commo
        //            _receiver.handleCoTFileTransfer(event, extra);
        //} else if (event.getType().equals(CoTAck.COT_TYPE)) {
        // Handled by common commo
        //            _receiver.handleCoTFileTransferAck(event, extra);
        //}
    }

    public MissionPackagePreferenceListener getPrefListener() {
        return _prefListener;
    }

    /**
     * Get URL of (the first) connected TAK Server
     * If none, then take first server in list
     * If none, return null
     *
     * @return
     */
    public String getConnectedServerUrl() {
        if (_serverListener == null) {
            Log.d(TAG, "No streams available");
            return null;
        }

        return _serverListener.getConnectedServerUrl();
    }

    public String getConnectedServerString() {
        if (_serverListener == null) {
            Log.d(TAG, "No streams available");
            return null;
        }

        return _serverListener.getConnectedServerString();
    }

    /**
     * Get all configured TAK Servers
     * @return List of configured TAK servers
     */
    public CotPort[] getServers() {
        if (_serverListener == null) {
            Log.d(TAG, "No streams available");
            return null;
        }

        return _serverListener.getServers();
    }

    /**
     * Get all connected TAK servers
     * @return List of connected TAK servers
     */
    public CotPort[] getConnectedServers() {
        List<CotPort> ret = new ArrayList<>();
        CotPort[] servers = getServers();
        if (servers != null) {
            for (CotPort c : servers) {
                if (c.isConnected())
                    ret.add(c);
            }
        }
        return ret.toArray(new CotPort[0]);
    }

    /**
     * Listen for streaming/server connections, pass CoT events up to the component
     */
    private class ServerListener extends CotStreamListener {

        /**
         * ctor
         *
         * @param context
         */
        public ServerListener(Context context) {
            super(context, TAG, MissionPackageMapComponent.this);
        }

        @Override
        protected void serviceConnected() {
            Log.d(TAG, "onCotServiceConnected");

            // now that CoT is ready, finish start up
            if (_stillStarting) {
                finishStartup();
            }
        }
    }
}
