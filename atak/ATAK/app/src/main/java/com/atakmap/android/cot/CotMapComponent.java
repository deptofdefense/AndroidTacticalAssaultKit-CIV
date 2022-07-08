
package com.atakmap.android.cot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.cot.detail.PrecisionLocationHandler;
import com.atakmap.android.cot.detail.TakVersionDetailHandler;
import com.atakmap.android.cot.importer.CotImporterManager;
import com.atakmap.android.http.rest.ServerVersion;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import com.atakmap.android.chat.GeoChatConnector;
import com.atakmap.android.contact.ContactConnectorManager;
import com.atakmap.android.contact.ContactDetailDropdown;
import com.atakmap.android.contact.ContactListAdapter;
import com.atakmap.android.contact.ContactListDetailHandler;
import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.contact.ContactStatusReceiver;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.EmailConnector;
import com.atakmap.android.contact.IndividualContact;
import com.atakmap.android.contact.IpConnector;
import com.atakmap.android.contact.SmsConnector;
import com.atakmap.android.contact.TelephoneConnector;
import com.atakmap.android.contact.VoIPConnector;
import com.atakmap.android.contact.XmppConnector;
import com.atakmap.android.cotdelete.CotDeleteEventMarshal;
import com.atakmap.android.cotdelete.CotDeleteImporter;
import com.atakmap.android.cotdetails.CoTInfoBroadcastReceiver;
import com.atakmap.android.data.ClearContentTask;
import com.atakmap.android.http.rest.ServerContact;
import com.atakmap.android.importexport.ImporterManager;
import com.atakmap.android.importexport.MarshalManager;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.DocumentedExtra;
import com.atakmap.android.location.LocationMapComponent;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapData;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.missionpackage.export.MissionPackageConnector;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.NotificationIdRecycler;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.app.system.FlavorProvider;
import com.atakmap.app.system.SystemComponentLoader;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.comms.CotDispatcher;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.comms.CotServiceRemote.CotEventListener;
import com.atakmap.comms.DispatchFlags;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.NetworkUtils;
import com.atakmap.comms.ReportingRate;
import com.atakmap.comms.SslNetCotPort;
import com.atakmap.comms.app.CotPortListActivity;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *  Per-MapItem properties * entry - always "CoT"
 */
