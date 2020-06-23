
package com.atakmap.android.bloodhound;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.routes.RouteNavigator;
import com.atakmap.android.user.PlacePointTool;
import com.atakmap.android.util.SimpleItemSelectedListener;

import com.atakmap.android.gui.ActionButton;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.MetaMapPoint;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.android.toolbar.ButtonTool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.menu.ActionMenuData;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.app.R;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.DirectionType;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.Ellipsoid;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MGRSPoint;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.map.AtakMapView;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static android.support.v4.app.ActivityCompat.invalidateOptionsMenu;

/**
 * Responsible for handling the display of the bloodhound information.
 */
public class BloodHoundButtonTool extends ButtonTool implements
        MapWidget.OnClickListener, MapWidget.OnLongPressListener,
        ActionBarReceiver.ActionBarChangeListener,
        View.OnLongClickListener, OnSharedPreferenceChangeListener {

    public static final String BLOOD_HOUND = "com.atakmap.android.toolbars.BLOOD_HOUND";

    private static ActionMenuData _amd;

    /****************************** FIELDS *************************/
    public static final String TAG = "BloodHoundButtonTool";

    private MapGroup _spiGroup;
    private List<MapItem> _spiItems;
    private PointMapItem _spiItem, _user;

    private PointMapItem _startItem = null;
    private PointMapItem _endItem = null;

    private final Map<String, Map<String, _Link>> _links = new HashMap<>();
    private MapGroup _linkGroup;
    private LinearLayoutWidget layoutWidget;
    private TextWidget textWidget;

    private MapTextFormat _format;
    private CoordinateFormat _coordMode;

    private boolean running = false;
    private boolean manuallyClosed = true;

    private NorthReference _northReference;
    private int _rangeUnits;
    private Angle _bearingUnits;
    /**
     * True for slant range, false for ground clamped
     */
    private boolean _displaySlantRange;

    private String _uid;
    private final BloodHoundPreferences _prefs;

    private boolean spinDoNotClear = false;

    private final Timer timer;
    private FlashTimerTask timerTask;

    // for the point selection activity (key listener, dispatcher, and boolean
    private boolean pointSelectionActive = false;
    private View.OnKeyListener _keyListener;

    private SimpleSpeedBearingComputer ssc = new SimpleSpeedBearingComputer(30);

    public static final String TOOL_IDENTIFIER = "com.atakmap.android.toolbars.BloodHoundButtonTool";

    private int flashColor;
    private int outerColor;
    private int middleColor;
    private int innerColor;
    private int flashETA;
    private int outerMiddleETA;
    private int middleInnerETA;

    /****************************** CONSTRUCTOR *************************/
    public BloodHoundButtonTool(final MapView mapView) {
        this(mapView, new ImageButton(mapView.getContext()));
    }

    private BloodHoundButtonTool(final MapView mapView,
            final ImageButton button) {
        super(mapView, button, TOOL_IDENTIFIER);

        _linkGroup = _mapView.getRootGroup().findMapGroup("Pairing Lines");
        if (_linkGroup == null) {
            _linkGroup = new DefaultMapGroup("Pairing Lines");
            String iconUri = "android.resource://"
                    + mapView.getContext().getPackageName()
                    + "/" + R.drawable.pairing_line_white;
            _mapView.getMapOverlayManager().addShapesOverlay(
                    new DefaultMapGroupOverlay(_mapView, _linkGroup, iconUri));
        }

        RootLayoutWidget root = (RootLayoutWidget) _mapView
                .getComponentExtra("rootLayoutWidget");
        this.layoutWidget = root.getLayout(RootLayoutWidget.BOTTOM_LEFT)
                .getOrCreateLayout("BL_H/BL_V/Bloodhound_V");
        this.layoutWidget.setVisible(false);
        this.layoutWidget.setMargins(16f, 0f, 0f, 16f);

        _prefs = new BloodHoundPreferences(mapView);

        _coordMode = _prefs.getCoordinateFormat();

        int textSize = _prefs.get("rab_bloodhound_large_textwidget",
                false) ? isTablet() ? 16 : 10 : isTablet() ? 6 : 3;
        _format = MapView.getTextFormat(Typeface.DEFAULT_BOLD, textSize);

        MarkerIconWidget iconWidget = new MarkerIconWidget();
        iconWidget.setName("Bloodhound Icon");

        final String imageUri = "android.resource://"
                + _mapView.getContext().getPackageName() + "/"
                + R.drawable.bloodhound_widget;

        Icon.Builder builder = new Icon.Builder();
        builder.setAnchor(0, 0);
        builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
        builder.setSize(48, 48);
        builder.setImageUri(Icon.STATE_DEFAULT, imageUri);

        final Icon icon = builder.build();
        iconWidget.setIcon(icon);
        iconWidget.addOnClickListener(this);

        textWidget = new TextWidget("", _format);
        textWidget.setName("Bloodhound Text");

        //Bloodhound ETA Flash and Radius Color and Time prefs
        flashColor = _prefs.getFlashColor();
        outerColor = _prefs.getOuterColor();
        middleColor = _prefs.getMiddleColor();
        innerColor = _prefs.getInnerColor();

        flashETA = _prefs.getFlashETA();
        outerMiddleETA = _prefs.getOuterETA();
        middleInnerETA = _prefs.getInnerETA();

        //end Bloodhound ETA Flash and Radius Color and Time prefs

        // try to compute the largest width without doing this each time the text is redrawn
        textWidget.setColor(outerColor);
        textWidget.addOnLongPressListener(this);

        this.layoutWidget.addWidget(textWidget);
        this.layoutWidget.addWidget(iconWidget);

        _northReference = _prefs.getNorthReference();
        _bearingUnits = _prefs.getBearingUnits();
        _rangeUnits = _prefs.getRangeSystem();
        _displaySlantRange = _prefs.get("rab_dist_slant_range",
                "slantrange").equals("slantrange");

        _prefs.registerListener(this);
        ToolManagerBroadcastReceiver.getInstance().registerTool(
                TOOL_IDENTIFIER, this);

        DocumentedIntentFilter bloodFilter = new DocumentedIntentFilter();
        bloodFilter.addAction(BLOOD_HOUND);
        AtakBroadcast.getInstance()
                .registerReceiver(houndReceiver, bloodFilter);

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

        timer = new Timer();

        button.setOnLongClickListener(this);
        ActionBarReceiver.registerActionBarChangeListener(this);
    }

    @Override
    public boolean onLongClick(View view) {
        Toast.makeText(_mapView.getContext(), R.string.bloodhound_tip,
                Toast.LENGTH_SHORT).show();
        return true;
    }

    /**
     * @return whether the device is a tablet or not!
     */
    public boolean isTablet() {
        return MapView.getMapView().getContext().getResources()
                .getBoolean(com.atakmap.app.R.bool.isTablet);
    }

    private void updateWidget() {
        int textSize = _prefs.get("rab_bloodhound_large_textwidget",
                false) ? isTablet() ? 16 : 10 : isTablet() ? 6 : 3;
        _format = MapView.getTextFormat(Typeface.DEFAULT_BOLD, textSize);
        this.textWidget.setTextFormat(_format);
        this.textWidget.setVisible(_prefs.get(
                "rab_bloodhound_display_textwidget", true));
    }

    @Override
    public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
        _zoom();
    }

    @Override
    public void onMapWidgetLongPress(MapWidget widget) {
        if (timerTask != null)
            timerTask.setDismissed(true);
    }

    /****************************** INHERITED METHODS *************************/

    @Override
    public void dispose() {
        super.dispose();
        if (houndReceiver != null) {
            AtakBroadcast.getInstance().unregisterReceiver(houndReceiver);
            houndReceiver = null;
        }

        _trackChangedListener = null;
        _pointChangedListener = null;
        _pointListener = null;
        _prefs.unregisterListener(this);
        if (timer != null)
            timer.cancel();

    }

    // don't allow the back button to end this tool, should only be stopped by the toolbar button
    @Override
    public boolean shouldEndOnBack() {
        return false;
    }

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
    public void setActive(boolean active) {
        super.setActive(running);
        Log.d(TAG, "bloodhound setActive:" + running);
        if (_amd != null) {
            _amd.setSelected(active);
            invalidateOptionsMenu((Activity) _mapView.getContext());
        }
    }

    @Override
    public boolean actionBarChanged() {
        _amd = ActionBarReceiver.getMenuItem("BloodHound Tool");
        if (_amd != null) {
            Log.d(TAG, "setting the menu icon based on: " + getActive());
            boolean retval = _amd.isSelected() != getActive();
            _amd.setSelected(getActive());
            return retval;
        }
        return false;
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        return super.onToolBegin(extras);
    }

    private BroadcastReceiver houndReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, Intent intent) {

            if (_amd == null) {
                _amd = ActionBarReceiver.getMenuItem("BloodHound Tool");
            }

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

    public boolean start() {
        if (running) {
            Log.d(TAG, "bloodhound already running");
            return true;
        }
        running = true;

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

        return true;
    }

    public void stop() {
        synchronized (this) {
            _onEndLink();
            if (_spiItem != null) {
                _spiItem.removeOnPointChangedListener(_pointListener);
                _spiItem.removeMetaData("bloodhoundEta");
            }
            if (_user != null) {
                _user.removeOnPointChangedListener(_pointListener);
                _user.removeMetaData("bloodhoundEta");
            }
            _spiGroup.removeOnItemListChangedListener(_addListener);
            running = false;
            if (timer != null && timerTask != null) {
                timerTask.cancel();
                timer.purge();
            }
            this.layoutWidget.setVisible(false);
            ssc.reset();

            if (pointSelectionActive) {
                TextContainer.getTopInstance().closePrompt();
                _mapView.getMapEventDispatcher().popListeners();
                _mapView.removeOnKeyListener(_keyListener);
            }

        }
    }

    @Override
    public void onToolEnd() {
        super.onToolEnd();
    }

    /****************************** PRIVATE METHODS *************************/

    private void _showDialog() {
        if (!isValid(_spiItem))
            _spiItem = null;

        LayoutInflater inflater = LayoutInflater.from(_mapView.getContext());
        View view = inflater.inflate(R.layout.bloodhound_select, null);

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
                        da = DistanceCalculations.computeDirection(userPoint,
                                ((PointMapItem) item).getPoint());
                    }

                    String title = getDisplayLabel(item, da);
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
                    Point p = event.getPoint();
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
                synchronized (BloodHoundButtonTool.this) {
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

    private void _startBloodhound(MapItem item) {

        this.layoutWidget.setVisible(true);
        try {
            if (timer != null && timerTask != null) {
                timerTask.cancel();
                timer.purge();
            }
        } catch (Exception ignored) {
        }

        timerTask = new FlashTimerTask();
        if (timer != null)
            timer.schedule(timerTask, 300, 300);

        _spiItem = (PointMapItem) item;
        _uid = _spiItem.getUID();
        _onBeginLink(_user, _spiItem);
        _spiItem.addOnPointChangedListener(_pointListener);
        _user.addOnPointChangedListener(_pointListener);
        // zoom based on the user icon and the SPI Icon - also accounts for usable width
        // height should always be the same as the MapView
        if (_prefs.get("rab_bloodhound_zoom", true)) {
            _zoom();
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
            if (parentItem != null) {
                String pCallSign = parentItem.getMetaString("callsign", null);
                if (pCallSign == null)
                    pCallSign = parentItem.getUID();
                //Log.d(TAG, "Found spoi_uid parent: " + parentItem.getUID() + ", callsign=" + pCallSign);
                spiItem.setMetaString("parent_callsign", pCallSign);
            } else {
                //otherwise just use SPI callsign, if none exists use the UID as label
                String pCallSign = spiItem.getMetaString("callsign", "SPI-"
                        + spiItem.getUID());

                //Log.w(TAG, "Couldn't determine parent of " + spiItem.getUID() + ", using callsign: " + pCallSign);
                spiItem.setMetaString("parent_callsign", pCallSign);
            }
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

    private static final double div3600d = 1d / 3600d;
    private static final double div60d = 1d / 60d;

    public static String formatTime(double timeInSeconds) {
        int hours = (int) (timeInSeconds * div3600d);
        int remainder = (int) timeInSeconds % 3600;
        int minutes = (int) (remainder * div60d);
        int seconds = remainder % 60;
        if (hours > 0)
            return String.format(LocaleUtil.getCurrent(), "%02d:%02d:%02d",
                    hours, minutes, seconds);
        else
            return String.format(LocaleUtil.getCurrent(), "%02d:%02d",
                    minutes, seconds);
    }

    private String pad3(final int v) {
        if (v >= 100)
            return Integer.toString(v);
        else if (v >= 10)
            return "  " + v;
        else if (v >= 0)
            return "  " + v;
        else
            return Integer.toString(v);
    }

    private void _updateLinkInfo() {
        PointMapItem startPoint = _startItem;
        PointMapItem endPoint = _endItem;
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
        double slantRange = DistanceCalculations.calculateSlantRange(
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

            //Depression angle is computed based on slant range
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
        Map<String, _Link> map = _links.get(startPoint.getUID());
        if (map != null) {
            _Link link = map.get(endPoint.getUID());
            if (link != null && link.line != null) {
                line = link.line;
            }
        }

        String bearingString = "---";
        String remainingEtaString = "---";
        if (!Double.isNaN(relativeBearing)) {
            int v = (int) Math.round(relativeBearing);
            if (v > 180)
                v = -(360 - v);

            bearingString = pad3(v);

            try {
                double avgSpeed = _user.getMetaDouble("avgSpeed30", Double.NaN);
                Log.d(TAG, "average speed: " + avgSpeed);
                if (Double.isNaN(avgSpeed) && _user instanceof Marker) {
                    avgSpeed = ((Marker) _user).getTrackSpeed();
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
                }
            } catch (Exception e) {
                Log.e(TAG, "caught an error obtaining the user speed", e);
            }
            if (!Double.isNaN(remainingEta)) {
                startPoint.setMetaDouble("bloodhoundEta", remainingEta);

                remainingEtaString = formatTime(remainingEta);
                if (!_prefs.get("rab_bloodhound_flash_colors", true)
                        || remainingEta > flashETA) { // default six minutes
                    timerTask.setEnable(false);
                } else {
                    timerTask.setEnable(true);
                }

                timerTask.setEta(remainingEta);

                if (remainingEta > outerMiddleETA) { // default three minutes
                    textWidget.setColor(outerColor);
                    if (line != null) {
                        line.setStrokeColor(outerColor);
                    }
                } else if (remainingEta > middleInnerETA) { // default one minute
                    textWidget.setColor(middleColor);
                    if (line != null) {
                        line.setStrokeColor(middleColor);
                    }
                } else {
                    textWidget.setColor(innerColor);
                    if (line != null) {
                        line.setStrokeColor(innerColor);
                    }
                }
            } else {
                textWidget.setColor(outerColor);
                if (line != null) {
                    line.setStrokeColor(outerColor);
                }
                startPoint.removeMetaData("bloodhoundEta");
            }

            //notify the line to update the ETA w/out delay
            if (line != null) {
                line.onPointChanged(startPoint);
            }
        }

        if (!_prefs.get("rab_bloodhound_display_textwidget", true)) {
            //still need to compute numbers above and set bloodhoundEta on the start point
            //but dont display the widget
            refreshWidget("");
        } else {
            String label = text + "\n" + bearingString
                    + Angle.DEGREE_SYMBOL + "R   ETA: "
                    + remainingEtaString;
            refreshWidget(label);
        }

        // Hook to the next waypoint
        PointMapItem next = RouteNavigator.getNextWaypoint(endPoint, range,
                _mapView, _prefs.getSharedPrefs());
        if (next != null)
            houndReceiver.onReceive(_mapView.getContext(), new Intent()
                    .putExtra("uid", next.getUID())
                    .putExtra("fromUID", startPoint.getUID()));
    }

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

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp,
            String key) {

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
                updateWidget();
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
        if (running && _startItem != null && _endItem != null)
            _updateLinkInfo();
    }

    private void _addLink(final PointMapItem start, final PointMapItem end) {
        _startItem = start;
        _endItem = end;

        if (_startItem == null || _endItem == null)
            return;

        final _Link link = new _Link(_startItem.getUID());
        link.line = create(_startItem, _endItem);
        link.line.setStrokeColor(outerColor);
        link.line.setStrokeWeight(3d);
        _linkGroup.addItem(link.line);

        _startItem.addOnPointChangedListener(_pointChangedListener);
        _endItem.addOnPointChangedListener(_pointChangedListener);

        if (_startItem.getType().equals("b-m-p-s-p-i"))
            _startItem.addOnVisibleChangedListener(link);
        if (_endItem.getType().equals("b-m-p-s-p-i"))
            _endItem.addOnVisibleChangedListener(link);
        if (_startItem.getUID().equals(_mapView.getSelfMarker().getUID()))
            ((Marker) _startItem)
                    .addOnTrackChangedListener(_trackChangedListener);
        _updateLinkInfo();

        _startItem.addOnGroupChangedListener(link);
        _endItem.addOnGroupChangedListener(link);

        _startItem.setMetaBoolean("pairingline_on", true);
        _endItem.setMetaBoolean("pairingline_on", true);

        Map<String, _Link> map = _links.get(_startItem.getUID());
        if (map == null) {
            map = new HashMap<>();
            _links.put(_startItem.getUID(), map);
        }
        map.put(_endItem.getUID(), link);
    }

    private boolean _removeLink(final String uid1, final String uid2) {
        _Link link = null;
        Map<String, _Link> map = _links.get(uid1);
        if (map != null) {
            link = map.remove(uid2);
            if (map.size() == 0) {
                _links.remove(uid2);
            }
        }

        if (link != null) {
            _startItem.removeOnGroupChangedListener(link);
            _startItem.removeOnVisibleChangedListener(link);
            _endItem.removeOnGroupChangedListener(link);
            _endItem.removeOnVisibleChangedListener(link);
            _startItem.setMetaBoolean("pairingline_on", false);
            _endItem.setMetaBoolean("pairingline_on", false);
            _linkGroup.removeItem(link.line);
            if (link.line != null)
                link.line.dispose();
        }
        return link != null;
    }

    private void _onBeginLink(PointMapItem start, PointMapItem end) {
        _startItem = start;
        _endItem = end;
        _endItem.setMetaBoolean("hounding", true);
        _addLink(_startItem, _endItem);
        Log.i(TAG, "Begin Link");
    }

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
                Log.d(TAG,
                        "could not remove link between: "
                                + _startItem.getTitle() +
                                " and " + _endItem.getTitle());
            }
        }

        refreshWidget("");
    }

    private void _zoom() {
        // when the videodropdown is open it sets the focus point of the map
        Point focus = _mapView.getMapController().getFocusPoint();
        if (_spiItem == null || _user == null)
            return;

        ATAKUtilities.scaleToFit(_mapView, new MapItem[] {
                _user, _spiItem
        },
                (int) (focus.x * 1.75), (int) (focus.y * 1.75));
    }

    /****************************** LISTENERS *************************/

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

    private Marker.OnTrackChangedListener _trackChangedListener = new Marker.OnTrackChangedListener() {

        @Override
        public void onTrackChanged(Marker marker) {
            _pointChangedListener.onPointChanged(marker);
        }
    };

    private RangeAndBearingMapItem create(final PointMapItem start,
            final PointMapItem end) {

        RangeAndBearingMapItem rb = RangeAndBearingMapItem
                .createOrUpdateRABLine(start.getUID() + "" + end.getUID(),
                        start, end, false);
        rb.setType("rb");
        rb.setZOrder(-1000d);
        rb.setStrokeColor(outerColor);
        rb.setMetaBoolean("nonremovable", true);
        rb.setBearingUnits(_bearingUnits);
        rb.setNorthReference(_northReference);
        rb.setMetaBoolean("displayBloodhoundEta", true);
        rb.setMetaBoolean("disable_polar", true);
        return rb;
    }

    private void refreshWidget(final String fText) {
        MapView.getMapView().post(new Runnable() {
            @Override
            public void run() {
                if (FileSystemUtils.isEmpty(fText)) {
                    textWidget.setVisible(false);
                } else {
                    textWidget.setVisible(true);
                    textWidget.setText(fText);
                }
            }
        });
    }

    private class _Link implements MapItem.OnGroupChangedListener,
            MapItem.OnVisibleChangedListener {

        RangeAndBearingMapItem line;
        String uid;

        _Link(String uid) {
            this.uid = uid;
        }

        @Override
        public void onItemAdded(MapItem item, MapGroup newParent) {
        }

        @Override
        public void onItemRemoved(MapItem item, MapGroup oldParent) {
            delete();
        }

        @Override
        public void onVisibleChanged(MapItem item) {
            if (item == null || !item.getVisible())
                delete();
        }

        private void delete() {
            refreshWidget("");

            if (_linkGroup != null) {
                _linkGroup.removeItem(line);
                if (line != null)
                    line.dispose();
                line = null;
            }

            if (_startItem != null) {
                _startItem.removeOnGroupChangedListener(this);
                _startItem.removeOnVisibleChangedListener(this);
                _startItem.removeOnPointChangedListener(
                        _pointChangedListener);
                if (uid.equals(_user.getUID()))
                    ((Marker) _startItem)
                            .removeOnTrackChangedListener(
                                    _trackChangedListener);
                _links.remove(_startItem.getUID());
            }
            if (_endItem != null) {
                _endItem.removeOnPointChangedListener(
                        _pointChangedListener);
                _endItem.removeOnGroupChangedListener(this);
                _endItem.removeOnVisibleChangedListener(this);
                _links.remove(_endItem.getUID());
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
    }

    private class FlashTimerTask extends TimerTask {
        private boolean enable = false;
        private boolean dismissed = false;
        private double eta;
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

        @Override
        public void run() {
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    if (!enable || dismissed)
                        return;

                    RangeAndBearingMapItem line = null;
                    if (_links != null && _startItem != null
                            && _endItem != null) {
                        Map<String, _Link> map = _links
                                .get(_startItem.getUID());
                        if (map != null) {
                            _Link link = map.get(_endItem.getUID());
                            if (link != null) {
                                line = link.line;
                            }
                        }
                    }

                    if (eta > outerMiddleETA && eta <= flashETA) {
                        if (blink % 9 == 0) {
                            textWidget.setColor(flashColor);
                            if (line != null)
                                line.setStrokeColor(flashColor);
                        } else {
                            textWidget.setColor(outerColor);
                            if (line != null)
                                line.setStrokeColor(outerColor);
                        }
                    } else if (eta <= outerMiddleETA && eta > middleInnerETA) {
                        if (blink % 6 == 0) {
                            textWidget.setColor(flashColor);
                            if (line != null)
                                line.setStrokeColor(flashColor);
                        } else {
                            textWidget.setColor(middleColor);
                            if (line != null)
                                line.setStrokeColor(middleColor);
                        }
                    } else if (eta <= middleInnerETA) {
                        if (blink % 3 == 0) {
                            textWidget.setColor(flashColor);
                            if (line != null)
                                line.setStrokeColor(flashColor);
                        } else {
                            textWidget.setColor(innerColor);
                            if (line != null)
                                line.setStrokeColor(innerColor);
                        }
                    } else {
                        textWidget.setColor(outerColor);
                        if (line != null)
                            line.setStrokeColor(outerColor);
                    }
                    ++blink;
                }
            });
        }
    }

    private static class SpiDistanceComparator implements Comparator<MapItem> {

        private final PointMapItem _ref;

        SpiDistanceComparator(PointMapItem ref) {
            _ref = ref;
        }

        @Override
        public int compare(final MapItem lhs, final MapItem rhs) {
            if (lhs == rhs)
                return 0;

            if (!(lhs instanceof PointMapItem))
                return 1;
            else if (!(rhs instanceof PointMapItem))
                return -1;

            PointMapItem plhs = (PointMapItem) lhs;
            PointMapItem prhs = (PointMapItem) rhs;
            if (plhs.getPoint() == null || !plhs.getPoint().isValid())
                return 1;
            else if (prhs.getPoint() == null || !prhs.getPoint().isValid())
                return -1;

            double lhsDist = _ref.getPoint().distanceTo(plhs.getPoint());
            double rhsDist = _ref.getPoint().distanceTo(prhs.getPoint());

            return lhsDist < rhsDist ? -1 : 1;
        }
    }
}
