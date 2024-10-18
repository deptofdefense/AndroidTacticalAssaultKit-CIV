
package com.atakmap.android.drawing;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;

import com.atakmap.android.drawing.details.ShapeMsdDialog;
import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.drawing.mapItems.DrawingEllipse;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.Toast;

import com.atakmap.android.drawing.details.GenericDetailsView;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapGroup.OnItemCallback;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.MultiPolyline;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.vehicle.VehicleShape;
import com.atakmap.android.vehicle.model.VehicleModel;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

public class DrawingToolsMapReceiver extends DropDownReceiver implements
        OnStateListener {

    public static final String DELETE_ACTION = "com.atakmap.android.maps.SHAPE_DELETE";
    public static final String DETAILS_ACTION = "com.atakmap.android.maps.DRAWING_DETAILS";
    public static final String ZOOM_ACTION = "com.atakmap.android.maps.ZOOM_ON_SHAPE";
    public static final String LABEL_ACTION = "com.atakmap.android.maps.LABEL_TOGGLE";
    public static final String EDIT_ACTION = "com.atakmap.android.maps.DRAWING_EDIT";
    public static final String MSD_ACTION = "com.atakmap.android.maps.SHAPE_MSD";

    /**
     * Extra used to define whether the drawing tools toolbar should redisplay when details are
     * closed
     */
    public static final String EXTRA_CREATION_MODE = "creation";

    public static final String IMPORT_KML_ACTION = "com.atakmap.android.maps.DRAWING_KML_IMPORT";

    private static final String SAVE_PATH = FileSystemUtils.getItem(
            FileSystemUtils.OVERLAYS_DIRECTORY)
            .getPath()
            + File.separatorChar;

    private static final String IMPORT_GROUP_NAME = "import_drawing_kml";

    private final MapGroup _drawingGroup; // stores fully inflated drawing objects
    private MapGroup _loadingMapGroup; // stores objects loaded by KML Component from autosave

    private final Context _context;

    String prevUID;

    private boolean _showToolbar = false;

    private ArrayList<GeoPoint> _allPointsBeingLoaded;

    private GenericDetailsView _genDetailsView;
    private int _ignoreClose = 0;

    static final String TAG = "DrawingToolsMapReceiver";

    // starts off with mgrs
    private MapItem _item;

    public DrawingToolsMapReceiver(MapView mapView, MapGroup drawingGroup,
            Context context) {
        super(mapView);
        _drawingGroup = drawingGroup;
        _context = context;

        // prepare to read in KML; first, find the group that the kml will be loaded into
        _loadingMapGroup = mapView.getRootGroup().findMapGroup(
                IMPORT_GROUP_NAME);
        if (_loadingMapGroup == null)
            _loadingMapGroup = mapView.getRootGroup().addGroup(
                    IMPORT_GROUP_NAME);

        // Set up the listener to convert the imported
        _loadingMapGroup.addOnItemListChangedListener(onKMLLoadedListener);
    }

    @Override
    public void disposeImpl() {
    }

    private final MapGroup.OnItemListChangedListener onKMLLoadedListener = new MapGroup.OnItemListChangedListener() {

        @Override
        public void onItemRemoved(MapItem item, MapGroup group) {
            // do nothing
        }

        // TODO: uhm is this even used any more? there's an importfactory, and idk if we allow
        // import of KML inthe UI ATM anyway?
        @Override
        public void onItemAdded(final MapItem item, MapGroup group) {
            // disable saving until item is added
            // _loadingInProgress = true;

            // if we already have a drawing object with that uid, remove it
            Map metaMap = item.getMetaMap("kmlExtendedData");
            String uid = null;

            if (metaMap != null)
                uid = (String) metaMap.get("uid");

            if (uid != null) {
                MapItem existingItem = _drawingGroup.findItem("uid", uid);
                if (existingItem != null)
                    _drawingGroup.removeItem(existingItem);
            } else {
                // TODO: if it doesn't have a UID, convert kml id to a UID-ish by making it
                // filename.id or something?
                uid = UUID.randomUUID().toString();
            }

            // proccess item
            if (item instanceof Polyline) {
                Polyline poly = (Polyline) item;
                GeoPointMetaData[] points = poly.getMetaDataPoints();

                // Don't allow shapes that will hang ATAK
                if (points.length > 1100) {
                    Toast.makeText(
                            _context,
                            "KML file being imported contains a shape with a very large number of points. Drawing tools currently does not support these shapes, it will be skipped.",
                            Toast.LENGTH_LONG).show();
                    group.removeItem(item);
                    return;
                }

                // Don't load empty shapes
                if (points.length == 0) {
                    group.removeItem(item);
                    return;
                }

                DrawingShape ds = new DrawingShape(getMapView(), _drawingGroup,
                        uid);
                ds.setTitle(poly.getMetaString("title", null));

                ds.setColor(poly.getStrokeColor());
                ds.setStrokeWeight(poly.getStrokeWeight());
                ds.setPoints(points);
                ds.setFilled(
                        (poly.getStyle() & Polyline.STYLE_FILLED_MASK) > 0);
                ds.setFillColor(poly.getFillColor());

                _drawingGroup.addItem(ds);

                // need this to remove the old polyline since it was moved from below
                group.removeItem(item);

                if (_allPointsBeingLoaded != null) {
                    // Compute shape's extremes instead of saving all points and then computing;
                    // should save a little memory at least if you have a lot of shapes in one file?
                    int[] e = GeoCalculations.findExtremes(points, 0,
                            points.length, false);
                    GeoPoint northWest = new GeoPoint(
                            points[e[1]].get().getLatitude(),
                            points[e[0]].get().getLongitude());
                    GeoPoint southEast = new GeoPoint(
                            points[e[3]].get().getLatitude(),
                            points[e[2]].get().getLongitude());

                    _allPointsBeingLoaded.add(northWest);
                    _allPointsBeingLoaded.add(southEast);

                    // Collections.addAll(_allPointsBeingLoaded, points);
                }

            } else if (item instanceof Marker) {
                item.setType("generic_point");
                item.setMetaString("kml", "true");
                _drawingGroup.addItem(item);

                if (_allPointsBeingLoaded != null)
                    _allPointsBeingLoaded.add(((Marker) item).getPoint());

            } else {
                // shouldn't happen, unless we're loading a KML created in another tool
                group.removeItem(item);
            }

            // reenable saving
            // _loadingInProgress = false;
        }
    };

    /**
     * this isn't safe to call twice before the first import is done. shouldn't be an issue since it
     * locks the UI thread ATM
     * 
     * @param path Valid paths at the moment are AUTOSAVE_PATH or SAVE_PATH+filename
     */
    void importKML(final String path) {
        // uncheck the kml file for kmlmapcoment. might be done in the UI since it'll be dealing
        // with the list anyway?
        if (path.startsWith(SAVE_PATH)) {
            _allPointsBeingLoaded = new ArrayList<>();

            String fileName = path.replace(SAVE_PATH, "");

            // Since KML files are always loaded now:
            // getMapView().getRootGroup().findMapGroup("KML").findMapGroup(fileName).setVisible(false);
            setVisible(getMapView().getRootGroup().findMapGroup("KML")
                    .findMapGroup(fileName), false);
        }

        // when file finishes loading, if filename isn't autosave_filename, save newly imported
        // objects
        DocumentedIntentFilter showFilter = new DocumentedIntentFilter();
        showFilter
                .addAction(
                        "com.atakmap.map.FINISHED_HANDLING_DATA",
                        "Intent used to signify KML has finished loading, and triggers saving of the newly imported objects.");
        AtakBroadcast.getInstance().registerReceiver(new BroadcastReceiver() {
            boolean done = false;

            @Override
            public void onReceive(final Context context, final Intent intent) {
                Bundle extras = intent.getExtras();
                if (extras == null)
                    return;

                if (IMPORT_GROUP_NAME.equals(extras.getString("group"))
                        && !done) {

                    AtakBroadcast.getInstance().unregisterReceiver(this);
                    done = true;

                    // If we loaded anything, show it on the map
                    if (_allPointsBeingLoaded != null
                            && _allPointsBeingLoaded.size() > 0)
                        ATAKUtilities.scaleToFit(
                                getMapView(),
                                _allPointsBeingLoaded
                                        .toArray(
                                                new GeoPoint[0]),
                                getMapView().getWidth(), getMapView()
                                        .getHeight());

                    _allPointsBeingLoaded = null;
                }
            }
        }, showFilter);

        // send the intent to tell KmlMapComponent to actually load the file
        try {
            final Uri kmlUri = Uri.fromFile(new File(FileSystemUtils
                    .validityScan(
                            path, new String[] {
                                    "kmz", "kml"
                            })));
            Intent intent = new Intent();
            intent.setAction("com.atakmap.map.IMPORT_DATA");
            intent.setDataAndType(kmlUri,
                    "application/vnd.google-earth.kml+xml");
            intent.putExtra("group", IMPORT_GROUP_NAME);
            AtakBroadcast.getInstance().sendBroadcast(intent);
        } catch (IOException ioe) {
            Toast.makeText(
                    _context,
                    "The KML file to import does not contain the correct file extension or has invalid characters in the filename.",
                    Toast.LENGTH_LONG).show();

        }

    }

    // Hide map groups like the overlays manager does it, more or less
    public void setVisible(MapGroup group, final boolean vis) {
        // deep for each item
        // if it doesn't have a filter
        // set visibility
        group.deepForEachItem(new OnItemCallback<MapItem>(MapItem.class) {
            @Override
            public boolean onMapItem(MapItem item) {
                if (item.getMetaBoolean("addToObjList", true)) {
                    item.setVisible(vis);
                }
                return false;
            }
        });
    }

    @Override
    public void onReceive(Context ignoreCtx, Intent intent) {
        final Context context = getMapView().getContext();

        final String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(IMPORT_KML_ACTION)) {
            final String file = intent.getStringExtra("file");
            if (file != null)
                importKML(file);
            else
                Log.d(TAG, "call to import without a file");

            return;
        }

        // Find the map item being targeted
        String uid = intent.getStringExtra("shapeUID");
        if (FileSystemUtils.isEmpty(uid))
            uid = intent.getStringExtra("assocSetUID");
        if (FileSystemUtils.isEmpty(uid))
            uid = intent.getStringExtra("uid");
        if (FileSystemUtils.isEmpty(uid)) {
            Log.w(TAG, "Failed to find UID for action: " + action);
            return;
        }

        MapItem item = getMapView().getMapItem(uid);
        if (item == null) {
            Log.w(TAG, "Failed to find item with UID " + uid
                    + " for action: " + action);
            return;
        }

        switch (action) {
            case DELETE_ACTION: {
                String title = ATAKUtilities.getDisplayName(item);
                final MapItem fItem = item;
                AlertDialog.Builder b = new AlertDialog.Builder(context);
                b.setTitle(R.string.confirmation_dialogue);
                b.setMessage(_context.getString(
                        R.string.confirmation_remove_details, title));
                b.setPositiveButton(R.string.yes, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        fItem.removeFromGroup();
                    }
                });
                b.setNegativeButton(R.string.cancel, null);
                b.show();
                break;
            }
            case DETAILS_ACTION:
            case ZOOM_ACTION: {
                _showToolbar = intent.getBooleanExtra(EXTRA_CREATION_MODE,
                        false);
                boolean edit = intent.getBooleanExtra("edit", false);
                if (action.equals(DETAILS_ACTION)) {
                    if (uid.equals(prevUID)
                            && _genDetailsView != null) {
                        // Un-hide drop-down if it's been hidden
                        if (!isVisible()
                                && DropDownManager.getInstance()
                                        .isTopDropDown(this))
                            DropDownManager.getInstance().unHidePane();
                        if (edit)
                            _genDetailsView.startEditingMode();
                    } else
                        showDetails(item, edit);
                    prevUID = uid;
                } else {
                    _item = item;
                    ATAKUtilities.scaleToFit(getMapView(), _item, false,
                            getMapView().getWidth(),
                            getMapView().getHeight());
                }
                break;
            }
            case LABEL_ACTION: {
                item = ATAKUtilities.findAssocShape(item);
                boolean labelsOn = item.hasMetaValue("labels_on");
                item.toggleMetaData("labels_on", !labelsOn);
                item.persist(getMapView().getMapEventDispatcher(),
                        null, getClass());
                break;
            }
            case MSD_ACTION:
                item = ATAKUtilities.findAssocShape(item);
                if (item instanceof Shape)
                    new ShapeMsdDialog(getMapView()).show((Shape) item);
                break;
        }

        // Register menu items in the tool selector
        if (action.equals("com.atakmap.android.maps.TOOLSELECTOR_READY")) {

            // intent to run when tool is selected
            Intent myLocationIntent = new Intent();
            myLocationIntent
                    .setAction("com.atakmap.android.maps.toolbar.SET_TOOLBAR");
            myLocationIntent.putExtra("toolbar",
                    "com.atakmap.android.drawing.DRAWING_TOOLS");
            // *need* a request code, or we'll overwrite other pending intents with the same action!
            // Hopefully hash code is unique enough?
            PendingIntent act = PendingIntent.getBroadcast(context,
                    this.hashCode(),
                    myLocationIntent, 0);

            // register with selector
            Intent toolSelectorRegisterIntent = new Intent();
            toolSelectorRegisterIntent
                    .setAction("com.atakmap.android.maps.TOOLSELECTION_NOTIFY");
            toolSelectorRegisterIntent
                    .addCategory("com.atakmap.android.maps.INTEGRATION"); // what
                                                                                                    // does
                                                                                                    // the
                                                                                                    // category
                                                                                                    // do?
            toolSelectorRegisterIntent.putExtra("title", "Drawing Tools");
            toolSelectorRegisterIntent.putExtra("action", act);
            AtakBroadcast.getInstance().sendBroadcast(
                    toolSelectorRegisterIntent);
        }
    }

    private void showDetails(final MapItem item, boolean edit) {

        if (_genDetailsView != null) {
            _ignoreClose++;
            cleanup(true);
        }

        int layoutID;
        if (item instanceof VehicleShape)
            layoutID = R.layout.vehicle_shape_details;
        else if (item instanceof VehicleModel)
            layoutID = R.layout.vehicle_model_details;
        else if (item instanceof MultiPolyline)
            layoutID = R.layout.multipolyline_details_view;
        else if (item instanceof DrawingShape)
            layoutID = R.layout.shape_details_view;
        else if (item instanceof Marker)
            layoutID = R.layout.generic_point_details_view;
        else if (item instanceof DrawingCircle)
            layoutID = R.layout.circle_details_view;
        else if (item instanceof DrawingEllipse)
            layoutID = R.layout.ellipse_details_view;
        else if (item instanceof Rectangle)
            layoutID = R.layout.rectangle_details_view;
        else {
            Log.d(TAG, "item not supported: " + item);
            return;
        }
        GenericDetailsView gdv = (GenericDetailsView) LayoutInflater
                .from(_context).inflate(layoutID, getMapView(), false);
        if (!gdv.setItem(getMapView(), item)) {
            Log.e(TAG, "Failed to set item in details view: " + item);
            return;
        }

        _item = item;
        gdv.setDropDownMapReceiver(this);
        setSelected(item, item instanceof Marker
                ? "asset:/icons/outline.png"
                : "");

        showDropDown(gdv, THREE_EIGHTHS_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                HALF_HEIGHT, this);

        _genDetailsView = gdv;

        if (edit) {
            getMapView().post(new Runnable() {
                @Override
                public void run() {
                    gdv.startEditingMode();
                }
            });
        }
    }

    @Override
    public void onDropDownSelectionRemoved() {
        cleanup(false);
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (v && !(_item instanceof Marker)) {
            MapItem menuItem = MapMenuReceiver.getCurrentItem();
            if (menuItem != null && menuItem != _item)
                return;
            ATAKUtilities.scaleToFit(getMapView(), new MapItem[] {
                    _item
            },
                    (int) (getMapView().getWidth() * TWO_THIRDS_WIDTH),
                    getMapView().getHeight());

            if (_genDetailsView != null)
                _genDetailsView.refresh();
        }
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
        if (_ignoreClose > 0)
            _ignoreClose--;
        else
            cleanup(true);
    }

    private void cleanup(boolean persist) {

        if (_genDetailsView != null) {
            _genDetailsView.onClose();
            _genDetailsView = null;
        }

        // save/send the changes that we make to the item
        if (persist && _item != null)
            _item.persist(getMapView().getMapEventDispatcher(), null,
                    this.getClass());

        prevUID = null;
        _item = null;
        if (_showToolbar) {
            Intent intent = new Intent();
            intent.setAction("com.atakmap.android.maps.toolbar.SHOW_TOOLBAR");
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }
    }
}