public class CotMapComponent extends AbstractMapComponent implements
        CotEventListener,
        CommsMapComponent.DirectCotProcessor,
        ReportingRate.Callback {

    public static final String TAG = "CotMapComponent";
    public static final String PREF_API_SECURE_PORT = "apiSecureServerPort";
    public static final String PREF_API_UNSECURE_PORT = "apiUnsecureServerPort";
    private final AtomicInteger batteryPct = new AtomicInteger(0);

    private Timer _checkStaleTimer = null;

    private static final CotDispatcher externalDispatcher = new CotDispatcher();
    private static final CotDispatcher internalDispatcher = new CotDispatcher();

    private static CotMapComponent _instance;

    private final CotDispatcher saDispatcher = new CotDispatcher();

    private NotificationIdRecycler _notificationId;

    private SharedPreferences _prefs;
    private CotImporterManager _importManager;
    private CotDetailManager _detailManager;
    private CotMapAdapter _adapter;
    private boolean _evtOtherUserNotification;
    private MapView _mapView;
    private Context _context;

    private static final int DEFAULT_WRGPS_PORT = 4349;

    private int listenPortHolder = DEFAULT_WRGPS_PORT;
    private CotDetail _takvDetail;

    /**
     * Only in place so that SiteExploitation can compile.
     * @deprecated
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public ContactListAdapter getContactListAdapter() {
        return contactAdapter;
    }

    private TaskCotReceiver _taskCotReceiver;
    private final Contacts contacts = Contacts.getInstance();
    private ContactListAdapter contactAdapter;
    private ContactPresenceDropdown contactPresenceReceiver;
    private ContactDetailDropdown contactDetailsReceiver;
    private ContactStatusReceiver contactStatusReceiver;
    private ContactListDetailHandler contactHandler;
    private ContactConnectorManager _contactConnectorMgr;

    private BroadcastReceiver telephoneViewReceiver;
    private BroadcastReceiver smsViewReceiver;
    private BroadcastReceiver voipViewReceiver;
    private BroadcastReceiver xmppViewReceiver;
    private BroadcastReceiver geochatViewReceiver;
    private BroadcastReceiver emailViewReceiver;
    private BroadcastReceiver missionPackageViewReceiver;

    private boolean _shareLocation;
    private boolean _hideLocation;
    private String phoneNumber = null;
    private boolean saHasPhoneNumber = false;
    private String emailAddress = null;
    private String xmppUsername = null;
    private String urn = null;
    private String sipAddressAssignment = null;
    private String sipAddress = null;
    private String sipAddressAssignmentDisabled;
    private String sipAddressAssignmentManualEntry;
    private String sipAddressAssignmentIP;
    private String sipAddressAssignmentCallsignAndIP;

    /**
     * CoT & server connection support
     */
    private CotMapServerListener _serverListener;
    private final static String CONNECTION_PING_COT_TYPE = "t-x-c-t";
    private final static String CONNECTION_PONG_COT_TYPE = "t-x-c-t-r";

    private final static String MISSION_PACKAGE_ACK_TYPE = "b-f-t-a";

    private ExternalGPSInput input;
    private Thread listenThread;

    private ReportingRate _reportingRate;
    private final static int STALE_CHECK_RATE = 2000;

    @Override
    public void onCreate(Context context, Intent intent, final MapView view) {
        _mapView = view;
        _context = context;
        _notificationId = new NotificationIdRecycler(8880, 5);

        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        //String team = preferences.getString("locationTeam", "NOTSET");
        // Start listening for the location marker
        // _listenForLocationMarker();

        MapData mapData = view.getMapData();
        UUID instanceUID = UUID.randomUUID();
        mapData.putString("uniqueCotInstance", instanceUID.toString());

        phoneNumber = LocationMapComponent._fetchTelephonyLine1Number(context);
        updateSAContact(_prefs);

        contactAdapter = new ContactListAdapter(view, contacts, context);

        contactStatusReceiver = new ContactStatusReceiver();
        DocumentedIntentFilter contactStatusFilter = new DocumentedIntentFilter();
        contactStatusFilter.addAction(ContactStatusReceiver.ITEM_STALE);
        contactStatusFilter.addAction(ContactStatusReceiver.ITEM_REFRESHED);
        AtakBroadcast.getInstance().registerReceiver(contactStatusReceiver,
                contactStatusFilter);

        contactPresenceReceiver = new ContactPresenceDropdown(view,
                contactAdapter);
        DocumentedIntentFilter chatPresenceFilter = new DocumentedIntentFilter();
        chatPresenceFilter.addAction(ContactPresenceDropdown.PRESENCE_LIST,
                "Open the contacts list (default mode)", new DocumentedExtra[] {
                        new DocumentedExtra("connector",
                                "The name of the contact connector handler to use",
                                true, String.class)
                });

        List<DocumentedExtra> sendExtras = new ArrayList<>(Arrays.asList(
                new DocumentedExtra("targetUID",
                        "The UID of the map item to send", true, String.class),
                new DocumentedExtra("targetsUID",
                        "Array of map item UIDs to send", true, String[].class),
                new DocumentedExtra("com.atakmap.contact.CotEvent",
                        "CoT event to send", true, CotEvent.class),
                new DocumentedExtra(
                        "com.atakmap.contact.MultipleCotEvents",
                        "List of CoT events to send", true, ArrayList.class),
                new DocumentedExtra("sendCallback",
                        "Intent action to broadcast once contacts are selected and ready to send",
                        true, String.class),
                new DocumentedExtra("filename",
                        "Path to file to send", true, String.class),
                new DocumentedExtra("disableBroadcast",
                        "True to hide the broadcast button", true,
                        Boolean.class)));
        chatPresenceFilter.addAction(ContactPresenceDropdown.SEND_LIST,
                "Open the contacts list for sending items or files",
                sendExtras.toArray(new DocumentedExtra[0]));

        sendExtras.add(0, new DocumentedExtra("contactUIDs",
                "Array of contact UIDs to send to", false, String[].class));
        chatPresenceFilter.addAction(ContactPresenceDropdown.SEND_TO_CONTACTS,
                "Send content directly to a given list of contacts",
                sendExtras.toArray(new DocumentedExtra[0]));

        chatPresenceFilter.addAction(ContactPresenceDropdown.GEO_CHAT_LIST,
                "Opens the contacts list for geo-chat");
        chatPresenceFilter.addAction(ContactPresenceDropdown.REFRESH_LIST,
                "Refresh the contacts list");

        AtakBroadcast.getInstance().registerReceiver(contactPresenceReceiver,
                chatPresenceFilter);

        contactDetailsReceiver = new ContactDetailDropdown(view);
        DocumentedIntentFilter contactDetailsFilter = new DocumentedIntentFilter();
        contactDetailsFilter
                .addAction(CoTInfoBroadcastReceiver.COTINFO_DETAILS);
        contactDetailsFilter.addAction(ContactDetailDropdown.CONTACT_DETAILS);
        AtakBroadcast.getInstance().registerReceiver(contactDetailsReceiver,
                contactDetailsFilter);

        // Create the ContactListDetailHandler's for each UI element that needs them
        contactHandler = new ContactListDetailHandler(contacts,
                "ContactAdapter");

        // Map group adapter (mostly deprecated)
        _adapter = new CotMapAdapter(view);

        // Responsible for managing CoT importers
        _importManager = new CotImporterManager(_mapView);

        // Responsible for managing CoT detail handlers
        _detailManager = new CotDetailManager(_mapView);
        _detailManager.registerHandler("contact", contactHandler);

        ImporterManager.registerImporter(_importManager);
        MarshalManager.registerMarshal(new GenericCotEventMarshal());

        final DocumentedIntentFilter batfilter = new DocumentedIntentFilter(
                Intent.ACTION_BATTERY_CHANGED);
        AtakBroadcast.getInstance().registerSystemReceiver(batteryRcvr,
                batfilter);

        //register for CoT delete tasking
        ImporterManager.registerImporter(new CotDeleteImporter(_mapView));
        MarshalManager.registerMarshal(new CotDeleteEventMarshal());

        _serverListener = new CotMapServerListener(_mapView.getContext(), this);

        DocumentedIntentFilter taskFilter = new DocumentedIntentFilter();
        taskFilter.addAction("com.atakmap.android.maps.TASK");
        AtakBroadcast.getInstance().registerReceiver(
                _taskCotReceiver = new TaskCotReceiver(_context, view),
                taskFilter);
        view.getMapEventDispatcher().addMapEventListener(MapEvent.ITEM_REMOVED,
                _taskCotReceiver);

        _contactConnectorMgr = new ContactConnectorManager(context,
                _prefs);

        DocumentedIntentFilter callFilter = new DocumentedIntentFilter();
        callFilter.addAction("com.atakmap.callAction");
        AtakBroadcast.getInstance().registerReceiver(
                telephoneViewReceiver = new BroadcastReceiver() {
                    public String toString() {
                        return "telephoneViewReceiver";
                    }

                    @Override
                    public void onReceive(Context context, Intent intent) {
                        _contactConnectorMgr.initiateContact(
                                TelephoneConnector.CONNECTOR_TYPE,
                                intent.getStringExtra("uid"),
                                intent.getStringExtra("phoneNumber"));
                    }
                }, callFilter);

        DocumentedIntentFilter smsFilter = new DocumentedIntentFilter();
        smsFilter.addAction("com.atakmap.smsAction");
        AtakBroadcast.getInstance().registerReceiver(
                smsViewReceiver = new BroadcastReceiver() {

                    @Override
                    public void onReceive(Context context, Intent intent) {
                        _contactConnectorMgr.initiateContact(
                                SmsConnector.CONNECTOR_TYPE,
                                intent.getStringExtra("uid"),
                                intent.getStringExtra("phoneNumber"));
                    }
                }, smsFilter);

        DocumentedIntentFilter voipFilter = new DocumentedIntentFilter();
        voipFilter.addAction("com.atakmap.voipAction");
        AtakBroadcast.getInstance().registerReceiver(
                voipViewReceiver = new BroadcastReceiver() {
                    public String toString() {
                        return "voipViewReceiver";
                    }

                    @Override
                    public void onReceive(Context context, Intent intent) {
                        _contactConnectorMgr.initiateContact(
                                VoIPConnector.CONNECTOR_TYPE,
                                intent.getStringExtra("uid"),
                                intent.getStringExtra("sipAddress"));
                    }
                }, voipFilter);

        DocumentedIntentFilter geoChatFilter = new DocumentedIntentFilter();
        geoChatFilter.addAction("com.atakmap.geochatAction");
        AtakBroadcast.getInstance().registerReceiver(
                geochatViewReceiver = new BroadcastReceiver() {
                    public String toString() {
                        return "geochatViewReceiver";
                    }

                    @Override
                    public void onReceive(Context context, Intent intent) {
                        _contactConnectorMgr.initiateContact(
                                GeoChatConnector.CONNECTOR_TYPE,
                                intent.getStringExtra("uid"),
                                intent.getStringExtra("uid"));
                    }
                }, geoChatFilter);

        DocumentedIntentFilter xmppFilter = new DocumentedIntentFilter();
        xmppFilter.addAction("com.atakmap.xmppAction");
        AtakBroadcast.getInstance().registerReceiver(
                xmppViewReceiver = new BroadcastReceiver() {
                    public String toString() {
                        return "xmppViewReceiver";
                    }

                    @Override
                    public void onReceive(Context context, Intent intent) {
                        _contactConnectorMgr.initiateContact(
                                XmppConnector.CONNECTOR_TYPE,
                                intent.getStringExtra("uid"),
                                intent.getStringExtra("xmppUsername"));
                    }
                }, xmppFilter);

        DocumentedIntentFilter emailFilter = new DocumentedIntentFilter();
        emailFilter.addAction("com.atakmap.emailAction");
        AtakBroadcast.getInstance().registerReceiver(
                emailViewReceiver = new BroadcastReceiver() {
                    public String toString() {
                        return "emailViewReceiver";
                    }

                    @Override
                    public void onReceive(Context context, Intent intent) {
                        _contactConnectorMgr.initiateContact(
                                EmailConnector.CONNECTOR_TYPE,
                                intent.getStringExtra("uid"),
                                intent.getStringExtra("emailAddress"));
                    }
                }, emailFilter);

        DocumentedIntentFilter missionPackageFilter = new DocumentedIntentFilter();
        missionPackageFilter.addAction("com.atakmap.missionPackageAction");
        AtakBroadcast.getInstance().registerReceiver(
                missionPackageViewReceiver = new BroadcastReceiver() {
                    public String toString() {
                        return "missionPackageViewReceiver";
                    }

                    @Override
                    public void onReceive(Context context, Intent intent) {
                        _contactConnectorMgr.initiateContact(
                                MissionPackageConnector.CONNECTOR_TYPE,
                                intent.getStringExtra("uid"),
                                intent.getStringExtra("uid"));
                    }
                }, missionPackageFilter);

        _updateCotPreferences(_prefs);

        _reportingRate = new ReportingRate(this, _prefs);

        _startListening(_prefs);

        _setServerPorts(_prefs);
        _prefs.registerOnSharedPreferenceChangeListener(
                _cotPrefsChangedListener);

        /*
         * Listen for all user created map items, as they need to be added the
         * CotMarkerMaintainer.
         */
        view.getMapEventDispatcher().addMapEventListener(MapEvent.ITEM_ADDED,
                _itemAddedListener);

        _checkStaleTimer = new Timer("CheckStaleTimerThread");
        _checkStaleTimer.schedule(_checkStaleTask, 0,
                STALE_CHECK_RATE);

        _takvDetail = new CotDetail(TakVersionDetailHandler.VERSION_DETAIL);
        _takvDetail.setAttribute(TakVersionDetailHandler.ATTR_PLATFORM,
                ATAKConstants.getBrand());
        _takvDetail.setAttribute(TakVersionDetailHandler.ATTR_VERSION,
                TakVersionDetailHandler.getVersion() + "-"
                        + ATAKConstants.getVersionBrand());
        _takvDetail.setAttribute(TakVersionDetailHandler.ATTR_DEVICE,
                TakVersionDetailHandler.getDeviceDescription());
        _takvDetail.setAttribute(TakVersionDetailHandler.ATTR_OS,
                TakVersionDetailHandler.getDeviceOS());

        _instance = this;
        _reportingRate.init();

        CommsMapComponent.getInstance().registerDirectProcessor(this);

        sipAddressAssignmentDisabled = context
                .getString(R.string.voip_assignment_disabled);
        sipAddressAssignmentManualEntry = context
                .getString(R.string.voip_assignment_manual_entry);
        sipAddressAssignmentIP = context.getString(R.string.voip_assignment_ip);
        sipAddressAssignmentCallsignAndIP = context
                .getString(R.string.voip_assignment_ip_callsign);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {

        // do not receive any more CotEvent's during the destruction of the
        // DirectProcessor aka CotMapComponent.

        CommsMapComponent.getInstance().registerDirectProcessor(null);

        if (input != null)
            input.interruptSocket();

        if (_reportingRate != null) {
            _reportingRate.dispose();
            _reportingRate = null;
        }

        try {
            _adapter.dispose();
        } catch (Exception e) {
            // Attempting to figure out what needs to be cleaned up
        }

        try {
            Marker locationMarker = ATAKUtilities.findSelf(_mapView);
            if (locationMarker != null) {
                GeoPoint agp = locationMarker.getPoint();

                //do not set these during clear content
                if (!ClearContentTask.isClearContent(_prefs)) {
                    Log.v(TAG, "recording rough positional");

                    SharedPreferences.Editor pref = _prefs.edit();
                    pref.putString("lastRecordedLocation", "" + agp.toString());
                    pref.apply(); // don't care about the return value.
                }
            } else {
                Log.v(TAG,
                        "self marker never set, not recording rough positional");
            }
        } catch (Exception e) {
            Log.e(TAG, "error: ", e);
        }

        if (_prefs != null && _cotPrefsChangedListener != null) {
            _prefs.unregisterOnSharedPreferenceChangeListener(
                    _cotPrefsChangedListener);
            _cotPrefsChangedListener = null;
        }

        contactPresenceReceiver.dispose();
        contactDetailsReceiver.dispose();

        if (_taskCotReceiver != null) {
            _taskCotReceiver.dispose();
            AtakBroadcast.getInstance().unregisterReceiver(_taskCotReceiver);
            _taskCotReceiver = null;
        }

        if (_serverListener != null) {
            _serverListener.dispose();
            _serverListener = null;
        }

        AtakBroadcast.getInstance().unregisterSystemReceiver(batteryRcvr);
        batteryRcvr = null;

        AtakBroadcast.getInstance().unregisterReceiver(contactStatusReceiver);
        contactStatusReceiver = null;

        contactAdapter.dispose();

        AtakBroadcast.getInstance().unregisterReceiver(contactPresenceReceiver);
        AtakBroadcast.getInstance().unregisterReceiver(contactDetailsReceiver);

        AtakBroadcast.getInstance().unregisterReceiver(telephoneViewReceiver);
        AtakBroadcast.getInstance().unregisterReceiver(smsViewReceiver);
        AtakBroadcast.getInstance().unregisterReceiver(voipViewReceiver);
        AtakBroadcast.getInstance().unregisterReceiver(xmppViewReceiver);
        AtakBroadcast.getInstance().unregisterReceiver(geochatViewReceiver);
        AtakBroadcast.getInstance().unregisterReceiver(emailViewReceiver);
        AtakBroadcast.getInstance().unregisterReceiver(
                missionPackageViewReceiver);

        if (_checkStaleTimer != null) {
            _checkStaleTimer.cancel();
            _checkStaleTimer.purge();
            _checkStaleTimer = null;
        }

        NotificationUtil.getInstance().cancelAll();

        _instance = null;
    }

    @Override
    public void onCotEvent(final CotEvent event, final Bundle extra) {
        processCotEvent(event, extra);
    }

    /**
     * Allow for the CotService.DirectCotProcessor processing of a CotEvent without
     * receiving it traditionally from the onCotEvent dispatch pump.
     */
    @Override
    public ImportResult processCotEvent(CotEvent event, Bundle extra) {

        // Just in case
        if (event == null) {
            Log.e(TAG, "Null event cannot be processed");
            return ImportResult.FAILURE;
        }

        if (CONNECTION_PING_COT_TYPE.equals(event.getType())
                || CONNECTION_PONG_COT_TYPE.equals(event.getType())) {
            //essentially a no-op as the AbstractStreaming instance will have updated the
            //last time for that connection
            Log.d(TAG, "Received ping/pong: " + event.getType());
            return ImportResult.SUCCESS;
        }

        // quietly consume the mission package ack - no reason to process it
        if (MISSION_PACKAGE_ACK_TYPE.equals(event.getType())) {
            return ImportResult.SUCCESS;
        }

        if (extra.getShort("CotMapProcessed") != 0) {

            Log.d(TAG, "CotEvent was already processed: " + event.getUID()
                    + " " + event.getType());
            // we've already been called, and this time the caller is the
            // onCotEvent function above, so it doesn't matter what we return
            return ImportResult.SUCCESS;
        }

        if (FileSystemUtils.isEmpty(event.getUID())) {
            // return success so it consume bad things without having it
            // sent around the system.
            return ImportResult.SUCCESS;
        }

        // HACK: filter out own UID in case of loopback, chomp both of these
        // so they will not get passed by into the onCotEvent dispatcher.
        String myUID = _mapView.getSelfMarker().getUID();

        if (event.getUID().equals(myUID)) {
            return ImportResult.SUCCESS;
        } else if (event.getUID().startsWith(myUID + ".SPI")) {
            return ImportResult.SUCCESS;
        } else if (event.getUID().startsWith(myUID + ".PLRF")) {
            return ImportResult.SUCCESS;
        } else if (event.getUID().equals("J.SELF")) {
            String mockingOption = _prefs.getString("mockingOption", "WRGPS");
            if (mockingOption.equals("WRGPS")
                    || mockingOption.equals("IgnoreInternalGPS"))
                ExternalGPSInput.getInstance().process(event);
            return ImportResult.SUCCESS;

        }

        ImportResult result = _adapter.adaptCotEvent(event,
                extra);

        return result;

    }

    public CotServiceRemote getCotServiceRemote() {
        if (_serverListener == null)
            return null;
        return _serverListener.getRemote();
    }

    public static CotMapComponent getInstance() {
        return _instance;
    }

    public ContactConnectorManager getContactConnectorMgr() {
        return _contactConnectorMgr;
    }

    private void updateSAContact(SharedPreferences preferences) {
        saHasPhoneNumber = preferences.getBoolean("saHasPhoneNumber", true);
        sipAddressAssignment = preferences.getString("saSipAddressAssignment",
                null);
        sipAddress = preferences.getString("saSipAddress", null);
        emailAddress = preferences.getString("saEmailAddress", null);
        xmppUsername = preferences.getString("saXmppUsername", null);
        urn = preferences.getString("saURN", null);
    }

    void addTadilJ(CotDetail detail, MapData mapData) {
        if (mapData.containsKey("tadiljId")) {
            if (mapData.containsKey("tadiljSelfPositionType") &&
                    mapData.getString("tadiljSelfPositionType")
                            .equals("J3.5")) {
                CotDetail tadilj = new CotDetail("uid");
                detail.addChild(tadilj);
                tadilj.setAttribute("tadilj", mapData.getString("tadiljId"));
            } else {
                CotDetail tadilj = new CotDetail("__jtids");
                detail.addChild(tadilj);
                tadilj.setAttribute("jstn", mapData.getString("tadiljId"));
            }
        }
    }

    private final TimerTask _checkStaleTask = new TimerTask() {
        @Override
        public void run() {
            _adapter.getCotMarkerSet().checkStaleTeams(_context, _prefs);
        }
    };

    private BroadcastReceiver batteryRcvr = new BroadcastReceiver() {
        public String toString() {
            return "batteryRecvr";
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            try {

                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                batteryPct.set((int) (level * 100 / (double) scale));
            } catch (Exception ignore) {
                Log.e(TAG, "battery intent does not contain any data");
            }
        }
    };

    private final Map<String, CotDetail> additionalDetails = new HashMap<>();

    private void sendSelfSA(final int offset, final int dispatchFlags) {
        if (!_shareLocation)
            return;

        CotEvent cotEvent = getSelfEvent(offset);
        if (cotEvent == null || !cotEvent.isValid()) {
            Log.w(TAG, "sendSelfSA cot info not ready");
            return;
        }

        //Log.d(TAG, "sendSelfSA: " + dispatchFlags);
        saDispatcher.setDispatchFlags(dispatchFlags);
        saDispatcher.dispatch(cotEvent);
    }

    /**
     * Get baseline self SA cot event
     * Includes self identity info, location, and additional details configured
     *
     * @param offset the stale time offset from the current time
     * @return the cot event used for the persons PPLI
     */
    private CotEvent getSelfEvent(final int offset) {
        final Marker self = ATAKUtilities.findSelfUnplaced(_mapView);
        final MapData mapData = _mapView.getMapData();
        if (self == null || mapData == null) {
            Log.w(TAG, "getSelfEvent not ready");
            return null;
        }

        final String myUID = self.getUID();
        final String myType = mapData.getString("deviceType");
        if (FileSystemUtils.isEmpty(myUID) || FileSystemUtils.isEmpty(myType)) {
            Log.w(TAG, "getSelfEvent my info not ready");
            return null;
        }

        CotEvent cotEvent = new CotEvent();
        cotEvent.setVersion("2.0");

        cotEvent.setUID(myUID);
        cotEvent.setType(myType);

        CoordinatedTime time = new CoordinatedTime();
        cotEvent.setTime(time);
        cotEvent.setStart(time);
        cotEvent.setStale(time.addMilliseconds(offset));

        //Log.d(TAG, " time: " + time.getMilliseconds() + " stale: " + time.addSeconds(offset).getMilliseconds());

        CotDetail detail = new CotDetail("detail");
        cotEvent.setDetail(detail);

        final GeoPoint p = self.getPoint();

        if (_hideLocation)
            cotEvent.setPoint(CotPoint.ZERO);
        else
            cotEvent.setPoint(new CotPoint(p));

        if (mapData.getBoolean("fineLocationAvailable", false) &&
                (mapData.getParcelable("fineLocation") != null)) {
            // from the GPS

            if (SystemClock.elapsedRealtime()
                    - self.getMetaLong("gpsUpdateTick",
                            0) > LocationMapComponent.GPS_TIMEOUT_MILLIS) {
                cotEvent.setHow(mapData.getString("how", "h-e"));
            } else {
                cotEvent.setHow(mapData.getString("how", "m-g"));
            }
        } else if (mapData.getBoolean("mockLocationAvailable", false) &&
                (mapData.getParcelable("mockLocation") != null)) {
            // from an external source
            cotEvent.setHow(mapData.getString("how", "m-g"));
        } else if (ATAKUtilities.findSelf(_mapView) != null) {
            // hand entered and on the map
            cotEvent.setHow(mapData.getString("how", "h-e"));
        } else {
            // not on the map
            cotEvent.setHow(mapData.getString("how", "h-g-i-g-o"));
        }

        detail.addChild(_takvDetail);

        CotDetail contact = new CotDetail("contact");
        detail.addChild(contact);

        contact.setAttribute("callsign", _mapView.getDeviceCallsign());
        //include alternate contact info, if provided via settings
        if (saHasPhoneNumber
                && LocationMapComponent.isValidTelephoneNumber(phoneNumber)) {
            contact.setAttribute("phone", phoneNumber);
        }
        String currentSipAddress = getSipAddress();
        if (!FileSystemUtils.isEmpty(currentSipAddress)) {
            contact.setAttribute("sipAddress", currentSipAddress);
        }
        if (!FileSystemUtils.isEmpty(emailAddress)) {
            contact.setAttribute("emailAddress", emailAddress);
        }
        if (!FileSystemUtils.isEmpty(xmppUsername)) {
            contact.setAttribute("xmppUsername", xmppUsername);
        }

        FlavorProvider fp = SystemComponentLoader.getFlavorProvider();
        if (fp != null && fp.hasMilCapabilities()) {
            if (!FileSystemUtils.isEmpty(urn)) {
                CotDetail variablemessage = new CotDetail("vmf");
                variablemessage.setAttribute("urn", urn);
                detail.addChild(variablemessage);
            }
        }

        UIDHandler.getInstance().toCotDetail(self, detail);
        PrecisionLocationHandler.getInstance().toCotDetail(self, cotEvent,
                detail);

        // added by Dc
        String endpoint = getEndpoint();
        if (endpoint != null)
            contact.setAttribute("endpoint", endpoint);

        CotDetail __group = new CotDetail("__group");
        detail.addChild(__group);

        __group.setAttribute("name", mapData.getString("deviceTeam"));
        __group.setAttribute("role",
                _prefs.getString("atakRoleType", "Team Member"));

        CotDetail status = new CotDetail("status");
        detail.addChild(status);
        status.setAttribute("battery", "" + getBatteryPercent());
        if (mapData.containsKey("readiness")) {
            status.setAttribute("readiness", mapData.getString("readiness"));
        }

        addTadilJ(detail, mapData);

        synchronized (additionalDetails) {
            for (Map.Entry<String, CotDetail> entry : additionalDetails
                    .entrySet()) {
                String key = entry.getKey();
                CotDetail value = entry.getValue();
                //Log.d(TAG, "addition information added for: " + key);
                detail.addChild(value);
            }
        }

        if (mapData.containsKey("archive") && mapData.getBoolean("archive")) {
            CotDetail archive = new CotDetail("archive");
            detail.addChild(archive);
        }

        String mockLocationParentUID = mapData
                .getString("mockLocationParentUID");
        String mockLocationParentType = mapData
                .getString("mockLocationParentType");

        if (mockLocationParentUID != null && mockLocationParentType != null) {
            CotDetail linkInfo = new CotDetail("link");
            detail.addChild(linkInfo);

            linkInfo.setAttribute("relation", "p-s"); // Parent of this object, source (this
            // object was derived from parent)
            linkInfo.setAttribute("uid", mockLocationParentUID);
            linkInfo.setAttribute("type", mockLocationParentType);
        }

        CotDetail track = new CotDetail("track");
        detail.addChild(track);

        /**
         * See appropriate comment on how the LocationMapComponent
         * actually populates the Speed, Bearing information
         * for the Self Coordinate Display.
         *
         */
        double speed = self.getMetaDouble("Speed", 0d);
        if (Double.isNaN(speed))
            speed = 0.0;

        double heading = self.getTrackHeading();

        // protect against a heading that is -Infinity
        if (Double.isNaN(heading) || Math.abs(heading) > 3600)
            heading = 0.0;

        track.setAttribute("speed", Double.toString(speed));
        track.setAttribute("course", Double.toString(heading));

        return cotEvent;
    }

    public Integer getBatteryPercent() {
        return batteryPct.get();
    }

    /**
     * 'Self' is not stored by Contacts
     * @return the individual contact constructed for the self marker
     */
    public IndividualContact getSelfContact(boolean bDeep) {
        if (!bDeep) {
            return new IndividualContact(MapView.getMapView()
                    .getDeviceCallsign(), MapView.getDeviceUid());
        }

        Marker selfMarker = _mapView.getSelfMarker();
        IndividualContact self = new IndividualContact(
                _mapView.getDeviceCallsign(),
                selfMarker.getUID(),
                selfMarker,
                null);
        self.setDispatch(false);

        NetConnectString ncs = NetConnectString.fromString(getEndpoint());
        self.addConnector(new IpConnector(ncs));
        self.addConnector(new GeoChatConnector(ncs));
        self.addConnector(new MissionPackageConnector(ncs));

        if (saHasPhoneNumber
                && LocationMapComponent.isValidTelephoneNumber(phoneNumber)) {
            self.addConnector(new TelephoneConnector(phoneNumber));
            self.addConnector(new SmsConnector(phoneNumber));
        }
        String currentSipAddress = getSipAddress();
        if (!FileSystemUtils.isEmpty(currentSipAddress)) {
            self.addConnector(new VoIPConnector(currentSipAddress));
        }
        if (!FileSystemUtils.isEmpty(emailAddress)) {
            self.addConnector(new EmailConnector(emailAddress));
        }
        if (!FileSystemUtils.isEmpty(xmppUsername)) {
            self.addConnector(new XmppConnector(xmppUsername));
        }

        return self;
    }

    private String getSipAddress() {
        if (FileSystemUtils.isEmpty(sipAddressAssignment)) {
            //error
            //Log.w(TAG, "sipAddressAssignment not set");
            return null;
        } else if (sipAddressAssignment.equals(sipAddressAssignmentDisabled)) {
            //no setting voip address
            //Log.d(TAG, "sipAddressAssignment disabled");
            return null;
        } else if (sipAddressAssignment
                .equals(sipAddressAssignmentManualEntry)) {
            //manual entry of voip address
            //Log.d(TAG, "sipAddressAssignment: " + sipAddress);
            return sipAddress;
        } else if (sipAddressAssignment.equals(sipAddressAssignmentIP)) {
            //automatically use current IP address
            //Log.d(TAG, "sipAddressAssignment: " + NetworkUtils.getIP());
            return NetworkUtils.getIP();
        } else if (sipAddressAssignment
                .equals(sipAddressAssignmentCallsignAndIP)) {
            //automatically use current callsign & IP address
            String myIP = NetworkUtils.getIP();
            if (FileSystemUtils.isEmpty(myIP)) {
                Log.w(TAG, "sipAddressAssignment IP not set");
                return null;
            }

            String sa = _mapView.getDeviceCallsign() + "@" + myIP;
            //Log.d(TAG, "sipAddressAssignment: " + sa);
            return sa;
        } else {
            Log.w(TAG, "sipAddressAssignment invalid: " + sipAddressAssignment);
            return null;
        }
    }

    /**
     * Allows for a Self SA (PPLI) message to contain additional dynamically registered information.
     * This detail can be modified by the client without having to add it again to the self marker.
     */
    public void addAdditionalDetail(final String detailName,
            final CotDetail detail) {
        synchronized (additionalDetails) {
            additionalDetails.put(detailName, detail);
        }
    }

    /**
     * Remove a detail tag previously registered by a client.
     * @param detailName
     * @return
     */
    public CotDetail removeAdditionalDetail(final String detailName) {
        synchronized (additionalDetails) {
            return additionalDetails.remove(detailName);
        }
    }

    private final MapEventDispatchListener _itemAddedListener = new MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            if (event.getItem() instanceof Marker) {
                Marker marker = (Marker) event.getItem();
                String entry = marker.getMetaString("entry", null);

                // user created or CoT created need to be 'maintained'
                if (entry != null) {
                    if ((entry.equals("user") || entry.equals("CoT"))) {
                        _adapter.getCotMarkerSet().addMarker(marker);
                    }
                }
            }
        }
    };

    private SharedPreferences.OnSharedPreferenceChangeListener _cotPrefsChangedListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(
                final SharedPreferences cotPrefs, final String key) {

            if (key == null)
                return;

            if (key.equals("dispatchLocationCotExternal") ||
                    key.equals("dispatchLocationHidden") ||
                    key.equals("mockingOption") ||
                    key.equals("atakControlOtherUserNotification") ||
                    key.equals("listenPort")) {
                _updateCotPreferences(cotPrefs);
            }

            if (key.equals("listenPort") ||
                    key.equals("mockingOption")) {
                try {
                    listenPortHolder = Integer.parseInt(cotPrefs
                            .getString(key, "" + DEFAULT_WRGPS_PORT));
                } catch (Exception ignored) {
                }

                // make sure _startListenening is run in a thread otherwise it can cause a
                // UI deadlock while changed during the preference fire.   This is observed when
                // rapidly starting a new ExternalGPSListener and closing the socket, then waiting
                // for the thread to stop.
                Thread t = new Thread() {
                    public void run() {
                        _startListening(cotPrefs);
                    }
                };
                t.start();
            }

            if (key.equals("saSipAddressAssignment")
                    || key.equals("saSipAddress")
                    || key.equals("saEmailAddress")
                    || key.equals("saHasPhoneNumber")
                    || key.equals("saXmppUsername")
                    || key.equals("saURN")) {
                updateSAContact(cotPrefs);
                //now immediate report to push updated contact info
                Log.d(TAG, "Alternate contact prefs changed");
                Log.d(TAG,
                        "Sending a system broadcast for now until CotService is augmented");
                AtakBroadcast
                        .getInstance()
                        .sendBroadcast(
                                new Intent(
                                        ReportingRate.REPORT_LOCATION)
                                                .putExtra("reason",
                                                        "Alternate contact preferences changed"));
            }

            //check for changes to server API port
            if (key.equals(PREF_API_SECURE_PORT)
                    || key.equals(PREF_API_UNSECURE_PORT)) {
                _setServerPorts(cotPrefs);
            }
        }
    };

    private void _setServerPorts(SharedPreferences prefs) {
        int port = 8080;
        try {
            port = Integer.parseInt(prefs.getString(PREF_API_UNSECURE_PORT,
                    "8080"));
            if (port < 1)
                port = 8080;
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse apiUnsecureServerPort: " + port);
            port = 8080;
        }
        SslNetCotPort.setUnsecureServerApiPort(port);
        CommsMapComponent.getInstance().setMissionPackageHttpPort(port);

        port = 8443;
        try {
            port = Integer.parseInt(prefs.getString(PREF_API_SECURE_PORT,
                    "8443"));
            if (port < 1)
                port = 8443;
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse apiSecureServerPort: " + port);
            port = 8443;
        }
        SslNetCotPort.setSecureServerApiPort(port);
        CommsMapComponent.getInstance().setMissionPackageHttpsPort(port);

        port = 8446;
        try {
            port = Integer.parseInt(prefs.getString("apiCertEnrollmentPort",
                    "8446"));
            if (port < 1)
                port = 8446;
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse apiCertEnrollmentPort: " + port);
            port = 8446;
        }
        SslNetCotPort.setCertEnrollmentApiPort(port);
    }

    synchronized private void _startListening(SharedPreferences cotPrefs) {

        Log.d(TAG, "start listening for externally supplied input on port: "
                + listenPortHolder);

        if (input != null)
            input.interruptSocket();
        if (listenThread != null) { // probably not necessary, but why not
            listenThread.interrupt();
            listenThread = null;
        }

        /**
         * start up the mocking socket. the descision to use the data can be done independently of
         * receipt of the data.
         */

        // XXX - should probably always start the mocking and use that as the only way gps is used
        // within ATAK.
        String listValue = cotPrefs.getString("mockingOption", "WRGPS");
        boolean _locationMocking = (listValue.equals("WRGPS")
                || listValue.equals("IgnoreInternalGPS"));

        if (_locationMocking) {
            input = new ExternalGPSInput(listenPortHolder, _mapView);
            listenThread = new Thread(input, "ExternalGPSInputThread");
            listenThread.start();
        }
    }

    private void _updateCotPreferences(SharedPreferences cotPrefs) {
        _shareLocation = cotPrefs.getBoolean("dispatchLocationCotExternal",
                true);
        _hideLocation = cotPrefs.getBoolean("dispatchLocationHidden",
                false);
        Marker locationMarker = ATAKUtilities.findSelf(_mapView);
        if (locationMarker != null) {
            locationMarker.setMetaBoolean("shared", _shareLocation);
        }

        _evtOtherUserNotification = cotPrefs.getBoolean(
                "atakControlOtherUserNotification", true);
    }

    public static String getEndpoint() {
        String myIp = NetworkUtils.getIP();
        if (FileSystemUtils.isEmpty(myIp))
            return null;

        return myIp + ":4242:tcp";
    }

    /**
     * Get the contract presence drop down for the system
     * @return the drop down
     */
    public ContactPresenceDropdown getContactPresenceReceiver() {
        return contactPresenceReceiver;
    }

    /**
     * Get last saved point, if no point has been saved return 0,0,0
     * 
     * @param prefs the shared preference to use when pulling the last saved point
     * @return the last save point as cursor on target.
     */
    public static CotPoint getLastPoint(MapView view, SharedPreferences prefs) {
        // look for a placed self
        PointMapItem self = ATAKUtilities.findSelf(view);

        if (self != null) {
            return new CotPoint(self.getPoint());
        }

        String p = prefs.getString("lastRecordedLocation", null);
        GeoPoint geoPoint = GeoPoint.ZERO_POINT;

        if (p != null) {
            try {
                geoPoint = GeoPoint.parseGeoPoint(p);
            } catch (Exception e) {
                Log.d(TAG, "error restoring last recorded location", e);
            }
        }
        return new CotPoint(geoPoint);
    }

    public static CotDispatcher getExternalDispatcher() {
        return externalDispatcher;
    }

    public static CotDispatcher getInternalDispatcher() {
        return internalDispatcher;
    }

    @Override
    public GeoPoint getReportingPoint() {
        Marker self = ATAKUtilities.findSelfUnplaced(_mapView);
        if (self == null)
            return null;

        return self.getPoint();
    }

    @Override
    public double getReportingSpeed() {
        Marker self = ATAKUtilities.findSelfUnplaced(_mapView);
        if (self == null)
            return 0.0;

        return self.getMetaDouble("Speed", 0.0);
    }

    @Override
    public void report(int stale, int flags) {
        sendSelfSA(stale, flags);
    }

    /**
     * Get list of server contacts, for servers that this device has connected to, at least
     * once since ATAK startup. Does not reach out to server, rather checks cached contacts
     *
     * @param connectString Pass null to get contacts for all servers
     * @return the list of server contacts for a given server.
     */
    public List<ServerContact> getServerContacts(String connectString) {
        if (_serverListener == null)
            return null;
        return _serverListener.getServerContacts(connectString);
    }

    /**
     * Given a connection string and a uid return the server contact for a server
     * @param connectString the connection string for a specific server or null for all the servers
     * @param uid the uid for the contact
     * @return the server contact or null if none can be found.
     */
    public ServerContact getServerContact(String connectString, String uid) {
        if (_serverListener == null)
            return null;
        return _serverListener.getContact(connectString, uid);
    }

    /**
     * Given a connection string and a uid return the callsign for the user on the server
     * @param connectString the connection string for a specific server or null for all the servers
     * @param uid the uid for the contact
     * @return the callsign for the user on the specific server or null if not found
     */
    public String getServerCallsign(String connectString, String uid) {
        if (_serverListener == null)
            return null;
        return _serverListener.getCallsign(connectString, uid);
    }

    /**
     * Given a server hostname, retrieve the server version number
     * @param hostname the hostname
     * @return the server version
     */
    public ServerVersion getServerVersion(String hostname) {
        if (_serverListener == null)
            return null;
        return _serverListener.getServerVersion(hostname);
    }

    /**
     * Given a server version, modify the currently known server information or add a new server
     * version record if none exists
     * @param ver the server version representing a server
     * @return returns the server version or null if the server version is null or not valid
     */
    public ServerVersion setServerVersion(ServerVersion ver) {
        if (_serverListener == null)
            return null;
        return _serverListener.setServerVersion(ver);
    }

    /**
     * Check if we have at least one connection to a TAK server
     * @return true if there is at least one connection
     */
    public boolean isServerConnected() {
        if (_serverListener == null)
            return false;
        return _serverListener.isConnected();
    }

    /**
     * Get server connections
     *
     * @return
     */
    public CotPortListActivity.CotPort[] getServers() {
        if (_serverListener == null)
            return null;
        return _serverListener.getServers();
    }

    /**
     * Returns true if one or more servers is configured
     * @return true if there is one or more connected.
     */
    public static boolean hasServer() {
        CotMapComponent inst = CotMapComponent.getInstance();
        if (inst != null) {
            CotPortListActivity.CotPort[] servers = inst.getServers();
            return servers != null && servers.length > 0;
        }

        return false;
    }

    /**
     * Request the specified tool profile be downloaded from TAK Server
     * Note, could extend this to provide a DeviceProfileCallback, this currently uses a default impl
     * @param tool the tool profile name
     * @return true if the request is added
     */
    public boolean addToolProfileRequest(String tool) {
        if (_serverListener == null)
            return false;
        return _serverListener.addToolProfileRequest(tool);
    }

    /**
     * Remove the request that a specific tool profile be downloaded from the TAK Sever
     * @param tool the tool profile name
     * @return true if the request is removed.
     */
    public boolean removeToolProfileRequest(String tool) {
        if (_serverListener == null)
            return false;
        return _serverListener.removeToolProfileRequest(tool);
    }

    /**
     * Callback for when CoT Service is available
     * @param remote
     */
    void setCotRemote(CotServiceRemote remote) {
        Bundle meta = new Bundle();
        meta.putString("description", "request.notify");
        meta.putString("management", "internal");
        remote.addInput("0.0.0.0:8087:tcp", meta);

        externalDispatcher.setDispatchFlags(DispatchFlags.EXTERNAL);

        internalDispatcher.setDispatchFlags(DispatchFlags.INTERNAL);
        _adapter.getCotMarkerSet().setCotRemote(externalDispatcher);
    }

    public void stale(String[] uid) {
        if (_adapter != null
                && _prefs.getBoolean("staleRemoteDisconnects", true)) {
            _adapter.getCotMarkerSet().staleMarkers(uid);
        }
    }

    /**
     * Callback for when a CoT stream is connected or becomes disconnected
     * @param port
     * @param connected
     */
    void connected(CotPortListActivity.CotPort port, boolean connected) {
        if (connected && _reportingRate != null) {
            _reportingRate.setReportAsap("Server connected: "
                    + port.getConnectString());
        }
    }
}
