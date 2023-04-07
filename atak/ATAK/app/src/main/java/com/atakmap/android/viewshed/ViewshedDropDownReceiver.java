
package com.atakmap.android.viewshed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;
import android.widget.TabHost;
import android.widget.TabHost.TabContentFactory;
import android.widget.TabHost.TabSpec;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.elev.ElevationOverlaysMapComponent;
import com.atakmap.android.elev.ViewShedReceiver;
import com.atakmap.android.elev.ViewShedReceiver.VsdLayer;
import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapItem.OnGroupChangedListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.LimitingThread;
import com.atakmap.android.util.SimpleItemSelectedListener;
import com.atakmap.android.util.SimpleSeekBarChangeListener;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;
import com.atakmap.map.elevation.ElevationManager;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class ViewshedDropDownReceiver extends DropDownReceiver implements
        OnPointChangedListener, MapEventDispatcher.MapEventDispatchListener,
        OnGroupChangedListener, OnClickListener {

    public final static String TAG = "ViewshedDropDownReceiver";
    /** max viewshed radius in meters**/
    public static final int MAX_RADIUS = 40000;

    /* the height above the path between 2 markers to show the viewshed */
    public static final double HEIGHT_ALONG_PATH = 6d;

    private final SharedPreferences prefs;
    private final ViewshedPointSelector pointSelector;

    private boolean showingViewshed = false;
    private boolean showingViewshedLine = false;

    private PointMapItem selectedMarker = null;

    private PointMapItem selectedLineMarker1 = null;
    private PointMapItem selectedLineMarker2 = null;

    private boolean viewshedListShowing = false;

    private final HashMap<String, Double> radiusMap = new HashMap<>();
    private final HashMap<String, Float> heightMap = new HashMap<>();
    private final HashMap<String, Integer> intensityMap = new HashMap<>();
    private final HashMap<String, GeoPoint> refPointMap = new HashMap<>();

    private TabHost tabHost;
    private TextView markerInfoTV;
    private EditText altitudeET;
    private EditText radiusET;
    private Button hideButton;
    private Button listButton;
    private CheckBox showHideHeatmapCB;
    private EditText intensityPercentageET;
    private TextView viewshedDtedTV;
    private SeekBar intensitySeek = null;
    private View sampleView;
    private View viewshedOpts;
    private LinearLayout viewshedListView;
    private ViewSwitcher viewshedViewSwitcher;
    private ImageView multiSelectBtn;
    private ImageView removeBtn;
    private Spinner sampleSpinner;
    private ArrayAdapter<Object> sampleAdapt;
    private Button contourGenButton;
    private ProgressBar contourProgress;
    private TextView contourProgressBarTextView;
    private ImageButton _colorButton;
    private TableRow _contourGenTableRow;
    private CheckBox majorCB;
    private CheckBox minorCB;

    private int sampleRate = 4;

    private final LimitingThread intensityLT;

    public ViewshedDropDownReceiver(final MapView mapView) {
        super(mapView);
        pointSelector = new ViewshedPointSelector(this, mapView);
        prefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());
        mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_REMOVED, this);

        intensityLT = new LimitingThread("viewshedopacity",
                new Runnable() {
                    @Override
                    public void run() {
                        if (intensitySeek == null)
                            return;

                        //broadcast intent to update the viewshed
                        int opacity = intensitySeek.getProgress();
                        if (opacity == 0)
                            opacity = 1;
                        Intent i = new Intent(
                                ViewShedReceiver.UPDATE_VIEWSHED_INTENSITY);
                        if (showingViewshed) {
                            i.putExtra(ViewShedReceiver.VIEWSHED_UID,
                                    selectedMarker.getUID());
                            intensityMap.put(selectedMarker.getUID(), opacity);

                        } else if (showingViewshedLine) {
                            i.putExtra(
                                    ViewShedReceiver.VIEWSHED_UID,
                                    selectedLineMarker1.getUID()
                                            +
                                            ViewShedReceiver.VIEWSHED_LINE_UID_SEPERATOR
                                            +
                                            selectedLineMarker2.getUID());
                            i.putExtra(ViewShedReceiver.SHOWING_LINE,
                                    true);
                        }
                        i.putExtra(ViewShedReceiver.EXTRA_ELEV_OPACITY,
                                opacity);
                        AtakBroadcast.getInstance().sendBroadcast(i);

                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException ignored) {
                        }
                    }
                });
        prefs.registerOnSharedPreferenceChangeListener(prefChanged);
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.ITEM_REMOVED)) {

            if (viewshedListShowing) {
                for (int i = 0; i < viewshedListView.getChildCount(); i++) {
                    View childView = viewshedListView.getChildAt(i);
                    CheckBox cb = childView.findViewById(R.id.selectCB);

                    String uid = (String) cb.getTag();
                    if (event.getItem().getUID().equals(uid)) {
                        viewshedListView.post(new Runnable() {
                            @Override
                            public void run() {
                                viewshedListView.removeView(childView);
                            }
                        });
                    }
                }
            }
            if (event.getItem() == selectedMarker) {
                setMarker(null);

            }
        }
    }

    @Override
    public void disposeImpl() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefChanged);
    }

    @Override
    public void onReceive(final Context ignoreCtx, final Intent intent) {

        final Context context = getMapView().getContext();

        if (intent.getAction() != null && intent.getAction()
                .equals(ViewshedMapComponent.UPDATE_CONTOUR_PROGRESS)) {
            if (intent.hasExtra(ViewshedMapComponent.CONTOUR_PROGRESS)) {
                updateProgress(intent.getIntExtra(
                        ViewshedMapComponent.CONTOUR_PROGRESS, -1));
            }
            return;
        }

        if (intent.getAction() != null && intent.getAction()
                .equals(ViewshedMapComponent.UPDATE_CONTOUR_GEN_ENABLED)) {
            if (intent.hasExtra(ViewshedMapComponent.CONTOUR_GEN_ENABLED)) {
                updateGenEnabled(intent.getBooleanExtra(
                        ViewshedMapComponent.CONTOUR_GEN_ENABLED, true));
            }
            return;
        }

        if (intent.getAction() != null && intent.getAction()
                .equals(ViewshedMapComponent.UPDATE_VIS_MAJOR_CHECKBOX)) {
            if (intent.hasExtra("state")) {
                majorCB.setVisibility(
                        intent.getBooleanExtra("state", true) ? View.VISIBLE
                                : View.INVISIBLE);
            }
            return;
        }

        if (intent.getAction() != null && intent.getAction()
                .equals(ViewshedMapComponent.UPDATE_VIS_MINOR_CHECKBOX)) {
            if (intent.hasExtra("state")) {
                minorCB.setVisibility(
                        intent.getBooleanExtra("state", true) ? View.VISIBLE
                                : View.INVISIBLE);
            }
            return;
        }

        if (tabHost == null) {
            // inflate the layout
            LayoutInflater inflater = LayoutInflater.from(context);
            ViewGroup vGroup = (ViewGroup) inflater
                    .inflate(
                            R.layout.layers_manager_tabhost, null);

            tabHost = vGroup.findViewById(R.id.ll_tabhost);
            tabHost.setup();

            // create views
            final View heatmapView = getHeatmapView(inflater);
            final View viewshedView = getViewshedView(inflater);
            final View contourView = getContourView(inflater);

            TabSpec heatmapSpec = tabHost.newTabSpec("heatmap").setIndicator(
                    context.getString(R.string.heatmap));

            heatmapSpec.setContent(new TabContentFactory() {
                @Override
                public View createTabContent(String tag) {
                    return heatmapView;
                }
            });

            TabSpec viewshedSpec = tabHost.newTabSpec("viewshed").setIndicator(
                    context.getString(R.string.viewshed));

            viewshedSpec.setContent(new TabContentFactory() {
                @Override
                public View createTabContent(String tag) {
                    return viewshedView;
                }
            });

            TabSpec contourSpec = tabHost.newTabSpec("contour").setIndicator(
                    context.getString(R.string.contour_lines));

            contourSpec.setContent(new TabContentFactory() {
                public View createTabContent(String tag) {
                    return contourView;
                }
            });
            tabHost.addTab(heatmapSpec);
            tabHost.addTab(viewshedSpec);
            tabHost.addTab(contourSpec);
        } else {
            //update the heatmap checkbox in case it was turned off in the overlay manager
            showHideHeatmapCB.setChecked(
                    ElevationOverlaysMapComponent.isHeatMapVisible());
        }


        showDropDown(tabHost, FIVE_TWELFTHS_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                HALF_HEIGHT);
        setRetain(true);
    }

    /**
     * Inflate and initialize the views in the Heat Map settings tab
     * 
     * @param inflater - the inflater to use to inflate the layout
     * @return - the view for the Heat Map tab
     */
    private View getHeatmapView(final LayoutInflater inflater) {

        View hmView = inflater.inflate(R.layout.heatmap_view, null);

        final Spinner overlayType = hmView
                .findViewById(R.id.heatmapOverlay_spinner);
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(
                inflater.getContext(), R.layout.spinner_text_view_dark);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        adapter.addAll(
                inflater.getContext().getString(R.string.heatmap),
                inflater.getContext().getString(R.string.terrainslope));
        overlayType.setAdapter(adapter);

        showHideHeatmapCB = hmView.findViewById(R.id.showHeatmap_cb);

        //set initial checked state of the cb to reflect the current state of the heatmap
        showHideHeatmapCB.setChecked(
                ElevationOverlaysMapComponent.isHeatMapVisible());

        showHideHeatmapCB
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        //enable or disable the heatmap
                        ElevationOverlaysMapComponent
                                .setHeatMapVisible(isChecked);
                    }
                });

        //TODO get dted level at map center
        //TextView dtedLevelTV = (TextView) hmView
        //        .findViewById(R.id.dtedLevel_tv);

        //set initial intensity percentage value and seek value
        String intensityPref = prefs.getString(
                ElevationOverlaysMapComponent.PREFERENCE_COLOR_INTENSITY_KEY,
                ElevationOverlaysMapComponent.PREFERENCE_COLOR_INTENSITY_VALUE);

        int intensityValue = Integer.parseInt(intensityPref);

        final EditText intensityPercentageET = hmView
                .findViewById(R.id.intensity_et);
        intensityPercentageET.setFilters(new InputFilter[] {
                new InputFilterMinMax(0, 100),
                new InputFilter.LengthFilter(3)
        });
        final SeekBar intensitySeek = hmView
                .findViewById(R.id.intensity_seek);
        intensitySeek.setProgress(intensityValue);
        intensitySeek.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                //there is a bug when the intensity is set to 100, so to avoid it set 99 as the max
                if (progress == 100) {
                    prefs.edit()
                            .putString(
                                    ElevationOverlaysMapComponent.PREFERENCE_COLOR_INTENSITY_KEY,
                                    String.valueOf(99))
                            .apply();
                } else {
                    prefs.edit()
                            .putString(
                                    ElevationOverlaysMapComponent.PREFERENCE_COLOR_INTENSITY_KEY,
                                    String.valueOf(progress))
                            .apply();
                }

                //update the percentage editText
                intensityPercentageET.setText(String.valueOf(progress));
            }
        });

        intensityPercentageET.setText(intensityPref);
        intensityPercentageET
                .addTextChangedListener(new AfterTextChangedWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {
                        if (s.length() > 0) {
                            try {
                                int i = Integer.parseInt(s.toString());
                                if (i <= 100 && i > 0) {
                                    if (intensitySeek.getProgress() != i) {
                                        intensitySeek.setProgress(i);
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                            intensityPercentageET.setSelection(
                                    intensityPercentageET.getText().length());
                        }
                    }
                });

        //Saturation Slider
        String satPref = prefs.getString(
                ElevationOverlaysMapComponent.PREFERENCE_COLOR_SATURATION_KEY,
                "50");
        int saturation = Integer.parseInt(satPref);

        final View satLayout = hmView.findViewById(R.id.heatmap_sat_layout);
        final EditText satPercentageET = hmView
                .findViewById(R.id.sat_et);
        satPercentageET.setFilters(new InputFilter[] {
                new InputFilterMinMax(0, 100),
                new InputFilter.LengthFilter(3)
        });
        final SeekBar satSeek = hmView.findViewById(R.id.sat_seek);
        satSeek.setProgress(saturation);
        satSeek.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                //set the heatmap saturation
                if (progress == 100)
                    prefs.edit()
                            .putString(
                                    ElevationOverlaysMapComponent.PREFERENCE_COLOR_SATURATION_KEY,
                                    String.valueOf(99))
                            .apply();
                else
                    prefs.edit()
                            .putString(
                                    ElevationOverlaysMapComponent.PREFERENCE_COLOR_SATURATION_KEY,
                                    String.valueOf(progress))
                            .apply();

                //update the percentage editText
                satPercentageET.setText(String.valueOf(progress));
            }
        });

        satPercentageET.setText(satPref);
        satPercentageET.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 0) {
                    try {
                        int i = Integer.parseInt(s.toString());
                        if (i <= 100 && i >= 0) {
                            if (satSeek.getProgress() != i)
                                satSeek.setProgress(i);
                        }
                    } catch (Exception ignored) {
                    }
                    satPercentageET.setSelection(
                            satPercentageET.getText().length());
                }
            }
        });

        //Value Slider
        String valPref = prefs.getString(
                ElevationOverlaysMapComponent.PREFERENCE_COLOR_VALUE_KEY, "50");
        int value = Integer.parseInt(valPref);

        final View valLayout = hmView.findViewById(R.id.heatmap_val_layout);
        final EditText valPercentageET = hmView
                .findViewById(R.id.val_et);
        valPercentageET.setFilters(new InputFilter[] {
                new InputFilterMinMax(0, 100),
                new InputFilter.LengthFilter(3)
        });
        final SeekBar valSeek = hmView.findViewById(R.id.val_seek);
        valSeek.setProgress(value);
        valSeek.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                if (progress == 100)
                    prefs.edit()
                            .putString(
                                    ElevationOverlaysMapComponent.PREFERENCE_COLOR_VALUE_KEY,
                                    String.valueOf(99))
                            .apply();
                else
                    prefs.edit()
                            .putString(
                                    ElevationOverlaysMapComponent.PREFERENCE_COLOR_VALUE_KEY,
                                    String.valueOf(progress))
                            .apply();

                //update the percentage editText
                valPercentageET.setText(String.valueOf(progress));
            }
        });

        valPercentageET.setText(valPref);
        valPercentageET.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 0) {
                    try {
                        int i = Integer.parseInt(s.toString());
                        if (i <= 100 && i >= 0) {
                            if (valSeek.getProgress() != i)
                                valSeek.setProgress(i);
                        }
                    } catch (Exception ignored) {
                    }
                    valPercentageET.setSelection(
                            valPercentageET.getText().length());
                }
            }
        });

        //set the sample rate slider
        final int xRange = ElevationOverlaysMapComponent.PREFERENCE_X_RES_MAX
                - ElevationOverlaysMapComponent.PREFERENCE_X_RES_MIN;
        final int yRange = ElevationOverlaysMapComponent.PREFERENCE_Y_RES_MAX
                - ElevationOverlaysMapComponent.PREFERENCE_Y_RES_MIN;

        int sampleValue = ElevationOverlaysMapComponent.PREFERENCE_X_RES_DEFAULT;
        try {
            int currentSample = Integer
                    .parseInt(prefs.getString(
                            ElevationOverlaysMapComponent.PREFERENCE_X_RES_KEY,
                            String.valueOf(
                                    ElevationOverlaysMapComponent.PREFERENCE_X_RES_DEFAULT)));
            sampleValue = (int) Math
                    .round(((double) (currentSample
                            - ElevationOverlaysMapComponent.PREFERENCE_X_RES_MIN)
                            /
                            (xRange - ElevationOverlaysMapComponent.PREFERENCE_X_RES_MIN))
                            * 100d);
        } catch (NumberFormatException nfe) {
            Log.d(TAG, "Invalid X sample rate");
        }
        final View sampleLayout = hmView
                .findViewById(R.id.heatmap_samplerate_layout);
        final SeekBar sampleSeek = hmView
                .findViewById(R.id.sample_seek);
        sampleSeek.setProgress(sampleValue);
        sampleSeek.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                //update the sample rate
                double percentage = progress / 100d;
                int xRate = ElevationOverlaysMapComponent.PREFERENCE_X_RES_MIN
                        + (int) Math.round(percentage * xRange);
                int yRate = ElevationOverlaysMapComponent.PREFERENCE_Y_RES_MIN
                        + (int) Math.round(percentage * yRange);

                prefs.edit()
                        .putString(
                                ElevationOverlaysMapComponent.PREFERENCE_X_RES_KEY,
                                String.valueOf(xRate))
                        .apply();
                prefs.edit()
                        .putString(
                                ElevationOverlaysMapComponent.PREFERENCE_Y_RES_KEY,
                                String.valueOf(yRate))
                        .apply();

            }
        });

        overlayType.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView,
                            View view, int pos, long id) {
                        final Object v = adapterView.getAdapter().getItem(pos);
                        prefs.edit()
                                .putString(
                                        ElevationOverlaysMapComponent.PREFERENCE_MODE_KEY,
                                        (String) v)
                                .apply();
                        final boolean extendedSettingsEnabled = inflater
                                .getContext().getString(R.string.heatmap)
                                .equals(v);

                        satLayout.setVisibility(
                                extendedSettingsEnabled ? View.VISIBLE
                                        : View.GONE);
                        valLayout.setVisibility(
                                extendedSettingsEnabled ? View.VISIBLE
                                        : View.GONE);
                        sampleLayout.setVisibility(
                                extendedSettingsEnabled ? View.VISIBLE
                                        : View.GONE);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
        // default to heatmap
        switch (ElevationOverlaysMapComponent.getHeatMapMode()) {
            case Elevation:
                overlayType.setSelection(adapter.getPosition(
                        inflater.getContext().getString(R.string.heatmap)));
                break;
            case TerrainSlope:
                overlayType.setSelection(adapter.getPosition(inflater
                        .getContext().getString(R.string.terrainslope)));
                break;
            default:
                overlayType.setSelection(adapter.getPosition(
                        inflater.getContext().getString(R.string.heatmap)));
                break;
        }

        return hmView;
    }

    /**
     * Inflate and initialize the views in the Viewshed settings tab
     * 
     * @param inflater - the inflater to use to inflate the layout
     * @return - the view for the Viewshed tab
     */
    private View getViewshedView(LayoutInflater inflater) {
        View vsView = inflater.inflate(R.layout.viewshed_view, null);
        viewshedViewSwitcher = vsView
                .findViewById(R.id.viewshedViewSwitcher);
        // set initial intensity percentage value and seek value
        String opPref = prefs.getString(
                ViewShedReceiver.VIEWSHED_PREFERENCE_COLOR_INTENSITY_KEY, "50");

        //intensity check to ensure user doesn't place invisible viewsheds
        int intensityValue = Math.max(Integer.parseInt(opPref), 10);

        sampleView = vsView.findViewById(R.id.viewshedLineSample_view);
        viewshedOpts = vsView.findViewById(R.id.viewshedOpts_view);
        sampleSpinner = vsView.findViewById(R.id.sample_spinner);

        final Object[] sampleOptions = new Object[5];
        for (int i = 3; i < sampleOptions.length + 3; i++) {
            sampleOptions[i - 3] = i;
        }
        sampleAdapt = new ArrayAdapter<>(MapView.getMapView()
                .getContext(),
                R.layout.spinner_text_view, sampleOptions);
        sampleAdapt
                .setDropDownViewResource(
                        android.R.layout.simple_spinner_dropdown_item);
        sampleSpinner.setAdapter(sampleAdapt);
        sampleSpinner
                .setOnItemSelectedListener(new SimpleItemSelectedListener() {

                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view,
                            int position, long id) {

                        if (view instanceof TextView)
                            ((TextView) view).setTextColor(Color.WHITE);

                        if (sampleRate != (Integer) sampleOptions[position]) {
                            sampleRate = (Integer) sampleOptions[position];
                            if (showingViewshedLine) {
                                hideViewshed(true);
                                setMarkerLine(selectedLineMarker1,
                                        selectedLineMarker2);
                            }
                        }
                    }

                });
        sampleSpinner.setSelection(sampleAdapt.getPosition(sampleRate));

        intensityPercentageET = vsView
                .findViewById(R.id.intensity_et);
        intensityPercentageET.setText(String.valueOf(intensityValue));

        intensitySeek = vsView
                .findViewById(R.id.intensity_seek);
        intensitySeek.setProgress(intensityValue);
        intensitySeek.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                //there is a bug when the intensity is set to 100, so to avoid it set 99 as the max
                if (progress == 100) {
                    prefs.edit()
                            .putString(
                                    ViewShedReceiver.VIEWSHED_PREFERENCE_COLOR_INTENSITY_KEY,
                                    String.valueOf(99))
                            .apply();

                } else {
                    prefs.edit()
                            .putString(
                                    ViewShedReceiver.VIEWSHED_PREFERENCE_COLOR_INTENSITY_KEY,
                                    String.valueOf(progress))
                            .apply();
                }

                //update the percentage editText
                intensityPercentageET.setText(String.valueOf(progress));
                if (showingViewshed || showingViewshedLine) {
                    intensityLT.exec();
                }
            }
        });

        intensityPercentageET
                .addTextChangedListener(new AfterTextChangedWatcher() {
                    @Override
                    public void afterTextChanged(Editable s) {

                        boolean changeSeek = false;
                        if (s.length() > 0) {
                            try {
                                int i = Integer.parseInt(s.toString());
                                if (i <= 100 && i >= 0) {
                                    if (intensitySeek.getProgress() != i) {
                                        intensitySeek.setProgress(i);
                                        changeSeek = true;
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                            intensityPercentageET.setSelection(
                                    intensityPercentageET.getText()
                                            .length());

                            final MapItem sMarker = selectedMarker;

                            if (sMarker != null && showingViewshed
                                    && changeSeek) {
                                //broadcast intent to update the viewshed
                                int intensity = intensitySeek.getProgress();
                                if (intensity == 0)
                                    intensity = 1;
                                Intent i = new Intent(
                                        ViewShedReceiver.UPDATE_VIEWSHED_INTENSITY);
                                i.putExtra(ViewShedReceiver.VIEWSHED_UID,
                                        sMarker.getUID());
                                i.putExtra(ViewShedReceiver.EXTRA_ELEV_OPACITY,
                                        intensity);
                                AtakBroadcast.getInstance().sendBroadcast(i);
                            }

                        }
                    }
                });

        //the altitude editText
        altitudeET = vsView.findViewById(R.id.altitude_et);
        setFtMLabel(altitudeET);
        setAltitudeFromPreference();


        altitudeET.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (showingViewshed)
                    showViewshed(selectedMarker);
            }
        });

        //the radius editText
        radiusET = vsView.findViewById(R.id.radius_et);
        radiusET.setText(String.valueOf(prefs.getInt(
                ViewShedReceiver.VIEWSHED_PREFERENCE_RADIUS_KEY, 1000)));
        radiusET.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (showingViewshed)
                    showViewshed(selectedMarker);
            }
        });

        markerInfoTV = vsView.findViewById(R.id.markerInfo_tv);
        viewshedDtedTV = vsView.findViewById(R.id.dtedInfo_tv);

        ImageButton viewshedMarkerButton = vsView
                .findViewById(R.id.viewshedMarker_ibtn);
        viewshedMarkerButton.setOnClickListener(this);

        ImageButton viewshedLineButton = vsView
                .findViewById(R.id.viewshedLine_ibtn);
        viewshedLineButton.setOnClickListener(this);

        hideButton = vsView.findViewById(R.id.hideViewshed_btn);
        hideButton.setOnClickListener(this);

        listButton = vsView.findViewById(R.id.selectViewshed_btn);
        listButton.setOnClickListener(this);
        listButton.setVisibility(View.GONE);

        viewshedListView = vsView
                .findViewById(R.id.viewshedList_ll);

        ImageView backBtn = vsView.findViewById(R.id.backBtn);
        backBtn.setOnClickListener(this);
        removeBtn = vsView.findViewById(R.id.removeBtn);
        removeBtn.setOnClickListener(this);
        multiSelectBtn = vsView.findViewById(R.id.multiSelectBtn);
        multiSelectBtn.setOnClickListener(this);

        CheckBox circleCB = vsView
                .findViewById(R.id.circularViewshed_cb);

        //boolean circlePref = prefs.getBoolean(
        //        ViewShedReceiver.VIEWSHED_PREFERENCE_CIRCULAR_VIEWSHED, false);

        boolean circlePref = true; // hard code this always

        circleCB.setChecked(circlePref);
        circleCB.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                prefs.edit()
                        .putBoolean(
                                ViewShedReceiver.VIEWSHED_PREFERENCE_CIRCULAR_VIEWSHED,
                                isChecked)
                        .apply();
                hideViewshed(false);
                showViewshed(selectedMarker);
            }
        });

        return vsView;
    }

    private View getContourView(LayoutInflater inflater) {
        final View cView = inflater.inflate(R.layout.contour_view, null);
        _contourGenTableRow = cView
                .findViewById(R.id.contourGenerationTableRow);
        final Spinner intervalSpinner = cView
                .findViewById(R.id.contour_interval_spinner);
        Integer[] intervals = {
                20, 25, 50, 100, 200, 250, 500, 1000
        };

        // Note: Please use R.layout.simple_spinner_item instead of android.R.etc
        // The android one uses black text which doesn't work with dark buttons
        final ArrayAdapter<Integer> intervalAdapter = new ArrayAdapter<>(
                getMapView().getContext(), R.layout.simple_spinner_item,
                intervals);
        intervalAdapter
                .setDropDownViewResource(R.layout.simple_spinner_item);
        intervalSpinner.setAdapter(intervalAdapter);
        intervalSpinner.setSelection(intervalAdapter.getPosition(
                prefs.getInt(
                        ContourLinesOverlay.CONTOUR_PREFERENCE_INTERVAL_KEY,
                        20)));
        intervalSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view,
                            int position, long id) {
                        if (position >= intervalAdapter.getCount())
                            // XXX - Is this check even necessary?
                            return;

                        Integer i = intervalAdapter.getItem(position);
                        if (i == null)
                            return;

                        prefs.edit().putInt(
                                ContourLinesOverlay.CONTOUR_PREFERENCE_INTERVAL_KEY,
                                i)
                                .apply();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });

        RadioGroup intervalRG = cView
                .findViewById(R.id.contour_radio_group);
        if (prefs
                .getString(ContourLinesOverlay.CONTOUR_PREFERENCE_UNIT_KEY, "m")
                .equals("m")) {
            intervalRG.check(R.id.contour_units_meters);
        } else {
            intervalRG.check(R.id.contour_units_feet);
        }
        intervalRG.setOnCheckedChangeListener(
                new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(RadioGroup group,
                            int checkedId) {
                        RadioButton rbChecked = cView
                                .findViewById(checkedId);
                        prefs.edit().putString(
                                ContourLinesOverlay.CONTOUR_PREFERENCE_UNIT_KEY,
                                rbChecked.getText().toString()).apply();
                    }
                });
        SeekBar _thickSeek = cView
                .findViewById(R.id.drawingShapeStrokeSeek);
        _thickSeek.setProgress(prefs
                .getInt(ContourLinesOverlay.CONTOUR_PREFERENCE_MAJOR_WIDTH_KEY,
                        4));
        _thickSeek.setOnSeekBarChangeListener(
                new SimpleSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar,
                            int progress, boolean fromUser) {
                        int strokeWeight = 1 + progress;
                        prefs.edit().putInt(
                                ContourLinesOverlay.CONTOUR_PREFERENCE_MAJOR_WIDTH_KEY,
                                strokeWeight).apply();
                    }
                });

        _colorButton = cView.findViewById(R.id.line_color);
        updateColorButton(
                prefs.getInt(
                        ContourLinesOverlay.CONTOUR_PREFERENCE_LINE_COLOR_KEY,
                        Color.WHITE));
        //_colorButton.setEnabled(true);
        _colorButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                displayColorSelectDialog();
            }
        });

        contourGenButton = cView
                .findViewById(R.id.generate_contour_lines);
        contourGenButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ElevationOverlaysMapComponent.setContourLinesVisible(true);
            }
        });

        ImageButton cancelButton = cView
                .findViewById(R.id.cancelContourGenerationButton);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                ElevationOverlaysMapComponent.cancelContourLinesGeneration();
            }
        });

        contourGenButton.setEnabled(getMapView().getMapResolution() < 300d);

        final LinearLayout checkBoxes = cView
                .findViewById(R.id.contour_cb_layout);
        final TextView showCB = cView
                .findViewById(R.id.contour_show_checkbox);
        showCB.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkBoxes.getVisibility() == View.GONE) {
                    checkBoxes.setVisibility(View.VISIBLE);
                    showCB.setCompoundDrawablesWithIntrinsicBounds(
                            getMapView().getResources()
                                    .getDrawable(R.drawable.arrow_down),
                            null, null, null);
                } else {
                    checkBoxes.setVisibility(View.GONE);
                    showCB.setCompoundDrawablesWithIntrinsicBounds(
                            getMapView().getResources()
                                    .getDrawable(R.drawable.arrow_right),
                            null, null, null);
                }
            }
        });

        CheckBox contourCB = cView.findViewById(R.id.contour_cb);
        contourCB.setChecked(prefs.getBoolean(
                ContourLinesOverlay.CONTOUR_PREFERENCE_CONTOUR_VISIBLE_KEY,
                true));
        contourCB.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                prefs.edit().putBoolean(
                        ContourLinesOverlay.CONTOUR_PREFERENCE_CONTOUR_VISIBLE_KEY,
                        isChecked).apply();
            }
        });
        CheckBox labelCB = cView.findViewById(R.id.contour_label_cb);
        labelCB.setChecked(prefs.getBoolean(
                ContourLinesOverlay.CONTOUR_PREFERENCE_LABEL_VISIBLE_KEY,
                false));
        labelCB.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                prefs.edit().putBoolean(
                        ContourLinesOverlay.CONTOUR_PREFERENCE_LABEL_VISIBLE_KEY,
                        isChecked).apply();
            }
        });
        majorCB = cView.findViewById(R.id.major_lines_cb);
        majorCB.setChecked(prefs.getBoolean(
                ContourLinesOverlay.CONTOUR_PREFERENCE_MAJOR_VISIBLE_KEY,
                true));
        majorCB.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                prefs.edit().putBoolean(
                        ContourLinesOverlay.CONTOUR_PREFERENCE_MAJOR_VISIBLE_KEY,
                        isChecked).apply();
            }
        });
        minorCB = cView.findViewById(R.id.minor_lines_cb);
        minorCB.setChecked(prefs.getBoolean(
                ContourLinesOverlay.CONTOUR_PREFERENCE_MINOR_VISIBLE_KEY,
                true));
        minorCB.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                prefs.edit().putBoolean(
                        ContourLinesOverlay.CONTOUR_PREFERENCE_MINOR_VISIBLE_KEY,
                        isChecked).apply();
            }
        });
        contourProgress = cView
                .findViewById(R.id.loading_contour_progress);
        contourProgressBarTextView = cView
                .findViewById(R.id.progressBarPercentageTextView);

        return cView;
    }

    private void displayColorSelectDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(
                getMapView().getContext())
                        .setTitle(getMapView().getResources().getString(
                                R.string.contour_choose_line_color));
        ColorPalette palette = new ColorPalette(getMapView().getContext(),
                prefs.getInt(
                        ContourLinesOverlay.CONTOUR_PREFERENCE_LINE_COLOR_KEY,
                        Color.WHITE));
        dialogBuilder.setView(palette);
        final AlertDialog alert = dialogBuilder.create();

        ColorPalette.OnColorSelectedListener l = new ColorPalette.OnColorSelectedListener() {
            @Override
            public void onColorSelected(int color, String label) {
                updateColorButton(color);
                prefs.edit().putInt(
                        ContourLinesOverlay.CONTOUR_PREFERENCE_LINE_COLOR_KEY,
                        color)
                        .apply();
                alert.cancel();
            }
        };

        palette.setOnColorSelectedListener(l);
        alert.show();
    }

    private void updateColorButton(final int color) {
        _colorButton.post(new Runnable() {
            @Override
            public void run() {
                _colorButton.setColorFilter((color & 0xFFFFFF) + 0xFF000000,
                        PorterDuff.Mode.MULTIPLY);
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.viewshedMarker_ibtn) {
            pointSelector.beginPointSelection();
        } else if (v.getId() == R.id.viewshedLine_ibtn) {
            pointSelector.beginFirstPointSelection();
        } else if (v.getId() == R.id.hideViewshed_btn) {
            if (showingViewshed || showingViewshedLine) {
                hideViewshed(true);
                hideButton.setVisibility(View.GONE);
            }
        } else if (v.getId() == R.id.selectViewshed_btn) {
            //Show dropdown with the list of viewsheds
            viewshedViewSwitcher.showNext();
            viewshedListShowing = true;
            viewshedListView.removeAllViews();
            //get the list of viewsheds
            HashMap<String, ArrayList<VsdLayer>> layerMap = ViewShedReceiver
                    .getSingleVsdLayerMap();
            LayoutInflater inflater = LayoutInflater
                    .from(getMapView().getContext());
            for (String key : layerMap.keySet()) {
                View viewshedItem = inflater.inflate(R.layout.viewshed_item,
                        null);
                final Marker m = (Marker) MapView.getMapView().getRootGroup()
                        .deepFindItem("uid", key);
                if (m == null)
                    return;

                ((TextView) viewshedItem.findViewById(R.id.viewshed_label))
                        .setText(m.getMetaString("callsign", ""));

                viewshedItem.findViewById(R.id.viewshed_label)
                        .setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                MapView.getMapView()
                                        .getMapController()
                                        .panZoomTo(
                                                m.getPoint(),
                                                MapView.getMapView()
                                                        .getMapScale(),
                                                true);
                            }
                        });
                viewshedItem.findViewById(R.id.detailsBtn)
                        .setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                MapView.getMapView()
                                        .getMapController()
                                        .panZoomTo(
                                                m.getPoint(),
                                                MapView.getMapView()
                                                        .getMapScale(),
                                                true);
                                showingViewshedLine = false;
                                showingViewshed = true;
                                hideButton.setVisibility(View.VISIBLE);
                                selectedMarker = m;
                                //setMarker(m);

                                try {
                                    changeViewshed(m);
                                } catch (Exception e) {
                                    // should not get here if the viewshed is deleted
                                    Log.e(TAG, "error showing viewshed", e);
                                }
                                viewshedViewSwitcher.showPrevious();
                                viewshedListShowing = false;
                            }
                        });

                viewshedItem.findViewById(R.id.selectCB).setTag(key);

                viewshedListView.addView(viewshedItem);
            }

            HashMap<String, ArrayList<VsdLayer>> layerLineMap = ViewShedReceiver
                    .getVsdLineLayerMap();
            for (String key : layerLineMap.keySet()) {
                View viewshedItem = inflater.inflate(R.layout.viewshed_item,
                        null);
                String uid1 = key.substring(0, key.indexOf('*'));
                String uid2 = key.substring(key.indexOf('*') + 1);
                final Marker m = (Marker) MapView.getMapView().getRootGroup()
                        .deepFindItem("uid", uid1);
                final Marker m2 = (Marker) MapView.getMapView().getRootGroup()
                        .deepFindItem("uid", uid2);
                final int samples = layerLineMap.get(key).size();
                if (m == null)
                    return;

                ((TextView) viewshedItem.findViewById(R.id.viewshed_label))
                        .setText(m.getMetaString("callsign", "") + " line");

                viewshedItem.findViewById(R.id.viewshed_label)
                        .setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                MapView.getMapView()
                                        .getMapController()
                                        .panZoomTo(
                                                m.getPoint(),
                                                MapView.getMapView()
                                                        .getMapScale(),
                                                true);
                            }
                        });
                viewshedItem.findViewById(R.id.detailsBtn)
                        .setOnClickListener(new OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                MapView.getMapView()
                                        .getMapController()
                                        .panZoomTo(
                                                m.getPoint(),
                                                MapView.getMapView()
                                                        .getMapScale(),
                                                true);
                                showingViewshedLine = true;
                                showingViewshed = false;
                                sampleRate = samples;
                                sampleSpinner.setSelection(sampleAdapt
                                        .getPosition(sampleRate));
                                hideButton.setVisibility(View.VISIBLE);
                                selectedMarker = m;
                                selectedLineMarker1 = m;
                                selectedLineMarker2 = m2;
                                sampleView.setVisibility(View.VISIBLE);
                                viewshedOpts.setVisibility(View.GONE);

                                viewshedViewSwitcher.showPrevious();
                                viewshedListShowing = false;

                            }
                        });

                viewshedItem.findViewById(R.id.selectCB).setTag(key);

                viewshedListView.addView(viewshedItem);
            }
        } else if (v.getId() == R.id.backBtn) {
            viewshedViewSwitcher.showPrevious();
            viewshedListShowing = false;
            multiSelectBtn.setVisibility(View.VISIBLE);
            removeBtn.setVisibility(View.GONE);
        } else if (v.getId() == R.id.removeBtn) {
            //remove the selected viewsheds
            ArrayList<Integer> removedViews = new ArrayList<>();
            for (int i = 0; i < viewshedListView.getChildCount(); i++) {
                View childView = viewshedListView.getChildAt(i);
                CheckBox cb = childView.findViewById(R.id.selectCB);
                if (cb.isChecked()) {
                    String uid = (String) cb.getTag();
                    if (uid.contains(
                            ViewShedReceiver.VIEWSHED_LINE_UID_SEPERATOR)) {
                        //remove viewshed lines as well
                        Intent intent = new Intent(
                                ViewShedReceiver.ACTION_DISMISS_VIEWSHED_LINE);
                        intent.putExtra(ViewShedReceiver.VIEWSHED_UID, uid);
                        AtakBroadcast.getInstance().sendBroadcast(intent);
                        removedViews.add(i);
                    } else {
                        Marker mi = (Marker) MapView.getMapView()
                                .getRootGroup().deepFindItem("uid", uid);
                        boolean multi = false;
                        if (mi == null) {
                            mi = (Marker) MapView.getMapView().getRootGroup()
                                    .deepFindItem("uid",
                                            uid.substring(0, uid.length() - 1));
                            multi = true;
                        }
                        if (mi != null) {
                            selectedMarker = mi;
                            showingViewshedLine = multi;
                            showingViewshed = !multi;
                            hideViewshed(true);
                            removedViews.add(i);
                        }
                    }
                } else {
                    //hide the checkboxes and replace with info buttons
                    cb.setVisibility(View.GONE);
                    childView.findViewById(R.id.detailsBtn).setVisibility(
                            View.VISIBLE);
                }
            }
            boolean returnAfter = removedViews.size() == viewshedListView
                    .getChildCount();
            for (int i = removedViews.size() - 1; i >= 0; i--)
                viewshedListView.removeViewAt(removedViews.get(i));

            multiSelectBtn.setVisibility(View.VISIBLE);
            removeBtn.setVisibility(View.GONE);
            if (returnAfter) {
                viewshedViewSwitcher.showPrevious();
                viewshedListShowing = false;

                listButton.setVisibility(View.GONE);
            }
        } else if (v.getId() == R.id.multiSelectBtn) {
            for (int i = 0; i < viewshedListView.getChildCount(); i++) {
                View childView = viewshedListView.getChildAt(i);
                childView.findViewById(R.id.detailsBtn)
                        .setVisibility(View.GONE);
                childView.findViewById(R.id.selectCB).setVisibility(
                        View.VISIBLE);
            }
            multiSelectBtn.setVisibility(View.GONE);
            removeBtn.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPointChanged(final PointMapItem m) {
        if (showingViewshed && selectedMarker == m) {
            final String title;

            if (m.getUID().equals(getMapView().getSelfMarker().getUID())) {
                title = getMapView().getContext().getString(R.string.self_loc);
            } else {
                title = m.getMetaString("callsign", "");
            }




            final String msltext = formatMSL(m.getPoint());

            getMapView().post(new Runnable() {
                @Override
                public void run() {
                    markerInfoTV.setText(title);
                    viewshedDtedTV.setText(msltext);
                }
            });
        }
        if (m.getPoint().isAltitudeValid())
            showViewshed(m);
        else {
            Intent i = new Intent(ViewShedReceiver.ACTION_DISMISS_VIEWSHED);
            i.putExtra(ViewShedReceiver.VIEWSHED_UID, m.getUID());
            AtakBroadcast.getInstance().sendBroadcast(i);
            Toast.makeText(
                    getMapView().getContext(),
                    getMapView().getContext().getString(
                            R.string.no_dted),
                    Toast.LENGTH_SHORT).show();
            radiusMap.remove(m.getUID());
            heightMap.remove(m.getUID());
            refPointMap.remove(m.getUID());
            intensityMap.remove(m.getUID());
        }
    }

    /**
     * Set the given marker to use as the reference point to calculate the viewshed
     * if there is valid DTED around the point
     * 
     * @param m - the marker to use as the reference point
     */
    public void setMarker(final PointMapItem m) {

        if (m == null) {
            hideViewshed(true);
            // the ui has probably not been initialized if the hideButton does 
            // not exist
            if (hideButton != null) {
                getMapView().post(new Runnable() {
                    @Override
                    public void run() {
                        markerInfoTV.setText(getMapView().getContext()
                                .getString(R.string.no_marker_set));
                        viewshedDtedTV.setText(getMapView().getContext()
                                .getString(R.string.none_caps));
                    }
                });
            }
            pointSelector.dispose();
            return;
        }

        selectedMarker = m;
        selectedMarker.addOnPointChangedListener(this);
        selectedMarker.addOnGroupChangedListener(this);

        // use the selected marker
        if (m.getPoint().isAltitudeValid()) {
            final String title;

            if (m.getUID().equals(getMapView().getSelfMarker().getUID())) {
                title = getMapView().getContext().getString(R.string.self_loc);
            } else {
                title = m.getMetaString("callsign", "");
            }

            final String msltext = formatMSL(m.getPoint());

            getMapView().post(new Runnable() {
                @Override
                public void run() {

                    markerInfoTV.setText(title);
                    viewshedDtedTV.setText(msltext);

                    if (showingViewshed) {
                        ElevationOverlaysMapComponent.setHeatMapVisible(false);
                        showHideHeatmapCB.setChecked(false);
                    }
                    showViewshed(selectedMarker);
                    hideButton.setVisibility(View.GONE);
                }
            });
        } else {
            getMapView().post(new Runnable() {
                @Override
                public void run() {
                    if (!m.getPoint().isAltitudeValid()) {
                        Toast.makeText(
                                getMapView().getContext(),
                                getMapView().getContext().getString(
                                        R.string.no_dted),
                                Toast.LENGTH_SHORT).show();

                    }
                }
            });
            if (showingViewshed)
                hideViewshed(false);
        }

    }

    OnPointChangedListener pmi = new OnPointChangedListener() {
        @Override
        public void onPointChanged(PointMapItem item) {
            //find the UID of the other marker in the line and recalc the line
            Set<String> uids = ViewShedReceiver.getVsdLineLayerMap().keySet();
            for (String uid : uids) {
                if (uid.contains(item.getUID())) {
                    //find if it is the first or second marker in the line
                    if (uid.startsWith(item.getUID())) {
                        PointMapItem m2 = (PointMapItem) MapView
                                .getMapView()
                                .getRootGroup()
                                .deepFindItem(
                                        "uid",
                                        uid.substring(uid
                                                .indexOf(
                                                        ViewShedReceiver.VIEWSHED_LINE_UID_SEPERATOR)
                                                + 1));
                        if (m2 != null)
                            showViewshedLine(item, m2);
                    } else {
                        PointMapItem m1 = (PointMapItem) MapView
                                .getMapView()
                                .getRootGroup()
                                .deepFindItem(
                                        "uid",
                                        uid.substring(
                                                0,
                                                uid.indexOf(
                                                        ViewShedReceiver.VIEWSHED_LINE_UID_SEPERATOR)));
                        if (m1 != null)
                            showViewshedLine(m1, item);
                    }
                }
            }
        }
    };

    public void setMarkerLine(final PointMapItem m, final PointMapItem m2) {
        if (m == null || m2 == null)
            return;

        selectedMarker = m;
        selectedLineMarker1 = m;
        selectedLineMarker2 = m2;

        m.addOnPointChangedListener(pmi);
        m2.addOnPointChangedListener(pmi);

        showingViewshed = false;
        showingViewshedLine = true;
        getMapView().post(new Runnable() {
            @Override
            public void run() {
                hideButton.setVisibility(View.VISIBLE);
            }
        });
        sampleView.setVisibility(View.VISIBLE);
        viewshedOpts.setVisibility(View.GONE);
        listButton.setVisibility(View.VISIBLE);

        showViewshedLine(m, m2);
    }

    private void showViewshedLine(final PointMapItem m, final PointMapItem m2) {

        GeoPoint refPoint1 = m.getPoint();
        GeoPoint refPoint2 = m2.getPoint();

        double heightAboveGround = HEIGHT_ALONG_PATH
                * ConversionFactors.FEET_TO_METERS;

        //get the marker alt in AGL
        double markerAltAGL = 0;
        if (!m.getType().equalsIgnoreCase("vsd-marker")) {
            double groundElev = ElevationManager.getElevation(
                    refPoint1.getLatitude(),
                    refPoint1.getLongitude(), null);
            if (GeoPoint.isAltitudeValid(groundElev)
                    && GeoPoint.isAltitudeValid(refPoint1.getAltitude())) {
                markerAltAGL = EGM96.getAGL(refPoint1,
                        groundElev);
            }
        }
        double markerStartHgt = heightAboveGround + markerAltAGL;

        refPoint1 = new GeoPoint(refPoint1.getLatitude(),
                refPoint1.getLongitude(),
                markerStartHgt, AltitudeReference.AGL,
                GeoPoint.UNKNOWN,
                GeoPoint.UNKNOWN);

        markerAltAGL = 0;
        if (!m2.getType().equalsIgnoreCase("vsd-marker")) {
            double groundElev = ElevationManager.getElevation(
                    refPoint2.getLatitude(),
                    refPoint2.getLongitude(), null);
            if (GeoPoint.isAltitudeValid(groundElev)
                    && GeoPoint.isAltitudeValid(refPoint2.getAltitude())) {
                markerAltAGL = EGM96.getAGL(refPoint2,
                        groundElev);
            }
        }
        double markerEndHgt = heightAboveGround + markerAltAGL;

        refPoint2 = new GeoPoint(refPoint2.getLatitude(),
                refPoint2.getLongitude(),
                markerEndHgt, AltitudeReference.AGL,
                GeoPoint.UNKNOWN,
                GeoPoint.UNKNOWN);

        String opPref = prefs.getString(
                ViewShedReceiver.VIEWSHED_PREFERENCE_COLOR_INTENSITY_KEY, "50");

        int opacity = 50;
        try {
            Integer.parseInt(opPref);
        } catch (NumberFormatException ignored) {
        }

        double radius;
        double distance = refPoint1.distanceTo(refPoint2);
        double bearing = refPoint1.bearingTo(refPoint2);
        double angle = 0;
        if (bearing < 90) {
            angle = 90 - bearing;
        } else if (bearing < 180) {
            angle = bearing - 90;
        } else if (bearing < 270) {
            angle = 270 - bearing;
        } else {
            angle = bearing - 270;
        }

        int samples = sampleRate;
        double xDist = distance * Math.cos(Math.toRadians(angle));
        double yDist = distance * Math.sin(Math.toRadians(angle));
        if (xDist > yDist) {
            radius = xDist / ((samples * 2) - 2);
        } else {
            radius = yDist / ((samples * 2) - 2);
        }

        ElevationOverlaysMapComponent.setHeatMapVisible(false);
        showHideHeatmapCB.setChecked(false);

        Intent i = new Intent(ViewShedReceiver.ACTION_SHOW_VIEWSHED_LINE);
        i.putExtra(ViewShedReceiver.VIEWSHED_UID, m.getUID());
        i.putExtra(ViewShedReceiver.VIEWSHED_UID2, m2.getUID());
        i.putExtra(ViewShedReceiver.EXTRA_ELEV_POINT, refPoint1);
        i.putExtra(ViewShedReceiver.EXTRA_ELEV_POINT2, refPoint2);
        i.putExtra(ViewShedReceiver.EXTRA_ELEV_RADIUS, radius);
        i.putExtra(ViewShedReceiver.EXTRA_ELEV_OPACITY, opacity);
        i.putExtra(ViewShedReceiver.EXTRA_LINE_SAMPLES, samples);

        AtakBroadcast.getInstance().sendBroadcast(i);
    }

    /**
     * Broadcast the intent to calculate and show the Viewhsed
     */
    public void showViewshed(final PointMapItem vsdMarker) {

        if (vsdMarker == null)
            return;

        GeoPoint refPoint = vsdMarker.getPoint();

        float heightAboveGround = 0;
        if (altitudeET.getText().length() > 0) {
            try {
                heightAboveGround = Float.parseFloat(altitudeET.getText()
                        .toString());
            } catch (Exception e) {
                return;
            }

            Span span = getSpan();
            if (span == Span.FOOT) {
                heightAboveGround *= ConversionFactors.FEET_TO_METERS;
            }

            // for storage the unit will always need to be meters
            prefs.edit()
                    .putFloat(ViewShedReceiver.VIEWSHED_PREFERENCE_HEIGHT_ABOVE_KEY,
                            heightAboveGround)
                    .apply();
            heightMap.put(vsdMarker.getUID(), heightAboveGround);

        }

        //get the marker alt in AGL
        double markerAltAGL = 0;
        if (!vsdMarker.getType().equalsIgnoreCase("vsd-marker")) {
            double groundElev = ElevationManager.getElevation(
                    refPoint.getLatitude(),
                    refPoint.getLongitude(), null);
            if (GeoPoint.isAltitudeValid(groundElev)) {
                markerAltAGL = EGM96
                        .getAGL(refPoint, groundElev);
            }
        }
        heightAboveGround += markerAltAGL;

        refPoint = new GeoPoint(refPoint.getLatitude(),
                refPoint.getLongitude(),
                heightAboveGround, AltitudeReference.AGL,
                GeoPoint.UNKNOWN,
                GeoPoint.UNKNOWN);
        refPointMap.put(vsdMarker.getUID(), refPoint);

        final String opPref = prefs.getString(
                ViewShedReceiver.VIEWSHED_PREFERENCE_COLOR_INTENSITY_KEY, "50");

        final GeoPoint finalRefPoint = refPoint;

        getMapView().post(new Runnable() {
            @Override
            public void run() {

                //ensure new viewsheds are visible by ensuring intensity is at least 10
                int intensity;
                if (Integer.parseInt(opPref) < 10) {
                    intensity = 10;
                    intensityMap.put(vsdMarker.getUID(), intensity);
                    intensityPercentageET.setText(String.valueOf(intensity));
                } else {
                    intensity = Integer.parseInt(opPref);
                }
                intensityPercentageET.setText(String.valueOf(intensity));
                intensitySeek.setProgress(intensity);
                intensityMap.put(vsdMarker.getUID(), intensity);

                double radius;
                if (radiusET.getText().length() == 0)
                    return;

                try {
                    radius = Double.parseDouble(radiusET.getText().toString());
                } catch (Exception e) {
                    Toast.makeText(
                            getMapView().getContext(),
                            getMapView().getContext().getString(
                                    R.string.radius_num_warn),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (radius < 1 || radius > MAX_RADIUS) {
                    Toast.makeText(
                            getMapView().getContext(),
                            getMapView().getContext().getString(
                                    R.string.radius_1_to_40000),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit()
                        .putInt(ViewShedReceiver.VIEWSHED_PREFERENCE_RADIUS_KEY,
                                (int) radius)
                        .apply();
                radiusMap.put(vsdMarker.getUID(), radius);

                ElevationOverlaysMapComponent.setHeatMapVisible(false);
                showHideHeatmapCB.setChecked(false);

                Intent i = new Intent(ViewShedReceiver.ACTION_SHOW_VIEWSHED);
                i.putExtra(ViewShedReceiver.VIEWSHED_UID, vsdMarker.getUID());
                i.putExtra(ViewShedReceiver.EXTRA_ELEV_POINT, finalRefPoint);
                i.putExtra(ViewShedReceiver.EXTRA_ELEV_RADIUS, radius);
                i.putExtra(ViewShedReceiver.EXTRA_ELEV_OPACITY, intensity);

                AtakBroadcast.getInstance().sendBroadcast(i);
                showingViewshed = true;

                hideButton.setVisibility(View.VISIBLE);

                sampleView.setVisibility(View.GONE);
                viewshedOpts.setVisibility(View.VISIBLE);
                listButton.setVisibility(View.VISIBLE);
            }
        });
    }

    /**
     * Broadcasts intent to change the viewshed view
     * While preserving their attributes
     * @param vsdMarker - marker being changed to.
     */
    private void changeViewshed(PointMapItem vsdMarker) {
        if (vsdMarker == null)
            return;

        final String uid = vsdMarker.getUID();

        Double radius = radiusMap.get(uid);
        if (radius == null) return;

        Integer intensity = intensityMap.get(uid);
        if (intensity == null) return;

        Float heightAboveGround = heightMap.get(uid);
        if (heightAboveGround == null) return;

        GeoPoint refPoint = refPointMap.get(uid);
        final String msltext = formatMSL(vsdMarker.getPoint());

        //Updates the text boxes
        markerInfoTV.setText(vsdMarker.getTitle());
        viewshedDtedTV.setText(msltext);

        radiusET.setText(String.valueOf(radius));


        prefs.edit()
                .putFloat(ViewShedReceiver.VIEWSHED_PREFERENCE_HEIGHT_ABOVE_KEY,
                        (float) (double)heightAboveGround)
                .apply();

        setAltitudeFromPreference();

        intensityPercentageET.setText(String.valueOf(intensity));
        intensitySeek.setProgress(intensity);
        prefs.edit()
                .putInt(ViewShedReceiver.VIEWSHED_PREFERENCE_RADIUS_KEY,
                        (int) (double)radius)
                .apply();

        ElevationOverlaysMapComponent.setHeatMapVisible(false);
        showHideHeatmapCB.setChecked(false);

        final Intent i = new Intent(ViewShedReceiver.ACTION_SHOW_VIEWSHED);
        i.putExtra(ViewShedReceiver.VIEWSHED_UID, vsdMarker.getUID());
        i.putExtra(ViewShedReceiver.EXTRA_ELEV_POINT, refPoint);
        i.putExtra(ViewShedReceiver.EXTRA_ELEV_RADIUS, radius);
        if (intensity != 100)
            i.putExtra(ViewShedReceiver.EXTRA_ELEV_OPACITY, intensity);
        //Bug when intensity is 100
        else
            i.putExtra(ViewShedReceiver.EXTRA_ELEV_OPACITY, 99);
        AtakBroadcast.getInstance().sendBroadcast(i);
        showingViewshed = true;
        getMapView().post(new Runnable() {
            @Override
            public void run() {
                hideButton.setVisibility(View.VISIBLE);
            }
        });

        sampleView.setVisibility(View.GONE);
        viewshedOpts.setVisibility(View.VISIBLE);
        listButton.setVisibility(View.VISIBLE);
    }

    /**
     * broadcast the intent to hide the viewshed
     *
     * @param removeMarker - if true, remove the 
     */
    private void hideViewshed(boolean removeMarker) {

        //check to see if the select button should be hidden
        if (ViewShedReceiver.getSingleVsdLayerMap().isEmpty() &&
                ViewShedReceiver.getVsdLineLayerMap().isEmpty())
            listButton.setVisibility(View.GONE);
        else
            listButton.setVisibility(View.VISIBLE);

        if (showingViewshed && selectedMarker != null) {
            Intent i = new Intent(ViewShedReceiver.ACTION_DISMISS_VIEWSHED);
            i.putExtra(ViewShedReceiver.VIEWSHED_UID, selectedMarker.getUID());
            AtakBroadcast.getInstance().sendBroadcast(i);
            radiusMap.remove(selectedMarker.getUID());
            heightMap.remove(selectedMarker.getUID());
            refPointMap.remove(selectedMarker.getUID());
            intensityMap.remove(selectedMarker.getUID());
            showingViewshed = false;

        } else if (showingViewshedLine && selectedLineMarker1 != null
                && selectedLineMarker2 != null) {
            Intent i = new Intent(
                    ViewShedReceiver.ACTION_DISMISS_VIEWSHED_LINE);
            i.putExtra(ViewShedReceiver.VIEWSHED_UID,
                    selectedLineMarker1.getUID() +
                            ViewShedReceiver.VIEWSHED_LINE_UID_SEPERATOR
                            + selectedLineMarker2.getUID());
            AtakBroadcast.getInstance().sendBroadcast(i);
            showingViewshedLine = false;
        }

        if (hideButton != null)
            getMapView().post(new Runnable() {
                @Override
                public void run() {
                    hideButton.setVisibility(View.GONE);
                }
            });

        if (removeMarker) {
            if (selectedMarker != null) {
                selectedMarker.removeOnGroupChangedListener(this);
                if (selectedMarker.getType().equals("vsd-marker")) {
                    selectedMarker.removeFromGroup();
                } else {
                    selectedMarker.removeOnPointChangedListener(this);
                }

                selectedMarker = null;
            }
            if (markerInfoTV != null)
                markerInfoTV.setText(getMapView().getContext()
                        .getString(R.string.no_marker_set));
            if (viewshedDtedTV != null)
                viewshedDtedTV.setText("");
        }

        sampleView.setVisibility(View.GONE);
        viewshedOpts.setVisibility(View.VISIBLE);

    }

    /**
     * Custom implementation of an input filter
     * requires 2 int values and returns if the value are in range of the
     * preset range supplied by class constructor
     * min- the lowest accept int value
     * max- the highest accept int value
     */
    private static class InputFilterMinMax implements InputFilter {
        private final int min;
        private final int max;

        InputFilterMinMax(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                Spanned dest, int dstart, int dend) {
            try {
                int input = Integer.parseInt(dest.subSequence(0, dstart)
                        .toString()
                        + source
                        + dest.subSequence(dend, dest.length()));
                if (isInRange(min, max, input))
                    return null;
            } catch (NumberFormatException nfe) {
                Log.d(TAG, "could not covert: " + dest, nfe);
            }
            return "";
        }

        private boolean isInRange(int a, int b, int c) {
            return b > a ? c >= a && c <= b : c >= b && c <= a;
        }
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {

        if (ViewShedReceiver.getSingleVsdLayerMap()
                .containsKey(item.getUID())) {
            Intent i = new Intent(ViewShedReceiver.ACTION_DISMISS_VIEWSHED);
            i.putExtra(ViewShedReceiver.VIEWSHED_UID, item.getUID());
            AtakBroadcast.getInstance().sendBroadcast(i);
            if (selectedMarker != null
                    && item.getUID()
                            .equalsIgnoreCase(selectedMarker.getUID())) {
                hideButton.setVisibility(View.GONE);
                selectedMarker = null;
                showingViewshed = false;
            }
        }
        radiusMap.remove(item.getUID());
        heightMap.remove(item.getUID());
        refPointMap.remove(item.getUID());
        intensityMap.remove(item.getUID());

        //make sure there is no viewshed line to that marker
        Set<String> uids = ViewShedReceiver.getVsdLineLayerMap().keySet();
        for (String uid : uids) {
            if (uid.contains(item.getUID())) {
                Intent i = new Intent(
                        ViewShedReceiver.ACTION_DISMISS_VIEWSHED_LINE);
                i.putExtra(ViewShedReceiver.VIEWSHED_UID, uid);
                AtakBroadcast.getInstance().sendBroadcast(i);
                if (showingViewshedLine && selectedLineMarker1 != null
                        && selectedLineMarker2 != null) {
                    if (selectedLineMarker1.getUID().equals(item.getUID()) ||
                            selectedLineMarker2.getUID()
                                    .equals(item.getUID())) {
                        hideButton.setVisibility(View.GONE);
                        selectedLineMarker1 = null;
                        selectedLineMarker2 = null;
                        showingViewshedLine = false;

                        sampleView.setVisibility(View.GONE);
                        viewshedOpts.setVisibility(View.VISIBLE);
                    }
                }
            }
        }

        if (ViewShedReceiver.getSingleVsdLayerMap().isEmpty() &&
                ViewShedReceiver.getVsdLineLayerMap().isEmpty())
            listButton.setVisibility(View.GONE);
    }

    private void updateProgress(int progress) {
        if (contourProgress != null && contourGenButton != null) {
            if (progress >= 0) {
                _contourGenTableRow.setVisibility(View.VISIBLE);
                contourGenButton.setVisibility(View.GONE);
                contourProgress.setProgress(progress);
                contourProgressBarTextView.setText(
                        String.format("%s%%", progress));
            } else {
                _contourGenTableRow.setVisibility(View.GONE);
                contourGenButton.setVisibility(View.VISIBLE);
                contourProgress.setProgress(0);
                contourProgressBarTextView.setText(
                        String.format("%s%%", progress));
            }
        }
    }

    private void updateGenEnabled(boolean enabled) {
        if (contourGenButton != null) {
            contourGenButton.setEnabled(enabled);
        }
    }

    private String formatMSL(GeoPoint point) {
        Span altUnits = getSpan();
        return EGM96.formatMSL(point, altUnits);
    }

    /**
     * This method is being added in so take care of the heavy lift of changing
     * the value from ft to meters for the Height Above Marker.   The EditText
     * for entering the value should be passed.   This can all be removed when the
     * field is labeled.
     */
    private void setFtMLabel(EditText et) {
        Span altUnits = getSpan();
        final String ftunit = et.getContext().getString(R.string.ft);
        final String munit = et.getContext().getString(R.string.meter_abbreviation);

        String unit;
        if (altUnits.equals(Span.FOOT)) {
            unit = ftunit;
        } else {
            unit = munit;
        }
        ViewGroup vg = (ViewGroup) et.getParent();
        int numChildren = vg.getChildCount();
        for (int i = 0; i < numChildren; ++i) {
            View v = vg.getChildAt(i);
            if (v instanceof TextView) {
                String s = ((TextView) v).getText().toString();
                if (s.equals(ftunit) || s.equals(munit)) {
                    ((TextView) v).setText(unit);
                }
            }
        }

    }

    private Span getSpan() {
        Span altUnits;
        switch (Integer
                .parseInt(prefs.getString("alt_unit_pref", "0"))) {
            case 0:
                altUnits = Span.FOOT;
                break;
            case 1:
                altUnits = Span.METER;
                break;
            default: // default to feet
                altUnits = Span.FOOT;
                break;
        }
        return altUnits;
    }

    private void setAltitudeFromPreference() {
        float heightAboveMarker = prefs.getFloat(
                ViewShedReceiver.VIEWSHED_PREFERENCE_HEIGHT_ABOVE_KEY,
                (float)(5 * ConversionFactors.FEET_TO_METERS));

        // for display purposes only
        if (getSpan() == Span.FOOT) {
            heightAboveMarker *= ConversionFactors.METERS_TO_FEET;
        }
        DecimalFormat _two = LocaleUtil.getDecimalFormat("0.00");
        altitudeET.setText(_two.format(heightAboveMarker));
    }


    private SharedPreferences.OnSharedPreferenceChangeListener prefChanged = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

            if (key == null)
                return;

            if (key.equals("alt_unit_pref") && altitudeET != null) {
                ((Activity)getMapView().getContext()).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setFtMLabel(altitudeET);
                        setAltitudeFromPreference();
                    }
                });
            }
        }
    };
}
