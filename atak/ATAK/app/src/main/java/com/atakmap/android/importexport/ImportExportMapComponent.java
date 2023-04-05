
package com.atakmap.android.importexport;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.data.URIContentSender;
import com.atakmap.android.hierarchy.action.GoTo;
import com.atakmap.android.importexport.send.TAKContactSender;
import com.atakmap.android.importexport.send.TAKServerSender;
import com.atakmap.android.importexport.send.ThirdPartySender;
import com.atakmap.android.importfiles.http.NetworkLinkDownloader;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.ftp.FtpUploader;
import com.atakmap.android.importexport.handlers.CotImportExportHandler;
import com.atakmap.android.importexport.handlers.KmlImportExportHandler;
import com.atakmap.android.importexport.http.ErrorLogsClient;
import com.atakmap.android.importfiles.http.ImportFileDownloader;
import com.atakmap.android.importfiles.http.KMLNetworkLinkDownloader;
import com.atakmap.android.importfiles.resource.RemoteResource;
import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.importfiles.task.ImportFileTask;
import com.atakmap.android.importfiles.task.ImportFilesTask;
import com.atakmap.android.importfiles.task.ImportRemoteFileTask;
import com.atakmap.android.importfiles.ui.ImportManagerDropdown;
import com.atakmap.android.importfiles.ui.ImportManagerMapOverlay;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.network.ui.CredentialsPreference;
import com.atakmap.android.util.ServerListDialog;
import com.atakmap.app.R;
import com.atakmap.app.preferences.NetworkConnectionPreferenceFragment;
import com.atakmap.comms.TAKServer;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.CotStreamListener;
import com.atakmap.comms.app.CotPortListActivity;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.comms.CotServiceRemote.ConnectionListener;
import com.atakmap.comms.CotServiceRemote.CotEventListener;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.net.AtakCertificateDatabaseIFace;
import com.atakmap.spatial.kml.KMLUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Map component to support data input and output from/to KML, CoT and survey XML.
 */
