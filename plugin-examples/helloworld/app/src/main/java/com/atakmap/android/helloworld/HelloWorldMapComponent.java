
package com.atakmap.android.helloworld;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.contact.ContactLocationView;
import com.atakmap.android.cot.detail.CotDetailHandler;
import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.cotdetails.ExtendedInfoView;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.helloworld.routes.RouteExportMarshal;
import com.atakmap.android.helloworld.sender.HelloWorldContactSender;
import com.atakmap.android.importexport.ExporterManager;
import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.importexport.ImportReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.ipc.DocumentedExtra;
import com.atakmap.android.layers.LayersMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.cot.UIDHandler;
import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.graphics.GLMapItemFactory;

import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.munitions.DangerCloseReceiver;
import com.atakmap.android.statesaver.StateSaverPublisher;
import com.atakmap.android.user.geocode.GeocodeManager;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.coremap.cot.event.CotDetail;

import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.helloworld.plugin.R;
import com.atakmap.app.preferences.ToolsPreferenceFragment;

import com.atakmap.android.cot.CotMapComponent;

import com.atakmap.android.radiolibrary.RadioMapComponent;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.net.DeviceProfileClient;

import android.location.Address;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.RelativeLayout.LayoutParams;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * This is an example of a MapComponent within the ATAK 
 * ecosphere.   A map component is the building block for all
 * activities within the system.   This defines a concrete 
 * thought or idea. 
 */
public class HelloWorldMapComponent extends DropDownMapComponent {

    public static final String TAG = "HelloWorldMapComponent";

    private Context pluginContext;
    private HelloWorldDropDownReceiver dropDown;
    private WebViewDropDownReceiver wvdropDown;
    private HelloWorldMapOverlay mapOverlay;
    private View genericRadio;
    private SpecialDetailHandler sdh;
    private CotDetailHandler aaaDetailHandler;
    private ContactLocationView.ExtendedSelfInfoFactory extendedselfinfo;
    private HelloWorldContactSender contactSender;

    @Override
    public void onStart(final Context context, final MapView view) {
        Log.d(TAG, "onStart");
    }

    @Override
    public void onPause(final Context context, final MapView view) {
        Log.d(TAG, "onPause");
    }

    @Override
    public void onResume(final Context context,
            final MapView view) {
        Log.d(TAG, "onResume");
    }

    @Override
    public void onStop(final Context context,
            final MapView view) {
        Log.d(TAG, "onStop");
    }

    /**
     * Simple uncalled example for how to import a file.
     */
    private void importFileExample(final File file) {
        /**
         * Case 1 where the file type is known and in this example, the file is a map type.
         */
        Log.d(TAG, "testImport: " + file.toString());
        Intent intent = new Intent(
                ImportExportMapComponent.ACTION_IMPORT_DATA);
        intent.putExtra(ImportReceiver.EXTRA_URI,
                file.getAbsolutePath());
        intent.putExtra(ImportReceiver.EXTRA_CONTENT,
                LayersMapComponent.IMPORTER_CONTENT_TYPE);
        intent.putExtra(ImportReceiver.EXTRA_MIME_TYPE,
                LayersMapComponent.IMPORTER_DEFAULT_MIME_TYPE);

        AtakBroadcast.getInstance().sendBroadcast(intent);
        Log.d(TAG, "testImportDone: " + file.toString());

        /**
         * Case 2 where the file type is unknown and the file is just imported.
         */
        Log.d(TAG, "testImport: " + file.toString());
        intent = new Intent(
                ImportExportMapComponent.USER_HANDLE_IMPORT_FILE_ACTION);
        intent.putExtra("filepath", file.toString());
        intent.putExtra("importInPlace", false); // copies it over to the general location if true
        intent.putExtra("promptOnMultipleMatch", true); //prompts the users if this could be multiple things
        intent.putExtra("zoomToFile", false); // zoom to the outer extents of the file.
        AtakBroadcast.getInstance().sendBroadcast(intent);
        Log.d(TAG, "testImportDone: " + file.toString());

    }

