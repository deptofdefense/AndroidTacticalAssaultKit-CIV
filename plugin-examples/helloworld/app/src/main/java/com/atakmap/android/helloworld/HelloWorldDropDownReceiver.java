
package com.atakmap.android.helloworld;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ClipboardManager;

import com.atakmap.android.helloworld.heatmap.GLSimpleHeatMapLayer;
import com.atakmap.android.helloworld.heatmap.SimpleHeatMapLayer;
import com.atakmap.android.preference.AtakPreferences;

import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.app.AlertDialog;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import android.os.Build;

import com.atakmap.android.cot.detail.SensorDetailHandler;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.helloworld.image.MapScreenshotExample;
import com.atakmap.android.helloworld.layers.LayerDownloadExample;
import com.atakmap.android.helloworld.menu.MenuFactory;
import com.atakmap.android.helloworld.view.ViewOverlayExample;
import com.atakmap.android.maps.Association;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MultiPolyline;
import com.atakmap.android.maps.SensorFOV;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.android.util.SimpleItemSelectedListener;
import com.atakmap.android.video.StreamManagementUtils;
import com.atakmap.android.video.ConnectionEntry;
import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.helloworld.recyclerview.RecyclerViewDropDown;
import com.atakmap.android.helloworld.speechtotext.SpeechBloodHound;
import com.atakmap.android.helloworld.speechtotext.SpeechBrightness;
import com.atakmap.android.helloworld.speechtotext.SpeechDetailOpener;
import com.atakmap.android.helloworld.speechtotext.SpeechItemRemover;
import com.atakmap.android.helloworld.speechtotext.SpeechLinker;
import com.atakmap.android.helloworld.speechtotext.SpeechNavigator;
import com.atakmap.android.helloworld.speechtotext.SpeechNineLine;
import com.atakmap.android.helloworld.speechtotext.SpeechPointDropper;
import com.atakmap.android.helloworld.speechtotext.SpeechToActivity;
import com.atakmap.android.image.quickpic.QuickPicReceiver;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.routes.Route.RouteMethod;

import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.menu.ActionBroadcastData;
import com.atakmap.android.tools.menu.ActionBroadcastExtraStringData;
import com.atakmap.android.tools.menu.ActionClickData;
import com.atakmap.android.tools.menu.ActionMenuData;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AbstractMapItemSelectionTool;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.CameraController;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.android.helloworld.samplelayer.*;

import com.atakmap.android.routes.RouteNavigator;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.comms.CotStreamListener;
import com.atakmap.android.menu.PluginMenuParser;
import com.atakmap.comms.app.CotPortListActivity;
import com.atakmap.comms.app.CotPortListActivity.CotPort;
import com.atakmap.android.chat.ChatManagerMapComponent;
import com.atakmap.android.contact.PluginConnector;
import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.android.util.AttachmentManager;

import com.atakmap.android.gui.CoordDialogView;

import com.atakmap.android.util.NotificationUtil;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.android.icons.UserIcon;

import android.graphics.Bitmap;

import com.atakmap.comms.CommsMapComponent;
import com.atakmap.android.drawing.mapItems.DrawingRectangle;
import com.atakmap.android.importfiles.sort.ImportMissionPackageSort.ImportMissionV1PackageSort;

import android.os.Bundle;
import android.os.Environment;

import java.io.ByteArrayOutputStream;
import java.io.File;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapData;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.coremap.cot.event.CotEvent;

import com.atakmap.android.toolbar.widgets.TextContainer;

import android.widget.Toast;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.javacodegeeks.android.contentprovidertest.BirthProvider;

import android.net.Uri;
import android.content.ContentValues;

import com.atakmap.comms.NetConnectString;
import com.atakmap.android.contact.Connector;
import com.atakmap.android.contact.IpConnector;
import com.atakmap.android.contact.Contacts;
import com.atakmap.android.contact.Contact;
import com.atakmap.android.contact.IndividualContact;

import android.os.SystemClock;

import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.util.Circle;
import com.atakmap.android.maps.Ellipse;

import com.atakmap.android.emergency.tool.EmergencyManager;
import com.atakmap.android.emergency.tool.EmergencyType;
import com.atakmap.android.maps.MapActivity;
import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapView.RenderStack;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.helloworld.plugin.R;

import com.atakmap.coremap.maps.coords.GeoPoint;

import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import android.content.DialogInterface;

import android.graphics.Color;

import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteMapComponent;
import com.atakmap.android.routes.RouteMapReceiver;

import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.elevation.ElevationData;

import com.atakmap.coremap.log.Log;

import android.app.Activity;

import java.io.FileOutputStream;
import java.lang.*;
import java.util.*;

import android.app.Notification;
import android.app.NotificationManager;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.core.content.ContextCompat;

/**
 * The DropDown Receiver should define the visual experience
 * that a user might have while using this plugin.   At a
 * basic level, the dropdown can be a view of your own design
 * that is inflated.   Please be wary of the type of context
 * you use.   As noted in the Map Component, there are two
 * contexts - the plugin context and the atak context.
 * When using the plugin context - you cannot build thing or
 * post things to the ui thread.   You use the plugin context
 * to lookup resources contained specifically in the plugin.
 */
