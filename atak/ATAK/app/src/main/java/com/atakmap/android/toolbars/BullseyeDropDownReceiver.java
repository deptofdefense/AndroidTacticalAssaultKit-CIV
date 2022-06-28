
package com.atakmap.android.toolbars;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.atakmap.android.cotdetails.extras.ExtraDetailsLayout;
import com.atakmap.android.drawing.details.GenericDetailsView;
import com.atakmap.android.hashtags.HashtagContent;
import com.atakmap.android.hashtags.HashtagManager;
import com.atakmap.android.hashtags.view.RemarksLayout;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.android.util.SimpleItemSelectedListener;

import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.maps.Shape.OnPointsChangedListener;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.widgets.AngleOverlayShape;
import com.atakmap.android.widgets.AutoSizeAngleOverlayShape;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.map.CameraController;

import java.text.DecimalFormat;

/**
 * TODO: Clean up bullseyes the same way we did with drawing circles
 * Why isn't there just a "Bullseye" or "BullseyeShape" class?
 * Why do we need to perform a blind lookup of the bullseye and range circle
 * every time we open the drop-down? These should just be members of
 * the Bullseye class
 */
public class BullseyeDropDownReceiver extends DropDownReceiver implements
        OnStateListener, OnPointsChangedListener, View.OnClickListener,
        MapItem.OnGroupChangedListener, HashtagManager.OnUpdateListener {

    private static final String TAG = "BullseyeDropDownReceiver";

    protected static final DecimalFormat DEC_FMT_2 = LocaleUtil
            .getDecimalFormat("#.##");

    public static final double STROKE_WEIGHT = 3d;
    public static final int STYLE = Polyline.STYLE_OUTLINE_STROKE_MASK
            | Polyline.STYLE_CLOSED_MASK |
            Polyline.STYLE_STROKE_MASK | Polyline.STYLE_FILLED_MASK;

    protected static final Span[] unitsArray = new Span[] {
            Span.METER, Span.KILOMETER, Span.NAUTICALMILE, Span.FOOT, Span.MILE
    };

    public static final String DROPDOWN_TOOL_IDENTIFIER = "com.atakmap.android.toolbars.BullseyeDropDown";
    public static final String RINGS_GROUP_PREFIX = "rangeRings.";

    protected AngleOverlayShape aos = null;

    protected final MapView _mapView;
    protected final Context _context;
    protected final UnitPreferences _rPrefs;
    protected final UnitPreferences _bPrefs;
    protected Marker centerMarker;
    protected MapGroup subGroup;
    protected RangeCircle rabCircle = null;

    protected ViewGroup bullseyeLayout;
    private TextView directionLabel;
    private TextView bearingUnitLabel, bearingRefLabel;
    protected Spinner bUnits;
    protected Spinner rUnits;
    private CheckBox showRingsCB;
    private TextView numRingsTV;
    private Button bRadiusButton, rRadiusButton;
    protected EditText title;
    private View ringsLayout;
    private View radiusLayout;
    protected UnitsArrayAdapter unitsAdapter;
    private TextView centerPointLabel;
    protected Intent reopenIntent;
    private RemarksLayout remarksLayout;
    protected ExtraDetailsLayout extrasLayout;

    protected final AutoSizeAngleOverlayShape.OnPropertyChangedListener propertyChangedListener = new AutoSizeAngleOverlayShape.OnPropertyChangedListener() {
        @Override
        public void onPropertyChanged() {
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    refresh();
                }
            });
        }
    };

    public BullseyeDropDownReceiver(MapView mapView) {
        super(mapView);
        _mapView = mapView;
        _context = mapView.getContext();
        _rPrefs = new UnitPreferences(mapView);
        _bPrefs = new UnitPreferences(_mapView);
    }

    @Override
    public void onReceive(Context ignoreContext, Intent intent) {

        if (isVisible()) {
            reopenIntent = intent;
            closeDropDown();
            return;
        }

        rabCircle = null;
        aos = null;
        centerMarker = null;

        boolean edit = intent.hasExtra("edit");

        String markerUID = intent.getStringExtra("marker_uid");
        MapItem mi = _mapView.getRootGroup().deepFindUID(markerUID);
        if (mi instanceof Marker)
            centerMarker = (Marker) mi;

        if (centerMarker == null)
            return;

        String bUID = centerMarker.getMetaString("bullseyeUID", "");
        mi = _mapView.getRootGroup().deepFindUID(bUID);
        if (mi instanceof AngleOverlayShape)
            aos = (AngleOverlayShape) mi;
        else if (edit)
            return;

        if (aos != null) {
            String rUID = aos.getMetaString("rangeRingUID", "");
            mi = _mapView.getRootGroup().deepFindUID(rUID);
            if (mi instanceof RangeCircle)
                rabCircle = (RangeCircle) mi;
        }

        if (!edit && aos == null) {
            //if center marker is a hostile, use default FAH circle size
            if (centerMarker.getType().startsWith("a-h")) {
                double distance = _rPrefs.get("fahDistance", 5) * 1852d;
                aos = BullseyeTool.createBullseye(centerMarker, distance);
            } else
                aos = BullseyeTool.createBullseye(centerMarker);
        }

        if (aos != null)
            subGroup = aos.getGroup();

        openBullseye();
    }

    protected void openBullseye() {
        if (!isClosed())
            closeDropDown();

        if (aos != null) {
            aos.addOnPointsChangedListener(this);
            aos.addOnGroupChangedListener(this);
            aos.addOnPropertyChangedListener(propertyChangedListener);
        }

        createLayout();

        showDropDown(bullseyeLayout, THREE_EIGHTHS_WIDTH, FULL_HEIGHT,
                FULL_WIDTH, HALF_HEIGHT, this);
        setRetain(true);
    }

    /**
     * Toggle if the Bullseye's range rings are shown or not
     * 
     * @param intent - the intent with the relevant info
     */
    public void toggleRings(Intent intent) {
        String markerUID = intent.getStringExtra("marker_uid");
        MapItem mi = _mapView.getRootGroup().deepFindUID(markerUID);
        if (!(mi instanceof Marker))
            return;

        Marker marker = (Marker) mi;

        String bUID = marker.getMetaString("bullseyeUID", "");
        mi = _mapView.getRootGroup().deepFindUID(bUID);
        if (!(mi instanceof AngleOverlayShape))
            return;

        AngleOverlayShape aos = (AngleOverlayShape) mi;
        MapGroup subGroup = aos.getGroup();
        if (subGroup == null)
            return;

        String rUID = aos.getMetaString("rangeRingUID", "");
        RangeCircle rabCircle = null;
        mi = _mapView.getRootGroup().deepFindUID(rUID);
        if (mi instanceof RangeCircle)
            rabCircle = (RangeCircle) mi;

        //if the dropdown is showing this bullseye's information, just
        //toggle the checkbox and the handler will do the rest
        if (isVisible() && this.aos == aos) {
            if (showRingsCB.isChecked())
                showRingsCB.setChecked(false);
            else
                showRingsCB.setChecked(true);
            return;
        }

        if (rabCircle == null) {
            //add a subgroup for the rings because it makes the rings easier to manage when removing them
            rabCircle = buildRings(marker, aos);

            aos.setMetaString("rangeRingUID", rabCircle.getUID());
            subGroup.addItem(rabCircle);

            marker.setMetaBoolean("rangeRingVisible", true);
            rabCircle.setVisible(true);

        } else {
            if (rabCircle.getVisible()) {
                rabCircle.setVisible(false);
                marker.setMetaBoolean("rangeRingVisible", false);
            } else {
                rabCircle.setVisible(true);
                marker.setMetaBoolean("rangeRingVisible", true);
            }
        }
        marker.persist(_mapView.getMapEventDispatcher(),
                null, getClass());
    }

    /**
     * Toggle if the Bullseye labels are from edge to center or center to edge 
     * 
     * @param intent - the intent with the relevant info
     */
    public void toggleDirection(Intent intent) {
        String markerUID = intent.getStringExtra("marker_uid");
        Marker centerMarker = (Marker) MapGroup.deepFindItemWithMetaString(
                MapView
                        .getMapView().getRootGroup(),
                "uid", markerUID);
        AngleOverlayShape aos;
        RangeCircle rabCircle = null;
        if (centerMarker != null) {
            MapItem mi = MapGroup.deepFindItemWithMetaString(
                    MapView._mapView.getRootGroup(), "uid",
                    centerMarker.getMetaString("bullseyeUID", ""));
            if (mi instanceof AngleOverlayShape)
                aos = (AngleOverlayShape) mi;
            else
                return;

            MapGroup subGroup = aos.getGroup();
            if (subGroup != null) {
                MapItem rings = subGroup.deepFindItem("uid",
                        aos.getMetaString("rangeRingUID", ""));
                if (rings instanceof RangeCircle)
                    rabCircle = (RangeCircle) rings;
            }

            if (aos.isShowingEdgeToCenter()) {
                aos.setEdgeToCenterDirection(false);
                if (rabCircle != null)
                    rabCircle.setColor(Color.GREEN);
            } else {
                aos.setEdgeToCenterDirection(true);
                if (rabCircle != null)
                    rabCircle.setColor(Color.RED);
            }
            centerMarker.persist(_mapView.getMapEventDispatcher(),
                    null, getClass());
            refresh();
        }
    }

    /**
     * Toggle if the bearing of the Bullseye is oriented about 
     * true or magnetic north
     * 
     * @param intent - the intent with the relevant info
     */
    public void toggleBearing(Intent intent) {
        String markerUID = intent.getStringExtra("marker_uid");
        Marker centerMarker = (Marker) MapGroup.deepFindItemWithMetaString(
                MapView
                        .getMapView().getRootGroup(),
                "uid", markerUID);
        AngleOverlayShape aos;
        if (centerMarker != null) {
            MapItem mi = MapGroup.deepFindItemWithMetaString(
                    MapView._mapView.getRootGroup(), "uid",
                    centerMarker.getMetaString("bullseyeUID", ""));
            if (mi instanceof AngleOverlayShape)
                aos = (AngleOverlayShape) mi;
            else
                return;

            if (intent.hasExtra("degrees")) {
                aos.setBearingUnits(true);
                if (!centerMarker.getType().startsWith("a-h")) {
                    centerMarker.removeMetaData("mils_mag");
                    centerMarker.setMetaBoolean("deg_mag", true);
                }
            } else {
                aos.setBearingUnits(false);
                if (!centerMarker.getType().startsWith("a-h")) {
                    centerMarker.removeMetaData("deg_mag");
                    centerMarker.setMetaBoolean("mils_mag", true);
                }
            }
            centerMarker.persist(_mapView.getMapEventDispatcher(),
                    null, getClass());
            refresh();
        }
    }

    /**
     * Build the rings to display for a Bullseye
     *
     * @return - the Rings object
     */
    protected RangeCircle buildRings(Marker centerMarker,
            AngleOverlayShape aos) {

        RangeCircle rabCircle = new RangeCircle(getMapView());
        rabCircle.setCenterMarker(centerMarker);
        rabCircle.setRadius(100);

        //setting color/line directions
        if (aos.isShowingEdgeToCenter())
            rabCircle.setColor(Color.RED); //RED
        else
            rabCircle.setColor(Color.GREEN); //GREEN
        rabCircle.setStrokeWeight(STROKE_WEIGHT);
        rabCircle.setStyle(STYLE);
        rabCircle.setClickable(false);
        rabCircle.setMetaBoolean("editable", false);
        rabCircle.setMetaBoolean("addToObjList", false);

        return rabCircle;
    }

    /**
     * Set up the view of the dropdown given the current AngleOverlayShape 
     *
     */
    protected void createLayout() {
        bullseyeLayout = (ViewGroup) LayoutInflater.from(_context).inflate(
                R.layout.bullseye_details, getMapView(), false);

        if (aos == null)
            return;

        GenericDetailsView.addEditTextPrompts(bullseyeLayout);
        title = bullseyeLayout
                .findViewById(R.id.nameEditText);
        title.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        title.setText(aos.getTitle());
        title.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            synchronized public void afterTextChanged(Editable s) {
                String newString = s.toString();
                if (!newString.equals(aos.getTitle())) {
                    aos.setTitle(newString);
                    if (centerMarker != null && centerMarker.getType()
                            .startsWith("u-r-b-bullseye"))
                        centerMarker.setTitle(newString);
                }
            }
        });

        unitsAdapter = new UnitsArrayAdapter(
                getMapView().getContext(),
                R.layout.spinner_text_view, unitsArray);
        unitsAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);

        bRadiusButton = bullseyeLayout
                .findViewById(R.id.bullseyeRadiusButton);
        bUnits = bullseyeLayout.findViewById(
                R.id.bullseyeRadiusUnitsSpinner);
        bUnits.setAdapter(unitsAdapter);
        bUnits.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                final Span selectedUnits = unitsAdapter.getItem(position);
                if (selectedUnits == null)
                    return;

                _bPrefs.setRangeSystem(selectedUnits.getType());

                Span rUnits = aos.getRadiusUnits();
                if (rUnits != selectedUnits) {
                    double radius = SpanUtilities.convert(aos.getRadius(),
                            Span.METER, selectedUnits);
                    aos.setRadius(radius, selectedUnits);
                    refresh();
                }
            }
        });
        bRadiusButton.setOnClickListener(this);

        numRingsTV = bullseyeLayout.findViewById(R.id.ringsText);

        rRadiusButton = bullseyeLayout.findViewById(
                R.id.ringRadiusButton);
        rUnits = bullseyeLayout.findViewById(
                R.id.ringRadiusUnitsSpinner);
        rUnits.setAdapter(unitsAdapter);
        rUnits.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                    int position, long id) {
                Span selectedUnits = unitsAdapter.getItem(position);
                if (selectedUnits != null) {
                    _rPrefs.setRangeSystem(selectedUnits.getType());
                    refresh();
                }
            }
        });

        ringsLayout = bullseyeLayout.findViewById(R.id.ringsView);
        radiusLayout = bullseyeLayout.findViewById(R.id.radiusView);
        showRingsCB = bullseyeLayout
                .findViewById(R.id.showRingsCB);
        showRingsCB.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                if (isChecked) {
                    if (rabCircle == null) {
                        //add a subgroup for the rings because it makes the rings easier to manage when removing them
                        rabCircle = buildRings(centerMarker, aos);
                        aos.setMetaString("rangeRingUID", rabCircle.getUID());
                        subGroup.addItem(rabCircle);
                    }

                    centerMarker.setMetaBoolean("rangeRingVisible", true);
                    rabCircle.setVisible(true);
                    ringsLayout.setVisibility(View.VISIBLE);
                    radiusLayout.setVisibility(View.VISIBLE);
                } else {
                    if (rabCircle != null) {
                        rabCircle.setVisible(false);
                        centerMarker.setMetaBoolean("rangeRingVisible", false);
                    }
                    ringsLayout.setVisibility(View.GONE);
                    radiusLayout.setVisibility(View.GONE);
                }
                refresh();
            }
        });

        rRadiusButton.setOnClickListener(this);

        bullseyeLayout.findViewById(R.id.ringsMinusButton)
                .setOnClickListener(this);
        bullseyeLayout.findViewById(R.id.ringsPlusButton)
                .setOnClickListener(this);

        if (centerMarker.getType()
                .contentEquals(BullseyeTool.BULLSEYE_COT_TYPE)) {
            View centerTextView = bullseyeLayout
                    .findViewById(R.id.centerPointButton);
            centerTextView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    BullseyeTool.displayCoordinateDialog(centerMarker);
                }
            });
        }
        centerPointLabel = bullseyeLayout
                .findViewById(R.id.centerPointLabel);
        String latLon = CoordinateFormatUtilities.formatToString(aos
                .getCenter().get(),
                CoordinateFormat.find(centerMarker.getMetaString(
                        "coordFormat",
                        CoordinateFormat.MGRS.getDisplayName())));
        centerPointLabel.setText(latLon);

        directionLabel = bullseyeLayout
                .findViewById(R.id.centerDirectionLabel);
        View centerDirection = bullseyeLayout
                .findViewById(R.id.centerDirectionView);
        centerDirection.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (directionLabel.getText().toString().equals(_context
                        .getString(R.string.bullseye_from_center))) {
                    aos.setEdgeToCenterDirection(true);
                    if (rabCircle != null)
                        rabCircle.setColor(Color.RED);
                } else {
                    aos.setEdgeToCenterDirection(false);
                    if (rabCircle != null)
                        rabCircle.setColor(Color.GREEN);
                }
                refresh();
            }
        });

        bearingUnitLabel = bullseyeLayout
                .findViewById(R.id.bearingUnitLabel);
        View bearingUnit = bullseyeLayout.findViewById(R.id.bearingUnitView);
        bearingUnit.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (aos.isShowingMils()) {
                    aos.setBearingUnits(true);
                    if (!centerMarker.getType().startsWith("a-h")) {
                        centerMarker.removeMetaData("mils_mag");
                        centerMarker.setMetaBoolean("deg_mag", true);
                    }
                } else {
                    aos.setBearingUnits(false);
                    if (!centerMarker.getType().startsWith("a-h")) {
                        centerMarker.removeMetaData("deg_mag");
                        centerMarker.setMetaBoolean("mils_mag", true);
                    }
                }
                refresh();
            }
        });

        bearingRefLabel = bullseyeLayout
                .findViewById(R.id.bearingRefLabel);
        View bearingRef = bullseyeLayout.findViewById(R.id.bearingRefView);
        bearingRef.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (aos.getNorthRef() == NorthReference.GRID) {
                    aos.setTrueAzimuth();
                } else if (aos.getNorthRef() == NorthReference.TRUE) {
                    aos.setMagneticAzimuth();
                } else {
                    aos.setGridAzimuth();
                }
                aos.refresh(getMapView().getMapEventDispatcher(), null,
                        AngleOverlayShape.class);
                refresh();
            }
        });
        bearingRef.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(_context);
                builder.setTitle(_context.getString(R.string.select_space) +
                        _context.getString(R.string.preferences_text352));
                Resources res = _context.getResources();
                String[] northList = res
                        .getStringArray(R.array.north_refs_label);
                builder.setItems(northList,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                switch (which) {
                                    case 0:
                                        aos.setTrueAzimuth();
                                        break;
                                    case 1:
                                        aos.setMagneticAzimuth();
                                        break;
                                    case 2:
                                        aos.setGridAzimuth();
                                        break;
                                    default:
                                        Log.d(TAG,
                                                "Unexpected North Reference Selection");
                                }
                                refresh();
                            }
                        });
                builder.setNegativeButton(R.string.cancel, null);
                builder.create().show();
                return true;
            }
        });

        remarksLayout = bullseyeLayout.findViewById(R.id.remarksLayout);
        remarksLayout.setText(centerMarker.getRemarks());
        remarksLayout.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                centerMarker.setRemarks(s.toString());
            }
        });

        extrasLayout = bullseyeLayout.findViewById(R.id.extrasLayout);

        bullseyeLayout.findViewById(R.id.sendLayout).setOnClickListener(this);

        HashtagManager.getInstance().registerUpdateListener(this);
    }

    protected void refresh() {
        if (!isVisible() || aos == null || bullseyeLayout == null)
            return;
        //Title
        title.setText(aos.getTitle());
        // Direction
        directionLabel.setText(aos.isShowingEdgeToCenter()
                ? R.string.bullseye_to_center
                : R.string.bullseye_from_center);

        // Bearing units
        bearingUnitLabel.setText(aos.isShowingMils()
                ? R.string.mils_full
                : R.string.degrees_full);

        // North reference
        NorthReference ref = aos.getNorthRef();
        if (ref == NorthReference.TRUE)
            bearingRefLabel.setText(R.string.tn_no_units);
        else if (ref == NorthReference.MAGNETIC)
            bearingRefLabel.setText(R.string.mz_no_units);
        else
            bearingRefLabel.setText(R.string.gn_no_units);

        // Rings toggle
        if (rabCircle != null && rabCircle.getVisible()) {
            showRingsCB.setChecked(true);
            ringsLayout.setVisibility(View.VISIBLE);
            radiusLayout.setVisibility(View.VISIBLE);
        } else {
            showRingsCB.setChecked(false);
            ringsLayout.setVisibility(View.GONE);
            radiusLayout.setVisibility(View.GONE);
        }

        // Radius
        if (_bPrefs.getRangeSystem() == Span.ENGLISH) {
            double bRadius = SpanUtilities.convert(aos.getRadius(), Span.METER,
                    _bPrefs.getRangeUnits(aos.getRadius()));
            Span bSpan = _bPrefs.getRangeUnits(bRadius);
            bUnits.setSelection(unitsAdapter.getPosition(bSpan));
            bRadiusButton.setText(aos.getFormattedRadius());
        } else {
            Span bSpan = _bPrefs.getRangeUnits(aos.getRadius());
            bUnits.setSelection(unitsAdapter.getPosition(bSpan));
            bRadiusButton.setText(aos.getFormattedRadius());
        }

        // Range circle
        if (rabCircle != null) {
            if (_rPrefs.getRangeSystem() == Span.ENGLISH) {
                double rRadius = SpanUtilities.convert(rabCircle.getRadius(),
                        Span.METER, Span.FOOT);
                Span units = _rPrefs.getRangeUnits(rRadius);
                rRadiusButton.setText(DEC_FMT_2.format(
                        SpanUtilities.convert(rRadius, Span.FOOT, units)));
                rUnits.setSelection(unitsAdapter.getPosition(units));
            } else {
                double radius = rabCircle.getRadius();
                Span units = _rPrefs.getRangeUnits(radius);
                rRadiusButton.setText(DEC_FMT_2.format(SpanUtilities.convert(
                        radius, Span.METER, _rPrefs.getRangeUnits(radius))));
                rUnits.setSelection(unitsAdapter.getPosition(units));
            }

            numRingsTV.setText(String.valueOf(rabCircle.getNumRings()));
        }

        extrasLayout.setItem(aos);
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        final Context ctx = getMapView().getContext();

        // Set bullseye or range ring radius
        if (id == R.id.bullseyeRadiusButton || id == R.id.ringRadiusButton) {
            final Button btn = (Button) v;
            Spinner s = id == R.id.bullseyeRadiusButton ? bUnits : rUnits;
            final Span span = (Span) s.getSelectedItem();
            final EditText et = new EditText(ctx);
            et.setInputType(InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            et.setText(btn.getText());
            et.selectAll();

            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setMessage(ctx.getString(R.string.rb_circle_dialog)
                    + span.getPlural() + ":");
            b.setView(et);
            b.setPositiveButton(R.string.ok, null);
            b.setNegativeButton(R.string.cancel, null);
            final AlertDialog d = b.create();
            if (d.getWindow() != null)
                d.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            d.show();
            d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(
                    new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            double radius = 0;
                            try {
                                radius = Double.parseDouble(et.getText()
                                        .toString());
                            } catch (Exception ignore) {
                            }
                            if (radius > 0.0) {
                                if (id == R.id.bullseyeRadiusButton)
                                    aos.setRadius(radius, span);
                                else
                                    rabCircle.setRadius(SpanUtilities.convert(
                                            radius, span, Span.METER));
                                btn.setText(et.getText().toString());
                                d.dismiss();
                            } else {
                                Toast.makeText(ctx, R.string.rb_circle_tip2,
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
            et.setOnEditorActionListener(new OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId,
                        KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE)
                        d.getButton(DialogInterface.BUTTON_POSITIVE)
                                .performClick();
                    return false;
                }
            });
            et.requestFocus();
        }

        // Add or subtract range ring
        else if (id == R.id.ringsMinusButton || id == R.id.ringsPlusButton) {
            if (rabCircle == null)
                return;
            boolean add = id == R.id.ringsPlusButton;
            if (!add && rabCircle.getNumRings() <= 1) {
                Toast.makeText(ctx, R.string.details_text57,
                        Toast.LENGTH_LONG).show();
                return;
            } else if (add
                    && rabCircle.getNumRings() >= RangeCircle.MAX_RINGS) {
                Toast.makeText(ctx, R.string.details_text59,
                        Toast.LENGTH_LONG).show();
                return;
            }
            int rings = rabCircle.getNumRings() + (add ? 1 : -1);
            rabCircle.setNumRings(rings);
            numRingsTV.setText((rings < 10 ? "0" : "") + rings);
        }

        // Send bullseye
        else if (id == R.id.sendLayout) {
            if (centerMarker == null)
                return;
            CotEvent event = CotEventFactory.createCotEvent(centerMarker);
            Intent i = new Intent(ContactPresenceDropdown.SEND_LIST);
            i.putExtra("com.atakmap.contact.CotEvent", event);
            AtakBroadcast.getInstance().sendBroadcast(i);
        }
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownClose() {
        HashtagManager.getInstance().unregisterUpdateListener(this);
        if (aos != null) {
            aos.removeOnPointsChangedListener(this);
            aos.removeOnGroupChangedListener(this);
            aos.removeOnPropertyChangedListener(propertyChangedListener);
        }
        if (reopenIntent != null) {
            onReceive(getMapView().getContext(), reopenIntent);
            reopenIntent = null;
        }
        if (centerMarker != null) {
            centerMarker.persist(_mapView.getMapEventDispatcher(),
                    null, this.getClass());
        }
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (v) {
            setSelected(centerMarker);
            if (centerMarker != null)
                CameraController.Programmatic.panTo(getMapView().getRenderer3(),
                        centerMarker.getPoint(), true);
            refresh();
        }
    }

    @Override
    protected void disposeImpl() {
    }

    @Override
    public void onPointsChanged(Shape s) {
        final String latLon = CoordinateFormatUtilities.formatToString(aos
                .getCenter().get(),
                CoordinateFormat.find(centerMarker.getMetaString(
                        "coordFormat",
                        CoordinateFormat.MGRS.getDisplayName())));
        getMapView().post(new Runnable() {
            @Override
            public void run() {
                centerPointLabel.setText(latLon);
            }
        });
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        if (this.aos == item)
            closeDropDown();
    }

    @Override
    public void onHashtagsUpdate(HashtagContent content) {
        if (content == centerMarker) {
            getMapView().post(new Runnable() {
                @Override
                public void run() {
                    remarksLayout.setText(centerMarker.getRemarks());
                }
            });
        }
    }
}
