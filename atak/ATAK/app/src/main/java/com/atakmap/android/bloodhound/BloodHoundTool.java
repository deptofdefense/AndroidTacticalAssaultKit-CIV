
package com.atakmap.android.bloodhound;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.atakmap.android.bloodhound.ui.BloodHoundHUD;
import com.atakmap.android.bloodhound.ui.BloodHoundNavWidget;
import com.atakmap.android.bloodhound.ui.BloodHoundRouteWidget;
import com.atakmap.android.bloodhound.ui.BloodHoundZoomWidget;
import com.atakmap.android.bloodhound.util.BloodHoundToolLink;
import com.atakmap.android.bloodhound.util.SpiDistanceComparator;
import com.atakmap.android.gui.ActionButton;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.MetaMapPoint;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.navigation.views.NavView;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteMapReceiver;
import com.atakmap.android.routes.RouteNavigator;
import com.atakmap.android.toolbar.ButtonTool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.DisplayManager;
import com.atakmap.android.util.SimpleItemSelectedListener;
import com.atakmap.app.R;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.app.system.ResourceUtil;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.DirectionType;
import com.atakmap.coremap.maps.coords.Ellipsoid;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MGRSPoint;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.map.AtakMapView;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/** Handles the toolbar button for bloodhound */
public class BloodHoundTool extends ButtonTool implements
        ImageButton.OnLongClickListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String BLOOD_HOUND = "com.atakmap.android.toolbars.BLOOD_HOUND";
    public static final String TOOL_IDENTIFIER = "com.atakmap.android.toolbars.BloodHoundButtonTool";
    private static final String TAG = "com.atakmap.android.bloodhound.BloodHoundToolbarButton";

    private final Timer timer;
    private FlashTimerTask timerTask;
    private int flashColor;
    private int outerColor;
    private int middleColor;
    private int innerColor;
    private int flashETA;
    private int outerMiddleETA;
    private int middleInnerETA;
    private NorthReference _northReference;
    private int _rangeUnits;
    private Angle _bearingUnits;
    private boolean _displaySlantRange;
    private CoordinateFormat _coordMode;

    private MapGroup _linkGroup;
    private MapGroup _spiGroup;
    private List<MapItem> _spiItems;
    private PointMapItem _spiItem, _user;

    private BloodHoundPreferences _prefs;

    private PointMapItem _startItem = null;
    private PointMapItem _endItem = null;

    private boolean running = false;
    private boolean manuallyClosed = true;

    private final SimpleSpeedBearingComputer ssc = new SimpleSpeedBearingComputer(
            30);

    private String _uid;

    private final BloodHoundHUD _bloodHoundHUD;
    private final BloodHoundZoomWidget _zoomWidget;
    public final BloodHoundRouteWidget _routeWidget;
    private final BloodHoundNavWidget _navWidget;

    private boolean spinDoNotClear = false;

    // for the point selection activity (key listener, dispatcher, and boolean
    private boolean pointSelectionActive = false;
    private View.OnKeyListener _keyListener;

    private BloodHoundToolLink _link = null;

    public BloodHoundToolLink getlink() {
        return _link;
    }

    public String getUid() {
        return _uid;
    }

    public PointMapItem getStartItem() {
        return _startItem;
    }

    public PointMapItem getSpiItem() {
        return _spiItem;
    }

    public PointMapItem getEndItem() {
        return _endItem;
    }

    public PointMapItem getUser() {
        return _user;
    }

    public BroadcastReceiver getHoundReceiver() {
        return houndReceiver;
    }

    public Boolean getRunning() {
        return running;
    }

    /****************************** CONSTRUCTOR *************************/
    public BloodHoundTool(final MapView mapView) {
        this(mapView, new ImageButton(mapView.getContext()),
                new BloodHoundHUD(mapView));
    }

    private BloodHoundTool(
            final MapView mapView,
            final ImageButton button,
            final BloodHoundHUD bloodHoundHUD) {
        super(mapView, button, TOOL_IDENTIFIER);

        _prefs = new BloodHoundPreferences(mapView);

        _bloodHoundHUD = bloodHoundHUD;
        _bloodHoundHUD.setToolbarButton(this);
        _zoomWidget = new BloodHoundZoomWidget(mapView, this);
        _routeWidget = new BloodHoundRouteWidget(mapView, this);
        _navWidget = new BloodHoundNavWidget(mapView, this);
        _routeWidget.setNavWidget(_navWidget);

        _linkGroup = _mapView.getRootGroup().findMapGroup("Pairing Lines");
        if (_linkGroup == null) {
            _linkGroup = new DefaultMapGroup("Pairing Lines");
            String iconUri = "android.resource://"
                    + mapView.getContext().getPackageName()
                    + "/" + R.drawable.pairing_line_white;
            _mapView.getMapOverlayManager().addShapesOverlay(
                    new DefaultMapGroupOverlay(_mapView, _linkGroup, iconUri));
        }

        _northReference = _prefs.getNorthReference();
        _bearingUnits = _prefs.getBearingUnits();
        _rangeUnits = _prefs.getRangeSystem();
        _coordMode = _prefs.getCoordinateFormat();
        _displaySlantRange = _prefs.get("rab_dist_slant_range",
                "slantrange").equals("slantrange");

        //Bloodhound ETA Flash and Radius Color and Time prefs
        flashColor = _prefs.getFlashColor();
        outerColor = _prefs.getOuterColor();
        middleColor = _prefs.getMiddleColor();
        innerColor = _prefs.getInnerColor();

        flashETA = _prefs.getFlashETA();
        outerMiddleETA = _prefs.getOuterETA();
        middleInnerETA = _prefs.getInnerETA();

        timer = new Timer();

        _prefs = new BloodHoundPreferences(mapView);

        ToolManagerBroadcastReceiver.getInstance().registerTool(
                TOOL_IDENTIFIER, this);

        AtakBroadcast.getInstance().registerReceiver(houndReceiver,
                new AtakBroadcast.DocumentedIntentFilter(BLOOD_HOUND));

        ToolsPreferenceFragment.register(
                new ToolsPreferenceFragment.ToolPreference(
                        _mapView.getContext().getString(
                                R.string.bloodhoundPreferences),
                        _mapView.getContext().getString(
                                R.string.bloodhoundSubPreferences),
                        "bloodhoundPreferences",
                        _mapView.getContext().getResources().getDrawable(
                                R.drawable.ic_menu_bloodhound),
                        new BloodHoundPreferenceFragment()));

        button.setOnLongClickListener(this);

    }

    /** Gets a formatted string of the coordinates of the given point
     *  based on the user's display preferences. */
    private String getCoordinateString(final GeoPoint point) {
        String locationStr = "";

        if (_coordMode == CoordinateFormat.MGRS) {
            MGRSPoint mgrs = MGRSPoint.fromLatLng(Ellipsoid.WGS_84,
                    point.getLatitude(),
                    point.getLongitude(), null);
            locationStr = mgrs.getFormattedString();
        } else if (_coordMode == CoordinateFormat.DD) {
            locationStr = CoordinateFormatUtilities.formatToString(point,
                    CoordinateFormat.DD);
        } else if (_coordMode == CoordinateFormat.DM) {
            locationStr = CoordinateFormatUtilities.formatToString(point,
                    CoordinateFormat.DM);
        } else if (_coordMode == CoordinateFormat.DMS) {
            locationStr = CoordinateFormatUtilities.formatToString(point,
                    CoordinateFormat.DMS);
        }
        return locationStr;
    }

    // Listener for the bloodhound toolbar button
    @Override
    public void onButtonClicked() {
        if (running) {
            manuallyClosed = true;
            running = false;
            stop();
            setActive(false);
        } else {
            manuallyClosed = false;
            running = start();
            setActive(running);
        }
    }

    @Override
    public boolean onLongClick(View view) {
        Toast.makeText(_mapView.getContext(), R.string.bloodhound_tip,
                Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        return super.onToolBegin(extras);
    }

    @Override
    public void setActive(boolean active) {
        super.setActive(running);

        if (active) {
            DisplayManager.acquireTemporaryScreenLock(_mapView,
                    "BloodHoundTool");
        } else {
            DisplayManager.releaseTemporaryScreenLock(_mapView,
                    "BloodHoundTool");
        }
        Log.d(TAG, "bloodhound setActive:" + running);
        NavView.getInstance().setButtonSelected("bloodhound.xml", active);
    }

    /**
     * Pad a positive number by the specified number of places.   If the
     * number is negative - just return the same number.
     * @param value the number of places
     * @return 0 padded number.
     */
    public static String pad3(int value) {
        if (value < 0)
            return Integer.toString(value);

        if (value < 9)
            return "00" + value;
        else if (value < 99)
            return "0" + value;
        else
            return "" + value;
    }

    /**
     * @return whether the device is a tablet or not!
     */
    public boolean isTablet() {
        return MapView.getMapView().getContext().getResources()
                .getBoolean(com.atakmap.app.R.bool.isTablet);
    }

    // TODO: What is this?
    private BroadcastReceiver houndReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, Intent intent) {

            if (intent == null || !intent.hasExtra("uid")) {
                Log.d(TAG, "hound button pressed");
                onButtonClicked();
                return;
            }

            final Bundle extras = intent.getExtras();

            if (extras == null)
                return;

            final String uid = extras.getString("uid");
            Log.d(TAG, "starting hound for: " + uid);
            if (uid == null) {
                Log.d(TAG, "Cannot bloodhound without UID");
                running = false;
                setActive(false);
                requestEndTool();
                return;
            }

            final MapItem item = _mapView.getMapItem(uid);

            if (!(item instanceof PointMapItem)) {
                if (item != null) {
                    Toast.makeText(_mapView.getContext(),
                            R.string.invalid_bloodhound_item,
                            Toast.LENGTH_LONG).show();
                }

                Log.d(TAG, "could not find corresponding map item: " + uid);
                running = false;
                setActive(false);
                requestEndTool();
                return;
            }

            final boolean doNotStart = item.getMetaBoolean("hounding", false);

            if (running) {
                running = false;
                stop();
                setActive(false);
            }

            // this specific map item was already being hounded
            if (doNotStart)
                return;

            if (_spiGroup == null) {
                _spiGroup = _mapView.getRootGroup().findMapGroup("SPIs");
            }

            PointMapItem user = null;
            String fromUID = extras.getString("fromUID");
            if (!FileSystemUtils.isEmpty(fromUID)) {
                MapItem from = _mapView.getRootGroup().deepFindUID(fromUID);
                if (from instanceof PointMapItem)
                    user = (PointMapItem) from;
            }
            if (user == null)
                user = _mapView.getSelfMarker();

            _user = user;
            if (_user.getGroup() == null) { // unplaced
                _mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(
                                _mapView.getContext(),
                                R.string.bloodhound_tip2,
                                Toast.LENGTH_LONG).show();
                        running = false;
                        setActive(false);
                        requestEndTool();
                    }
                });
                return;
            }

            // would occur if the item became unplaced while bh was starting (spi)
            final MapGroup mg = item.getGroup();
            if (mg == null) {
                Log.e(TAG, "item mapgroup is null for bh, returning");
                return;
            }
            item.getGroup().addOnItemListChangedListener(_addListener);

            _startBloodhound(item);
            running = true;

            setActive(true);
        }
    };

    /** Starts the bloodhound tool */
    public boolean start() {
        if (running) {
            Log.d(TAG, "bloodhound already running");
            return true;
        }
        running = true;

        // Disable the nav widget incase it was enabled when the Bloodhound
        // tool was last closed.
        _navWidget.disableWidget();

        _spiGroup = _mapView.getRootGroup().findMapGroup("SPIs");

        PointMapItem pmi = _mapView.getSelfMarker();
        if (pmi.getGroup() != null)
            _user = pmi;
        else
            _user = null;

        if (_spiItem == _user)
            _spiItem = null;

        if (_spiGroup != null) {
            // get all SPI points
            _spiItems = _spiGroup.deepFindItems("type", "b-m-p-s-p-i");
            _searchSPIItems();
            _showDialog();
        }

        _prefs.registerListener(this);

        return true;
    }

    /**
     * Remove invisible SPIs, set parent callsign for other SPIs
     */

    private void _searchSPIItems() {
        Iterator<MapItem> i = _spiItems.iterator();
        while (i.hasNext()) {
            MapItem spiItem = i.next(); //the SPI map item
            if (spiItem == null || !spiItem.getVisible()) {
                // do not allow for bloodhound on non-visible SPI's
                if (spiItem != null)
                    Log.w(TAG, "SPI not visible: " + spiItem.getUID());
                else
                    Log.w(TAG, "SPI does not exist");

                i.remove();
                continue;
            }

            //first look for parent UID
            MapItem parentItem = _mapView.getMapItem(spiItem.getMetaString(
                    "parent_uid", null));
            if (parentItem != null) {
                String pCallSign = parentItem.getMetaString("callsign", null);
                if (pCallSign == null)
                    pCallSign = parentItem.getUID();
                //Log.d(TAG, "Found parent: " + parentItem.getUID() + " with callsign: " + pCallSign);
                spiItem.setMetaString("parent_callsign", pCallSign);
                continue;
            }

            //Now look for spoi_uid. Not sure what this impacts
            parentItem = _mapView.getRootGroup().deepFindItem("spoi_uid",
                    spiItem.getUID());
            String pCallSign;
            if (parentItem != null) {
                pCallSign = parentItem.getMetaString("callsign", null);
                if (pCallSign == null)
                    pCallSign = parentItem.getUID();
                //Log.d(TAG, "Found spoi_uid parent: " + parentItem.getUID() + ", callsign=" + pCallSign);
            } else {
                //otherwise just use SPI callsign, if none exists use the UID as label
                pCallSign = spiItem.getMetaString("callsign", "SPI-"
                        + spiItem.getUID());

                //Log.w(TAG, "Couldn't determine parent of " + spiItem.getUID() + ", using callsign: " + pCallSign);
            }
            spiItem.setMetaString("parent_callsign", pCallSign);
        }

        //now sort based on distance from self
        if (_spiItems.size() > 0 && _user != null && _user.getPoint() != null
                && _user.getPoint().isValid()) {
            Collections.sort(_spiItems, new SpiDistanceComparator(_user));
        }

        // add a blank spi at the very beginning.
        _spiItems.add(0, new MetaMapPoint(
                GeoPointMetaData.wrap(GeoPoint.ZERO_POINT), "fake"));
    }

    public void stop() {
        synchronized (this) {
            _onEndLink();
            _spiGroup.removeOnItemListChangedListener(_addListener);

            if (_spiItem != null) {
                _spiItem.removeOnPointChangedListener(_pointListener);
                _spiItem.removeMetaData("bloodhoundEta");
            }
            if (_user != null) {
                _user.removeOnPointChangedListener(_pointListener);
                _user.removeMetaData("bloodhoundEta");
            }

            _routeWidget.stop();
            _zoomWidget.stop();
            _bloodHoundHUD.stop();

            _trackChangedListener = null;

            running = false;

            _prefs.unregisterListener(this);

            if (pointSelectionActive) {
                TextContainer.getTopInstance().closePrompt();
                _mapView.getMapEventDispatcher().popListeners();
                _mapView.removeOnKeyListener(_keyListener);
            }
        }
    }

    /** Shows the dialog that opens when starting the bloodhound tool */
    private void _showDialog() {
        if (!isValid(_spiItem))
            _spiItem = null;

        LayoutInflater inflater = LayoutInflater.from(_mapView.getContext());
        View view = inflater.inflate(R.layout.bloodhound_select, null);

        ((TextView) view.findViewById(R.id.spinnerItemsLabel)).setText(
                ResourceUtil.getResource(R.string.civ_quick_select_spi,
                        R.string.quick_select_spi));

        final AlertDialog.Builder builder = new AlertDialog.Builder(
                _mapView.getContext());

        builder.setTitle(R.string.bloodhound_dialog);
        builder.setView(view);

        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                running = false;
                setActive(false);
                requestEndTool(); // ok button wasn't pressed
            }
        });

        builder.setNegativeButton(R.string.cancel, // implemented
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                            int which) {
                        running = false;
                        setActive(false);
                        requestEndTool(); // ok button wasn't pressed
                    }
                });

        final Spinner spin = view.findViewById(R.id.spinnerItems);
        final TextView spinLabel = view
                .findViewById(R.id.spinnerItemsLabel);
        spinLabel.setText(ResourceUtil.getResource(
                R.string.civ_quick_select_spi, R.string.quick_select_spi));

        if (_spiItems.size() > 1) {
            ArrayAdapter<MapItem> adapter = new ArrayAdapter<MapItem>(
                    _mapView.getContext(),
                    android.R.layout.simple_spinner_item, _spiItems) {
                @NonNull
                @Override
                public View getView(final int position, final View convertView,
                        @NonNull
                final ViewGroup parent) {
                    View ret = super.getView(position, convertView, parent);
                    return process(ret, position);
                }

                private View process(final View ret, final int position) {
                    MapItem item = this.getItem(position);

                    if (item instanceof MetaMapPoint) {
                        if (ret instanceof TextView)
                            ((TextView) ret).setText("");
                        return ret;
                    } else if (_user == null) {
                        String title = ATAKUtilities
                                .getDisplayName(item);
                        if (ret instanceof TextView)
                            ((TextView) ret).setText(title);
                        return ret;
                    }

                    // otherwise calculate the bearing and range.

                    final GeoPoint userPoint = _user.getPoint();
                    double[] da = new double[] {
                            0d, 0d
                    };

                    if (item instanceof PointMapItem) {
                        da[0] = GeoCalculations.distanceTo(userPoint,
                                ((PointMapItem) item).getPoint());
                        da[1] = GeoCalculations.bearingTo(userPoint,
                                ((PointMapItem) item).getPoint());
                    }

                    final String title = getDisplayLabel(item, da);
                    if (ret instanceof TextView)
                        ((TextView) ret).setText(title);
                    return ret;
                }

                @Override
                public View getDropDownView(int position, View convertView,
                        @NonNull ViewGroup parent) {
                    View ret = super.getDropDownView(position, convertView,
                            parent);
                    return process(ret, position);
                }
            };

            adapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item);
            spin.setAdapter(adapter);
            spin.setVisibility(View.VISIBLE);
            spinLabel.setVisibility(View.VISIBLE);
        } else {
            spin.setVisibility(View.GONE);
            spinLabel.setVisibility(View.GONE);
        }

        builder.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                            int which) {
                        if (_user == null || _spiItem == null
                                || _user == _spiItem) {
                            Toast.makeText(
                                    _mapView.getContext(),
                                    R.string.unable_to_start_bloodhound,
                                    Toast.LENGTH_LONG).show();
                            running = false;
                            setActive(false);
                            requestEndTool();
                            return;
                        }
                        final PointMapItem item = _spiItem;

                        if (isValid(item)) { // Ensure item is still valid
                            // if it has no group, it might be a kml point
                            if (item.getGroup() != null)
                                item.getGroup().addOnItemListChangedListener(
                                        _addListener);
                            _startBloodhound(item);
                        } else {
                            Toast.makeText(
                                    _mapView.getContext(),
                                    R.string.bloodhound_tip3,
                                    Toast.LENGTH_LONG).show();
                            running = false;
                            setActive(false);
                            requestEndTool();
                        }

                    }
                });

        final AlertDialog ad = builder.create();

        final ImageButton btnFromChoose = view
                .findViewById(R.id.btnFromChoose);
        final ActionButton btnFrom = new ActionButton(
                view.findViewById(R.id.btnFrom));
        btnFrom.setEnabled(false);
        setButtonText(_user, btnFrom);

        final ImageButton btnToChoose = view
                .findViewById(R.id.btnToChoose);
        final ActionButton btnTo = new ActionButton(
                view.findViewById(R.id.btnTo));
        btnTo.setEnabled(false);
        if (_spiItem != null)
            btnTo.setText(ATAKUtilities.getDisplayName(_spiItem));

        spin.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view,
                            int pos, long id) {

                        // in the case of the value being set by the map item selector,
                        // do not actually clear out the values
                        if (spinDoNotClear) {
                            spinDoNotClear = false;
                            return;
                        }
                        spinDoNotClear = false;

                        MapItem mi = (MapItem) parent.getItemAtPosition(pos);
                        if (mi instanceof MetaMapPoint) {
                            _spiItem = null;
                            btnTo.setText("");
                        } else if (isValid(mi)) {
                            _spiItem = (PointMapItem) mi;
                            btnTo.setText(ATAKUtilities
                                    .getDisplayName(_spiItem));
                        }
                        ad.getButton(Dialog.BUTTON_POSITIVE).setEnabled(
                                _spiItem != null && _user != null
                                        && _user != _spiItem);
                    }
                });
        btnFromChoose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ad.dismiss();
                select(ad, btnFrom, spin, true);
            }
        });

        btnToChoose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ad.dismiss();
                select(ad, btnTo, spin, false);
            }
        });

        ad.show();
        ad.getButton(Dialog.BUTTON_POSITIVE).setEnabled(
                _spiItem != null && _user != null && _user != _spiItem);
    }

    /**
     * Select either the from or to part of the bloodhound.
     * @param ad the alert dialog to show when selection is finished.
     * @param btn the action button used in the dialog
     * @param from if setting the from point, set this to true - otherwise it sets the to point
     */
    private synchronized void select(final AlertDialog ad,
            final ActionButton btn, final Spinner spin, final boolean from) {
        final MapEventDispatcher dispatcher = _mapView.getMapEventDispatcher();

        pointSelectionActive = true;
        dispatcher.pushListeners();
        dispatcher.clearUserInteractionListeners(false);
        if (from)
            TextContainer.getTopInstance().displayPrompt(
                    _mapView.getContext().getString(R.string.bloodhound_from));
        else
            TextContainer.getTopInstance().displayPrompt(
                    _mapView.getContext().getString(R.string.bloodhound_to));

        _keyListener = new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    TextContainer.getTopInstance().closePrompt();
                    dispatcher.popListeners();
                    _mapView.removeOnKeyListener(this);
                    ad.show();
                    ad.getButton(Dialog.BUTTON_POSITIVE).setEnabled(
                            _spiItem != null && _user != null
                                    && _user != _spiItem);
                    pointSelectionActive = false;
                    return true;
                }
                return false;
            }
        };
        final MapEventDispatcher.MapEventDispatchListener _fromMapListener = new MapEventDispatcher.MapEventDispatchListener() {

            @Override
            public void onMapEvent(MapEvent event) {
                String type = event.getType();
                if (MapEvent.MAP_CLICK.equals(type)) {
                    PointF p = event.getPointF();
                    GeoPointMetaData gp = _mapView.inverse(p.x, p.y,
                            AtakMapView.InverseMode.RayCast);
                    PointMapItem pmi = new PlacePointTool.MarkerCreator(gp)
                            .setType("b-m-p-w-GOTO")
                            .showCotDetails(false)
                            .placePoint();
                    if (from) {
                        _user = pmi;
                        setButtonText(_user, btn);
                    } else {
                        spinDoNotClear = true;
                        _spiItem = pmi;
                        spin.setSelection(0);
                        setButtonText(_spiItem, btn);
                        spinDoNotClear = false;
                    }

                } else if (MapEvent.ITEM_CLICK.equals(type)) {
                    if (event.getItem() instanceof PointMapItem) {
                        if (from) {
                            _user = (PointMapItem) event.getItem();
                            setButtonText(_user, btn);
                        } else {
                            // when the spin selection listener fires, it will attempt to
                            // null out the current selection.  Prevent that with spinDoNotClear
                            spinDoNotClear = true;
                            _spiItem = (PointMapItem) event.getItem();
                            spin.setSelection(0);
                            setButtonText(_spiItem, btn);
                            spinDoNotClear = false;
                        }
                    }

                }
                synchronized (BloodHoundTool.this) {
                    TextContainer.getTopInstance().closePrompt();
                    dispatcher.popListeners();
                    _mapView.removeOnKeyListener(_keyListener);
                    pointSelectionActive = false;
                }
                ad.show();
                ad.getButton(Dialog.BUTTON_POSITIVE).setEnabled(
                        _spiItem != null && _user != null
                                && _user != _spiItem);
            }
        };

        _mapView.addOnKeyListener(_keyListener);
        dispatcher.addMapEventListener(MapEvent.ITEM_CLICK, _fromMapListener);
        dispatcher.addMapEventListener(MapEvent.MAP_CLICK, _fromMapListener);
    }

    // TODO: This is confusing -- we have two OnPointChangedListeners, one named _pointChangedListener,
    // and the other named _pointListener
    private PointMapItem.OnPointChangedListener _pointChangedListener = new PointMapItem.OnPointChangedListener() {
        @Override
        public void onPointChanged(PointMapItem item) {
            final PointMapItem si = _startItem;
            final PointMapItem ei = _endItem;

            if (si != null && ei != null) {
                final String uid = item.getUID();
                String startUID = si.getUID();
                String endUID = ei.getUID();

                if ((startUID.compareTo(uid) == 0)
                        || (endUID.compareTo(uid) == 0)) {
                    _updateLinkInfo();
                }
            }
        }
    };

    private PointMapItem.OnPointChangedListener _pointListener = new PointMapItem.OnPointChangedListener() {
        @Override
        public void onPointChanged(PointMapItem item) {
            // _updateAutoZoom(); //if either move then adjust the map... is this going to be
            // jolting?
            // I think ATAKUtilities accounts for a padding

            // if this is the user and does not have the avgSpeed30 flag set,
            // then do simple speed calculations.
            if (item == _user) {
                if (Double.isNaN(item.getMetaDouble("avgSpeed30", Double.NaN)))
                    ssc.add(item.getPoint());
            }
        }
    };

    /**
     * Starts the bloodhound tool for a given MapItem.
     */
    private void _startBloodhound(final MapItem item) {

        if (timerTask != null) {
            timer.purge();
        }

        timerTask = new FlashTimerTask();
        timer.schedule(timerTask, 300, 300);

        _zoomWidget.setVisible(true);
        _bloodHoundHUD.updateWidget();
        _bloodHoundHUD.setLayoutVisible(true);
        _routeWidget.setVisible(true);
        _navWidget.setVisible(true);

        _spiItem = (PointMapItem) item;
        _uid = _spiItem.getUID();
        _onBeginLink(_user, _spiItem);
        _spiItem.addOnPointChangedListener(_pointListener);
        _user.addOnPointChangedListener(_pointListener);

        // zoom based on the user icon and the SPI Icon - also accounts for usable width
        // height should always be the same as the MapView
        if (_prefs.get("rab_bloodhound_zoom", true)) {
            _zoomWidget.zoom();
        }

        // cause the mechanics of it all to start immediately
        _pointChangedListener.onPointChanged(_spiItem);

    }

    /*
     * This ensures that the bloodhound line will persist after an object has gone stale and
     * returned.
     *
     * Fix for bug 6067: Added a flag to determine whether the user is no longer interested in
     * bloodhounding and the reappearance should be ignored or if we lost comms with the
     * bloodhounded object and should resume tracking it. Also, if tracking is to be resumed, the
     * bloodhound's toolbar button state is correctly set back to active.
     */
    private final MapGroup.OnItemListChangedListener _addListener = new MapGroup.OnItemListChangedListener() {

        @Override
        public void onItemAdded(MapItem item, MapGroup group) {
            if (!manuallyClosed && item.getUID().equals(_uid)) {
                _startBloodhound(item);
                _mapView.post(new Runnable() {
                    @Override
                    public void run() {
                        running = true;
                        setActive(true);
                    }
                });
            }
        }

        @Override
        public void onItemRemoved(MapItem item, MapGroup group) {
            if (item.getUID().equals(_uid)) {
                _user.removeOnPointChangedListener(_pointListener);
            }
        }

    };

    private Marker.OnTrackChangedListener _trackChangedListener = new Marker.OnTrackChangedListener() {

        @Override
        public void onTrackChanged(Marker marker) {
            if (_pointChangedListener != null)
                _pointChangedListener.onPointChanged(marker);
        }
    };

    private void _addLink(final PointMapItem start, final PointMapItem end) {
        _startItem = start;
        _endItem = end;

        if (_startItem == null || _endItem == null)
            return;

        final BloodHoundToolLink linkListener = new BloodHoundToolLink(
                _prefs, _startItem.getUID(), _startItem, _endItem,
                new BloodHoundToolLink.OnDeleteListener() {
                    @Override
                    public void onDelete(BloodHoundToolLink linkListener) {
                        _bloodHoundHUD.refreshWidget("");
                        if (_linkGroup != null) {
                            _linkGroup.removeItem(linkListener.line);
                        }

                        linkListener.line.dispose();

                        if (_link != null) {
                            RouteMapReceiver.getInstance().getRouteGroup()
                                    .removeItem(_link.route);
                            _link.route.dispose();
                            _link = null;
                        }

                        if (_startItem != null) {
                            _startItem
                                    .removeOnGroupChangedListener(linkListener);
                            _startItem.removeOnVisibleChangedListener(
                                    linkListener);
                            _startItem.removeOnPointChangedListener(
                                    _pointChangedListener);
                            if (linkListener.uid.equals(_user.getUID()))
                                ((Marker) _startItem)
                                        .removeOnTrackChangedListener(
                                                _trackChangedListener);
                        }
                        if (_endItem != null) {
                            _endItem.removeOnPointChangedListener(
                                    _pointChangedListener);
                            _endItem.removeOnGroupChangedListener(linkListener);
                            _endItem.removeOnVisibleChangedListener(
                                    linkListener);
                            _endItem.toggleMetaData("hounding", false);
                        }

                        // set to null _startItem and _endItem after both _endItem and _startItem
                        // are cleaned up
                        _startItem = null;
                        _endItem = null;

                        // just in case this has come from a non-button click
                        running = false;
                        stop();
                        _mapView.post(new Runnable() {
                            @Override
                            public void run() {
                                setActive(false);
                            }
                        });
                    }
                });

        try {

            if (_startItem.getType().equals("b-m-p-s-p-i"))
                _startItem.addOnVisibleChangedListener(linkListener);
            if (_endItem.getType().equals("b-m-p-s-p-i"))
                _endItem.addOnVisibleChangedListener(linkListener);
            if (_startItem.getUID().equals(_mapView.getSelfMarker().getUID()))
                ((Marker) _startItem)
                        .addOnTrackChangedListener(_trackChangedListener);
            _linkGroup.addItem(linkListener.line);
            linkListener.line.addOnGroupChangedListener(linkListener);

            _startItem.addOnPointChangedListener(_pointChangedListener);
            _endItem.addOnPointChangedListener(_pointChangedListener);
            _updateLinkInfo();

            _startItem.addOnGroupChangedListener(linkListener);
            _endItem.addOnGroupChangedListener(linkListener);

            _endItem.toggleMetaData("hounding", true);

            _link = linkListener;
        } catch (Exception ignored) {
            // ATAK-14272 NullPointerException Bloodhound Tool - since this logic in thread unsafe
            // but it is lower risk during this sprint than syncronizing modifications to the startItem
            // and endItem
        }
    }

    private boolean _removeLink(final String uid1, final String uid2) {
        _link.delete();
        return true;
    }

    /**
     * Given a mapview and a map item, set the action button text for the title or
     * callsign of the map item.
     */
    private static void setButtonText(final MapItem mi,
            final ActionButton ab) {
        if (mi == null) {
            ab.setText("");
            return;
        }
        String title = "";
        if (mi instanceof Marker) {
            if (!mi.getType().contentEquals("b-r-f-h-c") //CASEVAC type, dt have callsign
                    && !mi.getMetaString("callsign", "").contentEquals(""))
                title = mi.getMetaString("callsign", "");
            else
                title = mi.getTitle();
        }
        if (FileSystemUtils.isEmpty(title))
            title = ATAKUtilities.getDisplayName(mi);
        ab.setText(title);
    }

    private boolean isValid(MapItem mi) {
        return mi instanceof PointMapItem
                && (mi.getVisible() || !mi.getType().equals("b-m-p-s-p-i"))
                && ((PointMapItem) mi).getPoint() != null
                && (mi.getGroup() != null
                        || mi.getType().equals("u-d-feature"));
    }

    private String getDisplayLabel(final MapItem item, final double[] da) {

        String title = ATAKUtilities.getDisplayName(item);
        String parent = item.getMetaString("parent_callsign",
                item.getMetaString("callsign", "[unnamed]"));

        if (!FileSystemUtils.isEquals(title, parent)) {
            title += ": "
                    + SpanUtilities.formatType(Span.METRIC, da[0],
                            Span.METER)
                    + " " +
                    DirectionType.getDirection(da[1]).getAbbreviation();
            title += " (" + parent + ")";
        } else {
            title += ": "
                    + SpanUtilities.formatType(Span.METRIC, da[0],
                            Span.METER)
                    + " " +
                    DirectionType.getDirection(da[1]).getAbbreviation();
        }

        return title;
    }

    /** Function called when creating a new link with the bloodhound toolbar button  */
    private void _onBeginLink(PointMapItem start, PointMapItem end) {
        _startItem = start;
        _endItem = end;
        _endItem.setMetaBoolean("hounding", true);
        _addLink(_startItem, _endItem);
        Log.i(TAG, "Begin Link");
    }

    /** Preform some cleanup when we are no longer tracking a link */
    private void _onEndLink() {
        if (_startItem != null) {
            _startItem.removeOnPointChangedListener(_pointChangedListener);
            if (_startItem.getUID().equals(_user.getUID())) {
                if (_startItem instanceof Marker) {
                    ((Marker) _startItem)
                            .removeOnTrackChangedListener(
                                    _trackChangedListener);
                }
            }
        }
        if (_endItem != null) {
            _endItem.removeMetaData("hounding");
            _endItem.removeOnPointChangedListener(_pointChangedListener);
        }

        if (_startItem != null && _endItem != null) {
            if (_removeLink(_startItem.getUID(), _endItem.getUID())) {
            } else {
                Log.d(TAG,
                        "could not remove link between: "
                                + _startItem.getTitle() +
                                " and " + _endItem.getTitle());
            }
        }
        _bloodHoundHUD.refreshWidget("");
    }

    @Override
    public void dispose() {
        super.dispose();
        if (houndReceiver != null) {
            AtakBroadcast.getInstance().unregisterReceiver(houndReceiver);
            houndReceiver = null;
        }

        _pointChangedListener = null;
        _pointListener = null;
        if (timer != null)
            timer.cancel();
    }

    // don't allow the back button to end this tool, should only be stopped by the toolbar button
    @Override
    public boolean shouldEndOnBack() {
        return false;
    }

    @Override
    public void onToolEnd() {
        super.onToolEnd();

        // No reason to keep the timer task going, only should be running the timer when the
        // tool enters onToolBegin and ends when the onToolEnd.
        timerTask.setEta(Double.NaN);
        timerTask.cancel();
        timer.purge();

        if (_link != null) {
            _link.route.dispose();
            _link = null;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp,
            String key) {

        if (key == null)
            return;

        switch (key) {
            case "rab_north_ref_pref":
                _northReference = _prefs.getNorthReference();
                break;
            case "rab_brg_units_pref":
                _bearingUnits = _prefs.getBearingUnits();
                break;
            case "rab_rng_units_pref":
                _rangeUnits = _prefs.getRangeSystem();
                break;
            case "rab_dist_slant_range":
                _displaySlantRange = _prefs.get("rab_dist_slant_range",
                        "slantrange").equals("slantrange");
                break;
            case "rab_bloodhound_large_textwidget":
            case "rab_bloodhound_display_textwidget":
                _bloodHoundHUD.updateWidget();
                break;
            case "coord_display_pref":
                _coordMode = _prefs.getCoordinateFormat();
                break;
            case "bloodhound_flash_eta":
                flashETA = _prefs.getFlashETA();
                break;
            case "bloodhound_outer_eta":
                outerMiddleETA = _prefs.getOuterETA();
                break;
            case "bloodhound_inner_eta":
                middleInnerETA = _prefs.getInnerETA();
                break;
            case "bloodhound_flash_color_pref":
                flashColor = _prefs.getFlashColor();
                break;
            case "bloodhound_outer_color_pref":
                outerColor = _prefs.getOuterColor();
                break;
            case "bloodhound_middle_color_pref":
                middleColor = _prefs.getMiddleColor();
                break;
            case "bloodhound_inner_color_pref":
                innerColor = _prefs.getInnerColor();
                break;
            default:
                return;
        }
        if (running
                && _startItem != null
                && _endItem != null)
            _updateLinkInfo();
    }

    public void _updateLinkInfo() {
        PointMapItem startPoint = getStartItem();
        PointMapItem endPoint = getEndItem();
        if (startPoint == null || endPoint == null)
            return;

        // currently updates the data when the link information changes, might be better to do
        // this when the data in listener onPointChanged?
        final double range = startPoint.getPoint().distanceTo(
                endPoint.getPoint());
        double bearing = startPoint.getPoint().bearingTo(endPoint.getPoint());
        double relativeBearing = Double.NaN;
        double remainingEta = Double.NaN;

        if (startPoint instanceof Marker) {
            double trackHeading = ((Marker) startPoint).getTrackHeading();
            if (!Double.isNaN(trackHeading))
                relativeBearing = bearing - trackHeading;
        }
        if (Double.isNaN(relativeBearing))
            relativeBearing = bearing - ssc.getBearing();

        String bs;
        if (_northReference == NorthReference.MAGNETIC) {
            double bearingMag = ATAKUtilities.convertFromTrueToMagnetic(
                    startPoint.getPoint(), bearing);
            bs = DirectionType.getDirection(bearingMag).getAbbreviation()
                    + "   " + AngleUtilities.format(bearingMag, _bearingUnits)
                    + "M";
        } else {
            bs = DirectionType.getDirection(bearing).getAbbreviation() + "   "
                    + AngleUtilities.format(bearing, _bearingUnits) + "T";
        }

        String title = ATAKUtilities.getDisplayName(endPoint);
        if (!FileSystemUtils.isEmpty(title) && title.length() > 20)
            title = title.substring(0, 17) + "...";
        String text = "" + title + "\n"
                + getCoordinateString(endPoint.getPoint()) + "\n";

        //be sure slant range was computed
        double slantRange = GeoCalculations.slantDistanceTo(
                startPoint.getPoint(), endPoint.getPoint());
        if (!Double.isNaN(slantRange)) {
            if (_displaySlantRange) {
                text += bs + " "
                        + SpanUtilities.formatType(_rangeUnits, slantRange,
                                Span.METER);
            } else {
                //user pref selected ground clamped direction instead of slant range
                text += bs + " " + SpanUtilities.formatType(_rangeUnits, range,
                        Span.METER);
            }

            double offset = -1;
            if (EGM96.getHAE(startPoint.getPoint()) < EGM96
                    .getHAE(endPoint.getPoint())) {
                offset = 1;
            }

            double depAngle = offset * ((Math.acos(range / slantRange) *
                    ConversionFactors.DEGREES_TO_RADIANS));

            // Depression angle is computed based on slant range
            if (startPoint.getPoint().isAltitudeValid()
                    && endPoint.getPoint().isAltitudeValid()) {
                text += "   "
                        + AngleUtilities.format(Math.abs(depAngle),
                                _bearingUnits, false);
                if (depAngle > 0) {
                    text += "\u2191"; //Up arrow
                } else if (depAngle < 0) {
                    text += "\u2193"; //Down arrow
                }
            }
        } else {
            //failed to compute slant range, set ground clamped direction
            text += SpanUtilities.formatType(_rangeUnits, range, Span.METER)
                    + " " + bs;
        }

        // Convert to azimuth
        while (relativeBearing < 0)
            relativeBearing += 360;
        relativeBearing %= 360;

        RangeAndBearingMapItem line = null;
        Route route = null;

        BloodHoundToolLink bloodhoundLink = getlink();
        if (bloodhoundLink != null && bloodhoundLink.line != null) {
            line = bloodhoundLink.line;
        }

        if (bloodhoundLink != null && bloodhoundLink.isRoute()) {
            route = bloodhoundLink.route;
        }

        String bearingString = "---";
        String remainingEtaString = "---";
        if (!Double.isNaN(relativeBearing)) {
            int v = (int) Math.round(relativeBearing);
            if (v > 180)
                v = -(360 - v);

            if (v < 0) v+=360;

            bearingString = pad3(v);

            try {
                double avgSpeed = getUser()
                        .getMetaDouble("avgSpeed30", Double.NaN);
                Log.d(TAG, "average speed: " + avgSpeed);
                if (Double.isNaN(avgSpeed)
                        && getUser() instanceof Marker) {
                    avgSpeed = ((Marker) getUser())
                            .getTrackSpeed();
                    Log.d(TAG,
                            "dbg - average speed not found, using instantaneous: "
                                    + avgSpeed);
                }
                if (Double.isNaN(avgSpeed)) {
                    avgSpeed = ssc.getAverageSpeed();
                    Log.d(TAG,
                            "dbg - average speed not found, using simple speed comp: "
                                    + avgSpeed);
                }
                if (!Double.isNaN(avgSpeed)
                        && Double.compare(avgSpeed, 0.0) != 0) {
                    remainingEta = range / avgSpeed;
                    if (route != null) {
                        Log.d(TAG, "Calculating eta by route");
                        remainingEta = route.getTotalDistance() / avgSpeed;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "caught an error obtaining the user speed", e);
            }

            if (!Double.isNaN(remainingEta)) {
                startPoint.setMetaDouble("bloodhoundEta", remainingEta);

                remainingEtaString = BloodHoundHUD.formatTime(remainingEta);
                if (!_prefs.get("rab_bloodhound_flash_colors", true)
                        || remainingEta > flashETA) { // default six minutes
                    timerTask.setEnable(false);
                } else {
                    timerTask.setEnable(true);
                }

                timerTask.setEta(remainingEta);

                int currentColor = currentColor();

                _bloodHoundHUD.setColor(currentColor);

                final BloodHoundToolLink link = getlink();
                if (link != null) {
                    link.setColor(currentColor);
                }
            } else {
                // TODO: What is this metadata used for?
                startPoint.removeMetaData("bloodhoundEta");
                timerTask.setEta(Double.NaN);
                _bloodHoundHUD.setColor(outerColor);
                final BloodHoundToolLink link = getlink();
                if (link != null) {
                    link.setColor(outerColor);
                }
            }

            //notify the line to update the ETA w/out delay
            if (line != null) {
                line.onPointChanged(startPoint);
            }
        }

        if (!_prefs.get("rab_bloodhound_display_textwidget", true)) {
            //still need to compute numbers above and set bloodhoundEta on the start point
            //but dont display the widget
            _bloodHoundHUD.refreshWidget("");
        } else {
            String label = text + "\n" + bearingString
                    + Angle.DEGREE_SYMBOL + "R   ETA: "
                    + remainingEtaString;
            _bloodHoundHUD.refreshWidget(label);
        }

        // Hook to the next waypoint
        PointMapItem next = RouteNavigator.getNextWaypoint(endPoint, range,
                _mapView, _prefs.getSharedPrefs());
        if (next != null)
            getHoundReceiver().onReceive(_mapView.getContext(),
                    new Intent()
                            .putExtra("uid", next.getUID())
                            .putExtra("fromUID", startPoint.getUID()));
    }

    public int currentColor() {
        if (timerTask != null) {
            double eta = timerTask.getEta();
            if (!Double.isNaN(eta)) {
                if (eta > outerMiddleETA) {
                    return outerColor;
                } else if (eta > middleInnerETA) {
                    return middleColor;
                } else {
                    return innerColor;
                }
            } else {
                return outerColor;
            }
        } else {
            return outerColor;
        }
    }

    public void dismissTimer() {
        if (timerTask != null)
            timerTask.setDismissed(true);
    }

    /** A task that is run to flash the color on the bloodhound link when a certain distance
     *  threshold is reached. */
    private class FlashTimerTask extends TimerTask {
        private boolean enable = false;
        private boolean dismissed = false;
        private double eta = Double.NaN;
        int blink = 0;

        public void setEnable(final boolean enable) {
            this.enable = enable;
            if (!this.enable)
                dismissed = false;
        }

        public void setDismissed(final boolean dismissed) {
            this.dismissed = dismissed;
        }

        public void setEta(final double eta) {
            this.eta = eta;
        }

        public double getEta() {
            return eta;
        }

        @Override
        public void run() {
            if (_mapView != null)
                _mapView.post(new Runnable() {
                    @Override
                    public void run() {

                        if (!enable || dismissed)
                            return;

                        BloodHoundToolLink link = getlink();
                        if (eta <= flashETA && blink % 9 == 0) {
                            _bloodHoundHUD.setColor(flashColor);
                            if (link != null)
                                link.setColor(flashColor);
                        } else {
                            int currentColor = currentColor();
                            _bloodHoundHUD.setColor(currentColor);
                            if (link != null)
                                link.setColor(currentColor);
                        }
                        ++blink;
                    }
                });
        }
    }
}