public class HelloWorldDropDownReceiver extends DropDownReceiver implements
        OnStateListener, SensorEventListener {

    private final NotificationManager nm;

    public static final String TAG = "HelloWorldDropDownReceiver";

    public static final String SHOW_HELLO_WORLD = "com.atakmap.android.helloworld.SHOW_HELLO_WORLD";
    public static final String CHAT_HELLO_WORLD = "com.atakmap.android.helloworld.CHAT_HELLO_WORLD";
    public static final String SEND_HELLO_WORLD = "com.atakmap.android.helloworld.SEND_HELLO_WORLD";
    public static final String LAYER_DELETE = "com.atakmap.android.helloworld.LAYER_DELETE";
    public static final String LAYER_VISIBILITY = "com.atakmap.android.helloworld.LAYER_VISIBILITY";
    private final View helloView;

    private final Context pluginContext;
    private final Contact helloContact;
    private RouteEventListener routeEventListener = null;
    private final HelloWorldMapOverlay mapOverlay;
    private final RecyclerViewDropDown recyclerView;
    private final TabViewDropDown tabView;

    // example menu factory
    final MenuFactory menuFactory;

    // inspection map selector
    final InspectionMapItemSelectionTool imis;

    private Timer issTimer = null;

    private Route r;

    private ExampleLayer exampleLayer;
    private final Map<Integer, ExampleMultiLayer> exampleMultiLayers = new HashMap<>();
    private SimpleHeatMapLayer simpleHeatMapLayer;

    private JoystickListener _joystickView;


    private LayerDownloadExample layerDownloader;

    private double currWidth = HALF_WIDTH;
    private double currHeight = HALF_HEIGHT;

    private final CameraActivity.CameraDataListener cdl = new CameraActivity.CameraDataListener();
    private final CameraActivity.CameraDataReceiver cdr = new CameraActivity.CameraDataReceiver() {
        public void onCameraDataReceived(Bitmap b) {
            Log.d(TAG, "==========img received======>" + b);
            b.recycle();
        }
    };

    private final SpeechToTextActivity.SpeechDataListener sd1 = new SpeechToTextActivity.SpeechDataListener();
    private final SpeechToTextActivity.SpeechDataReceiver sdr = new SpeechToTextActivity.SpeechDataReceiver() {
        public void onSpeechDataReceived(HashMap<String, String> s) {
            Log.d(TAG, "==========speech======>" + s);
            createSpeechMarker(s);

        }
    };
    /**
     * This receives the intent from SpeechToActivity.
     * It uses the info from the activityInfoBundle to decide
     * what to do next. The bundle always contains a destination
     * and an activity intent. Other stuff is added on a case-by-case basis.
     * See SpeechToActivity for more details.
     */
    private final SpeechToActivity.SpeechDataListener sd1a = new SpeechToActivity.SpeechDataListener();
    private final SpeechToActivity.SpeechDataReceiver sdra = new SpeechToActivity.SpeechDataReceiver() {
        /**
         * This receives the activityInfoBundle from SpeechToActivity. The switch case decides what classes to call
         * @param activityInfoBundle - Bundle containing the activity intent, destination, origin, marker type and more.
         */
        public void onSpeechDataReceived(Bundle activityInfoBundle) {
            MapView view = getMapView();
            switch (activityInfoBundle
                    .getInt(SpeechToActivity.ACTIVITY_INTENT)) {
                //This case is for drawing and navigating routes
                case SpeechToActivity.NAVIGATE_INTENT:
                    new SpeechNavigator(view,
                            activityInfoBundle
                                    .getString(SpeechToActivity.DESTINATION),
                            activityInfoBundle
                                    .getBoolean(SpeechToActivity.QUICK_INTENT));
                    break;
                // This case is for plotting down markers
                case SpeechToActivity.PLOT_INTENT:
                    new SpeechPointDropper(
                            activityInfoBundle
                                    .getString(SpeechToActivity.DESTINATION),
                            view, pluginContext);
                    break;
                //This case is for bloodhounding to markers,routes, or addresses
                case SpeechToActivity.BLOODHOUND_INTENT:
                    new SpeechBloodHound(view,
                            activityInfoBundle
                                    .getString(SpeechToActivity.DESTINATION),
                            pluginContext);
                    break;
                //This case is for launching the 9 Line window on a target
                case SpeechToActivity.NINE_LINE_INTENT:
                    new SpeechNineLine(
                            activityInfoBundle
                                    .getString(SpeechToActivity.DESTINATION),
                            view, pluginContext);
                    break;
                //DOESNT WORK//This case is to open the compass on your self marker
                case SpeechToActivity.COMPASS_INTENT:
                    AtakBroadcast.getInstance()
                            .sendBroadcast(new Intent()
                                    .setAction(
                                            "com.atakmap.android.maps.COMPASS")
                                    .putExtra("targetUID",
                                            view.getSelfMarker().getUID()));
                    break;
                //This case toggles the brightness slider
                case SpeechToActivity.BRIGHTNESS_INTENT:
                    new SpeechBrightness(view, pluginContext, activityInfoBundle
                            .getString(SpeechToActivity.DESTINATION));
                    break;
                //this case deletes a shape, marker, or route
                case SpeechToActivity.DELETE_INTENT:
                    new SpeechItemRemover(
                            activityInfoBundle
                                    .getString(SpeechToActivity.DESTINATION),
                            view, pluginContext);
                    break;
                //this case opens the hostiles window from fire tools
                case SpeechToActivity.SHOW_HOSTILES_INTENT:
                    AtakBroadcast.getInstance()
                            .sendBroadcast(new Intent().setAction(
                                    "com.atakmap.android.maps.MANAGE_HOSTILES"));
                    break;
                //This case opens a markers detail menu
                case SpeechToActivity.OPEN_DETAILS_INTENT:
                    new SpeechDetailOpener(activityInfoBundle
                            .getString(SpeechToActivity.DESTINATION), view);
                    break;
                //this case starts an emergency
                case SpeechToActivity.EMERGENCY_INTENT:
                    EmergencyManager.getInstance()
                            .setEmergencyType(EmergencyType.fromDescription(
                                    activityInfoBundle.getString(
                                            SpeechToActivity.EMERGENCY_TYPE)));
                    EmergencyManager.getInstance().initiateRepeat(
                            EmergencyType.fromDescription(
                                    activityInfoBundle.getString(
                                            SpeechToActivity.EMERGENCY_TYPE)),
                            false);
                    EmergencyManager.getInstance().setEmergencyOn(true);
                    break;
                //This case draws a R&B line between 2 map items
                case SpeechToActivity.LINK_INTENT:
                    new SpeechLinker(
                            activityInfoBundle
                                    .getString(SpeechToActivity.DESTINATION),
                            view, pluginContext);
                    break;
                //This case launches the camera
                case SpeechToActivity.CAMERA_INTENT:
                    AtakBroadcast.getInstance().sendBroadcast(
                            new Intent().setAction(QuickPicReceiver.QUICK_PIC));
                    break;
                default:
                    Toast.makeText(getMapView().getContext(),
                            "I did not understand please try again",
                            Toast.LENGTH_SHORT).show();

            }
        }
    };

    private final CotServiceRemote csr;
    private boolean connected = false;

    final CotServiceRemote.ConnectionListener cl = new CotServiceRemote.ConnectionListener() {
        @Override
        public void onCotServiceConnected(Bundle fullServiceState) {
            Log.d(TAG, "onCotServiceConnected: ");
            connected = true;
        }

        @Override
        public void onCotServiceDisconnected() {
            Log.d(TAG, "onCotServiceDisconnected: ");
            connected = false;
        }

    };

    final CotStreamListener csl;
    final CotServiceRemote.OutputsChangedListener _outputsChangedListener = new CotServiceRemote.OutputsChangedListener() {
        @Override
        public void onCotOutputRemoved(Bundle descBundle) {
            Log.d(TAG, "stream removed");
        }

        @Override
        public void onCotOutputUpdated(Bundle descBundle) {
            Log.v(TAG,
                    "Received ADD message for "
                            + descBundle
                                    .getString(CotPort.DESCRIPTION_KEY)
                            + ": enabled="
                            + descBundle.getBoolean(
                                    CotPort.ENABLED_KEY, true)
                            + ": connected="
                            + descBundle.getBoolean(
                                    CotPort.CONNECTED_KEY, false));
        }
    };

    /**************************** CONSTRUCTOR *****************************/

    public HelloWorldDropDownReceiver(final MapView mapView,
            final Context context, HelloWorldMapOverlay overlay) {
        super(mapView);
        this.pluginContext = context;
        this.mapOverlay = overlay;
        final Activity parentActivity = (Activity) mapView.getContext();

        _joystickView = new JoystickListener();

        csr = new CotServiceRemote();
        csr.setOutputsChangedListener(_outputsChangedListener);

        csr.connect(cl);

        imis = new InspectionMapItemSelectionTool();

        csl = new CotStreamListener(mapView.getContext(), TAG, null) {
            @Override
            public void onCotOutputRemoved(Bundle bundle) {
                Log.d(TAG, "stream outputremoved");
            }

            @Override
            protected void enabled(CotPortListActivity.CotPort port,
                    boolean enabled) {
                Log.d(TAG, "stream enabled");
            }

            @Override
            protected void connected(CotPortListActivity.CotPort port,
                    boolean connected) {
                Log.d(TAG, "stream connected");
            }

            @Override
            public void onCotOutputUpdated(Bundle descBundle) {
                Log.d(TAG, "stream added/updated");
            }

        };

        printNetworks();

        AtakBroadcast.DocumentedIntentFilter dif = new AtakBroadcast.DocumentedIntentFilter(
                "com.atakmap.android.helloworld.FAKE_PHONE_CALL");
        AtakBroadcast.getInstance().registerReceiver(fakePhoneCallReceiver,
                dif);

        // If you are using a custom layout you need to make use of the PluginLayoutInflator to clear
        // out the layout cache so that the plugin can be properly unloaded and reloaded.
        helloView = PluginLayoutInflater.inflate(pluginContext,
                R.layout.hello_world_layout, null);
        // Add "Hello World" contact
        this.helloContact = addPluginContact(pluginContext.getString(
                R.string.hello_world));

        //Find buttons by id and implement code for long click
        View.OnLongClickListener longClickListener = new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                int id = v.getId();
                if (id == R.id.smallerButton) {
                    toast(context.getString(R.string.smallerButton));
                } else if (id == R.id.largerButton) {
                    toast(context.getString(R.string.largerButton));
                } else if (id == R.id.showSearchIcon) {
                    toast(context.getString(R.string.showSeachIcon));
                } else if (id == R.id.fly) {
                    toast(context.getString(R.string.fly));
                } else if (id == R.id.specialWheelMarker) {
                    toast(context.getString(R.string.specialWheelMarker));
                } else if (id == R.id.addAnAircraft) {
                    toast(context.getString(R.string.addAnAircraft));
                } else if (id == R.id.svgMarker) {
                    toast(context.getString(R.string.svgMarker));
                } else if (id == R.id.staleoutMarker) {
                    toast(context.getString(R.string.staleoutMarker));
                } else if (id == R.id.addStream) {
                    toast(context.getString(R.string.addStream));
                } else if (id == R.id.removeStream) {
                    toast(context.getString(R.string.removeStream));
                } else if (id == R.id.itemInspect) {
                    toast(context.getString(R.string.itemInspect));
                } else if (id == R.id.customType) {
                    toast(context.getString(R.string.customType));
                } else if (id == R.id.customMenuDefault) {
                    toast(context.getString(R.string.customMenuDefault));
                } else if (id == R.id.issLocation) {
                    toast(context.getString(R.string.issLocation));
                } else if (id == R.id.sensorFOV) {
                    toast(context.getString(R.string.sensorFOV));
                } else if (id == R.id.listRoutes) {
                    toast(context.getString(R.string.listRoutes));
                } else if (id == R.id.addXRoute) {
                    toast(context.getString(R.string.addXRoute));
                } else if (id == R.id.reXRoute) {
                    toast(context.getString(R.string.reXRoute));
                } else if (id == R.id.dropRoute) {
                    toast(context.getString(R.string.dropRoute));
                } else if (id == R.id.emergency) {
                    toast(context.getString(R.string.emergency));
                } else if (id == R.id.no_emergency) {
                    toast(context.getString(R.string.no_emergency));
                } else if (id == R.id.addRectangle) {
                    toast(context.getString(R.string.addRectangle));
                } else if (id == R.id.drawShapes) {
                    toast(context.getString(R.string.drawShapes));
                } else if (id == R.id.groupAdd) {
                    toast("Add a shape to a custom group called MyCustomGroup for rendering in the overlay manager");
                } else if (id == R.id.associations) {
                    toast("Demonstrate the proper way to add two map items and an association");
                } else if (id == R.id.rbcircle) {
                    toast(context.getString(R.string.rbcircle));
                } else if (id == R.id.externalGps) {
                    toast(context.getString(R.string.externalGps));
                } else if (id == R.id.surfaceAtCenter) {
                    toast(context.getString(R.string.surfaceAtCenter));
                } else if (id == R.id.fakeContentProvider) {
                    toast(context.getString(R.string.fakeContentProvider));
                } else if (id == R.id.pluginNotification) {
                    toast(context.getString(R.string.pluginNotification));
                } else if (id == R.id.notificationSpammer) {
                    toast(context.getString(R.string.notificationSpammer));
                } else if (id == R.id.notificationWithOptions) {
                    toast(context
                            .getString(R.string.notificationWithOptions));
                } else if (id == R.id.videoLauncher) {
                    toast(context.getString(R.string.videoLauncher));
                } else if (id == R.id.addToolbarItem) {
                    toast(context.getString(R.string.addToolbarItem));
                } else if (id == R.id.cameraLauncher) {
                    toast(context.getString(R.string.cameraLauncher));
                } else if (id == R.id.imageAttach) {
                    toast(context.getString(R.string.imageAttach));
                } else if (id == R.id.webView) {
                    toast(context.getString(R.string.webView));
                } else if (id == R.id.addLayer) {
                    toast(context.getString(R.string.addLayer));
                } else if (id == R.id.bumpControl) {
                    toast(context.getString(R.string.bumpControl));
                } else if (id == R.id.speechToActivity) {
                    toast(context.getString(R.string.speechToActivity));
                } else if (id == R.id.btnHookNavigationEvents) {
                    toast(context.getString(R.string.hookNavigation));
                } else if (id == R.id.downloadMapLayer) {
                    toast(context
                            .getString(R.string.download_map_layer_msg));
                } else if (id == R.id.mapScreenshot) {
                    toast(context.getString(R.string.map_screenshot_desc));
                }
                return true;
            }
        };

        // The button bellow shows how one might go about
        // programatically changing the size of the drop down.
        final Button smaller = helloView
                .findViewById(R.id.smallerButton);
        smaller.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                resize(THIRD_WIDTH, FULL_HEIGHT);
            }
        });

        // The button bellow shows how one might go about
        // programatically changing the size of the drop down.
        final Button larger = helloView
                .findViewById(R.id.largerButton);
        larger.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                resize(FULL_WIDTH, HALF_HEIGHT);
            }
        });

        // The button bellow shows how one might go about
        // programatically flying through a list of points.
        // In this case they are synthetically generated.
        // They could just as easily be points on a route, etc.
        final Button fly = helloView.findViewById(R.id.fly);
        fly.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    public void run() {

                        CameraController.Programmatic.zoomTo( getMapView().getRenderer3(),
                                .00001d, false);
                        for (int i = 0; i < 20; ++i) {
                            CameraController.Programmatic.panTo(
                                    getMapView().getRenderer3(),
                                    new GeoPoint(42, -79 - (double) i / 100), false);

                            try {
                                Thread.sleep(1000);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }).start();
            }
        });

        // The button bellow shows how one might go about
        // overriding the button of a specific marker.
        // The ATAK core does not allow someone to override
        // all markers of a specific type with a new menu.
        // This can be done by a combination of searching
        // items on a map and replacing the menu on start,
        // and then listening for ITEM_ADDED for each
        // additional placement of a new item.
        final Button wheel = helloView
                .findViewById(R.id.specialWheelMarker);
        wheel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                createUnit();

            }
        });

        final Button addAnAircraft = helloView
                .findViewById(R.id.addAnAircraft);
        addAnAircraft.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                createAircraftWithRotation();
            }
        });

        final Button svgMarker = helloView
                .findViewById(R.id.svgMarker);
        svgMarker.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String uid = UUID.randomUUID().toString();
                GeoPoint location = getMapView().getCenterPoint().get();

                Marker marker = new Marker(location, uid);
                marker.setAlwaysShowText(true);
                marker.setTitle("HelloWorld");
                marker.setType("custom-type");
                marker.setTouchable(true);

                Bitmap icon = getBitmap(pluginContext,
                        R.drawable.svg_example);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                icon.compress(Bitmap.CompressFormat.PNG, 100, baos);
                byte[] b = baos.toByteArray();
                String encoded = "base64://" + android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP | Base64.URL_SAFE);


                Icon.Builder markerIconBuilder = new Icon.Builder().
                        setImageUri(0, encoded);

                marker.setIcon(markerIconBuilder.build());

                getMapView().getRootGroup().addItem(marker);

            }
        });

        // The button bellow shows how one might go about
        // programmatically listing all routes on the map.
        final Button listRoutes = helloView
                .findViewById(R.id.listRoutes);
        listRoutes.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                RouteMapReceiver routeMapReceiver = getRouteMapReceiver();
                if (routeMapReceiver == null)
                    return;

                AlertDialog.Builder builderSingle = new AlertDialog.Builder(
                        mapView.getContext());
                builderSingle.setTitle("Select a Route");
                final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(
                        pluginContext,
                        android.R.layout.select_dialog_singlechoice);

                for (Route route : routeMapReceiver.getCompleteRoutes()) {
                    arrayAdapter.add(route.getTitle());
                }
                builderSingle.setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss();
                            }
                        });
                builderSingle.setAdapter(arrayAdapter, null);
                builderSingle.show();
            }

        });

        // The button bellow shows how one might go about
        // setting up a custom map widget.
        final Button showSearchIcon = helloView
                .findViewById(R.id.showSearchIcon);
        showSearchIcon.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "sending broadcast SHOW_MY_WACKY_SEARCH");
                Intent intent = new Intent("SHOW_MY_WACKY_SEARCH");
                AtakBroadcast.getInstance()
                        .sendBroadcast(intent);

            }
        });

        recyclerView = new RecyclerViewDropDown(getMapView(), pluginContext);
        helloView.findViewById(R.id.recyclerViewBtn)
                .setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setRetain(true);
                        recyclerView.show();
                    }
                });

        tabView = new TabViewDropDown(getMapView(), pluginContext);
        helloView.findViewById(R.id.tabViewBtn)
                .setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setRetain(true);
                        tabView.show();
                    }
                });

        View overlayViewBtn = helloView.findViewById(R.id.overlayViewBtn);
        overlayViewBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        ViewOverlayExample.TOGGLE_OVERLAY_VIEW));
            }
        });

        // The button bellow shows how one might go about
        // programatically add a route to the system. Adding
        // an array of points will be much faster than adding
        // them one at a time.
        final Button addRoute = helloView.findViewById(R.id.addXRoute);
        addRoute.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "creating a quick route");
                GeoPointMetaData sp = getMapView().getPointWithElevation();
                r = new Route(getMapView(),
                        "My Route",
                        Color.WHITE, "CP",
                        UUID.randomUUID().toString());

                Marker[] m = new Marker[5];
                for (int i = 0; i < 5; ++i) {
                    GeoPoint x = new GeoPoint(
                            sp.get().getLatitude() + (i * .0001),
                            sp.get().getLongitude());

                    // the first call will trigger a refresh each time across all of the route points
                    //r.addMarker(Route.createWayPoint(x, UUID.randomUUID().toString()));
                    m[i] = Route
                            .createWayPoint(GeoPointMetaData.wrap(x),
                                    UUID.randomUUID().toString());
                }
                r.addMarkers(0, m);

                MapGroup _mapGroup = getMapView().getRootGroup()
                        .findMapGroup("Route");
                _mapGroup.addItem(r);

                r.persist(getMapView().getMapEventDispatcher(), null,
                        this.getClass());
                Log.d(TAG, "route created");

            }
        });

        final Button reRoute = helloView.findViewById(R.id.reXRoute);
        reRoute.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (r == null) {
                    toast("No Route added during this run");
                    return;
                }

                GeoPointMetaData sp = getMapView().getPointWithElevation();
                PointMapItem[] m = new PointMapItem[16];
                for (int i = 1; i < m.length; ++i) {
                    if (i % 2 == 0) {
                        GeoPoint x = new GeoPoint(sp.get().getLatitude()
                                - (i * .0001),
                                sp.get().getLongitude() + (i * .0001),
                                GeoPoint.UNKNOWN);

                        // the first call will trigger a refresh each time across all of the route points
                        //r.addMarker(2, Route.createWayPoint(x, UUID.randomUUID().toString()));
                        m[i - 1] = Route.createWayPoint(
                                GeoPointMetaData.wrap(x), UUID.randomUUID()
                                        .toString());
                    } else {
                        GeoPoint x = new GeoPoint(sp.get().getLatitude()
                                + (i * .0002),
                                sp.get().getLongitude() + (i * .0002),
                                GeoPoint.UNKNOWN);
                        m[i - 1] = Route.createControlPoint(x, UUID
                                .randomUUID().toString());
                    }
                }
                r.addMarkers(2, m);
            }
        });

        final Button dropRoute = helloView
                .findViewById(R.id.dropRoute);
        dropRoute.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Route _route = new Route(getMapView(), "my flying route",
                        Color.RED, "cp", UUID.randomUUID().toString());
                _route.setRouteMethod(RouteMethod.Flying.toString());
                RouteMapReceiver _receiver = RouteMapReceiver.getInstance();
                // Finalize route and show details
                _route.setMetaString("entry", "user");
                _receiver.getRouteGroup().addItem(_route);
                _route.setVisible(true);
                _receiver.showRouteDetails(_route, null, true);
                getMapView().post(new Runnable() {
                    @Override
                    public void run() {
                        DropDownManager.getInstance().hidePane();
                    }
                });

            }
        });

        final Button emergency = helloView
                .findViewById(R.id.emergency);
        emergency.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                EmergencyManager.getInstance().initiateRepeat(
                        EmergencyType.NineOneOne, false);
            }
        });

        final Button noemergency = helloView
                .findViewById(R.id.no_emergency);
        noemergency.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                EmergencyManager.getInstance().cancelRepeat(
                        EmergencyType.NineOneOne, false);
            }
        });

        final Button rbCircle = helloView.findViewById(R.id.rbcircle);
        rbCircle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                MapItem mi = getMapView().getMapItem("detect-ae:3e:ee");
                if (mi == null) {

                    PlacePointTool.MarkerCreator mc = new PlacePointTool.MarkerCreator(
                            new GeoPoint(32, -72. - 100));
                    mc.setUid("detect-ae:3e:ee");
                    mc.setCallsign("detect 1");
                    mc.setType("a-h-G");
                    mc.showCotDetails(false);
                    mc.setNeverPersist(true);
                    Marker m = mc.placePoint();

                    // demonstrate the ability to define a base 64 image using the base64:// url
                    m.setIcon(new Icon(
                            "base64:\\iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAABHNCSVQICAgIfAhkiAAAAmJJREFUWIXNl79LG1EcwD93zSWN2oJ1ECQEDoyg2Ey1UHAJFUIFhyq0S0PAsX9Cl0KnQql_QDOeFETo0pLWQTMUDR10UIpVA-mQRRKjQ7locrnr0CTkmmt-3J3aL3yH-_Le-3zeu8e7d-AsQrW8lggBR7W8cokQcCSJYkkSxdJVSzTgG_F4dSMerzqREGzAk5IoBtZiMW9ElkWAVDarRxWlXNH1HDBbk3FdwBJeD7sS3Qq0hTuR6EagK7hdiU4CPcHtSLQTsAXvVeJfAo7gvUhYCbgC71bib4EW-IWm8WJ9nYWJCS40jU-Hh6YOwwMDPJ-aYimdbgx4y-fjoSxzd3i4o0Tz7CxnXtF1ltJpfp6dkSkWSezs0O_1NrJPkjjXNF6mUvwoFDjXND4eHHA_kWD3-BiAiCyLa7GYVxLFAJCk6cT0tINbxW2fj1eRiKlWUFUAYuEwj0IhDMPg8coKbzY3UebnTRJRRQlUdD1ZXwmxFzjAiaryZHW1kcu7uy1tBEFgbmyMz5mMqW61Ep6W3h3CL0ksjI83nseGhizbCYLATU_n4T382RCzFV1PRhWl4yr0SRJPJydNtforaI5vuRz3RkZMNavNWFfsWuJXuczbrS1T7Vk4DMCH_X2-5_NkT095t73N18XFtnCAG03jFIEvumHMvd_b658OBkV5cFAwDINytcqDQACfx4NhGORV1ZTR0VEKqooBnJRK3PH7eT0zw3Qw2BYO_-FB5KqE3aPYFQmnHyNHEm59jm1JuH0h6Unisq5kXUlc9qW0rYSTa7mdcPXHxLHEdcBNEtcFb5ZwBP8NALbcQk1BI7gAAAAASUVORK5CYII="));

                    createEllipse(m);
                    //createCircle(m);

                    // In order to persist a circle, the center marker must be
                    // persisted as opposed to the circle itself
                    m.persist(mapView.getMapEventDispatcher(), null,
                            HelloWorldDropDownReceiver.class);
                } else {
                    toast("marker already placed");
                }
            }

        });

        final Button addRect = helloView
                .findViewById(R.id.addRectangle);
        addRect.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                MapGroup doGroup = mapView.getRootGroup()
                        .findMapGroup("Drawing Objects");

                // due to a bug in the code, this is required to be a child group that is the same
                // name as the title.   This will be fixed in 3.8 and the fix will likely contain
                // a change to the constructor
                MapGroup group = doGroup.addGroup("Test Rectangle");

                DrawingRectangle drawingRectangle = new DrawingRectangle(group,
                        GeoPointMetaData.wrap(new GeoPoint(10, 10)),
                        GeoPointMetaData.wrap(new GeoPoint(10, 5)),
                        GeoPointMetaData.wrap(new GeoPoint(5, 5)),
                        GeoPointMetaData.wrap(new GeoPoint(5, 10)),
                        UUID.randomUUID().toString());

                drawingRectangle.setStyle(0);
                drawingRectangle.setLineStyle(0);
                drawingRectangle.setFillColor(0x00000000);
                drawingRectangle.setStrokeColor(Color.WHITE);
                drawingRectangle.setMetaString("shape_name", "Test Rectangle");
                drawingRectangle.setMetaString("title", "Test Rectangle");
                drawingRectangle.setMetaString("callsign", "Test Rectangle");

                // then you need to add this to the parent group.
                doGroup.addItem(drawingRectangle);

                // example on how to dispatch this rectangle externally
                final CotEvent cotEvent = CotEventFactory
                        .createCotEvent(drawingRectangle);
                CotMapComponent.getExternalDispatcher()
                        .dispatchToBroadcast(cotEvent);

            }
        });

        final Button drawShapes = helloView
                .findViewById(R.id.drawShapes);
        drawShapes.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                drawShapes();
            }
        });

        final Button groupAdd = helloView.findViewById(R.id.groupAdd);
        groupAdd.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                MapGroup dmg = new DefaultMapGroup("MyCustomGroup");
                DefaultMapGroupOverlay dmo = new DefaultMapGroupOverlay(mapView,
                        dmg,
                        "android.resource://" + pluginContext.getPackageName()
                                + "/" + R.drawable.ic_launcher_badge);

                mapView.getRootGroup().addGroup(dmg);
                mapView.getMapOverlayManager().addOverlay(dmo);

                //Start of Overlay Menu Test ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                //Create a shape
                GeoPoint[] points = {
                        (new GeoPoint(43.08321804, -77.67835268)),
                        (new GeoPoint(43.09321804, -77.67835268)),
                        (new GeoPoint(43.09321804, -77.67935268)),
                        (new GeoPoint(43.08821804, -77.67895268)),
                        (new GeoPoint(43.08321804, -77.67935268)),
                        (new GeoPoint(43.08321804, -77.67835268))
                };
                DrawingShape ds = new DrawingShape(mapView,
                        UUID.randomUUID().toString());
                ds.setPoints(points);
                ds.setClickable(true);
                ds.setMetaBoolean("editable", true);
                ds.setClosed(true);
                ds.setStrokeColor(Color.DKGRAY);
                ds.setFillColor(Color.GREEN);
                ds.setTitle("test polygon");
                ds.setMetaBoolean("archive", false);

                dmg.addItem(ds);

                Marker point = new Marker(
                        new GeoPoint(43.10321804, -77.67835268),
                        UUID.randomUUID().toString());
                point.setType("a-u-g");
                point.setTitle("ovTest");
                point.setMetaString("callsign", "ovTest");
                point.setMetaString("title", "ovTest");

                point.setMetaBoolean("readiness", true);
                point.setMetaBoolean("archive", false);
                point.setMetaBoolean("editable", true);
                point.setMetaBoolean("movable", true);
                point.setMetaBoolean("removable", true);
                point.setMetaString("entry", "user");
                point.setShowLabel(true);

                dmg.addItem(point);

            }
        });

        final Button associations = helloView.findViewById(R.id.associations);
        associations.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                PlacePointTool.MarkerCreator mc = new PlacePointTool.MarkerCreator(new GeoPoint(43,-72));
                mc.setType("a-f");
                mc.setNeverPersist(true);
                PointMapItem point1 = mc.placePoint();

                mc = new PlacePointTool.MarkerCreator(new GeoPoint(43.1,-72.2));
                mc.setType("a-f");
                mc.setNeverPersist(true);
                PointMapItem point2 = mc.placePoint();

                Association a = new Association(point1, point2, UUID.randomUUID().toString());
                a.setColor(Color.RED);
                mapView.getRootGroup().addItem(a);

            }
        });

        final Button externalGps = helloView
                .findViewById(R.id.externalGps);
        externalGps.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Thread t = new Thread() {
                    public void run() {
                        runSim();
                    }
                };
                t.start();
            }
        });

        final Button staleout = helloView
                .findViewById(R.id.staleoutMarker);
        staleout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                PlacePointTool.MarkerCreator mc = new PlacePointTool.MarkerCreator(
                        getMapView().getCenterPoint());
                mc.showCotDetails(false);
                mc.setArchive(false); // allows for the creation of CoT but marks it so it
                // does not persist.
                mc.setType("a-f-A");
                mc.setCallsign("WT888");
                final Marker m = mc.placePoint();
                m.setMetaLong("lastUpdateTime",
                        new CoordinatedTime().getMilliseconds());
                m.setMetaLong("autoStaleDuration", 20000);
                m.setMetaBoolean("movable", false);
                m.setTrack(280, 50);
                m.setMetaDouble("Speed", 50d);
                m.setStyle(m.getStyle()
                        | Marker.STYLE_ROTATE_HEADING_MASK);

                final CotEvent cotEvent = CotEventFactory
                        .createCotEvent(m);
                CotMapComponent.getExternalDispatcher()
                        .dispatchToBroadcast(cotEvent);

            }
        });

        final Button surfaceAtCenter = helloView
                .findViewById(R.id.surfaceAtCenter);
        surfaceAtCenter.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                ElevationManager.QueryParameters DSM_FILTER = new ElevationManager.QueryParameters();
                DSM_FILTER.elevationModel = ElevationData.MODEL_SURFACE;
                ElevationManager.QueryParameters DTM_FILTER = new ElevationManager.QueryParameters();
                DTM_FILTER.elevationModel = ElevationData.MODEL_TERRAIN;

                double terrain;
                double surface;
                GeoPointMetaData terrainMetaData = new GeoPointMetaData();
                GeoPointMetaData surfaceMetaData = new GeoPointMetaData();

                GeoPoint point = mapView.getCenterPoint().get();
                if (point != null) {
                    // pull terrain
                    terrain = ElevationManager.getElevation(
                            point.getLatitude(),
                            point.getLongitude(),
                            DTM_FILTER, terrainMetaData);
                    // pull surface
                    surface = ElevationManager.getElevation(
                            point.getLatitude(),
                            point.getLongitude(),
                            DSM_FILTER, surfaceMetaData);

                    toast("Terrain: " + terrain);
                    toast("Surface: " + surface);
                }
            }

        });

        final Button fakeContentProvider = helloView
                .findViewById(R.id.fakeContentProvider);
        fakeContentProvider.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                manipulateFakeContentProvider();
            }
        });

        final Button pluginNotification = helloView
                .findViewById(R.id.pluginNotification);
        pluginNotification.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent startServiceIntent = new Intent(
                        "com.atakmap.android.helloworld.notification.NotificationService");
                startServiceIntent
                        .setPackage("com.atakmap.android.helloworld.plugin");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    getMapView().getContext().startForegroundService(
                            startServiceIntent);
                else
                    getMapView().getContext().startService(
                            startServiceIntent);

            }
        });

        final Button addStream = helloView
                .findViewById(R.id.addStream);
        addStream.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "connected to the cotservice: " + connected);
                try {
                    if (connected) {
                        final File dir = new File(Environment
                                .getExternalStorageDirectory()
                                .getPath() + "/serverconnections");
                        File[] listing = dir.listFiles();
                        if (listing != null) {
                            for (File f : listing) {
                                Log.d(TAG, "found: " + f);

                                ImportMissionV1PackageSort importer = new ImportMissionV1PackageSort(
                                        getMapView().getContext(),
                                        true, true,
                                        false);
                                if (!importer.match(f)) {
                                    Toast.makeText(getMapView().getContext(),
                                            "failure [1]: " + f,
                                            Toast.LENGTH_SHORT).show();
                                } else {

                                    boolean success = importer.beginImport(f);
                                    if (success) {
                                        Toast.makeText(
                                                getMapView().getContext(),
                                                "success: " + f,
                                                Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(
                                                getMapView().getContext(),
                                                "failure [2]: " + f,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }

                            }
                        }
                    }
                } catch (Exception ioe) {
                    Log.d(TAG, "error: ", ioe);
                }
            }
        });

        final Button removeStream = helloView
                .findViewById(R.id.removeStream);
        removeStream.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "connected to the cotservice: " + connected);
                if (connected)
                    csr.removeStream("**");
            }
        });

        final Button itemInspect = helloView
                .findViewById(R.id.itemInspect);
        itemInspect.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean val = itemInspect.isSelected();
                if (val) {
                    imis.requestEndTool();
                } else {

                    AtakBroadcast.getInstance().registerReceiver(
                            inspectionReceiver,
                            new AtakBroadcast.DocumentedIntentFilter(
                                    "com.atakmap.android.helloworld.InspectionMapItemSelectionTool.Finished"));
                    Bundle extras = new Bundle();
                    ToolManagerBroadcastReceiver.getInstance().startTool(
                            "com.atakmap.android.helloworld.InspectionMapItemSelectionTool",
                            extras);

                }
                itemInspect.setSelected(!val);
            }
        });

        final Button cameraLauncher = helloView
                .findViewById(R.id.cameraLauncher);
        cameraLauncher.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                cdl.register(getMapView().getContext(), cdr);
                // this makes use of an activity that cannot know anything
                // about ATAK.   This is the same problem as we have with
                // notifications.  They run outside of the current ATAK
                // classloader paradigm.
                Intent intent = new Intent();
                intent.setClassName("com.atakmap.android.helloworld.plugin",
                        "com.atakmap.android.helloworld.CameraActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getMapView().getContext().startActivity(intent);
            }
        });

        final Button bumpControl = helloView
                .findViewById(R.id.bumpControl);
        bumpControl.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean b = bumpControl.isSelected();
                bumpControl.setSelected(!b);
                SensorManager sensorManager = (SensorManager) context
                        .getSystemService(Context.SENSOR_SERVICE);

                if (!b) {
                    TextContainer.getTopInstance()
                            .displayPrompt(
                                    "Tilt the phone to perform an action");

                    sensorManager.registerListener(
                            HelloWorldDropDownReceiver.this,
                            sensorManager.getDefaultSensor(
                                    Sensor.TYPE_ACCELEROMETER),
                            SensorManager.SENSOR_DELAY_NORMAL);

                } else {
                    sensorManager.unregisterListener(
                            HelloWorldDropDownReceiver.this);
                    TextContainer.getTopInstance().closePrompt();

                }
            }
        });

        /* Functionallity implemented into SpeechToActivity: SpeechPointDropper specifically
        final Button speechToText = (Button) helloView
                .findViewById(R.id.speechToText);
        speechToText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sd1.register(getMapView().getContext(), sdr);
                // this makes use of an activity that cannot know anything
                // about ATAK.   This is the same problem as we have with
                // notifications.  They run outside of the current ATAK
                // classloader paradigm.
                Intent intent = new Intent();
                intent.setClassName("com.atakmap.android.helloworld.plugin",
                        "com.atakmap.android.helloworld.SpeechToTextActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("EXTRA_MESSAGE", "");
                parentActivity.startActivityForResult(intent, 0);
        
            }
        });*/

        final Button speechToActivity = helloView
                .findViewById(R.id.speechToActivity);
        speechToActivity.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sd1a.register(getMapView().getContext(), sdra);
                // this makes use of an activity that cannot know anything
                // about ATAK.   This is the same problem as we have with
                // notifications.  They run outside of the current ATAK
                // classloader paradigm.
                Intent intent = new Intent();
                intent.setClassName("com.atakmap.android.helloworld.plugin",
                        "com.atakmap.android.helloworld.speechtotext.SpeechToActivity");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("EXTRA_MESSAGE", "");
                parentActivity.startActivityForResult(intent, 0);

            }
        });

        final Button customType = helloView
                .findViewById(R.id.customType);
        customType.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final Marker m = new Marker(
                        getMapView().getPointWithElevation(),
                        UUID.randomUUID().toString());
                Log.d(TAG, "creating a new marker for: " + m.getUID());
                m.setType("b-g-n-M-O-B");
                m.setMetaString("how", "h-g-i-g-o");
                m.setMetaString("callsign", "Custom Marker");
                m.setTitle("Custom Marker");
                m.setMetaString("menu", "menus/a-n.xml");

                // prevents the icon from changing automatically
                m.setMetaBoolean("adapt_marker_icon", false);

                Icon.Builder iBuilder = new Icon.Builder().setImageUri(0,
                        "android.resource://" + pluginContext.getPackageName()
                                + "/" + R.drawable.abc);

                //iBuilder = new Icon.Builder().setImageUri(0,
                //        "file:///sdcard/custom_marker.png");
                m.setIcon(iBuilder.build());

                MapGroup _mapGroup = getMapView().getRootGroup()
                        .findMapGroup("Cursor on Target");
                _mapGroup.addItem(m);

                // looking for the color red for the text
                Thread t = new Thread() {
                    public void run() {
                        try {
                            Thread.sleep(10000);
                        } catch (Exception ignored) {
                        }
                        m.setTextColor(0xFFFF0000);
                        Log.d(TAG, "text color set");

                    }
                };
                t.start();

            }
        });

        final Button customMenuDefault = helloView
                .findViewById(R.id.customMenuDefault);
        customMenuDefault.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.isSelected()) {
                    // previously this was done by intent
                    MapMenuReceiver.getInstance().unregisterMenu("a-f");
                } else {
                    // previously this was done by intent and we were unable to get the menu
                    // based on a specific type
                    MapMenuReceiver.getInstance().registerMenu("a-f",
                            MapMenuReceiver.getInstance().lookupMenu("a-h"));
                }
                v.setSelected(!v.isSelected());
            }
        });

        menuFactory = new MenuFactory(pluginContext);
        final Button customMenuFactory = helloView
                .findViewById(R.id.customMenuFactory);
        customMenuFactory.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.isSelected()) {
                    MapMenuReceiver.getInstance()
                            .unregisterMapMenuFactory(menuFactory);
                } else {
                    MapMenuReceiver.getInstance()
                            .registerMapMenuFactory(menuFactory);
                }
                v.setSelected(!v.isSelected());
            }
        });

        final Button issLocation = helloView
                .findViewById(R.id.issLocation);
        issLocation.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean b = issLocation.isSelected();
                if (issTimer != null) {
                    issTimer.cancel();
                    issTimer.purge();
                    issTimer = null;
                }
                issTimer = new Timer();
                issLocation.setSelected(!b);
                if (!b) {
                    issTimer.schedule(new TimerTask() {

                        @Override
                        public void run() {
                            plotISSLocation();
                        }
                    }, 0, 3000);
                }

            }

        });

        // ATAK 4.1 disables the cleartext block
        // The current ISS plotting site uses cleartext http connection and offers no https ability.
        // Since this is not allowed on Android 9 or higher, hide the capability until the web site 
        // offers https
        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        //     issLocation.setVisibility(View.GONE);

        final Button sensorFOV = helloView
                .findViewById(R.id.sensorFOV);
        sensorFOV.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                createOrModifySensorFOV();
            }
        });
        nm = (NotificationManager) MapView.getMapView().getContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);

        /**
         * This button is to test a NW ROM bug and should not be used to display plugin
         * specific notification.   This WILL crash a NW device.   This is not  to be used as
         * an example of how to send Plugin Specific Notifications.   Please see the Plugin
         * Notification example.
         */
        final Button notificationSpammer = helloView
                .findViewById(R.id.notificationSpammer);
        notificationSpammer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                int indx = 10000;

                Log.d(TAG, "spam using the NotificationUtil");
                for (int i = 0; i < 30; ++i) {
                    //int evtReceivedIcon = com.atakmap.app.R.drawable.ic_notify_drawing;
                    int evtReceivedIcon = com.atakmap.app.R.drawable.team_human;
                    String contentTitle = "Test Spammer: " + i;
                    // remember this is just an example on how to crash the NW devices.
                    // not how to send custom plugin specific notification.
                    NotificationUtil.getInstance().postNotification(indx + i,
                            evtReceivedIcon, contentTitle, contentTitle,
                            contentTitle, null, true);

                }

                Log.d(TAG, "spam using the Android Notification");

                for (int i = 0; i < 40; ++i) {
                    //spamNotification(i+ indx);
                }
            }
        });

        final Button notificationWithOptions = helloView
                .findViewById(R.id.notificationWithOptions);
        notificationWithOptions.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent atakFrontIntent = new Intent();

                atakFrontIntent
                        .setComponent(new ComponentName("com.atakmap.app.civ",
                                "com.atakmap.app.ATAKActivity"));
                atakFrontIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                Intent fakePhoneCall = new Intent(
                        "com.atakmap.android.helloworld.FAKE_PHONE_CALL");
                int id = fakePhoneCall.hashCode();

                fakePhoneCall.putExtra("mytime",
                        "my time: " + System.currentTimeMillis());
                fakePhoneCall.putExtra("notificationId", id);
                atakFrontIntent.putExtra("internalIntent", fakePhoneCall);
                PendingIntent appIntent = PendingIntent.getActivity(
                        mapView.getContext(), fakePhoneCall.hashCode(),
                        atakFrontIntent, 0);

                NotificationManager nm = (NotificationManager) mapView
                        .getContext()
                        .getSystemService(Context.NOTIFICATION_SERVICE);

                Notification.Builder notificationBuilder;
                if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    notificationBuilder = new Notification.Builder(
                            mapView.getContext());
                } else {
                    notificationBuilder = new Notification.Builder(
                            mapView.getContext(), "com.atakmap.app.def");
                }
                String gid = java.util.UUID.randomUUID().toString();

                notificationBuilder.setContentTitle("Test Notification")
                        .setSmallIcon(
                                com.atakmap.app.R.drawable.ic_atak_launcher)
                        .setOngoing(false)
                        .setGroup(gid)
                        .addAction(com.atakmap.app.R.drawable.phone_icon,
                                "Phone Call", appIntent);
                Notification notification = notificationBuilder.build();
                nm.notify(notification.hashCode(), notification);
            }
        });

        final Button videoLauncher = helloView
                .findViewById(R.id.videoLauncher);
        videoLauncher.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ConnectionEntry ce = StreamManagementUtils
                        .createConnectionEntryFromUrl("big buck bunny",
                                "rtsp://3.84.6.190:554/vod/mp4:BigBuckBunny_115k.mov");
                Intent i = new Intent("com.atakmap.maps.video.DISPLAY");
                i.putExtra("CONNECTION_ENTRY", ce);
                i.putExtra("layers", new String[] {
                        "test-layer"
                });
                i.putExtra("cancelClose", "true");
                AtakBroadcast.getInstance().sendBroadcast(i);
            }
        });

        // show a drop down without any extras passed in.
        ActionBroadcastData abd = new ActionBroadcastData(
                "com.ford.tool.showtoast",
                new ArrayList<ActionBroadcastExtraStringData>());

        List<ActionClickData> acdList = new ArrayList<>();
        acdList.add(new ActionClickData(abd, "click"));

        final ActionMenuData amd = new ActionMenuData("com.ford.tool/TowTruck",
                "Ford TowTruck", "ic_menu_drawing", null, null, "overflow",
                false, acdList, false, false, false);

        AtakBroadcast.getInstance().registerReceiver(fordReceiver,
                new AtakBroadcast.DocumentedIntentFilter(
                        "com.ford.tool.showtoast"));

        final Button addToolbarItem = helloView
                .findViewById(R.id.addToolbarItem);
        addToolbarItem.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.isSelected()) {
                    Intent intent = new Intent(ActionBarReceiver.REMOVE_TOOLS);
                    intent.putExtra("menus", new ActionMenuData[] {
                            amd
                    });
                    AtakBroadcast.getInstance().sendBroadcast(intent);
                } else {
                    Intent intent = new Intent(ActionBarReceiver.ADD_NEW_TOOLS);
                    intent.putExtra("menus", new ActionMenuData[] {
                            amd
                    });
                    AtakBroadcast.getInstance().sendBroadcast(intent);
                }
                v.setSelected(!v.isSelected());
            }
        });

        final Button addCountToIcon = helloView
                .findViewById(R.id.addCountToIcon);
        addCountToIcon.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(
                        "com.atakmap.android.helloworld.plugin.iconcount");
                AtakBroadcast.getInstance().sendBroadcast(i);
            }
        });

        final Button imageAttach = helloView
                .findViewById(R.id.imageAttach);
        imageAttach.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                PlacePointTool.MarkerCreator mc = new PlacePointTool.MarkerCreator(
                        getMapView()
                                .getCenterPoint());

                mc.setType("a-f-A");
                mc.setCallsign("Marker with Attachment");
                final Marker m = mc.placePoint();

                String folder = AttachmentManager.getFolderPath(m.getUID());
                String destFile = folder + "/" + "test.png";
                // for the copyFromAssets call, the folder needs to be relative
                destFile = destFile.replace("/storage/emulated/0/atak/", "");

                Log.d(TAG, "writing attachment to the folder: " + folder);
                FileSystemUtils.copyFromAssetsToStorageFile(
                        getMapView().getContext(),
                        "icons/ac130.png", destFile, true);
            }
        });

        final Button webView = helloView
                .findViewById(R.id.webView);
        webView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setRetain(true);
                Intent webViewIntent = new Intent();

                webViewIntent
                        .setAction(WebViewDropDownReceiver.SHOW_WEBVIEW);
                AtakBroadcast.getInstance().sendBroadcast(webViewIntent);

            }
        });

        final Button mapScreenshot = helloView.findViewById(R.id.mapScreenshot);
        mapScreenshot.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new MapScreenshotExample(mapView, pluginContext).start();
                Toast.makeText(context, pluginContext.getString(
                        R.string.map_screenshot_started),
                        Toast.LENGTH_LONG).show();
            }
        });

        final Button addLayer = helloView
                .findViewById(R.id.addLayer);
        addLayer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                File f = FileSystemUtils.getItem("tools/helloworld/logo.png");
                f.getParentFile().mkdir();
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(f);
                    if (FileSystemUtils.assetExists(pluginContext,
                            "logo.png")) {
                        FileSystemUtils.copyFromAssets(pluginContext,
                                "logo.png",
                                fos);
                    }
                } catch (Exception e) {
                    toast("file: " + f
                            + " does not exist, please create it before trying this example");
                    Log.e(TAG, "error exracting: " + f);
                    return;

                } finally {
                    if (fos != null)
                        try {
                            fos.close();
                        } catch (Exception ignored) {
                        }
                }

                synchronized (HelloWorldDropDownReceiver.this) {
                    if (exampleLayer == null) {
                        GLLayerFactory.register(GLExampleLayer.SPI);
                        exampleLayer = new ExampleLayer(pluginContext,
                                "HelloWorld Test Layer", f.getAbsolutePath());
                    }
                }

                if (addLayer.isSelected()) {
                    // Remove the layer from the map
                    getMapView().removeLayer(RenderStack.MAP_SURFACE_OVERLAYS,
                            exampleLayer);
                } else {
                    // Add the layer to the map
                    getMapView().addLayer(RenderStack.MAP_SURFACE_OVERLAYS,
                            exampleLayer);
                    exampleLayer.setVisible(true);

                    // Pan and zoom to the layer
                    ATAKUtilities.scaleToFit(mapView, exampleLayer.getPoints(),
                            mapView.getWidth(), mapView.getHeight());
                }
                // Refresh Overlay Manager
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        HierarchyListReceiver.REFRESH_HIERARCHY));

                addLayer.setSelected(!addLayer.isSelected());

            }
        });

        GLLayerFactory.register(GLExampleMultiLayer.SPI);
        final Button addMultiLayer = helloView
                .findViewById(R.id.addMultiLayer);
        addMultiLayer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                File f = FileSystemUtils.getItem("tools/helloworld/logo.png");
                f.getParentFile().mkdir();
                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(f);
                    if (FileSystemUtils.assetExists(pluginContext,
                            "logo.png")) {
                        FileSystemUtils.copyFromAssets(pluginContext,
                                "logo.png",
                                fos);
                    }
                } catch (Exception e) {
                    toast("file: " + f
                            + " does not exist, please create it before trying this example");
                    Log.e(TAG, "error exracting: " + f);
                    return;

                } finally {
                    if (fos != null)
                        try {
                            fos.close();
                        } catch (Exception ignored) {
                        }
                }
                synchronized (HelloWorldDropDownReceiver.this) {
                    if ((exampleMultiLayers == null)
                            || exampleMultiLayers.isEmpty()) {
                        for (int index = 0; index < 3; index++) {
                            int altitude = (index + 1) * 50;
                            GeoPoint ul = GeoPoint.createMutable();
                            GeoPoint ur = GeoPoint.createMutable();
                            GeoPoint lr = GeoPoint.createMutable();
                            GeoPoint ll = GeoPoint.createMutable();
                            ur.set(50, -49.999, altitude);
                            lr.set(49.999, -49.999, altitude);
                            ll.set(49.999, -50, altitude);
                            ul.set(50, -50, altitude);
                            ExampleMultiLayer exampleMultiLayer = new ExampleMultiLayer(
                                    pluginContext,
                                    String.format(
                                            "HelloWorld Test Multi Layer %4d",
                                            altitude),
                                    f.getAbsolutePath(), ul, ur, lr, ll);
                            exampleMultiLayers.put(altitude,
                                    exampleMultiLayer);
                        }
                    }
                }

                if (addMultiLayer.isSelected()) {
                    // Remove the layer from the map
                    if (!exampleMultiLayers.isEmpty()) {
                        for (ExampleMultiLayer layer : exampleMultiLayers
                                .values()) {
                            // Remove the layer from the map
                            getMapView().removeLayer(
                                    MapView.RenderStack.MAP_SURFACE_OVERLAYS,
                                    layer);
                        }
                        exampleMultiLayers.clear();
                    }
                } else {
                    // Add the layer to the map
                    if (!exampleMultiLayers.isEmpty()) {
                        for (ExampleMultiLayer layer : exampleMultiLayers
                                .values()) {
                            // Remove the layer from the map
                            getMapView().addLayer(
                                    RenderStack.MAP_SURFACE_OVERLAYS,
                                    layer);
                            layer.setVisible(true);
                        }
                    }

                    // Pan and zoom to the layer
                    ATAKUtilities.scaleToFit(mapView,
                            exampleMultiLayers.entrySet().iterator().next()
                                    .getValue().getPoints(),
                            mapView.getWidth(), mapView.getHeight());
                }
                // Refresh Overlay Manager
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        HierarchyListReceiver.REFRESH_HIERARCHY));

                addMultiLayer.setSelected(!addMultiLayer.isSelected());
            }
        });



        final Button addHeatMap = helloView
                .findViewById(R.id.addHeatMap);
        addHeatMap.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                GeoBounds bounds = mapView.getBounds();

                if (simpleHeatMapLayer == null) {
                    GLLayerFactory.register(GLSimpleHeatMapLayer.SPI);
                    simpleHeatMapLayer = new SimpleHeatMapLayer(pluginContext, "simple heat map",
                            8, 8, bounds);
                }

                view.setSelected(!view.isSelected());

                if (view.isSelected()) {

                    simpleHeatMapLayer.setCorners(mapView.getBounds());
                    simpleHeatMapLayer.setData(generateHeatMap());
                    simpleHeatMapLayer.refresh();
                    getMapView().addLayer(RenderStack.MAP_SURFACE_OVERLAYS,
                            simpleHeatMapLayer);
                    simpleHeatMapLayer.setVisible(true);

                } else {
                    getMapView().removeLayer(RenderStack.MAP_SURFACE_OVERLAYS,
                            simpleHeatMapLayer);
                    simpleHeatMapLayer.setVisible(false);
                }
            }
        });



        final Button btnHookNavigationEvents = helloView
                .findViewById(R.id.btnHookNavigationEvents);
        btnHookNavigationEvents.setText(
                routeEventListener == null ? "Hook into navigation events"
                        : "UnHook into navigation events");

        /*
         * NOTE: Depending on the use case, the following is not usually necessary
         *
         * if(RouteNavigator.getInstance().getNavManager() != null){
                        RouteNavigator.getInstance().getNavManager().(un)registerListener(routeEventListener);
                    }
         *
         *
         * Typically, you'll register for navigation lifecycle events (e.g. Navigation Started, Navigation Stopped, etc.)
         * and then register your navManagerListener once navigation has started see `RouteEventListener.java`
         *
         * In this example, since we are dynamically hooking and unhooking into navigation events, this was done.
         *
         * Also note, that there is a race condition inherent with doing it this way, but it is pretty unlikely
         * to actually pose a problem.
         *
         */

        btnHookNavigationEvents.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (routeEventListener != null) {
                    //Unregister Listener
                    RouteNavigator.getInstance()
                            .unregisterRouteNavigatorListener(
                                    routeEventListener);
                    if (RouteNavigator.getInstance().getNavManager() != null) {
                        RouteNavigator.getInstance().getNavManager()
                                .unregisterListener(routeEventListener);
                    }

                    btnHookNavigationEvents
                            .setText("Hook into navigation events");
                    toast("You should no longer get toasts for events");

                    routeEventListener = null;
                } else {

                    routeEventListener = new RouteEventListener();
                    //Register for events
                    RouteNavigator.getInstance()
                            .registerRouteNavigatorListener(routeEventListener);
                    if (RouteNavigator.getInstance().getNavManager() != null) {
                        RouteNavigator.getInstance().getNavManager()
                                .registerListener(routeEventListener);
                    }

                    btnHookNavigationEvents.setText("Unhook navigation events");

                    toast("Start navigation on a route to start getting your toasts for events");

                }
            }
        });

        final Button coordinateEntry = helloView
                .findViewById(R.id.coordinateEntry);
        coordinateEntry.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder b = new AlertDialog.Builder(
                        getMapView().getContext());
                LayoutInflater inflater = LayoutInflater
                        .from(getMapView().getContext());
                final CoordDialogView coordView = (CoordDialogView) inflater
                        .inflate(
                                com.atakmap.app.R.layout.draper_coord_dialog,
                                null);
                b.setTitle(com.atakmap.app.R.string.rb_coord_title)
                        .setView(coordView)
                        .setPositiveButton(com.atakmap.app.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        GeoPointMetaData gp = coordView
                                                .getPoint();
                                        if (coordView
                                                .getResult() == CoordDialogView.Result.VALID_CHANGED) {
                                            toast(gp.toString());
                                        }

                                    }
                                });
                CoordinateFormat _cFormat;
                AtakPreferences sharedPrefs = new AtakPreferences(getMapView().getContext());
                _cFormat = CoordinateFormat
                        .find(sharedPrefs
                                .get(
                                        "coord_display_pref",
                                        getMapView().getContext()
                                                .getString(
                                                        com.atakmap.app.R.string.coord_display_pref_default)));

                coordView.setParameters(null, MapView.getMapView().getPoint(),
                        _cFormat);

                // Overrides setPositive button onClick to keep the window open when the
                // input is invalid.
                final AlertDialog locDialog = b.create();
                locDialog.setCancelable(false);
                locDialog.show();

            }
        });

        // blind cast
        final LinearLayout secondRender = helloView
                .findViewById(R.id.secondRender);
        final OffscreenMapCapture mc = new OffscreenMapCapture(secondRender);
        final Button getimage = helloView
                .findViewById(R.id.getImage);
        getimage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getimage.isSelected()) {
                    mc.capture(false);
                    secondRender.setVisibility(View.GONE);
                } else {
                    mc.capture(true);
                    secondRender.setVisibility(View.VISIBLE);
                }
                getimage.setSelected(!getimage.isSelected());
            }
        });

        // Downloading a map layer
        final Button downloadLayer = helloView.findViewById(
                R.id.downloadMapLayer);
        downloadLayer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (layerDownloader != null)
                    layerDownloader.dispose();
                layerDownloader = new LayerDownloadExample(mapView,
                        pluginContext);
                layerDownloader.start();
            }
        });

        // Dark themed spinners need some text color correction
        final Spinner spinner = helloView
                .findViewById(R.id.spinner1);

        spinner.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent,
                    View view,
                    int position, long id) {
                if (view instanceof TextView)
                    ((TextView) view).setTextColor(Color.WHITE);
            }
        });
        spinner.setSelection(0);

        //implement onLongClickListener for buttons
        smaller.setOnLongClickListener(longClickListener);
        larger.setOnLongClickListener(longClickListener);
        showSearchIcon.setOnLongClickListener(longClickListener);
        fly.setOnLongClickListener(longClickListener);
        wheel.setOnLongClickListener(longClickListener);
        addAnAircraft.setOnLongClickListener(longClickListener);
        staleout.setOnLongClickListener(longClickListener);
        addStream.setOnLongClickListener(longClickListener);
        removeStream.setOnLongClickListener(longClickListener);
        itemInspect.setOnLongClickListener(longClickListener);
        customType.setOnLongClickListener(longClickListener);
        listRoutes.setOnLongClickListener(longClickListener);
        addRoute.setOnLongClickListener(longClickListener);
        reRoute.setOnLongClickListener(longClickListener);
        emergency.setOnLongClickListener(longClickListener);
        noemergency.setOnLongClickListener(longClickListener);
        addRect.setOnLongClickListener(longClickListener);
        rbCircle.setOnLongClickListener(longClickListener);
        externalGps.setOnLongClickListener(longClickListener);
        surfaceAtCenter.setOnLongClickListener(longClickListener);
        fakeContentProvider.setOnLongClickListener(longClickListener);
        pluginNotification.setOnLongClickListener(longClickListener);
        notificationSpammer.setOnLongClickListener(longClickListener);
        cameraLauncher.setOnLongClickListener(longClickListener);
        imageAttach.setOnLongClickListener(longClickListener);
        //speechToText.setOnLongClickListener(longClickListener);
        btnHookNavigationEvents.setOnLongClickListener(longClickListener);
        issLocation.setOnLongClickListener(longClickListener);
        downloadLayer.setOnLongClickListener(longClickListener);
        mapScreenshot.setOnLongClickListener(longClickListener);

    }

    private void spamNotification(int i) {
        Notification.Builder nBuilder = new Notification.Builder(
                MapView.getMapView().getContext());
        nBuilder.setContentTitle("Test: " + i)
                .setContentText("This is a test: " + i)
                .setSmallIcon(com.atakmap.app.R.drawable.team_human)
                .setStyle(new Notification.BigTextStyle()
                        .bigText("Test (big): " + i))

                .setOngoing(false)
                .setAutoCancel(true);
        final Notification notification = nBuilder.build();
        nm.notify(i + 8000, notification);

    }

    private void test(View v) {

    }

    /**
     * This class makes use of a compact class to aid with the selection of map items.   Prior to
     * 3.12, this all had to be manually done playing with the dispatcher and listening for map
     * events.
     */
    public class InspectionMapItemSelectionTool
            extends AbstractMapItemSelectionTool {
        public InspectionMapItemSelectionTool() {
            super(getMapView(),
                    "com.atakmap.android.helloworld.InspectionMapItemSelectionTool",
                    "com.atakmap.android.helloworld.InspectionMapItemSelectionTool.Finished",
                    "Select Map Item on the screen",
                    "Invalid Selection");
        }

        @Override
        protected boolean isItem(MapItem mi) {
            return true;
        }

    }

    final BroadcastReceiver inspectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            AtakBroadcast.getInstance().unregisterReceiver(this);
            final Button itemInspect = helloView
                    .findViewById(R.id.itemInspect);
            itemInspect.setSelected(false);

            String uid = intent.getStringExtra("uid");
            if (uid == null)
                return;

            MapItem mi = getMapView().getMapItem(uid);

            if (mi == null)
                return;

            Log.d(TAG, "class: " + mi.getClass());
            Log.d(TAG, "type: " + mi.getType());

            final CotEvent cotEvent = CotEventFactory
                    .createCotEvent(mi);

            String val;
            if (cotEvent != null)
                val = cotEvent.toString();
            else if (mi.hasMetaValue("nevercot"))
                val = "map item set to never persist (nevercot)";
            else
                val = "error turning a map item into CoT";

            AlertDialog.Builder builderSingle = new AlertDialog.Builder(
                    getMapView().getContext());
            TextView showText = new TextView(getMapView().getContext());
            showText.setText(val);
            showText.setTextIsSelectable(true);
            showText.setOnLongClickListener(new View.OnLongClickListener() {

                @Override
                public boolean onLongClick(View v) {
                    // Copy the Text to the clipboard
                    ClipboardManager manager = (ClipboardManager) getMapView()
                            .getContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    TextView showTextParam = (TextView) v;
                    manager.setText(showTextParam.getText());
                    Toast.makeText(v.getContext(),
                            "copied the data", Toast.LENGTH_SHORT).show();
                    return true;
                }
            });

            builderSingle.setTitle("Resulting CoT");
            builderSingle.setView(showText);
            builderSingle.show();
        }
    };

    synchronized public void runSim() {
        Marker item = getMapView().getSelfMarker();
        if (item != null) {

            final MapData data = getMapView().getMapData();

            GeoPoint gp = new GeoPoint(-44.0, 22.0); // decimal degrees

            data.putDouble("mockLocationSpeed", 20); // speed in meters per second
            data.putFloat("mockLocationAccuracy", 5f); // accuracy in meters

            data.putString("locationSourcePrefix", "mock");
            data.putBoolean("mockLocationAvailable", true);

            data.putString("mockLocationSource", "Hello World Plugin");
            data.putString("mockLocationSourceColor", "#FFAFFF00");
            data.putBoolean("mockLocationCallsignValid", true);

            data.putParcelable("mockLocation", gp);

            data.putLong("mockLocationTime", SystemClock.elapsedRealtime());

            data.putLong("mockGPSTime",
                    new CoordinatedTime().getMilliseconds()); // time as reported by the gps device

            data.putInt("mockFixQuality", 2);

            Intent gpsReceived = new Intent();

            gpsReceived
                    .setAction("com.atakmap.android.map.WR_GPS_RECEIVED");
            AtakBroadcast.getInstance().sendBroadcast(gpsReceived);

            Log.d(TAG,
                    "received gps for: " + gp
                            + " with a fix quality: " + 2 +
                            " setting last seen time: "
                            + data.getLong("mockLocationTime"));

        }

    }

    /**
     * Slower of the two methods to create a circle, but more accurate.
     */
    public void createEllipse(final Marker marker) {
        final Ellipse _accuracyEllipse = new Ellipse(UUID.randomUUID()
                .toString());
        _accuracyEllipse.setCenterHeightWidth(marker.getGeoPointMetaData(), 20,
                20);
        _accuracyEllipse.setFillColor(Color.argb(50, 238, 187, 255));
        _accuracyEllipse.setFillStyle(2);
        _accuracyEllipse.setStrokeColor(Color.GREEN);
        _accuracyEllipse.setStrokeWeight(4);
        _accuracyEllipse.setMetaString("shapeName", "Error Ellipse");
        _accuracyEllipse.setMetaBoolean("addToObjList", false);
        getMapView().getRootGroup().addItem(_accuracyEllipse);
        marker.addOnPointChangedListener(new OnPointChangedListener() {
            @Override
            public void onPointChanged(final PointMapItem item) {
                _accuracyEllipse.setCenterHeightWidth(
                        item.getGeoPointMetaData(), 20, 20);
            }
        });
        MapEventDispatcher dispatcher = getMapView().getMapEventDispatcher();
        dispatcher.addMapEventListener(MapEvent.ITEM_REMOVED,
                new MapEventDispatcher.MapEventDispatchListener() {
                    public void onMapEvent(MapEvent event) {
                        if (event.getType().equals(MapEvent.ITEM_REMOVED)) {
                            MapItem item = event.getItem();
                            if (item.getUID().equals(marker.getUID()))
                                getMapView().getRootGroup().removeItem(
                                        _accuracyEllipse);
                        }
                    }
                });

    }

    /**
     * Faster of the two methods to create a circle.
     */
    public void createCircle(final Marker marker) {
        final Circle _accuracyCircle = new Circle(marker.getGeoPointMetaData(),
                20);

        _accuracyCircle.setFillColor(Color.argb(50, 238, 187, 255));
        //_accuracyCircle.setFillStyle(2);
        _accuracyCircle.setStrokeColor(Color.GREEN);
        _accuracyCircle.setStrokeWeight(4);
        _accuracyCircle.setMetaString("shapeName", "Error Ellipse");
        _accuracyCircle.setMetaBoolean("addToObjList", false);

        getMapView().getRootGroup().addItem(_accuracyCircle);
        marker.addOnPointChangedListener(new OnPointChangedListener() {
            @Override
            public void onPointChanged(final PointMapItem item) {
                _accuracyCircle.setCenterPoint(marker.getGeoPointMetaData());
                _accuracyCircle.setRadius(20);
            }
        });
        MapEventDispatcher dispatcher = getMapView().getMapEventDispatcher();
        dispatcher.addMapEventListener(MapEvent.ITEM_REMOVED,
                new MapEventDispatcher.MapEventDispatchListener() {
                    public void onMapEvent(MapEvent event) {
                        if (event.getType().equals(MapEvent.ITEM_REMOVED)) {
                            MapItem item = event.getItem();
                            if (item.getUID().equals(marker.getUID()))
                                getMapView().getRootGroup().removeItem(
                                        _accuracyCircle);
                        }
                    }
                });

    }

    private void manipulateFakeContentProvider() {
        // delete all the records and the table of the database provider
        String URL = "content://com.javacodegeeks.provider.Birthday/friends";
        Uri friends = Uri.parse(URL);
        int count = pluginContext.getContentResolver().delete(
                friends, null, null);
        String countNum = "Javacodegeeks: " + count + " records are deleted.";
        toast(countNum);

        String[] names = new String[] {
                "Joe", "Bob", "Sam", "Carol"
        };
        String[] dates = new String[] {
                "01/01/2001", "01/01/2002", "01/01/2003", "01/01/2004"
        };
        for (int i = 0; i < names.length; ++i) {
            ContentValues values = new ContentValues();
            values.put(BirthProvider.NAME, names[i]);
            values.put(BirthProvider.BIRTHDAY, dates[i]);
            Uri uri = pluginContext.getContentResolver().insert(
                    BirthProvider.CONTENT_URI, values);
            toast("Javacodegeeks: " + uri + " inserted!");
        }

    }

    /**************************** PUBLIC METHODS *****************************/

    @Override
    public void disposeImpl() {
        // Remove Hello World contact
        removeContact(this.helloContact);
        try {
            AtakBroadcast.getInstance().unregisterReceiver(fordReceiver);
        } catch (Exception e) {
            Log.e(TAG, "error", e);
        }

        _joystickView.dispose();

        if (issTimer != null) {
            issTimer.cancel();
            issTimer.purge();
            issTimer = null;
        }

        AtakBroadcast.getInstance().unregisterReceiver(fakePhoneCallReceiver);

        SensorManager sensorManager = (SensorManager) getMapView().getContext()
                .getSystemService(Context.SENSOR_SERVICE);
        sensorManager.unregisterListener(HelloWorldDropDownReceiver.this);
        TextContainer.getTopInstance().closePrompt();

        imis.dispose();

        // make sure we unregister, say when a new version is hot loaded ...
        MapMenuReceiver.getInstance()
                .unregisterMapMenuFactory(menuFactory);

        try {
            if (exampleLayer != null) {
                getMapView().removeLayer(RenderStack.MAP_SURFACE_OVERLAYS,
                        exampleLayer);
                GLLayerFactory.unregister(GLExampleLayer.SPI);
            }
            exampleLayer = null;
        } catch (Exception e) {
            Log.e(TAG, "error", e);
        }
        GLLayerFactory.unregister(GLExampleMultiLayer.SPI);

    }

    /**************************** INHERITED METHODS *****************************/

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "showing hello world drop down");

        final String action = intent.getAction();
        if (action == null)
            return;

        // Show drop-down
        switch (action) {
            case SHOW_HELLO_WORLD:
                if (!isClosed()) {
                    Log.d(TAG, "the drop down is already open");
                    unhideDropDown();
                    return;
                }

                showDropDown(helloView, HALF_WIDTH, FULL_HEIGHT,
                        FULL_WIDTH, HALF_HEIGHT, false, this);
                setAssociationKey("helloWorldPreference");
                List<Contact> allContacts = Contacts.getInstance()
                        .getAllContacts();
                for (Contact c : allContacts) {
                    if (c instanceof IndividualContact)
                        Log.d(TAG, "Contact IP address: "
                                + getIpAddress((IndividualContact) c));
                }

                break;

            // Chat message sent to Hello World contact
            case CHAT_HELLO_WORLD:
                Bundle cotMessage = intent.getBundleExtra(
                        ChatManagerMapComponent.PLUGIN_SEND_MESSAGE_EXTRA);

                String msg = cotMessage.getString("message");

                if (!FileSystemUtils.isEmpty(msg)) {
                    // Display toast to show the message was received
                    toast(helloContact.getName() + " received: " + msg);
                }
                break;

            // Sending CoT to Hello World contact
            case SEND_HELLO_WORLD:
                // Map item UID
                String uid = intent.getStringExtra("targetUID");
                MapItem mapItem = getMapView().getRootGroup().deepFindUID(uid);
                if (mapItem != null) {
                    // Display toast to show the CoT was received
                    toast(helloContact.getName() + " received request to send: "
                            + ATAKUtilities.getDisplayName(mapItem));
                }
                break;

            // Toggle visibility of example layer
            case LAYER_VISIBILITY: {
                Log.d(TAG,
                        "used the custom action to toggle layer visibility on: "
                                + intent
                                        .getStringExtra("uid"));
                ExampleLayer l = mapOverlay.findLayer(intent
                        .getStringExtra("uid"));
                if (l != null) {
                    l.setVisible(!l.isVisible());
                } else {
                    ExampleMultiLayer ml = mapOverlay.findMultiLayer(intent
                            .getStringExtra("uid"));
                    if (ml != null)
                        ml.setVisible(!ml.isVisible());
                }
                break;
            }

            // Delete example layer
            case LAYER_DELETE: {
                Log.d(TAG,
                        "used the custom action to delete the layer on: "
                                + intent
                                        .getStringExtra("uid"));
                ExampleLayer l = mapOverlay.findLayer(intent
                        .getStringExtra("uid"));
                if (l != null) {
                    getMapView().removeLayer(RenderStack.MAP_SURFACE_OVERLAYS,
                            l);
                } else {
                    ExampleMultiLayer ml = mapOverlay.findMultiLayer(intent
                            .getStringExtra("uid"));
                    if (ml != null)
                        getMapView().removeLayer(
                                RenderStack.MAP_SURFACE_OVERLAYS, ml);
                }
                break;
            }
        }
    }

    public NetConnectString getIpAddress(IndividualContact ic) {
        Connector ipConnector = ic.getConnector(IpConnector.CONNECTOR_TYPE);
        if (ipConnector != null) {
            String connectString = ipConnector.getConnectionString();
            return NetConnectString.fromString(connectString);
        } else {
            return null;
        }

    }

    @Override
    protected void onStateRequested(int state) {
        if (state == DROPDOWN_STATE_FULLSCREEN) {
            if (!isPortrait()) {
                if (Double.compare(currWidth, HALF_WIDTH) == 0) {
                    resize(FULL_WIDTH - HANDLE_THICKNESS_LANDSCAPE,
                            FULL_HEIGHT);
                }
            } else {
                if (Double.compare(currHeight, HALF_HEIGHT) == 0) {
                    resize(FULL_WIDTH, FULL_HEIGHT - HANDLE_THICKNESS_PORTRAIT);
                }
            }
        } else if (state == DROPDOWN_STATE_NORMAL) {
            if (!isPortrait()) {
                resize(HALF_WIDTH, FULL_HEIGHT);
            } else {
                resize(FULL_WIDTH, HALF_HEIGHT);
            }
        }
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
        currWidth = width;
        currHeight = height;
    }

    @Override
    public void onDropDownClose() {

        // make sure that if the Map Item inspector is running
        // turn off the map item inspector
        final Button itemInspect = helloView
                .findViewById(R.id.itemInspect);
        boolean val = itemInspect.isSelected();
        if (val) {
            itemInspect.setSelected(false);
            imis.requestEndTool();

        }

    }

    /************************* Helper Methods *************************/

    private RouteMapReceiver getRouteMapReceiver() {

        // TODO: this code was copied from another plugin.
        // Not sure why we can't just callRouteMapReceiver.getInstance();
        MapActivity activity = (MapActivity) getMapView().getContext();
        MapComponent mc = activity.getMapComponent(RouteMapComponent.class);
        if (mc == null || !(mc instanceof RouteMapComponent)) {
            Log.w(TAG, "Unable to find route without RouteMapComponent");
            return null;
        }

        RouteMapComponent routeComponent = (RouteMapComponent) mc;
        return routeComponent.getRouteMapReceiver();
    }

    final BroadcastReceiver fordReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(getMapView().getContext(),
                    "Ford Tow Truck Application", Toast.LENGTH_SHORT).show();
        }
    };

    private void toast(String str) {
        Toast.makeText(getMapView().getContext(), str,
                Toast.LENGTH_LONG).show();
    }

    public void createSpeechMarker(HashMap<String, String> s) {
        final GeoPoint mgrsPoint;
        try {
            String[] coord = new String[] {
                    s.get("numericGrid") + s.get("alphaGrid"),
                    s.get("squareID"),
                    s.get("easting"),
                    s.get("northing")
            };
            mgrsPoint = CoordinateFormatUtilities.convert(coord,
                    CoordinateFormat.MGRS);

        } catch (IllegalArgumentException e) {
            String msg = "An error has occurred getting the MGRS point";
            Log.e(TAG, msg, e);
            toast(msg);
            return;
        }

        Marker m = new Marker(mgrsPoint, UUID
                .randomUUID().toString());
        Log.d(TAG, "creating a new unit marker for: " + m.getUID());

        switch (s.get("markerType").charAt(0)) {
            case 'U':
                m.setType("a-u-G-U-C-F");
                break;
            case 'N':
                m.setType("a-n-G-U-C-F");
                break;
            case 'F':
                m.setType("a-f-G-U-C-F");
                break;
            case 'H':
            default:
                m.setType("a-h-G-U-C-F");
                break;
        }

        new Thread(new Runnable() {
            public void run() {
                CameraController.Programmatic.zoomTo(
                        getMapView().getRenderer3(),
                        .00001d, false);

                CameraController.Programmatic.panTo(
                        getMapView().getRenderer3(),
                        mgrsPoint, false);

                try {
                    Thread.sleep(1000);
                } catch (Exception ignored) {
                }

            }
        }).start();

        m.setMetaBoolean("readiness", true);
        m.setMetaBoolean("archive", true);
        m.setMetaString("how", "h-g-i-g-o");
        m.setMetaBoolean("editable", true);
        m.setMetaBoolean("movable", true);
        m.setMetaBoolean("removable", true);
        m.setMetaString("entry", "user");
        m.setMetaString("callsign", "Speech Marker");
        m.setTitle("Speech Marker");

        MapGroup _mapGroup = getMapView().getRootGroup()
                .findMapGroup("Cursor on Target")
                .findMapGroup(s.get("markerType"));
        _mapGroup.addItem(m);

        m.persist(getMapView().getMapEventDispatcher(), null,
                this.getClass());

        Intent new_cot_intent = new Intent();
        new_cot_intent.setAction("com.atakmap.android.maps.COT_PLACED");
        new_cot_intent.putExtra("uid", m.getUID());
        AtakBroadcast.getInstance().sendBroadcast(
                new_cot_intent);
    }

    public void createAircraftWithRotation() {
        PlacePointTool.MarkerCreator mc = new PlacePointTool.MarkerCreator(
                getMapView().getPointWithElevation());
        mc.setUid(UUID.randomUUID().toString());
        mc.setCallsign("SNF");
        mc.setType("a-f-A");
        mc.showCotDetails(false);
        mc.setNeverPersist(true);
        Marker m = mc.placePoint();
        // the stle of the marker is by default set to show an arrow, this will allow for full
        // rotation.   You need to enable the heading mask as well as the noarrow mask
        m.setStyle(m.getStyle()
                | Marker.STYLE_ROTATE_HEADING_MASK
                | Marker.STYLE_ROTATE_HEADING_NOARROW_MASK);
        m.setTrack(310, 20);
        m.setMetaInteger("color", Color.YELLOW);
        m.setMetaString(UserIcon.IconsetPath,
                "34ae1613-9645-4222-a9d2-e5f243dea2865/Military/A10.png");
        m.refresh(getMapView().getMapEventDispatcher(), null,
                this.getClass());

    }

    public void createOrModifySensorFOV() {
        final MapView mapView = getMapView();
        final String cameraID = "sensor-fov-example-uid";
        final GeoPointMetaData point = mapView.getCenterPoint();
        final int color = 0xFFFF0000;

        MapItem mi = mapView.getMapItem(cameraID);
        if (mi == null) {
            PlacePointTool.MarkerCreator markerCreator = new PlacePointTool.MarkerCreator(
                    point);
            markerCreator.setUid(cameraID);
            //this settings automatically pops open to CotDetails page after dropping the marker
            markerCreator.showCotDetails(false);
            //this settings determines if a CoT persists or not.
            markerCreator.setArchive(true);
            //this is the type of the marker.  Could be set to a known 2525B value or custom
            markerCreator.setType("b-m-p-s-p-loc");
            //this shows under the marker
            markerCreator.setCallsign("Sensor");
            //this also determines if the marker persists or not??
            markerCreator.setNeverPersist(false);
            mi = markerCreator.placePoint();

        }
        // blind cast, ensure this is really a marker.
        Marker camera1 = (Marker) mi;
        camera1.setPoint(point);

        mi = mapView.getMapItem(camera1.getUID() + "-fov");
        if (mi instanceof SensorFOV) {
            SensorFOV sFov = (SensorFOV) mi;
            float r = ((0x00FF0000 & color) >> 16) / 256f;
            float g = ((0x0000FF00 & color) >> 8) / 256f;
            float b = ((0x000000FF & color) >> 0) / 256f;

            sFov.setColor(color); // currently broken
            sFov.setColor(r, g, b);
            sFov.setMetrics((int) (90 * Math.random()),
                    (int) (70 * Math.random()), 400);
        } else { // use this case
            float r = ((0x00FF0000 & color) >> 16) / 256f;
            float g = ((0x0000FF00 & color) >> 8) / 256f;
            float b = ((0x000000FF & color) >> 0) / 256f;
            SensorDetailHandler.addFovToMap(camera1, 90, 70, 400, new float[] {
                    r, g, b, 90
            }, true);
        }
    }

    public void createUnit() {

        Marker m = new Marker(getMapView().getPointWithElevation(), UUID
                .randomUUID().toString());
        Log.d(TAG, "creating a new unit marker for: " + m.getUID());
        m.setType("a-f-G-U-C-I");
        // m.setMetaBoolean("disableCoordinateOverlay", true); // used if you don't want the coordinate overlay to appear
        m.setMetaBoolean("readiness", true);
        m.setMetaBoolean("archive", true);
        m.setMetaString("how", "h-g-i-g-o");
        m.setMetaBoolean("editable", true);
        m.setMetaBoolean("movable", true);
        m.setMetaBoolean("removable", true);
        m.setMetaString("entry", "user");
        m.setMetaString("callsign", "Test Marker");
        m.setTitle("Test Marker");
        m.setMetaString("menu", getMenu());

        MapGroup _mapGroup = getMapView().getRootGroup()
                .findMapGroup("Cursor on Target")
                .findMapGroup("Friendly");
        _mapGroup.addItem(m);

        m.persist(getMapView().getMapEventDispatcher(), null,
                this.getClass());

        Intent new_cot_intent = new Intent();
        new_cot_intent.setAction("com.atakmap.android.maps.COT_PLACED");
        new_cot_intent.putExtra("uid", m.getUID());
        AtakBroadcast.getInstance().sendBroadcast(
                new_cot_intent);

    }

    void printNetworks() {
        /*
         *    CotPort.DESCRIPTION_KEY
         *    CotPort.ENABLED_KEY
         *    CotPort.CONNECTED_KEY
         *    CotPort.CONNECT_STRING_KEY
         */
        Bundle b = CommsMapComponent.getInstance().getAllPortsBundle();
        Bundle[] streams = (Bundle[]) b.getParcelableArray("streams");
        Bundle[] outputs = (Bundle[]) b.getParcelableArray("outputs");
        Bundle[] inputs = (Bundle[]) b.getParcelableArray("inputs");
        if (inputs != null) {
            for (Bundle input : inputs)
                Log.d(TAG, "input " + input.getString(CotPort.DESCRIPTION_KEY)
                        + ": " + input.getString(CotPort.CONNECT_STRING_KEY));
        }
        if (outputs != null) {
            for (Bundle output : outputs)
                Log.d(TAG, "output " + output.getString(CotPort.DESCRIPTION_KEY)
                        + ": " + output.getString(CotPort.CONNECT_STRING_KEY));
        }
        if (streams != null) {
            for (Bundle stream : streams)
                Log.d(TAG, "stream " + stream.getString(CotPort.DESCRIPTION_KEY)
                        + ": " + stream.getString(CotPort.CONNECT_STRING_KEY));
        }
    }

    void drawShapes() {
        MapView mapView = getMapView();
        MapGroup group = mapView.getRootGroup().findMapGroup(
                "Drawing Objects");
        List<DrawingShape> dslist = new ArrayList<>();

        DrawingShape ds = new DrawingShape(mapView, "ds-1");
        ds.setStrokeColor(Color.RED);
        ds.setPoints(new GeoPoint[] {
                new GeoPoint(0, 0), new GeoPoint(1, 1), new GeoPoint(2, 1)
        });
        ds.setHeight(100);
        //group.addItem(ds);
        dslist.add(ds);
        // test to set closed after adding to a group
        ds.setClosed(true);

        ds = new DrawingShape(mapView, "ds-2");
        ds.setPoints(new GeoPoint[] {
                new GeoPoint(0, 0), new GeoPoint(-1, -1), new GeoPoint(-2, -1)
        });
        ds.setHeight(200);
        ds.setClosed(true);
        ds.setStrokeColor(Color.BLUE);

        //group.addItem(ds);
        dslist.add(ds);

        MultiPolyline mp = new MultiPolyline(mapView, group, dslist, "list-1");
        group.addItem(mp);
        mp.setMovable(false);
        ds = new DrawingShape(mapView, "ds-3");
        ds.setPoints(new GeoPoint[] {
                new GeoPoint(0, 0), new GeoPoint(2, 0), new GeoPoint(2, -1)
        });
        ds.setClosed(true);
        ds.setStrokeColor(Color.YELLOW);
        ds.setHeight(300);

        ds.setMovable(false);
        group.addItem(ds);
        ds = new DrawingShape(mapView, "ds-4");
        ds.setPoints(new GeoPoint[] {
                new GeoPoint(0, 0), new GeoPoint(-2, 0), new GeoPoint(-2, 1)
        });
        ds.setStrokeColor(Color.GREEN);
        group.addItem(ds);
        ds.setHeight(400);
        ds.setMovable(false);
        ds.setClosed(true);

    }

    /**
     * For plugins to have custom radial menus, we need to set the "menu" metadata to
     * contain a well formed xml entry.   This only allows for reskinning of existing
     * radial menus with icons and actions that already exist in ATAK.
     * In order to perform a completely custom radia menu instalation. You need to
     * define the radial menu as below and then uuencode the sub elements such as
     * images or instructions.
     */
    private String getMenu() {
        return PluginMenuParser.getMenu(pluginContext, "menu.xml");
    }

    /**
     * This is an example of a completely custom xml definition for a menu.   It uses the
     * plaintext stringified version of the current menu language plus uuencoded images
     * and actions.
     */
    public String getMenu2() {
        return PluginMenuParser.getMenu(pluginContext, "menu2.xml");
    }

    /**
     * Add a plugin-specific contact to the contacts list
     * This contact fires an intent when a message is sent to it,
     * instead of using the default chat implementation
     *
     * @param name Contact display name
     * @return New plugin contact
     */
    public Contact addPluginContact(String name) {

        // Add handler for messages
        HelloWorldContactHandler contactHandler = new HelloWorldContactHandler(
                pluginContext);
        CotMapComponent.getInstance().getContactConnectorMgr()
                .addContactHandler(contactHandler);

        // Create new contact with name and random UID
        IndividualContact contact = new IndividualContact(
                name, UUID.randomUUID().toString());

        // Add plugin connector which points to the intent action
        // that is fired when a message is sent to this contact
        contact.addConnector(new PluginConnector(CHAT_HELLO_WORLD));

        // Add IP connector so the contact shows up when sending CoT or files
        contact.addConnector(new IpConnector(SEND_HELLO_WORLD));

        // Set default connector to plugin connector
        AtakPreferences prefs = new AtakPreferences(getMapView().getContext());
        prefs.set("contact.connector.default." + contact.getUID(),
                PluginConnector.CONNECTOR_TYPE);

        // Add new contact to master contacts list
        Contacts.getInstance().addContact(contact);

        return contact;
    }

    /**
     * Remove a contact from the master contacts list
     * This will remove it from the contacts list drop-down
     *
     * @param contact Contact object
     */
    public void removeContact(Contact contact) {
        Contacts.getInstance().removeContact(contact);
    }

    private void plotISSLocation() {
        double lat = Double.NaN, lon = Double.NaN;
        try {
            final java.io.InputStream input = new java.net.URL(
                    "http://api.open-notify.org/iss-now.json").openStream();
            final String returnJson = FileSystemUtils.copyStreamToString(input,
                    true, FileSystemUtils.UTF8_CHARSET);

            Log.d(TAG, "return json: " + returnJson);

            android.util.JsonReader jr = new android.util.JsonReader(
                    new java.io.StringReader(returnJson));
            jr.beginObject();
            while (jr.hasNext()) {
                String name = jr.nextName();
                switch (name) {
                    case "iss_position":
                        jr.beginObject();
                        while (jr.hasNext()) {
                            String n = jr.nextName();
                            switch (n) {
                                case "latitude":
                                    lat = jr.nextDouble();
                                    break;
                                case "longitude":
                                    lon = jr.nextDouble();
                                    break;
                                case "message":
                                    jr.skipValue();
                                    break;
                            }
                        }
                        jr.endObject();
                        break;
                    case "timestamp":
                        jr.skipValue();
                        break;
                    case "message":
                        jr.skipValue();
                        break;
                }
            }
            jr.endObject();
        } catch (Exception e) {
            Log.e(TAG, "error", e);
        }
        if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
            final MapItem mi = getMapView().getMapItem("iss-unique-identifier");
            if (mi != null) {
                if (mi instanceof Marker) {
                    Marker marker = (Marker) mi;

                    GeoPoint newPoint = new GeoPoint(lat, lon);
                    GeoPoint lastPoint = ((Marker) mi).getPoint();
                    long currTime = SystemClock.elapsedRealtime();

                    double dist = lastPoint.distanceTo(newPoint);
                    double dir = lastPoint.bearingTo(newPoint);

                    double delta = currTime -
                            mi.getMetaLong("iss.lastUpdateTime", 0);

                    double speed = dist / (delta / 1000f);

                    marker.setTrack(dir, speed);

                    marker.setPoint(newPoint);
                    mi.setMetaLong("iss.lastUpdateTime",
                            SystemClock.elapsedRealtime());

                }
            } else {
                PlacePointTool.MarkerCreator mc = new PlacePointTool.MarkerCreator(
                        new GeoPoint(lat, lon));
                mc.setUid("iss-unique-identifier");
                mc.setCallsign("International Space Station");
                mc.setType("a-f-P-T");
                mc.showCotDetails(false);
                mc.setNeverPersist(true);
                Marker m = mc.placePoint();
                // don't forget to turn on the arrow so that we know where the ISS is going
                m.setStyle(Marker.STYLE_ROTATE_HEADING_MASK);
                //m.setMetaBoolean("editable", false);
                m.setMetaBoolean("movable", false);
                m.setMetaString("how", "m-g");
            }
        }
    }

    @Override
    public void onSensorChanged(final SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            final float[] values = event.values;
            // Movement
            float x = values[0];
            float y = values[1];
            float z = values[2];

            float asr = (x * x + y * y + z * z)
                    / (SensorManager.GRAVITY_EARTH
                            * SensorManager.GRAVITY_EARTH);
            if (Math.abs(x) > 6 || Math.abs(y) > 6 || Math.abs(z) > 8)
                Log.d(TAG, "gravity=" + SensorManager.GRAVITY_EARTH + " x=" + x
                        + " y=" + y + " z=" + z + " asr=" + asr);
            if (y > 7) {
                TextContainer.getTopInstance().displayPrompt("Tilt Right");
                Log.d(TAG, "tilt right");
            } else if (y < -7) {
                TextContainer.getTopInstance().displayPrompt("Tilt Left");
                Log.d(TAG, "tilt left");
            } else if (x > 7) {
                TextContainer.getTopInstance().displayPrompt("Tilt Up");
                Log.d(TAG, "tilt up");
            } else if (x < -7) {
                TextContainer.getTopInstance().displayPrompt("Tilt Down");
                Log.d(TAG, "tilt down");
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            Log.d(TAG, "accuracy for the accelerometer: " + accuracy);
        }
    }

    BroadcastReceiver fakePhoneCallReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            Log.d(TAG, "intent: " + intent.getAction() + " "
                    + intent.getStringExtra("mytime"));
            getMapView().post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getMapView().getContext(),
                            "intent: " + intent.getAction() + " "
                                    + intent.getStringExtra("mytime"),
                            Toast.LENGTH_LONG).show();
                }
            });
            NotificationManager nm = (NotificationManager) getMapView()
                    .getContext()
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            int id = intent.getIntExtra("notificationId", 0);
            Log.d(TAG, "cancelling id: " + id);
            if (id > 0) {
                nm.cancel(id);
            }
        }
    };


    /**
     * Note - this will become a API offering in 4.5.1 and beyond.
     * @param context
     * @param drawableId
     * @return
     */
    private static Bitmap getBitmap(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (drawable instanceof BitmapDrawable) {
            return BitmapFactory.decodeResource(context.getResources(), drawableId);
        } else if (drawable instanceof VectorDrawable) {
            VectorDrawable vectorDrawable = (VectorDrawable) drawable;
            Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(),
                    vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            vectorDrawable.draw(canvas);
            return bitmap;
        } else {
            return null;
        }
    }


    private int[] generateHeatMap() {
        int[] data = new int[] {
                1,1,2,0,0,0,0,0,
                1,4,2,0,0,0,0,0,
                2,2,0,0,0,0,0,0,
                6,6,0,3,0,0,0,3,
                6,6,5,3,1,3,3,3,
                6,6,3,3,0,3,6,6,
                3,3,3,0,0,0,6,5,
                3,0,0,0,0,0,5,5,
        };
        for (int i = 0; i < data.length;++i) {
            if (data[i] != 0)
                data[i] = Color.BLACK / data[i];
        }
        return data;

    }
}
