
package com.atakmap.android.cotdetails;

import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.graphics.drawable.shapes.Shape;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Spinner;

import com.atakmap.android.drawing.details.GenericDetailsView;
import com.atakmap.android.hashtags.view.RemarksLayout;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.SimpleItemSelectedListener;

import com.atakmap.android.contact.ContactPresenceDropdown;
import com.atakmap.android.cot.detail.SensorDetailHandler;
import com.atakmap.android.drawing.details.GenericPointDetailsView;
import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.gui.ColorPalette.OnColorSelectedListener;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.SensorFOV;
import com.atakmap.android.maps.SensorFOV.OnMetricsChangedListener;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.video.AddEditAlias;
import com.atakmap.android.video.AddEditAlias.AliasModifiedListener;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.android.video.VideoListDialog;
import com.atakmap.android.video.manager.VideoManager;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class SensorDetailsView extends GenericPointDetailsView implements
        OnMetricsChangedListener, AliasModifiedListener {

    public static final String TAG = "SensorDetailsView";
    public static final int MAX_SENSOR_RANGE = 15000;

    private static final String TRUE_NORTH = Angle.DEGREE_SYMBOL + "TN";
    private static final String MAGNETIC_AZIMUTH = Angle.DEGREE_SYMBOL + "MZ";
    private static final String[] DIRECTION_AZIMUTH_OPTIONS = new String[] {
            TRUE_NORTH, MAGNETIC_AZIMUTH
    };

    private String sensorUID;

    private int currentAzimuth = 0;
    private ImageButton _sendButton;
    private MapView _mapView;

    private MapItem sensorItem;
    private Marker sensorMarker;
    private SensorFOV sensorFOV;

    private boolean isInitalized = false;
    private EditText nameET;
    private Button videoAliasButton;
    private ImageButton selectVideoAliasButton;
    private EditText rangeET;
    private Spinner directionAzimuthSpin;
    private EditText directionET;
    private EditText fovET;
    private SeekBar rangeSeek;
    private SeekBar directionSeek;
    private SeekBar fovSeek;
    private RemarksLayout remarksLayout;
    private SeekBar alphaSeek;
    private CheckBox fovVisibleCB;
    private Button placeSensorButton;
    private ImageButton _colorButton;

    private Button _coordButton;

    private boolean ignoreRangeUpdate = false;

    /**
     * *************************** CONSTRUCTOR ***************************
     */

    public SensorDetailsView(Context context) {
        super(context);
        if (!isInEditMode())
            _mapView = MapView.getMapView();
    }

    public SensorDetailsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode())
            _mapView = MapView.getMapView();
    }

    @Override
    protected void _init() {
        GenericDetailsView.addEditTextPrompts(this);
        sensorMarker = (Marker) sensorItem;

        _addressLayout = this.findViewById(R.id.dgAddressLayout);
        _addressText = this.findViewById(R.id.dgInfoAddress);
        _addressInfoText = this.findViewById(R.id.dgInfoAddressInfo);

        nameET = this.findViewById(R.id.sensorNameET);
        nameET.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                //Apply new callsign
                sensorItem.setMetaString("callsign", s.toString());
                sensorMarker.setTitle(s.toString());
                sensorItem.persist(_mapView.getMapEventDispatcher(), null,
                        this.getClass());
            }
        });

        final SensorDetailsView self = this;
        videoAliasButton = this.findViewById(R.id.sensorVideoUrlBtn);
        videoAliasButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AddEditAlias aea = new AddEditAlias(getContext());

                String videoUID = sensorItem.getMetaString("videoUID", "");
                ConnectionEntry entry = VideoManager.getInstance()
                        .getEntry(videoUID);

                //show the view to enter a new alias or modify an existing one
                aea.addEditConnection(entry, self);
            }
        });

        selectVideoAliasButton = this
                .findViewById(R.id.videoAliasButton);
        selectVideoAliasButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //show video alias selector
                VideoListDialog d = new VideoListDialog(_mapView);
                d.show(false, new VideoListDialog.Callback() {
                    @Override
                    public void onVideosSelected(List<ConnectionEntry> s) {
                        if (s.isEmpty())
                            return;
                        ConnectionEntry ce = s.get(0);
                        videoAliasButton.setText(ce.getAlias());
                        sensorItem.setMetaString("videoUID", ce.getUID());
                        sensorItem.setMetaString("videoUrl",
                                ConnectionEntry.getURL(ce, false));
                        sensorItem.persist(_mapView.getMapEventDispatcher(),
                                null, this.getClass());
                    }
                });
            }
        });

        fovVisibleCB = this.findViewById(R.id.fovVisibilityCB);
        fovVisibleCB.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                sensorFOV.setVisible(isChecked);
                if (isChecked) {
                    if (sensorItem.hasMetaValue(SensorDetailHandler.HIDE_FOV))
                        sensorItem.removeMetaData(SensorDetailHandler.HIDE_FOV);
                } else {
                    sensorItem.setMetaBoolean(SensorDetailHandler.HIDE_FOV,
                            true);
                }
                sensorItem.persist(_mapView.getMapEventDispatcher(), null,
                        this.getClass());
            }
        });

        rangeET = this.findViewById(R.id.sensorRangeET);
        rangeET.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                int currentSel = rangeET.getSelectionEnd();
                if (s.length() <= 0)
                    return;

                int val;
                try {
                    val = (int) Math.round(Double.parseDouble(s.toString()));
                } catch (NumberFormatException nfe) {
                    try {
                        val = Integer.parseInt(s.toString());
                    } catch (NumberFormatException nfe2) {
                        val = sensorItem.getMetaInteger(
                                SensorDetailHandler.RANGE_ATTRIBUTE, 0);
                        String s2 = String.valueOf(val);
                        rangeET.setText(s2);
                        rangeET.setSelection(s2.length());
                        return;
                    }
                }
                if (val > MAX_SENSOR_RANGE) {
                    String s2 = String.valueOf(MAX_SENSOR_RANGE);
                    rangeET.setText(s2);
                    rangeET.setSelection(s2.length());
                    return;
                }

                //Apply to new range to FOV
                if (sensorFOV != null && (int) sensorFOV.getExtent() != val) {
                    sensorFOV.setMetrics(sensorFOV.getAzimuth(),
                            sensorFOV.getFOV(), val);
                    sensorItem.setMetaInteger(
                            SensorDetailHandler.RANGE_ATTRIBUTE, val);
                    sensorItem.persist(_mapView.getMapEventDispatcher(), null,
                            this.getClass());
                }

                int estimatedProg = (int) Math.round(Math.sqrt(val));
                int currentProg = rangeSeek.getProgress();
                if (estimatedProg != currentProg) {
                    ignoreRangeUpdate = true;
                    rangeSeek.setProgress((int) Math.round(Math.sqrt(val)));
                    ignoreRangeUpdate = false;
                }
                try {
                    rangeET.setSelection(Math.min(rangeET.getText().length(),
                            currentSel));
                    rangeET.setSelection(s.length());
                } catch (IndexOutOfBoundsException ioobe) {
                    Log.d(TAG, "error occured setting the selection");
                }
            }
        });
        rangeSeek = this.findViewById(R.id.rangeSeek);
        rangeSeek.setMax(122);
        rangeSeek.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (!ignoreRangeUpdate)
                    rangeET.setText(String.valueOf(seekBar.getProgress()
                            * seekBar
                                    .getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                rangeET.requestFocus();
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                //if the seek bar was changed by the user, update the range 
                if (!ignoreRangeUpdate)
                    //exponentially grow the range so that there is finer adjustment
                    //on the low end and more coarse adjustment on the high end
                    rangeET.setText(String.valueOf(progress * progress));
            }
        });

        directionAzimuthSpin = this
                .findViewById(R.id.directionAzimuthSpinner);
        ArrayAdapter<Object> adapter = new ArrayAdapter<Object>(
                _mapView.getContext(),
                R.layout.spinner_text_view_dark, DIRECTION_AZIMUTH_OPTIONS);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        directionAzimuthSpin.setOnItemSelectedListener(
                new SimpleItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> arg0, View arg1,
                            int arg2, long arg3) {

                        final String ref = DIRECTION_AZIMUTH_OPTIONS[arg2];
                        directionET.setContentDescription(getContext()
                                .getString(R.string.direction_units, ref));
                        if (currentAzimuth == arg2)
                            return;
                        currentAzimuth = arg2;
                        if (ref.equals(TRUE_NORTH)) {
                            directionET.setText(String
                                    .valueOf((int) sensorFOV.getAzimuth()));
                        } else if (ref.equals(MAGNETIC_AZIMUTH)) {
                            float trueAzimuth = sensorFOV.getAzimuth();
                            double magAzimuth = ATAKUtilities
                                    .convertFromTrueToMagnetic(
                                            sensorMarker.getPoint(),
                                            trueAzimuth);
                            directionET.setText(String.valueOf((int) Math
                                    .round(magAzimuth)));
                        }

                        //save azimuth preference to the marker
                        sensorItem.setMetaInteger(
                                SensorDetailHandler.MAG_REF_ATTRIBUTE,
                                currentAzimuth);
                        sensorItem.persist(_mapView.getMapEventDispatcher(),
                                null, this.getClass());
                    }
                });

        directionAzimuthSpin.setAdapter(adapter);
        directionET = this.findViewById(R.id.directionET);
        directionSeek = this.findViewById(R.id.directionSeek);
        directionSeek.setMax(359);
        directionET.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() <= 0)
                    return;
                int val = Integer.parseInt(s.toString());
                int rounded = AngleUtilities.roundDeg(val);
                if (val != rounded) {
                    final String displayString = String.valueOf(rounded);
                    directionET.setText(displayString);
                    directionET.setSelection(displayString.length());
                    return;
                }
                val = rounded;
                int trueVal = val % 360;
                if (DIRECTION_AZIMUTH_OPTIONS[currentAzimuth]
                        .equals(MAGNETIC_AZIMUTH)) {
                    //convert back to true
                    trueVal = (int) Math.round(ATAKUtilities
                            .convertFromMagneticToTrue(sensorMarker.getPoint(),
                                    val));
                }

                //Apply to new direction to FOV
                if (sensorFOV != null
                        && (int) sensorFOV.getAzimuth() != trueVal) {
                    sensorFOV.setMetrics(trueVal, sensorFOV.getFOV(),
                            sensorFOV.getExtent());
                    sensorItem.setMetaInteger(
                            SensorDetailHandler.AZIMUTH_ATTRIBUTE, trueVal);
                    sensorItem.persist(_mapView.getMapEventDispatcher(), null,
                            this.getClass());
                }

                //if seekbar progress does not match angle, update seekbar
                if (directionSeek.getProgress() != val - 1)
                    directionSeek.setProgress(val - 1);
                directionET.setSelection(s.length());
            }
        });

        directionSeek.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                directionET.setText(
                        String.valueOf(directionSeek.getProgress() + 1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                directionET.requestFocus();
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                directionET.setText(
                        String.valueOf(directionSeek.getProgress() + 1));
            }
        });

        fovET = this.findViewById(R.id.fovET);
        fovSeek = this.findViewById(R.id.fovSeek);
        fovSeek.setMax(359);
        fovET.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() <= 0)
                    return;
                int val = Integer.parseInt(s.toString());
                if (val >= 360) {
                    fovET.setText(R.string.threefiftynine);
                    fovET.setSelection(fovET.length());
                    return;
                }

                //Apply to new direction to FOV
                if (sensorFOV != null && (int) sensorFOV.getFOV() != val) {
                    sensorFOV.setMetrics(sensorFOV.getAzimuth(), val,
                            sensorFOV.getExtent());
                    sensorItem.setMetaInteger(
                            SensorDetailHandler.FOV_ATTRIBUTE, val);
                    sensorItem.persist(_mapView.getMapEventDispatcher(), null,
                            this.getClass());
                }

                //if seekbar progress does not match angle, update seekbar
                if (fovSeek.getProgress() != val)
                    fovSeek.setProgress(val);

                fovET.setSelection(s.length());
            }
        });
        fovSeek.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                fovET.setText(String.valueOf(fovSeek.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                fovET.requestFocus();
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                fovET.setText(String.valueOf(fovSeek.getProgress()));
            }
        });

        remarksLayout = this.findViewById(R.id.remarksLayout);
        remarksLayout.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                Log.d(TAG, "sensor change: " + s);
                //Apply new callsign
                sensorItem.setMetaString("remarks", s.toString());
                sensorItem.persist(_mapView.getMapEventDispatcher(), null,
                        this.getClass());
            }
        });

        _coordButton = this
                .findViewById(R.id.sensorCenterButton);
        _point = sensorMarker;

        _coordButton.setText(_unitPrefs.formatPoint(
                _point.getGeoPointMetaData(), true));
        _coordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _onCoordSelected();
            }
        });

        formatAddress();

        _sendButton = this.findViewById(R.id.sendButton);
        _sendButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent contactList = new Intent();
                contactList.setAction(ContactPresenceDropdown.SEND_LIST);
                contactList.putExtra("targetUID", sensorUID);

                AtakBroadcast.getInstance().sendBroadcast(contactList);
            }
        });

        alphaSeek = this.findViewById(R.id.alphaSeek);
        alphaSeek.setMax(100);
        alphaSeek.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sensorItem.setMetaDouble(SensorDetailHandler.FOV_ALPHA,
                        seekBar.getProgress() / 100d);
                sensorItem.persist(_mapView.getMapEventDispatcher(), null,
                        this.getClass());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                clearFocus();
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                double alpha = progress / 100d;
                //dont save if value is already equal
                if (sensorItem.hasMetaValue(SensorDetailHandler.FOV_ALPHA)) {
                    double savedVal = sensorItem.getMetaDouble(
                            SensorDetailHandler.FOV_ALPHA, 0.3d);
                    if (Double.compare(savedVal, alpha) == 0)
                        return;
                }
                sensorFOV.setAlpha((float) alpha);
                sensorItem.setMetaDouble(SensorDetailHandler.FOV_ALPHA, alpha);
                sensorItem.persist(_mapView.getMapEventDispatcher(), null,
                        this.getClass());
            }
        });

        _colorButton = this.findViewById(R.id.fovColorButton);

        _colorButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                _onColorSelected();
            }
        });
        _updateColorButtonDrawable();

        placeSensorButton = this
                .findViewById(R.id.placeEndPointButton);
        placeSensorButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                SensorDetailHandler
                        .selectFOVEndPoint(sensorMarker, false, true);
            }
        });

        isInitalized = true;
    }

    @Override
    public void _updateColorButtonDrawable() {
        Shape rect = new RectShape();
        rect.resize(50, 50);
        ShapeDrawable color = new ShapeDrawable();
        color.setBounds(0, 0, 50, 50);
        color.setIntrinsicHeight(50);
        color.setIntrinsicWidth(50);
        if (sensorFOV == null)
            return;
        float[] colorArray = sensorFOV.getColor();
        sensorItem.setMetaDouble(SensorDetailHandler.FOV_RED, colorArray[0]);
        sensorItem.setMetaDouble(SensorDetailHandler.FOV_GREEN, colorArray[1]);
        sensorItem.setMetaDouble(SensorDetailHandler.FOV_BLUE, colorArray[2]);
        sensorItem.persist(_mapView.getMapEventDispatcher(), null,
                this.getClass());
        int col = Color.argb(255, (int) (colorArray[0] * 255),
                (int) (colorArray[1] * 255), (int) (colorArray[2] * 255));
        color.getPaint().setColor(col);
        color.setShape(rect);

        _colorButton.setImageDrawable(color);
    }

    @Override
    protected void _onColorSelected(int color, String label) {
        sensorFOV.setColor(Color.red(color) / 255f, Color.green(color) / 255f,
                Color.blue(color) / 255f);
        _updateColorButtonDrawable();
    }

    public void setSensorMarker(String uid) {
        //find the marker, and set the UI to the values
        MapItem mi = _mapView.getMapItem(uid);
        if (mi != null) {
            if (mi.getType().equals("b-m-p-s-p-loc")) {
                sensorItem = mi;
                sensorMarker = (Marker) sensorItem;
                sensorUID = uid;
                MapItem sfov = _mapView.getMapItem(uid
                        + SensorDetailHandler.UID_POSTFIX);
                if (sfov instanceof SensorFOV)
                    sensorFOV = (SensorFOV) sfov;
                if (sfov == null) {
                    //if no sensor FOV was found create a default sensor FOV and set it to not be visible
                    SensorDetailHandler.addFovToMap(sensorMarker, 270, 45, 100,
                            new float[] {
                                    1, 1, 1, 0.3f
                            }, false);

                    sensorItem.setMetaBoolean(SensorDetailHandler.HIDE_FOV,
                            true);
                    sensorItem.persist(_mapView.getMapEventDispatcher(), null,
                            this.getClass());

                    sfov = _mapView.getMapItem(uid
                            + SensorDetailHandler.UID_POSTFIX);
                    if (sfov instanceof SensorFOV)
                        sensorFOV = (SensorFOV) sfov;
                    if (sensorFOV == null) {
                        Log.e(TAG, "sensor field of view was not found: " +
                                (uid + SensorDetailHandler.UID_POSTFIX));
                        return;
                    }
                }

                if (!isInitalized)
                    _init();

                if (sensorItem
                        .hasMetaValue(SensorDetailHandler.MAG_REF_ATTRIBUTE)) {
                    int i = sensorItem.getMetaInteger(
                            SensorDetailHandler.MAG_REF_ATTRIBUTE, 0);
                    if (i != 0)
                        directionAzimuthSpin.setSelection(1);
                }

                updateFOVDetails();

                sensorFOV.addOnMetricsChangedListener(this);
                sensorMarker.addOnPointChangedListener(this);
            }
        }
    }

    @Override
    public void onClose() {
        if (sensorFOV != null)
            sensorFOV.removeOnMetricsChangedListener(this);
        if (sensorMarker != null)
            sensorMarker.removeOnPointChangedListener(this);
    }

    private void updateFOVDetails() {
        //run on UI thread
        ((Activity) _mapView.getContext()).runOnUiThread(new Runnable() {
            @Override
            public void run() {

                nameET.setText(sensorItem.getMetaString("callsign", ""));
                String videoUID = sensorItem.getMetaString("videoUID", "");

                ConnectionEntry entry = VideoManager.getInstance()
                        .getEntry(videoUID);
                if (entry != null && entry.isRemote())
                    videoAliasButton.setText(entry.getAlias());
                else
                    videoAliasButton.setText(sensorItem.getMetaString(
                            "videoUrl", "None"));

                remarksLayout.setText(sensorItem.getMetaString("remarks", ""));
                rangeET.setText(String.valueOf((int) sensorFOV.getExtent()));

                if (directionAzimuthSpin.getSelectedItemPosition() == 1) {
                    float trueAzimuth = sensorFOV.getAzimuth();
                    double magAzimuth = ATAKUtilities
                            .convertFromTrueToMagnetic(sensorMarker.getPoint(),
                                    trueAzimuth);
                    directionET.setText(String.valueOf((int) Math
                            .round(magAzimuth)));
                } else
                    directionET.setText(String.valueOf((int) sensorFOV
                            .getAzimuth()));
                fovET.setText(String.valueOf((int) sensorFOV.getFOV()));
                if (sensorItem.hasMetaValue(SensorDetailHandler.FOV_ALPHA)) {
                    final double i = sensorItem.getMetaDouble(
                            SensorDetailHandler.FOV_ALPHA, 0.3d);
                    if (i >= 0 && i <= 1)
                        ((Activity) _mapView.getContext())
                                .runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        int prog = (int) (i * 100d);
                                        alphaSeek.setProgress(prog);
                                    }
                                });
                } else
                    alphaSeek.setProgress(30);
                if (sensorItem.hasMetaValue(SensorDetailHandler.HIDE_FOV))
                    fovVisibleCB.setChecked(false);

                _extrasLayout.setItem(sensorItem);
            }
        });
    }

    // show dialog box to enter the altitude
    protected void _onCoordSelected() {
        AlertDialog.Builder b = new AlertDialog.Builder(getContext());
        LayoutInflater inflater = LayoutInflater.from(getContext());
        // custom view that allows entry of geo via mgrs, dms, and decimal degrees
        final CoordDialogView coordView = (CoordDialogView) inflater.inflate(
                R.layout.draper_coord_dialog, null);
        b.setTitle(R.string.point_dropper_text19);
        b.setView(coordView);
        b.setPositiveButton(R.string.ok, null);
        b.setNegativeButton(R.string.cancel, null);
        coordView.setParameters(_point.getGeoPointMetaData(),
                _mapView.getPoint(),
                _cFormat);

        // Overrides setPositive button onClick to keep the window open when the input is invalid.
        final AlertDialog locDialog = b.create();
        locDialog.show();
        locDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // On click get the geopoint and elevation double in ft
                        GeoPointMetaData p = coordView.getPoint();
                        CoordinateFormat cf = coordView.getCoordFormat();
                        CoordDialogView.Result result = coordView.getResult();
                        if (result == CoordDialogView.Result.INVALID)
                            return;
                        if (result == CoordDialogView.Result.VALID_UNCHANGED
                                && cf != _cFormat) {
                            // The coordinate format was changed but not the point itself
                            _coordButton
                                    .setText(coordView.getFormattedString());
                        }
                        _cFormat = cf;
                        if (result == CoordDialogView.Result.VALID_CHANGED) {

                            setAddress(coordView, _point, _nameEdit);

                            sensorMarker.setPoint(p);
                            _coordButton
                                    .setText(coordView.getFormattedString());

                            _mapView.getMapController()
                                    .panTo(p.get(), true);
                            sensorItem.persist(
                                    _mapView.getMapEventDispatcher(), null,
                                    this.getClass());
                        }
                        locDialog.dismiss();
                    }
                });

    }

    @Override
    public void onPointChanged(final PointMapItem item) {
        item.removeOnPointChangedListener(this);
        _point = sensorMarker;
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                _coordButton.setText(_unitPrefs.formatPoint(
                        _point.getGeoPointMetaData(), true));
                _coordButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        _onCoordSelected();
                    }
                });
                formatAddress();
            }
        });
    }

    @Override
    public void onMetricsChanged(SensorFOV fov) {
        //update the Sensor FOV details UI
        updateFOVDetails();
    }

    @Override
    protected void _onColorSelected() {
        AlertDialog.Builder b = new AlertDialog.Builder(getContext())
                .setTitle(R.string.point_dropper_text21);
        ShapeDrawable drawable = (ShapeDrawable) _colorButton.getDrawable();
        ColorPalette palette = new ColorPalette(getContext(), drawable
                .getPaint().getColor());
        b.setView(palette);
        final AlertDialog alert = b.create();
        OnColorSelectedListener l = new OnColorSelectedListener() {
            @Override
            public void onColorSelected(int color, String label) {
                alert.cancel();
                _onColorSelected(color, label);
            }
        };
        palette.setOnColorSelectedListener(l);
        alert.show();
    }

    /**
     * If the user modified the associated video alias, update the name 
     * and save the URL
     */
    @Override
    public void aliasModified(ConnectionEntry selected) {
        if (selected == null) {
            videoAliasButton.setText(R.string.none);
            sensorItem.setMetaString("videoUID", "");
            sensorItem.persist(
                    _mapView.getMapEventDispatcher(), null,
                    this.getClass());

            return;
        }
        videoAliasButton.setText(selected.getAlias());
        ConnectionEntry entry = VideoManager.getInstance()
                .getEntry(selected.getUID());
        if (entry != null && entry.isRemote()) {
            sensorItem.setMetaString("videoUrl", ConnectionEntry.getURL(entry, false));
            sensorItem.setMetaString("videoUID", entry.getUID());
            sensorItem.persist(_mapView.getMapEventDispatcher(), null,
                    getClass());
        }
    }
}