    @Override
    public void onCreate(final Context context, Intent intent,
            final MapView view) {

        // Set the theme.  Otherwise, the plugin will look vastly different
        // than the main ATAK experience.   The theme needs to be set 
        // programatically because the AndroidManifest.xml is not used.
        context.setTheme(R.style.ATAKPluginTheme);

        super.onCreate(context, intent, view);
        pluginContext = context;

        GLMapItemFactory.registerSpi(GLSpecialMarker.SPI);

        // Register capability to handle detail tags that TAK does not 
        // normally process.
        CotDetailManager.getInstance().registerHandler(
                "__special",
                sdh = new SpecialDetailHandler());

        CotDetailManager.getInstance().registerHandler(
                aaaDetailHandler = new CotDetailHandler("__aaa") {
                    private final String TAG = "AAACotDetailHandler";

                    @Override
                    public CommsMapComponent.ImportResult toItemMetadata(
                            MapItem item, CotEvent event, CotDetail detail) {
                        Log.d(TAG, "detail received: " + detail + " in:  "
                                + event);
                        return CommsMapComponent.ImportResult.SUCCESS;
                    }

                    @Override
                    public boolean toCotDetail(MapItem item, CotEvent event,
                            CotDetail root) {
                        Log.d(TAG, "converting to cot detail from: "
                                + item.getUID());
                        return true;
                    }
                });

        //HelloWorld MapOverlay added to Overlay Manager.
        this.mapOverlay = new HelloWorldMapOverlay(view, pluginContext);
        view.getMapOverlayManager().addOverlay(this.mapOverlay);

        //MapView.getMapView().getRootGroup().getChildGroupById(id).setVisible(true);

        /*Intent new_cot_intent = new Intent();
        new_cot_intent.setAction("com.atakmap.android.maps.COT_PLACED");
        new_cot_intent.putExtra("uid", point.getUID());
        AtakBroadcast.getInstance().sendBroadcast(
                new_cot_intent);*/

        // End of Overlay Menu Test ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

        // In this example, a drop down receiver is the 
        // visual component within the ATAK system.  The 
        // trigger for this visual component is an intent.   
        // see the plugin.HelloWorldTool where that intent
        // is triggered.
        this.dropDown = new HelloWorldDropDownReceiver(view, context,
                this.mapOverlay);

        // We use documented intent filters within the system
        // in order to automatically document all of the 
        // intents and their associated purposes.

        Log.d(TAG, "registering the show hello world filter");
        DocumentedIntentFilter ddFilter = new DocumentedIntentFilter();
        ddFilter.addAction(HelloWorldDropDownReceiver.SHOW_HELLO_WORLD,
                "Show the Hello World drop-down");
        ddFilter.addAction(HelloWorldDropDownReceiver.CHAT_HELLO_WORLD,
                "Chat message sent to the Hello World contact");
        ddFilter.addAction(HelloWorldDropDownReceiver.SEND_HELLO_WORLD,
                "Sending CoT to the Hello World contact");
        ddFilter.addAction(HelloWorldDropDownReceiver.LAYER_DELETE,
                "Delete example layer");
        ddFilter.addAction(HelloWorldDropDownReceiver.LAYER_VISIBILITY,
                "Toggle visibility of example layer");
        this.registerDropDownReceiver(this.dropDown, ddFilter);
        Log.d(TAG, "registered the show hello world filter");

        this.wvdropDown = new WebViewDropDownReceiver(view, context);
        Log.d(TAG, "registering the webview filter");
        DocumentedIntentFilter wvFilter = new DocumentedIntentFilter();
        wvFilter.addAction(WebViewDropDownReceiver.SHOW_WEBVIEW,
                "web view");
        this.registerDropDownReceiver(this.wvdropDown, wvFilter);

        // in this case we also show how one can register
        // additional information to the uid detail handle when 
        // generating cursor on target.   Specifically the 
        // NETT-T service specification indicates the the 
        // details->uid should be filled in with an appropriate
        // attribute.   

        // add in the nett-t required uid entry.
        UIDHandler.getInstance().addAttributeInjector(
                new UIDHandler.AttributeInjector() {
                    public void injectIntoDetail(Marker marker,
                            CotDetail detail) {
                        if (marker.getType().startsWith("a-f"))
                            return;
                        detail.setAttribute("nett", "XX");
                    }

                    public void injectIntoMarker(CotDetail detail,
                            Marker marker) {
                        if (marker.getType().startsWith("a-f"))
                            return;
                        String callsign = detail.getAttribute("nett");
                        if (callsign != null)
                            marker.setMetaString("nett", callsign);
                    }

                });

        // In order to use shared preferences with a plugin you will need
        // to use the context from ATAK since it has the permission to read
        // and write preferences.
        // Additionally - in the XML file you cannot use PreferenceCategory
        // to enclose your Prefences - otherwise the preference will not
        // be persisted.   You can fake a PreferenceCategory by adding an
        // empty preference category at the top of each group of preferences.
        // See how this is done in the current example.

        DangerCloseReceiver.ExternalMunitionQuery emq = new DangerCloseReceiver.ExternalMunitionQuery() {
            @Override
            public String queryMunitions() {
                return BuildExternalMunitionsQuery();
            }
        };

        DangerCloseReceiver.getInstance().setExternalMunitionQuery(emq);

        // for custom preferences
        ToolsPreferenceFragment
                .register(
                        new ToolsPreferenceFragment.ToolPreference(
                                "Hello World Preferences",
                                "This is the sample preference for Hello World",
                                "helloWorldPreference",
                                context.getResources().getDrawable(
                                        R.drawable.ic_launcher, null),
                                new HelloWorldPreferenceFragment(context)));

        // example for how to register a radio with the radio map control.

        LayoutInflater inflater = LayoutInflater.from(pluginContext);
        genericRadio = inflater.inflate(R.layout.radio_item_generic, null);

        RadioMapComponent.getInstance().registerControl(genericRadio);

        // demonstrate how to customize the view for ATAK contacts.   In this case
        // it will show a customized line of test when pulling up the contact 
        // detail view.
        ContactLocationView.register(
                extendedselfinfo = new ContactLocationView.ExtendedSelfInfoFactory() {
                    @Override
                    public ExtendedInfoView createView() {
                        return new ExtendedInfoView(view.getContext()) {
                            @Override
                            public void setMarker(PointMapItem m) {
                                Log.d(TAG, "setting the marker: "
                                        + m.getMetaString("callsign", ""));
                                TextView tv = new TextView(view.getContext());
                                tv.setLayoutParams(new LayoutParams(
                                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                                        LayoutParams.WRAP_CONTENT));
                                this.addView(tv);
                                tv.setText("Example: " + m
                                        .getMetaString("callsign", "unknown"));

                            }
                        };
                    }
                });

        // send out some customized information as part of the SA or PPLI message.
        CotDetail cd = new CotDetail("temp");
        cd.setAttribute("temp", Integer.toString(76));
        CotMapComponent.getInstance().addAdditionalDetail(cd.getElementName(),
                cd);

        // register a listener for when a the radial menu asks for a special 
        // drop down.  SpecialDetail is really a skeleton of a class that 
        // shows a very basic drop down.
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction("com.atakmap.android.helloworld.myspecialdetail",
                "this intent launches the special drop down",
                new DocumentedExtra[] {
                        new DocumentedExtra("targetUID",
                                "the map item identifier used to populate the drop down")
                });
        registerDropDownReceiver(new SpecialDetail(view, pluginContext),
                filter);

        //see if any hello profiles/data are available on the TAK Server. Requires the server to be
        //properly configured, and "Apply TAK Server profile updates" setting enabled in ATAK prefs
        Log.d(TAG, "Checking for Hello profile on TAK Server");
        DeviceProfileClient.getInstance().getProfile(view.getContext(),
                "hello");

        //register profile request to run upon connection to TAK Server, in case we're not yet
        //connected, or the the request above fails
        CotMapComponent.getInstance().addToolProfileRequest("hello");

        registerSpisVisibilityListener(view);

        view.addOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent event) {
                Log.d(TAG, "dispatchKeyEvent: " + event.toString());
                return false;
            }
        });

        GeocodeManager.getInstance(context).registerGeocoder(fakeGeoCoder);

        TextView tv = new TextView(context);
        LayoutParams lp_tv = new RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        lp_tv.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        tv.setText("Test Center Layout");
        tv.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(TAG, "Test Test Test");
            }
        });
        com.atakmap.android.video.VideoDropDownReceiver.registerVideoViewLayer(
                new com.atakmap.android.video.VideoViewLayer("test-layer", tv,
                        lp_tv));

        ExporterManager.registerExporter(
                context.getString(R.string.route_exporter_name),
                context.getDrawable(R.drawable.ic_route),
                RouteExportMarshal.class);

        // Code to listen for when a state saver is completely loaded or wait to perform some action
        // after all of the markers are completely loaded.

        final BroadcastReceiver ssLoadedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // action for when the statesaver is completely loaded.
            }
        };
        AtakBroadcast.getInstance().registerReceiver(ssLoadedReceiver,
                new DocumentedIntentFilter(
                        StateSaverPublisher.STATESAVER_COMPLETE_LOAD));
        // because the plugin can be loaded after the above intent has been fired, there is a method
        // to check to see if a load has already occured.

        if (StateSaverPublisher.isFinished()) {
            // no need to listen for the intent
            AtakBroadcast.getInstance().unregisterReceiver(ssLoadedReceiver);
            // action for when the statesaver is completely loaded
        }

        // example of how to save and retrieve credentials using the credential management system
        // within core ATAK
        saveAndRetrieveCredentials();

        // Content sender example
        URIContentManager.getInstance().registerSender(contactSender =
                new HelloWorldContactSender(view, pluginContext));
    }

    private final GeocodeManager.Geocoder fakeGeoCoder = new GeocodeManager.Geocoder() {
        @Override
        public String getUniqueIdentifier() {
            return "fake-geocoder";
        }

        @Override
        public String getTitle() {
            return "Gonna get you Lost";
        }

        @Override
        public String getDescription() {
            return "Sample Geocoder implementation registered with TAK";
        }

        @Override
        public boolean testServiceAvailable() {
            return true;
        }

        @Override
        public List<Address> getLocation(GeoPoint geoPoint) {
            Address a = new Address(Locale.getDefault());
            a.setAddressLine(0, "100 WrongWay Street");
            a.setAddressLine(1, "Boondocks, Nowhere");
            a.setCountryCode("UNK");
            a.setPostalCode("999999");
            a.setLatitude(geoPoint.getLatitude());
            a.setLongitude(geoPoint.getLongitude());
            return new ArrayList<>(Collections.singleton(a));
        }

        @Override
        public List<Address> getLocation(String s, GeoBounds geoBounds) {
            Address a = new Address(Locale.getDefault());
            a.setAddressLine(0, "100 WrongWay Street");
            a.setAddressLine(1, "Boondocks, Nowhere");
            a.setCountryCode("UNK");
            a.setPostalCode("999999");
            a.setLatitude(0);
            a.setLongitude(0);
            return new ArrayList<>(Collections.singleton(a));
        }
    };

    private void registerSpisVisibilityListener(MapView view) {
        spiListener = new SpiListener(view);
        for (int i = 0; i < 4; ++i) {
            MapItem mi = view
                    .getMapItem(view.getSelfMarker().getUID() + ".SPI" + i);
            if (mi != null) {
                mi.addOnVisibleChangedListener(spiListener);
            }
        }

        final MapEventDispatcher dispatcher = view.getMapEventDispatcher();
        dispatcher.addMapEventListener(MapEvent.ITEM_REMOVED, spiListener);
        dispatcher.addMapEventListener(MapEvent.ITEM_ADDED, spiListener);

    }

    private SpiListener spiListener;

    private static class SpiListener implements MapEventDispatchListener,
            MapItem.OnVisibleChangedListener {
        private final MapView view;

        SpiListener(MapView view) {
            this.view = view;
        }

        @Override
        public void onMapEvent(MapEvent event) {
            MapItem item = event.getItem();
            if (item == null)
                return;
            if (event.getType().equals(MapEvent.ITEM_ADDED)) {
                if (item.getUID()
                        .startsWith(view.getSelfMarker().getUID() + ".SPI")) {
                    item.addOnVisibleChangedListener(this);
                    Log.d(TAG, "visibility changed for: " + item.getUID() + " "
                            + item.getVisible());
                }
            } else if (event.getType().equals(MapEvent.ITEM_REMOVED)) {
                if (item.getUID()
                        .startsWith(view.getSelfMarker().getUID() + ".SPI"))
                    item.removeOnVisibleChangedListener(this);
            }
        }

        @Override
        public void onVisibleChanged(MapItem item) {
            Log.d(TAG, "visibility changed for: " + item.getUID() + " "
                    + item.getVisible());
        }
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        Log.d(TAG, "calling on destroy");
        ContactLocationView.unregister(extendedselfinfo);
        GLMapItemFactory.unregisterSpi(GLSpecialMarker.SPI);
        this.dropDown.dispose();
        ToolsPreferenceFragment.unregister("helloWorldPreference");
        RadioMapComponent.getInstance().unregisterControl(genericRadio);
        view.getMapOverlayManager().removeOverlay(mapOverlay);
        CotDetailManager.getInstance().unregisterHandler(
                sdh);
        CotDetailManager.getInstance().unregisterHandler(aaaDetailHandler);
        ExporterManager.unregisterExporter(
                context.getString(R.string.route_exporter_name));
        URIContentManager.getInstance().unregisterSender(contactSender);
        super.onDestroyImpl(context, view);

        // Example call on how to end ATAK if the plugin is unloaded.
        // It would be important to possibly show the user a dialog etc.

        //Intent intent = new Intent("com.atakmap.app.QUITAPP");
        //intent.putExtra("FORCE_QUIT", true);
        //AtakBroadcast.getInstance().sendBroadcast(intent);

    }

    private String BuildExternalMunitionsQuery() {
        String xmlString = "";
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
                    .newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory
                    .newDocumentBuilder();
            Document doc = documentBuilder.newDocument();

            Element rootEl = doc.createElement("Current_Flights");
            Element catEl = doc.createElement("category");
            catEl.setAttribute("name", "lead");
            Element weaponEl = doc.createElement("weapon");
            weaponEl.setAttribute("name", "GBU-12");
            weaponEl.setAttribute("proneprotected", "130");
            weaponEl.setAttribute("standing", "175");
            weaponEl.setAttribute("prone", "200");
            weaponEl.setAttribute("description", "(500-lb LGB)");
            weaponEl.setAttribute("active", "false");
            weaponEl.setAttribute("id", "1");
            catEl.appendChild(weaponEl);
            rootEl.appendChild(catEl);
            doc.appendChild(rootEl);

            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();

            DOMSource domSource = new DOMSource(doc.getDocumentElement());
            OutputStream output = new ByteArrayOutputStream();
            StreamResult result = new StreamResult(output);

            transformer.transform(domSource, result);
            xmlString = output.toString();
        } catch (Exception ex) {
            Log.d(TAG, "Exception in BuildExternalMunitionsQuery: "
                    + ex.getMessage());
        }
        return xmlString;
    }

    /**
     * This is a simple example on how to save, retrieve and delete credentials in ATAK using the
     * credential management system.
     */
    private void saveAndRetrieveCredentials() {
        AtakAuthenticationDatabase.saveCredentials("helloworld.plugin", "",
                "username", "password", false);
        // can also specify a host if needed
        AtakAuthenticationCredentials aac = AtakAuthenticationDatabase
                .getCredentials("helloworld.plugin", "");
        if (aac != null) {
            Log.d(TAG, "credentials: " + aac.username + " " + aac.password);
        }
        AtakAuthenticationDatabase.delete("helloworld.plugin", "");

        aac = AtakAuthenticationDatabase.getCredentials("helloworld.plugin",
                "");
        if (aac == null)
            Log.d(TAG, "deleted credentials");
        else
            Log.d(TAG, "credentials: " + aac.username + " " + aac.password);

    }
}
