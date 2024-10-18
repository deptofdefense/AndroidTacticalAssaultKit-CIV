
package com.atakmap.android.toolbars;

import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.cotdetails.extras.ExtraDetailsLayout;
import com.atakmap.android.drawing.details.GenericDetailsView;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.gui.ColorPalette.OnColorSelectedListener;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.gui.RangeAndBearingTableHandler;
import com.atakmap.android.hashtags.view.RemarksLayout;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.elevation.RouteElevationBroadcastReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class RangeAndBearingDropDown extends DropDownReceiver implements
        OnStateListener, View.OnClickListener, MapItem.OnGroupChangedListener,
        PointMapItem.OnPointChangedListener,
        MapEventDispatcher.MapEventDispatchListener {

    private final SharedPreferences _prefs;
    protected ViewGroup _dropDownView;
    protected RangeAndBearingMapItem _rabItem;
    private PointMapItem _rabPoint1, _rabPoint2;

    protected EditText _nameEditText;
    protected RemarksLayout _remarksEditText;

    protected ImageButton _colorButton;
    protected Button _elevationButton;
    protected ImageButton _sendButton;

    protected ImageButton _reverseButton;

    protected LinearLayout _startPointButton;
    protected LinearLayout _endPointButton;

    protected ImageButton _rbPanTo;

    protected ImageView _startPointIcon;
    protected ImageView _endPointIcon;

    protected TextView _startPointLabel;
    protected TextView _endPointLabel;

    // eta and speed group
    protected ViewGroup _etaParent;
    protected TextView _speedTitle;
    protected EditText _speedEditText;
    private TextView _etaTitle;
    protected TextView _etaText;
    private boolean _showEta;

    protected ExtraDetailsLayout _extrasLayout;

    protected int _paddingValue;

    protected RangeAndBearingTableHandler rabtable;

    private CoordinateFormat _coordinateFormat;
    protected RouteRangeAndBearingWrapper _tmpRoute;
    private int _speedUnits;
    private Resources _resources;

    public static final String TAG = "RangeAndBearingDropDown";

    public RangeAndBearingDropDown(MapView mapView) {
        super(mapView);

        _resources = mapView.getContext().getResources();

        _prefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());
        _prefs.registerOnSharedPreferenceChangeListener(prefsChangedListener);

        _coordinateFormat = CoordinateFormat.find(_prefs.getString(
                "coord_display_pref",
                getMapView().getContext().getString(
                        R.string.coord_display_pref_default)));

        // eta & speed
        _speedUnits = Integer
                .parseInt(_prefs.getString("speed_unit_pref", "3"));
        _showEta = _prefs.getBoolean("rab_preference_show_eta", false);

        _dropDownView = (ViewGroup) LayoutInflater.from(mapView.getContext())
                .inflate(R.layout.rab_line_details, mapView, false);

        mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_PERSIST, this);
        initializeWidgets();
    }

    @Override
    public void disposeImpl() {
        _prefs.unregisterOnSharedPreferenceChangeListener(prefsChangedListener);
        getMapView().getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_PERSIST, this);
    }

    RangeAndBearingMapItem pending = null;

    protected void openRangeAndBearing() {
        showDropDown(_dropDownView, THREE_EIGHTHS_WIDTH, FULL_HEIGHT,
                FULL_WIDTH, HALF_HEIGHT, this);
        setSelected(_rabItem, "");
        setRetain(true);
        populateWidgets();
        //add listeners to update listeners if a point is removed
        createOnRemoveListeners();
    }

    @Override
    public void onMapEvent(MapEvent event) {
        final MapItem mi = event.getItem();
        if (mi != null && mi == _rabItem) {
            getMapView().post(new Runnable() {
                @Override
                public void run() {
                    if (mi == _rabItem)
                        populateWidgets();
                }
            });
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String id = intent.getExtras().getString("id", null);
        pending = (RangeAndBearingMapItem) getMapView().getRootGroup()
                .deepFindItem("uid", id);

        if (_rabItem != null) {
            if (!isClosed())
                closeDropDown();
        } else if (pending != null) {
            _rabItem = pending;
            openRangeAndBearing();
            pending = null;
        } else {
            Log.d(TAG,
                    "show range and bearing requested, but the item is not found");
        }

    }

    final OnSharedPreferenceChangeListener prefsChangedListener = new OnSharedPreferenceChangeListener() {

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs,
                String key) {

            if (key == null)
                return;

            switch (key) {
                case "coord_display_pref":
                    _coordinateFormat = CoordinateFormat.find(_prefs.getString(
                            "coord_display_pref",
                            getMapView().getContext().getString(
                                    R.string.coord_display_pref_default)));
                    break;
                case "speed_unit_pref":
                    _speedUnits = Integer.parseInt(
                            prefs.getString(key,
                                    Integer.toString(_speedUnits)));
                    if (!isClosed()) {
                        populateWidgets();
                    }
                    break;
                case "rab_preference_show_eta":
                    _showEta = prefs.getBoolean("rab_preference_show_eta",
                            false);
                    if (!isClosed()) {
                        populateWidgets();
                    }
                    break;
            }
        }
    };

    protected void initializeWidgets() {
        GenericDetailsView.addEditTextPrompts(_dropDownView);
        _nameEditText = _dropDownView
                .findViewById(R.id.nameEditText);
        _nameEditText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        _nameEditText.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (_rabItem != null)
                    _rabItem.setTitle(s.toString());
            }
        });

        _remarksEditText = _dropDownView.findViewById(R.id.remarksLayout);
        _remarksEditText.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (_rabItem != null)
                    _rabItem.setRemarks(s.toString());
            }
        });

        _colorButton = _dropDownView
                .findViewById(R.id.colorButton);
        _colorButton.setOnClickListener(this);

        _elevationButton = _dropDownView
                .findViewById(R.id.elevationButton);
        _elevationButton.setOnClickListener(this);

        _sendButton = _dropDownView.findViewById(R.id.sendLayout);
        _sendButton.setOnClickListener(this);

        _reverseButton = _dropDownView
                .findViewById(R.id.reverseButton);
        _reverseButton.setOnClickListener(this);

        _startPointButton = _dropDownView
                .findViewById(R.id.startPointButton);
        _startPointButton.setOnClickListener(this);

        _endPointButton = _dropDownView
                .findViewById(R.id.endPointButton);
        _endPointButton.setOnClickListener(this);

        _rbPanTo = _dropDownView.findViewById(R.id.rbPanTo);
        _rbPanTo.setOnClickListener(this);

        _startPointIcon = _dropDownView
                .findViewById(R.id.startPointIcon);
        _endPointIcon = _dropDownView
                .findViewById(R.id.endPointIcon);

        _startPointLabel = _dropDownView
                .findViewById(R.id.startPointLabel);
        _endPointLabel = _dropDownView
                .findViewById(R.id.endPointLabel);

        // eta & speed
        _etaParent = _dropDownView
                .findViewById(R.id.eta_and_speed_group);
        _etaText = _dropDownView.findViewById(R.id.eta_text);
        _speedTitle = _dropDownView
                .findViewById(R.id.manual_speed_entry_title);
        _speedEditText = _dropDownView
                .findViewById(R.id.manual_speed_entry);
        _speedEditText.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (_rabItem != null) {
                    if (s == null || s.length() == 0) {
                        _rabItem.setSpeed(Double.NaN);
                    } else {
                        try {
                            double speed = Double.parseDouble(s.toString());
                            _rabItem.setSpeed(convertSpeedToMps(speed));
                            populateLocationWidgets();
                        } catch (NumberFormatException nfe) {
                            Log.e(TAG,
                                    "Invalid entry for speed in the R&B Details",
                                    nfe);
                            _rabItem.setSpeed(Double.NaN);
                        }
                    }
                }
            }
        });
        _paddingValue = _startPointButton.getPaddingLeft();

        rabtable = new RangeAndBearingTableHandler(_dropDownView);

        _extrasLayout = _dropDownView.findViewById(R.id.extrasLayout);
    }

    @Override
    public void onClick(View v) {

        // Set line color
        if (v == _colorButton)
            displayColorSelectDialog();

        // Reverse line points
        else if (v == _reverseButton)
            reverseLine();

        // Pan to entire line
        else if (v == _rbPanTo) {
            try {
                PointMapItem start = _rabItem.getPoint1Item();
                PointMapItem end = _rabItem.getPoint2Item();
                if (start != null && end != null)
                    ATAKUtilities.scaleToFit(getMapView(), start, end);
            } catch (Exception e) {
                Log.d(TAG, "error: ", e);
            }
        }

        // Focus on start point
        else if (v == _startPointButton) {
            if (_startPointIcon.getVisibility() != View.GONE)
                MapTouchController.goTo(_rabItem.getPoint1Item(), true);
            else
                displayCoordinateDialog(_rabItem.getPoint1(), 1);
        }

        // Focus on end point
        else if (v == _endPointButton) {
            if (_endPointIcon.getVisibility() != View.GONE)
                MapTouchController.goTo(_rabItem.getPoint2Item(), true);
            else
                displayCoordinateDialog(_rabItem.getPoint2(), 2);
        }

        // Send Range and Bearing line
        else if (v == _sendButton) {
            AtakBroadcast.getInstance().sendBroadcast(new Intent(
                    ContactPresenceDropdown.SEND_LIST)
                            .putExtra("targetUID", _rabItem.getUID()));
        }

        // View elevation profile
        else if (v == _elevationButton) {
            // quick and dirty solution after refactoring is done.
            // XXX: bandaid, pass down null;
            if (_tmpRoute != null)
                _tmpRoute.dispose();
            _tmpRoute = new RouteRangeAndBearingWrapper(getMapView(),
                    _rabItem, getMapView().getRootGroup());

            RouteElevationBroadcastReceiver.getInstance().setRoute(
                    _tmpRoute,
                    false);
            RouteElevationBroadcastReceiver.getInstance().setTitle(
                    _tmpRoute.getTitle());
            RouteElevationBroadcastReceiver.getInstance().openDropDown();
        }
    }

    protected void populateWidgets() {
        populateLocationWidgets();

        boolean editable = !_rabItem.hasMetaValue("nevercot");
        boolean hounding = _rabItem.hasMetaValue("hounding");
        _nameEditText.setText(_rabItem.getTitle());
        _nameEditText.setEnabled(editable);

        _remarksEditText.setText(_rabItem.getMetaString("remarks", ""));
        _remarksEditText.setEnabled(editable);

        updateColorButton(_rabItem.getStrokeColor());
        _colorButton.setEnabled(editable && !hounding);
        _sendButton.setVisibility(editable ? View.VISIBLE : View.GONE);

        // eta & speed
        if (_rabItem != null && _showEta) {
            // set up the speed section
            String[] speedUnitDisplay = _resources
                    .getStringArray(R.array.speed_units_display);
            String speedUnits = speedUnitDisplay[_speedUnits];
            String speedTitle = _resources.getString(R.string.estimated_speed);
            String descr = String.format("%s (%s)", speedTitle, speedUnits);
            _speedTitle.setText(descr);
            _speedEditText.setContentDescription(descr);

            double speed = _rabItem != null
                    ? _rabItem.getMetaDouble(
                            RangeAndBearingMapItem.META_USER_SPEED, Double.NaN)
                    : Double.NaN;
            if (!Double.isNaN(speed)) {
                speed = convertSpeedFromMps(speed);
                String text = String.format(LocaleUtil.getCurrent(), "%.0f",
                        speed);
                _speedEditText.setText(text);
                _speedEditText.setSelection(text.length());
            } else {
                _speedEditText.setText("");
            }
            _etaParent.setVisibility(View.VISIBLE);
        } else {
            _etaParent.setVisibility(View.GONE);
        }

        _extrasLayout.setItem(_rabItem);
    }

    protected void populateLocationWidgets() {
        getMapView().post(new Runnable() {
            @Override
            public void run() {
                if (_rabItem == null)
                    return;
                PointMapItem start = _rabItem.getPoint1Item(),
                        end = _rabItem.getPoint2Item();
                updateItem(start, _startPointLabel, _startPointIcon,
                        _startPointButton);
                updateItem(end, _endPointLabel, _endPointIcon,
                        _endPointButton);
                _reverseButton.setEnabled(!_rabItem.getMetaBoolean(
                        "disable_polar", false));

                // set up the eta section when one of the endpoints changes
                String etaString = _rabItem
                        .getMetaString(RangeAndBearingMapItem.META_ETA, null);
                _etaText.setText(etaString != null ? etaString : "--");
            }
        });

        if (_rabItem != null)
            rabtable.update(_rabItem.getPoint1().get(),
                    _rabItem.getPoint2().get());
    }

    private void updateItem(PointMapItem pmi, TextView label, ImageView icon,
            View btn) {
        if (pmi == null)
            return;
        MapItem mi = pmi;
        if (mi.hasMetaValue("shapeUID"))
            mi = ATAKUtilities.findAssocShape(mi);
        String title = null;
        if (mi != null && !(mi instanceof RangeAndBearingEndpoint))
            title = ATAKUtilities.getDisplayName(mi);
        if (FileSystemUtils.isEmpty(title)) {
            String coordinate = CoordinateFormatUtilities.formatToString(
                    pmi.getPoint(), _coordinateFormat);
            label.setText(coordinate);
            label.setGravity(Gravity.CENTER);
            icon.setVisibility(View.GONE);
            btn.setPadding(10, btn.getPaddingTop(), btn.getPaddingRight(),
                    btn.getPaddingBottom());
        } else {
            ATAKUtilities.SetIcon(getMapView().getContext(), icon, mi);
            label.setText(title);
            label.setGravity(Gravity.START);
            btn.setPadding(_paddingValue, btn.getPaddingTop(),
                    btn.getPaddingRight(), btn.getPaddingBottom());
        }
    }

    @Override
    public void onPointChanged(PointMapItem pointMapItem) {
        populateLocationWidgets();
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        ((PointMapItem) item).removeOnPointChangedListener(this);
        if (_rabItem == null)
            return;
        populateLocationWidgets();
        PointMapItem pm1 = _rabItem.getPoint1Item();
        PointMapItem pm2 = _rabItem.getPoint2Item();
        if (pm1 != null && pm1 != item && _rabPoint1 == item) {
            pm1.addOnPointChangedListener(this);
            pm1.addOnGroupChangedListener(this);
        }
        if (pm2 != null && pm2 != item && _rabPoint2 == item) {
            pm2.addOnPointChangedListener(this);
            pm2.addOnGroupChangedListener(this);
        }
        _rabPoint1 = pm1;
        _rabPoint2 = pm2;
    }

    protected void createOnRemoveListeners() {
        removeOnRemoveListeners();
        if (_rabItem != null) {
            _rabPoint1 = _rabItem.getPoint1Item();
            _rabPoint2 = _rabItem.getPoint2Item();
            if (_rabPoint1 != null)
                _rabPoint1.addOnGroupChangedListener(this);
            if (_rabPoint2 != null)
                _rabPoint2.addOnGroupChangedListener(this);
        }
    }

    private void removeOnRemoveListeners() {
        if (_rabItem != null) {
            if (_rabPoint1 != null)
                _rabPoint1.removeOnGroupChangedListener(this);
            if (_rabPoint2 != null)
                _rabPoint2.removeOnGroupChangedListener(this);
        }
    }

    private void createOnMoveListeners() {
        removeOnMoveListeners();
        if (_rabItem != null) {
            if (_rabItem.getPoint1Item() != null)
                _rabItem.getPoint1Item().addOnPointChangedListener(this);
            if (_rabItem.getPoint2Item() != null)
                _rabItem.getPoint2Item().addOnPointChangedListener(this);
        }
    }

    private void removeOnMoveListeners() {
        if (_rabItem != null) {
            if (_rabItem.getPoint1Item() != null)
                _rabItem.getPoint1Item().removeOnPointChangedListener(this);
            if (_rabItem.getPoint2Item() != null)
                _rabItem.getPoint2Item().removeOnPointChangedListener(this);
        }
    }

    private void createMetaBoolListener() {
        if (_rabItem != null)
            _rabItem.addMetaBooleanListener(this);
    }

    private void removeMetaBoolListener() {
        if (_rabItem != null)
            _rabItem.addMetaBooleanListener(null);
    }

    public void updateUnits() {
        populateLocationWidgets();
    }

    private void displayColorSelectDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(
                getMapView().getContext())
                        .setTitle(getMapView().getResources().getString(
                                R.string.rb_color_dialog));
        ColorPalette palette = new ColorPalette(getMapView().getContext(),
                _rabItem.getStrokeColor());
        dialogBuilder.setView(palette);
        final AlertDialog alert = dialogBuilder.create();

        OnColorSelectedListener l = new OnColorSelectedListener() {
            @Override
            public void onColorSelected(int color, String label) {
                updateColorButton(color);
                _rabItem.setStrokeColor(color);
                alert.cancel();
            }
        };

        palette.setOnColorSelectedListener(l);
        alert.show();
    }

    protected void updateColorButton(final int color) {
        _colorButton.post(new Runnable() {
            @Override
            public void run() {
                _colorButton.setColorFilter((color & 0xFFFFFF) + 0xFF000000,
                        PorterDuff.Mode.MULTIPLY);
            }
        });
    }

    protected void reverseLine() {
        _rabItem.reverse();
        populateLocationWidgets();
    }

    protected void displayCoordinateDialog(GeoPointMetaData point,
            final int pointNum) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getMapView()
                .getContext());
        LayoutInflater inflater = LayoutInflater
                .from(getMapView().getContext());

        final CoordDialogView coordView = (CoordDialogView) inflater.inflate(
                R.layout.draper_coord_dialog, null);

        builder.setTitle(
                getMapView().getResources().getString(R.string.rb_coord_title))
                .setView(coordView)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {

                                _coordinateFormat = coordView.getCoordFormat();
                                GeoPointMetaData p = coordView.getPoint();

                                // bail if the point is null
                                if (p == null)
                                    return;

                                if (pointNum == 1) {
                                    _rabItem.setPoint1(p);
                                } else if (pointNum == 2) {
                                    _rabItem.setPoint2(p);
                                }

                                populateLocationWidgets();
                            }
                        })
                .setNegativeButton(R.string.cancel, null);

        coordView.setParameters(point, getMapView().getPoint(),
                _coordinateFormat);

        builder.show();

    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownClose() {
        if (_tmpRoute != null)
            _tmpRoute.dispose();
        _tmpRoute = null;

        removeOnRemoveListeners();
        removeOnMoveListeners();
        removeMetaBoolListener();

        _rabItem.setMetaString("remarks",
                _remarksEditText.getText());
        _rabItem.setTitle(_nameEditText.getText().toString());

        // TODO: probably shouldn't replicate if nothing changes,
        // this is also the case for some other dropdowns though.
        _rabItem.persist(getMapView().getMapEventDispatcher(), null,
                this.getClass());

        if (pending != null) {
            _rabItem = pending;
            pending = null;
            openRangeAndBearing();
        } else {
            _rabItem = null;
        }
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {

    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (v) {
            if (_tmpRoute != null)
                _tmpRoute.dispose();
            _tmpRoute = null;
            createOnMoveListeners(); //add listeners to update display when a point moves
            createMetaBoolListener(); //add listeners to watch for changes to metaBool values (to
            populateLocationWidgets();
        } else {
            removeOnMoveListeners();
            removeMetaBoolListener();
        }
    }

    protected double convertSpeedToMps(double speed) {
        if (Double.isNaN(speed)) {
            return speed;
        }

        switch (_speedUnits) {
            case 0:
                return speed / ConversionFactors.METERS_PER_S_TO_MILES_PER_H;
            case 1:
                return speed
                        / ConversionFactors.METERS_PER_S_TO_KILOMETERS_PER_H;
            case 2:
                return speed / ConversionFactors.METERS_PER_S_TO_KNOTS;
            case 3:
            default:
                return speed;
            // otherwise, we're in meters per second
        }
    }

    private double convertSpeedFromMps(double speed) {
        if (Double.isNaN(speed)) {
            return speed;
        }

        switch (_speedUnits) {
            case 0:
                return speed * ConversionFactors.METERS_PER_S_TO_MILES_PER_H;
            case 1:
                return speed
                        * ConversionFactors.METERS_PER_S_TO_KILOMETERS_PER_H;
            case 2:
                return speed * ConversionFactors.METERS_PER_S_TO_KNOTS;
            case 3:
            default:
                return speed;
        }
    }
}