public class ImportExportMapComponent extends AbstractMapComponent implements
        ConnectionListener,
        CotEventListener, MapEventDispatcher.MapEventDispatchListener {

    protected static final String TAG = "ImportExportMapComponent";

    private HandlerThread handlerThread;

    public final static String ACTION_IMPORT_DATA = "com.atakmap.android.importexport.IMPORT_DATA";
    public final static String ACTION_DELETE_DATA = "com.atakmap.android.importexport.DELETE_DATA";

    public final static String IMPORT_COMPLETE = "com.atakmap.android.importexport.IMPORT_COMPLETE";

    /**
     * Intent action to display UI to allow user to import a file from availble sources
     */
    public static final String USER_IMPORT_FILE_ACTION = "com.atakmap.android.importfiles.IMPORT_FILE";

    /**
     * Intent action to allow import a file being opened by user/3rd party app.
     */
    public static final String USER_HANDLE_IMPORT_FILE_ACTION = "com.atakmap.android.importfiles.USER_HANDLE_IMPORT_FILE_ACTION";

    /**
     * Intent action to start/stop a KML Network Link refresh timer/task
     */
    public static final String KML_NETWORK_LINK_REFRESH = "com.atakmap.android.importfiles.KML_NETWORK_LINK";

    /**
     * Handle the CoT Event that is being imported
     */
    public static final String IMPORT_COT = "com.atakmap.android.importfiles.IMPORT_COT";

    /**
     * Required extra for import factory class registration. The class name for the import factory.
     */
    public static final String IMPORT_EXPORT_COMPONENT_READY_ACTION = "com.atakmap.android.importexport.IMPORT_EXPORT_READY";

    /**
     * Intent action to allow user to upload a file from file system to an FTP server.
     */
    public static final String FTP_UPLOAD_FILE_ACTION = "com.atakmap.android.export.FTP_UPLOAD_FILE";

    /**
     * Intent action to export crash logs
     */
    public final static String EXPORT_LOGS = "com.atakmap.android.importexport.EXPORT_LOGS";

    public final static String SET_EXPORT_LOG_SERVER = "com.atakmap.android.importexport.SET_EXPORT_LOG_SERVER";

    public static final String ZOOM_TO_FILE_ACTION = "com.atakmap.android.importexport.ZOOM_TO_FILE_ACTION";

    private ImportManagerDropdown _dropDown;

    private boolean _stillStarting;

    /**
     * CoT Sending support
     */
    private CotServiceRemote _cotRemote;

    /**
     * CoT & server connection support
     */
    private ServerListener _serverListener;

    /**
     * List of import resolvers
     */
    private List<ImportResolver> _importerResolvers;

    /**
     * List of import listeners
     */
    private final List<ImportListener> _importListeners = new ArrayList<>();

    private static ImportExportMapComponent _instance;
    private boolean _isSoftReset = false;

    public static ImportExportMapComponent getInstance() {
        return _instance;
    }

    private ErrorLogsClient _errorLogsClient;

    // Listens for an Intent specifying that the MapComponents in ATAK have
    // finished being created.
    private final BroadcastReceiver componentsCreatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String action = intent.getAction();
            if (action == null)
                return;

            if (action
                    .equals("com.atakmap.app.COMPONENTS_CREATED")) {
                // After components have been created, it is safe to say that
                // file import can occur since the import factories have been
                // registered at that point
                Intent ready = new Intent();
                ready.setAction(IMPORT_EXPORT_COMPONENT_READY_ACTION);
                AtakBroadcast.getInstance().sendBroadcast(ready);

                Log.d(TAG, "Import/Export ready");
            }
        }
    };

    // Listens for an Intent specifying that user wishes to import a file
    private final BroadcastReceiver importFileReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null)
                return;

            switch (action) {
                case USER_IMPORT_FILE_ACTION:
                    if (_dropDown != null)
                        _dropDown.onShow();
                    break;
                case USER_HANDLE_IMPORT_FILE_ACTION:
                    String filepath = intent.getStringExtra("filepath");
                    if (!FileSystemUtils.isFile(filepath)) {
                        Log.w(TAG, "No file to import...");
                        return;
                    }

                    boolean bFlagImportInPlace = intent.getBooleanExtra(
                            "importInPlace", false);
                    boolean bFlagPromptOnMultipleMatch = intent.getBooleanExtra(
                            "promptOnMultipleMatch", true);
                    boolean zoomToFile = intent.getBooleanExtra(
                            ImportReceiver.EXTRA_ZOOM_TO_FILE, false);
                    boolean hideFile = intent.getBooleanExtra(
                            ImportReceiver.EXTRA_HIDE_FILE, false);

                    Log.d(TAG, "Handle import file: " + filepath);

                    // task to copy file
                    ImportFileTask importTask = new ImportFileTask(
                            _mapView.getContext(), null);
                    importTask
                            .addFlag(ImportFileTask.FlagValidateExt
                                    | ImportFileTask.FlagPromptOverwrite);
                    if (bFlagImportInPlace)
                        importTask.addFlag(ImportFileTask.FlagImportInPlace);
                    if (bFlagPromptOnMultipleMatch)
                        importTask
                                .addFlag(
                                        ImportFileTask.FlagPromptOnMultipleMatch);
                    if (zoomToFile)
                        importTask.addFlag(ImportFileTask.FlagZoomToFile);
                    if (hideFile)
                        importTask.addFlag(ImportFileTask.FlagHideFile);
                    importTask.execute(filepath);
                    break;
                case ZOOM_TO_FILE_ACTION:
                    String path = intent.getStringExtra("filepath");
                    if (FileSystemUtils.isEmpty(path))
                        return;
                    URIContentHandler h = URIContentManager.getInstance()
                            .getHandler(new File(FileSystemUtils
                                    .sanitizeWithSpacesAndSlashes(path)));
                    if (h != null && h.isActionSupported(GoTo.class))
                        ((GoTo) h).goTo(false);
                    break;
            }
        }
    };

    private final BroadcastReceiver networkLinkRefreshReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (KML_NETWORK_LINK_REFRESH.equals(intent.getAction())) {
                String url = intent.getStringExtra("kml_networklink_url");
                String filename = intent
                        .getStringExtra("kml_networklink_filename");
                long intervalSeconds = intent.getLongExtra(
                        "kml_networklink_intervalseconds",
                        KMLUtil.DEFAULT_NETWORKLINK_INTERVAL_SECS);
                boolean bStop = intent.getBooleanExtra("kml_networklink_stop",
                        false);
                refreshNetworkLink(url, filename, intervalSeconds, bStop);
            }
        }
    };

    private final BroadcastReceiver importCotReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (IMPORT_COT.equals(intent.getAction())) {
                Bundle extras = intent.getExtras();
                if (extras == null)
                    return;

                String xml = extras.getString("xml");
                CotEvent event = extras.getParcelable("event");
                if (FileSystemUtils.isEmpty(xml) && event == null) {
                    Log.e(TAG, "Unable to handle empty CoT");
                    return;
                }

                if (event == null && !FileSystemUtils.isEmpty(xml)) {
                    event = CotEvent.parse(xml);
                }

                if (event != null && event.isValid()) {
                    // send to an internal CoT Dispatcher so it will be passed around ATAK
                    // and onto State Saver if necessary
                    // indicate that this was not an internal event
                    CotMapComponent.getInternalDispatcher().dispatchFrom(event,
                            "ImportExportMapComponent");
                    Log.d(TAG, "Importing CoT event: " + event.getUID());
                } else {
                    Log.e(TAG, "Unable to parse CoT Event: " + xml);
                }
            }
        }
    };

    private final BroadcastReceiver ftpUploadReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null)
                return;

            if (FTP_UPLOAD_FILE_ACTION.equals(intent.getAction())) {
                Bundle extras = intent.getExtras();
                if (extras == null)
                    return;

                String filepath = extras.getString("filepath");
                boolean binaryMode = extras.getBoolean("binary");

                //optional params
                int notificationId = extras.getInt("notificationId", -1);
                String passwd = extras.getString("passwd");
                boolean skipDialog = extras.getBoolean("skipdialog");
                String callbackAction = extras.getString("callbackAction");
                Parcelable callbackExtra = extras
                        .getParcelable("callbackExtra");

                if (!FileSystemUtils.isFile(filepath)) {
                    Log.e(TAG, "Unable to FTP update empty file");
                    return;
                }

                File file = new File(filepath);
                _ftp.upload(file, binaryMode, notificationId, passwd,
                        skipDialog, callbackAction, callbackExtra);
            }
        }
    };

    private final BroadcastReceiver exportLogsReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (EXPORT_LOGS.equals(intent.getAction())) {
                Log.d(TAG, "EXPORT_LOGS");

                String autoUploadLogServer = intent
                        .getStringExtra("autoUploadLogServer");
                new ExportCrashLogsTask(_mapView,
                        autoUploadLogServer)
                                .execute();
            }
        }
    };

    private final BroadcastReceiver setExportLogServerReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (SET_EXPORT_LOG_SERVER.equals(intent.getAction())) {
                Log.d(TAG, "SET_EXPORT_LOG_SERVER");

                // pull the currently connected servers
                final CotPortListActivity.CotPort[] servers = _serverListener
                        .getServers();

                if (servers != null) {
                    final SharedPreferences.Editor editor = _prefs.edit();
                    final String logFile = FileSystemUtils
                            .sanitizeWithSpacesAndSlashes(intent
                                    .getStringExtra("logFile"));

                    // if we have more than one, let the user pick which server
                    if (servers.length > 1) {
                        //select server
                        ServerListDialog.selectServer(
                                _mapView.getContext(),
                                _mapView.getContext().getString(
                                        R.string.select_server),
                                servers,
                                new ServerListDialog.Callback() {
                                    @Override
                                    public void onSelected(
                                            TAKServer takServer) {
                                        // cancel
                                        if (takServer == null) {
                                            editor.remove(
                                                    "autoUploadLogServer")
                                                    .apply();
                                            return;
                                        }
                                        String server = takServer
                                                .getConnectString();
                                        editor.putString("autoUploadLogServer",
                                                server).apply();
                                        if (logFile != null) {
                                            _errorLogsClient.sendLogsToServer(
                                                    new File(logFile),
                                                    server,
                                                    false,
                                                    _mapView.getSelfMarker()
                                                            .getUID(),
                                                    _mapView.getDeviceCallsign());
                                        }
                                    }
                                });
                    } else {
                        String server = servers[0].getConnectString();
                        editor.putString("autoUploadLogServer", server).apply();
                        if (logFile != null) {
                            _errorLogsClient.sendLogsToServer(
                                    new File(logFile), server, false,
                                    _mapView.getSelfMarker().getUID(),
                                    _mapView.getDeviceCallsign());
                        }
                    }
                }
            }
        }
    };

    private final BroadcastReceiver certUpdatedReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            // did a cert just get updated?
            if (intent.getStringExtra("type").equalsIgnoreCase(
                    AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE) &&
                    intent.getBooleanExtra("promptForPassword", false)) {

                final String host = intent.getStringExtra("host") != null
                        ? intent.getStringExtra("host")
                        : AtakAuthenticationCredentials.TYPE_clientPassword;

                // do we have a client cert password already?
                String clientCertPassword = context
                        .getString(R.string.defaultTrustStorePassword);
                AtakAuthenticationCredentials clientCertCreds = AtakAuthenticationDatabase
                        .getCredentials(
                                AtakAuthenticationCredentials.TYPE_clientPassword);
                if (clientCertCreds != null && clientCertCreds.password != null
                        && !clientCertCreds.password.isEmpty()) {
                    clientCertPassword = clientCertCreds.password;
                }

                // set the client password in the dialog
                View credentialsView = LayoutInflater.from(
                        _mapView.getContext()).inflate(R.layout.client_cert,
                                null);
                final EditText passwordET = credentialsView
                        .findViewById(R.id.client_cert_password);
                passwordET.setText(clientCertPassword);

                // build out the dialog
                AlertDialog.Builder credentialsBuilder = new AlertDialog.Builder(
                        _mapView.getContext());
                credentialsBuilder
                        .setTitle("SSL/TLS Client Certificate Password")
                        .setView(credentialsView)
                        .setPositiveButton(R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    // save the password and update everyone
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        String clientCertPassword = passwordET
                                                .getText().toString().trim();
                                        AtakAuthenticationDatabase
                                                .saveCredentials(
                                                        AtakAuthenticationCredentials.TYPE_clientPassword,
                                                        host,
                                                        "",
                                                        clientCertPassword,
                                                        true);

                                        AtakBroadcast
                                                .getInstance()
                                                .sendBroadcast(
                                                        new Intent(
                                                                CredentialsPreference.CREDENTIALS_UPDATED)
                                                                        .putExtra(
                                                                                "type",
                                                                                AtakAuthenticationCredentials.TYPE_clientPassword)
                                                                        .putExtra(
                                                                                "host",
                                                                                host));

                                        dialog.dismiss();
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null)
                        .setCancelable(false)
                        .show();
            }
        }
    };

    // Handlers need to be created in the GUI thread
    private Handler handler;
    private SharedPreferences _prefs;
    private ImportFileDownloader _downloader;
    private KMLNetworkLinkDownloader _kmlDownloader;
    private FtpUploader _ftp;
    private MapView _mapView;
    private ImportManagerMapOverlay _overlay;
    private final List<URIContentSender> _senders = new ArrayList<>();

    CotImportExportHandler cotHandler;
    KmlImportExportHandler kmlHandler;

    public ImportExportMapComponent() {
        super(true);
    }

    @Override
    public void onCreate(Context context, Intent intent, MapView mapView) {
        _mapView = mapView;
        handlerThread = new HandlerThread(
                "ImportExportHandlerThread");
        _stillStarting = true;
        handlerThread.start();

        Looper threadLooper = handlerThread.getLooper();
        if (threadLooper == null) {
            Log.e(TAG,
                    "Error getting looper for thread " + handlerThread.getId());
            return;
        }

        // Add resource items overlay
        _overlay = new ImportManagerMapOverlay(mapView, this);
        _mapView.getMapOverlayManager().addOverlay(_overlay);

        handler = new Handler(threadLooper);
        _prefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());
        _dropDown = new ImportManagerDropdown(mapView, this, _prefs);
        _downloader = new ImportFileDownloader(mapView.getContext(),
                ImportRemoteFileTask.FlagNotifyUserSuccess
                        | ImportRemoteFileTask.FlagUpdateResourceLocalPath);
        _kmlDownloader = new KMLNetworkLinkDownloader(context);
        _ftp = new FtpUploader(context, _prefs);

        // Now that download handlers are ready, get resource list
        _overlay.getResources();

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(ACTION_IMPORT_DATA);
        filter.addAction(ACTION_DELETE_DATA);
        this.registerReceiver(context, new ImportReceiver(), filter);

        DocumentedIntentFilter toolIntegrationFilter = new DocumentedIntentFilter();
        toolIntegrationFilter
                .addAction("com.atakmap.app.COMPONENTS_CREATED");

        DocumentedIntentFilter importFileFilter = new DocumentedIntentFilter();
        importFileFilter.addAction(USER_IMPORT_FILE_ACTION);
        importFileFilter.addAction(USER_HANDLE_IMPORT_FILE_ACTION);
        importFileFilter.addAction(ZOOM_TO_FILE_ACTION);

        DocumentedIntentFilter networkLinkRefreshFilter = new DocumentedIntentFilter();
        networkLinkRefreshFilter.addAction(KML_NETWORK_LINK_REFRESH);

        DocumentedIntentFilter importCotFilter = new DocumentedIntentFilter();
        importCotFilter.addAction(IMPORT_COT);

        DocumentedIntentFilter ddFilter = new DocumentedIntentFilter();
        ddFilter.addAction(ImportManagerDropdown.ADD_RESOURCE);
        ddFilter.addAction(ImportManagerDropdown.DLOAD_RESOURCE);
        ddFilter.addAction(ImportManagerDropdown.UPDATE_RESOURCE);

        DocumentedIntentFilter ftpUploadFilter = new DocumentedIntentFilter();
        ftpUploadFilter.addAction(FTP_UPLOAD_FILE_ACTION);

        DocumentedIntentFilter exportLogsFilter = new DocumentedIntentFilter();
        exportLogsFilter.addAction(EXPORT_LOGS,
                "Intent used to kick off the process of exporting the log files to a server");

        DocumentedIntentFilter setExportLogServerFilter = new DocumentedIntentFilter();
        setExportLogServerFilter.addAction(SET_EXPORT_LOG_SERVER);

        DocumentedIntentFilter certUpdatedfilter = new DocumentedIntentFilter();
        certUpdatedfilter
                .addAction(
                        NetworkConnectionPreferenceFragment.CERTIFICATE_UPDATED);

        this.registerReceiver(context, componentsCreatedReceiver,
                toolIntegrationFilter);
        this.registerReceiver(context, importFileReceiver, importFileFilter);
        this.registerReceiver(context, networkLinkRefreshReceiver,
                networkLinkRefreshFilter);
        this.registerReceiver(context, _dropDown, ddFilter);
        this.registerReceiver(context, importCotReceiver, importCotFilter);
        this.registerReceiver(context, ftpUploadReceiver, ftpUploadFilter);
        this.registerReceiver(context, exportLogsReceiver, exportLogsFilter);
        this.registerReceiver(context, setExportLogServerReceiver,
                setExportLogServerFilter);
        this.registerReceiver(context, certUpdatedReceiver, certUpdatedfilter);

        // Set up CoT ImportExport (in a separate class just to keep things nicely partitioned)
        cotHandler = new CotImportExportHandler(mapView, handler);
        kmlHandler = new KmlImportExportHandler(mapView, handler);

        // set up CoT sending/recv'ing
        _cotRemote = new CotServiceRemote();
        _cotRemote.connect(this);
        _cotRemote.setCotEventListener(this);

        boolean enableAutoUploadLogs = _prefs.getBoolean(
                "enableAutoUploadLogs", false);
        String prefsAutoUploadLogServer = _prefs.getString(
                "autoUploadLogServer", "");
        _serverListener = new ServerListener(context,
                enableAutoUploadLogs,
                prefsAutoUploadLogServer);

        mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_ADDED, this);

        if (!_isSoftReset)
            _importerResolvers = new ArrayList<>();

        // Send methods used by export
        _senders.add(new TAKContactSender(mapView));
        _senders.add(new TAKServerSender(mapView));
        _senders.add(new ThirdPartySender(mapView));
        for (URIContentSender s : _senders)
            URIContentManager.getInstance().registerSender(s);

        _errorLogsClient = new ErrorLogsClient(_mapView.getContext());

        if (!_isSoftReset)
            _instance = this;

        //now check for streaming KML connections
        //_dropDown.refresh();

        Log.d(TAG, "Component created.");
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        // all receivers unregistered in `super.onDestroy(...)`

        if (_errorLogsClient != null) {
            _errorLogsClient.dispose();
            _errorLogsClient = null;
        }
        for (URIContentSender s : _senders)
            URIContentManager.getInstance().unregisterSender(s);
        _senders.clear();
        if (!_isSoftReset)
            _importerResolvers.clear();
        _mapView.getMapEventDispatcher()
                .removeMapEventListener(MapEvent.ITEM_ADDED, this);
        if (_serverListener != null) {
            _serverListener.dispose();
            _serverListener = null;
        }
        if (_cotRemote != null) {
            _cotRemote.setCotEventListener(null);
            _cotRemote.disconnect();
            _cotRemote = null;
        }
        if (kmlHandler != null) {
            kmlHandler.shutdown();
            kmlHandler = null;
        }
        if (cotHandler != null) {
            cotHandler.shutdown();
            cotHandler = null;
        }
        if (_ftp != null) {
            _ftp.dispose();
            _ftp = null;
        }
        if (_kmlDownloader != null) {
            _kmlDownloader.shutdown();
            _kmlDownloader.shutdown();
        }
        if (_downloader != null) {
            _downloader.shutdown();
            _downloader = null;
        }
        if (_dropDown != null) {
            _dropDown.dispose();
            _dropDown = null;
        }
        if (handler != null) {
            handler.getLooper().quit();
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
        if (_overlay != null) {
            _mapView.getMapOverlayManager().removeOverlay(_overlay);
            _overlay = null;
        }
        if (handlerThread != null) {
            handlerThread.interrupt();
            handlerThread = null;
        }
    }

    public void download(RemoteResource resource, boolean showNotifications) {
        if (resource == null) {
            return;
        }

        if (!resource.isValid()) {
            Log.w(TAG,
                    "Unable to download invalid resource: "
                            + resource);
            return;
        }

        if (resource.isKML())
            _kmlDownloader.download(resource, showNotifications);
        else
            _downloader.download(resource, showNotifications);

        // Ping refresher in case it hasn't been started yet
        if (resource.getRefreshSeconds() > 0)
            refreshNetworkLink(resource, false);
    }

    public void download(RemoteResource resource) {
        download(resource, true);
    }

    @Override
    public void onCotServiceConnected(Bundle fullServiceState) {
        Log.d(TAG, "onCotServiceConnected");

        // now that CoT is ready, begin importing data from "atakdata" directory
        if (_stillStarting) {
            new ImportFilesTask(_mapView.getContext()).execute();
            _stillStarting = false;
        }
    }

    @Override
    public void onCotServiceDisconnected() {
    }

    @Override
    public void onMapEvent(final MapEvent event) {
        try {
            String eventType = event.getType();
            MapItem item = event.getItem();
            Bundle extras = event.getExtras();

            if (eventType.equals(MapEvent.ITEM_ADDED)
                    && item instanceof Marker) {
                String cot = item.getMetaString("legacy_cot_event", null);
                if (cot != null) {
                    Bundle b = new Bundle();
                    if (extras != null)
                        b.putString("from", extras.getString("from"));

                    Log.d(TAG, "Marker: " + item.getUID() + " " +
                            cot + " FROM: " + b.getString("from"));
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
        if (event == null) {
            Log.w(TAG, "Unable to process empty CoT event");
            return;
        }

        if (_dropDown == null) {
            Log.w(TAG, "Unable to process CoT event without handler");
            return;
        }

        // see if its a CoT event type we care about
        if ((event.getType() != null)
                && event.getType().equals(RemoteResource.COT_TYPE)) {
            _dropDown.add(event, extra);
        }
    }

    /**
     * Dynamically register an <code>ImportResolver</code> instance to import files
     * from Import Manager and Mission Package Tool
     *
     * @param resolver
     */
    public synchronized void addImporterClass(ImportResolver resolver) {
        if (resolver == null) {
            Log.w(TAG, "Invalid importer resolver");
            return;
        }

        if (!_importerResolvers.contains(resolver)) {
            _importerResolvers.add(resolver);
            Log.d(TAG, "Adding import resolver class: "
                    + resolver.getClass().getName());
            ImportFilesTask.registerExtension(resolver.getExt());
        }
    }

    public synchronized void removeImporterClass(ImportResolver resolver) {
        if (resolver == null) {
            Log.w(TAG, "Invalid importer resolver");
            return;
        }

        if (_importerResolvers.contains(resolver)) {
            _importerResolvers.remove(resolver);
            Log.d(TAG, "Removing import resolver class: "
                    + resolver.getClass().getName());
            //ImportFilesTask.unregisterExtension(resolver.getExt());
        }
    }

    /**
     * Get list of <code>ImportResolver</code>
     * @return
     */
    public synchronized Collection<ImportResolver> getImporterResolvers() {
        if (FileSystemUtils.isEmpty(_importerResolvers)) {
            return new ArrayList<>();
        }

        return Collections.unmodifiableCollection(_importerResolvers);
    }

    public synchronized void addImportListener(ImportListener l) {
        if (!_importListeners.contains(l))
            _importListeners.add(l);
    }

    public synchronized void removeImportListener(ImportListener l) {
        _importListeners.remove(l);
    }

    public synchronized List<ImportListener> getImportListeners() {
        return new ArrayList<>(_importListeners);
    }

    /**
     * Listen for streaming/server connections, pass CoT events up to the component
     */
    private class ServerListener extends CotStreamListener {

        private boolean enableAutoUploadLogs;
        private String prefsAutoUploadLogServer;

        /**
         * ctor
         *
         * @param context
         */
        public ServerListener(Context context, boolean enableAutoUploadLogs,
                String prefsAutoUploadLogServer) {
            super(context, TAG, ImportExportMapComponent.this);
            this.enableAutoUploadLogs = enableAutoUploadLogs;
            this.prefsAutoUploadLogServer = prefsAutoUploadLogServer;
        }

        /**
         * Invoked when connection to a TAK Server is established or disconnected
         * @param port
         */
        @Override
        protected void connected(CotPortListActivity.CotPort port,
                boolean connected) {

            // bail if we've just disconnected
            if (!connected) {
                return;
            }

            // bail if we're not auto-uploading logs
            if (!enableAutoUploadLogs) {
                return;
            }

            // if we only have 1 server, use it
            String autoUploadLogServer;
            if (getServers().length == 1) {
                autoUploadLogServer = getServers()[0].getConnectString();
                // pull server from prefs
            } else {
                autoUploadLogServer = prefsAutoUploadLogServer;
            }

            // did the log server just connect?
            if (autoUploadLogServer != null
                    && autoUploadLogServer.equals(port.getConnectString())) {

                // send an intent to kick off the export
                Intent intent = new Intent(
                        ImportExportMapComponent.EXPORT_LOGS);
                intent.putExtra("autoUploadLogServer", autoUploadLogServer);
                AtakBroadcast.getInstance().sendBroadcast(intent);
            }
        }
    }

    public void refreshNetworkLink(RemoteResource res, boolean stop) {
        if (_kmlDownloader == null || _downloader == null) {
            Log.w(TAG, "Unable to process KML_NETWORK_LINK_REFRESH: " + res);
            return;
        }
        if (stop) {
            if (res == null) {
                Log.w(TAG,
                        "Unable to remove KML_NETWORK_LINK_REFRESH with missing parameters");
                return;
            }
            _downloader.removeRefreshLink(res);
            _kmlDownloader.removeRefreshLink(res);
        } else {
            if (res == null) {
                Log.w(TAG,
                        "Unable to add KML_NETWORK_LINK_REFRESH with missing parameters");
                return;
            }
            NetworkLinkDownloader downloader = res.isKML()
                    ? _kmlDownloader
                    : _downloader;
            downloader.addRefreshLink(res);
        }
    }

    public void refreshNetworkLink(String url, String name, long interval,
            boolean stop) {
        RemoteResource res = new RemoteResource();
        res.setUrl(url);
        res.setName(name);
        res.setType(RemoteResource.Type.KML);
        res.setDeleteOnExit(false);
        res.setLocalPath("");
        res.setRefreshSeconds(interval);
        res.setLastRefreshed(0);
        res.setMd5("");
        res.setSource(RemoteResource.Source.LOCAL_STORAGE);
        refreshNetworkLink(res, stop);
    }
}
