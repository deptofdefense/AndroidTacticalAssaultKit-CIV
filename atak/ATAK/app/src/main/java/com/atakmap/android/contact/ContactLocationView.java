
package com.atakmap.android.contact;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.concurrent.ConcurrentLinkedQueue;
import android.widget.LinearLayout;
import com.atakmap.android.cotdetails.ExtendedInfoView;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.android.gui.CoordDialogView;
import com.atakmap.android.gui.RangeAndBearingTableHandler;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.user.TLECategory;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.SpeedFormatter;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.ErrorCategory;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.CameraController;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

public class ContactLocationView extends ContactDetailView {

    public static final String TAG = "ContactLocationView";

    private Button _coordButton;
    private ImageButton _panButton;
    private View _noGps;
    private View _contactInfoRangeBearingView;
    private RangeAndBearingTableHandler _rabtable;
    private TextView _tleText;
    private TextView _tleFromText;

    private TextView _addressText;
    private TextView _addressInfoText;
    private View _addressLayout;

    private TextView _speedText;
    private TextView _courseText;
    private TextView _contactInfoRangeBearingTitle;

    private UnitPreferences _prefs;
    private CoordinateFormat _cFormat;
    private NorthReference _northReference;
    private Angle _bearingUnits;

    private LinearLayout _extendedCotInfo;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.contact_detail_location, container,
                false);

        _coordButton = v.findViewById(R.id.contactInfoCoordButton);
        _panButton = v.findViewById(R.id.contactInfoPanButton);
        _noGps = v.findViewById(R.id.contactInfoRangeBearingNoGps);
        _contactInfoRangeBearingView = v
                .findViewById(R.id.contactInfoRangeBearingView);
        _rabtable = new RangeAndBearingTableHandler(v);
        _tleText = v.findViewById(R.id.contactInfoTLE_CAT);
        _tleFromText = v.findViewById(R.id.cotInfoDerivedFrom);

        _addressLayout = v.findViewById(R.id.cotAddressLayout);
        _addressText = v.findViewById(R.id.cotInfoAddress);
        _addressInfoText = v.findViewById(R.id.cotInfoAddressInfo);

        _speedText = v.findViewById(R.id.contactInfoSpeedText);
        _courseText = v.findViewById(R.id.contactInfoCourseText);
        _contactInfoRangeBearingTitle = v
                .findViewById(R.id.contactInfoRangeBearingTitle);

        _panButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (_marker != null) {
                    GeoPoint gp = _marker.getPoint();
                    CameraController.Programmatic.panTo(
                            _mapView.getRenderer3(), gp, false);
                    CameraController.Programmatic.panTo(
                            _mapView.getRenderer3(),
                            gp, false);
                }
            }
        });

        _coordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                final PointMapItem m = _marker;
                if (m == null)
                    return;

                AlertDialog.Builder b = new AlertDialog.Builder(_mapView
                        .getContext());
                LayoutInflater inflater = LayoutInflater.from(_mapView
                        .getContext());
                final CoordDialogView coordView = (CoordDialogView) inflater
                        .inflate(
                                R.layout.draper_coord_dialog, null);
                b.setTitle(R.string.rb_coord_title_readonly)
                        .setView(coordView)
                        .setPositiveButton(R.string.ok, null);
                //display read only view
                coordView.setParameters(m.getGeoPointMetaData(),
                        _mapView.getPoint(), _cFormat, !m.getMovable());

                // Overrides setPositive button onClick to keep the window open when the input is invalid.
                final AlertDialog locDialog = b.create();
                locDialog.show();
                locDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                GeoPointMetaData p = coordView.getPoint();
                                boolean changedFormat = coordView
                                        .getCoordFormat() != _cFormat;

                                if (coordView
                                        .getCoordFormat() != CoordinateFormat.ADDRESS)
                                    _cFormat = coordView
                                            .getCoordFormat();

                                CoordDialogView.Result result = coordView
                                        .getResult();

                                if (result == CoordDialogView.Result.INVALID)
                                    return;
                                if (result == CoordDialogView.Result.VALID_UNCHANGED
                                        && changedFormat) {
                                    // The coordinate format was changed but not the point itself
                                    onPointChanged();
                                } else if (result == CoordDialogView.Result.VALID_CHANGED) {
                                    com.atakmap.android.drawing.details.GenericPointDetailsView
                                            .setAddress(coordView, m,
                                                    null);
                                    onPointChanged();

                                }

                                if (m.getMovable()) {
                                    // if the point is null, do not set it
                                    // should check for VALID_CHANGED but this
                                    // will work as well.
                                    if (p != null) {
                                        m.setPoint(p);
                                        CameraController.Programmatic.panTo(
                                                _mapView.getRenderer3(),
                                                p.get(),
                                                false);
                                    }
                                }
                                locDialog.dismiss();
                            }
                        });
            }
        });

        _extendedCotInfo = v
                .findViewById(R.id.extendedCotInfo);
        for (ExtendedSelfInfoFactory esif : extendedInfoFactories) {
            _extendedCotInfo.addView(esif.createView());
        }

        updateDeviceLocation(null);
        return v;
    }

    private static final ConcurrentLinkedQueue<ExtendedSelfInfoFactory> extendedInfoFactories = new ConcurrentLinkedQueue<>();

    public interface ExtendedSelfInfoFactory {
        ExtendedInfoView createView();
    }

    /**
     * Ability to show other information not already shown as part of the 
     * ContactLocationView.   This behavior is very similiar to the ability
     * to extend out the CoT details screen.
     * @param eiv the EntendedSelfInfoFactory that creates a new view each
     * time it is requested.
     */
    static public synchronized void register(ExtendedSelfInfoFactory eiv) {
        //Log.d(TAG, "register: " + eiv);
        if (!extendedInfoFactories.contains(eiv))
            extendedInfoFactories.add(eiv);
    }

    /**
     * Unregister a previously registered ExtendedSelfInfoFactory.
     */
    static public synchronized void unregister(ExtendedSelfInfoFactory eiv) {
        //Log.d(TAG, "unregister: " + eiv);
        extendedInfoFactories.remove(eiv);
    }

    private void refreshCustomViews() {
        if (_extendedCotInfo != null) {
            _extendedCotInfo.removeAllViews();
            for (ExtendedSelfInfoFactory esif : extendedInfoFactories) {
                _extendedCotInfo.addView(esif.createView());
            }
        }
    }

    @Override
    public void init(final MapView mapView, final SharedPreferences prefs,
            ContactDetailDropdown parent) {
        super.init(mapView, prefs, parent);

        _prefs = new UnitPreferences(mapView);
        _prefs.registerListener(_sharedPrefsListener);
        _updatePreferences();
    }

    private final OnSharedPreferenceChangeListener _sharedPrefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(
                final SharedPreferences sp,
                final String key) {

            if (key == null)
                return;

            _updatePreferences();
        }
    };

    private void _updatePreferences() {
        _cFormat = _prefs.getCoordinateFormat();
        _northReference = _prefs.getNorthReference();
        _bearingUnits = _prefs.getBearingUnits();

        if (_cFormat == null)
            _cFormat = CoordinateFormat.MGRS;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh(_parent.getSelectedItem(), _parent.getSelectedContact());
    }

    @Override
    protected void refresh() {
        if (_coordButton == null) {
            Log.w(TAG, "refresh not ready");
            return;
        }

        if (_marker != null) {
            _marker.addOnPointChangedListener(_parent);
            //Note this may not pick up changes to speed for self marker, but we currently refresh
            // that when location or heading changes anyway
            if (_marker instanceof Marker) {
                ((Marker) _marker).addOnTrackChangedListener(_parent);
            }

            onPointChanged();

            com.atakmap.android.drawing.details.GenericPointDetailsView
                    .controlAddressUI(_marker, _addressText, _addressInfoText,
                            _addressLayout);

            _updatePreferences();

            if (ATAKUtilities.isSelf(_mapView, _marker)) {
                _contactInfoRangeBearingView.setVisibility(View.GONE);
            } else {
                _contactInfoRangeBearingView.setVisibility(View.VISIBLE);
            }

            // Update CE button text
            _tleText.setText(TLECategory.getCEString(_marker.getPoint())
                    + "   " + ErrorCategory.getCategory(
                            _marker.getPoint().getCE()).getName());

            final String pointSource = _marker.getGeoPointMetaData()
                    .getGeopointSource();
            StringBuilder txt = new StringBuilder();
            txt.append(_mapView.getContext().getString(R.string.derived_from));
            txt.append(pointSource);

            _tleFromText.setText(txt);

            refreshCustomViews();

            //Log.d(TAG, "found: " + ((ViewGroup)_extendedCotInfo).getChildCount());
            for (int i = 0; i < _extendedCotInfo.getChildCount(); ++i) {
                View nextChild = _extendedCotInfo.getChildAt(i);
                //Log.d(TAG, "found: " + nextChild);
                //Log.d(TAG, "found: " + nextChild.getClass());
                if (nextChild instanceof ExtendedInfoView) {
                    try {
                        ((ExtendedInfoView) nextChild).setMarker(_marker);
                    } catch (Exception e) {
                        Log.e(TAG, "error occurred setting the marker on: "
                                + nextChild);
                    }
                }
            }

            // Disable button if marker doesn't belong to our device
            //String parentUID = _marker.getMetaString("parent_uid", null);

            if (_marker instanceof Marker) {
                Marker m = (Marker) _marker;

                if (m == null) {
                    _speedText.setVisibility(View.INVISIBLE);
                    _courseText.setVisibility(View.INVISIBLE);
                } else {
                    _courseText.setVisibility(View.VISIBLE);
                    _speedText.setVisibility(View.VISIBLE);
                    onTrackChanged(m);
                }
            } else {
                _speedText.setVisibility(View.GONE);
                _courseText.setVisibility(View.GONE);
            }
        } else {
            cleanup();
        }
    }

    @Override
    protected void cleanup() {
        if (_marker != null && _parent != null) {
            _marker.removeOnPointChangedListener(_parent);
            if (_marker instanceof Marker) {
                ((Marker) _marker).removeOnTrackChangedListener(_parent);
            }
        }
    }

    void onPointChanged() {
        final PointMapItem m = _marker;
        if (m == null)
            return;

        final String p = CoordinateFormatUtilities.formatToString(
                m.getPoint(), _cFormat);
        final String a = _prefs.formatAltitude(m.getPoint());

        _mapView.post(new Runnable() {
            @Override
            public void run() {
                _coordButton.setText(p + "\n" + a);
            }
        });

        // easiest way to refresh on start since the self location change
        // is fired from the receiver.
        updateDeviceLocation(ATAKUtilities.findSelf(_mapView));

        if (m instanceof Marker)
            onTrackChanged((Marker) m);

        com.atakmap.android.drawing.details.GenericPointDetailsView
                .controlAddressUI(m, _addressText, _addressInfoText,
                        _addressLayout);

    }

    void onTrackChanged(final Marker m) {
        if (m == null)
            return;

        String orientationString = "---" + Angle.DEGREE_SYMBOL;
        double orientation = m.getTrackHeading();
        if (!Double.isNaN(orientation)) {
            String unit = "T";
            if (_northReference == NorthReference.MAGNETIC) {
                orientation = ATAKUtilities.convertFromTrueToMagnetic(
                        m.getPoint(), orientation);
                unit = "M";
            } else if (_northReference == NorthReference.GRID) {
                orientation -= ATAKUtilities.computeGridConvergence(
                        m.getPoint(), orientation, 1);
                unit = "G";
            }
            orientation = AngleUtilities.wrapDeg(orientation);
            orientationString = AngleUtilities.format(orientation,
                    _bearingUnits)
                    + unit;
        }

        final String s = SpeedFormatter.getInstance().getSpeedFormatted(m);
        final String c = orientationString;

        _mapView.post(new Runnable() {
            @Override
            public void run() {
                _speedText.setVisibility(View.VISIBLE);
                _speedText.setText(s);

                _courseText.setVisibility(View.VISIBLE);
                _courseText.setText(c);
            }
        });
    }

    public void updateDeviceLocation(final PointMapItem device) {
        if (_rabtable == null)
            return;

        if (ATAKUtilities.isSelf(_mapView, _marker)) {
            _noGps.setVisibility(View.GONE);
            _rabtable.setVisibility(View.GONE);
            _contactInfoRangeBearingTitle.setVisibility(View.GONE);
            return;
        }

        // It's possible that we don't have GPS and therefore don't have a controller point
        _contactInfoRangeBearingTitle.setVisibility(View.VISIBLE);
        _rabtable.update(device, _marker);

        _mapView.post(new Runnable() {
            @Override
            public void run() {
                if (device != null) {
                    _noGps.setVisibility(View.GONE);
                    _rabtable.setVisibility(View.VISIBLE);
                } else {
                    _noGps.setVisibility(View.VISIBLE);
                    _rabtable.setVisibility(View.GONE);
                }
            }
        });
    }
}
