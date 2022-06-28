
package com.atakmap.android.resection;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.TextView;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.NorthReference;
import com.atakmap.map.CameraController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ResectionLandmarkAdapter extends BaseAdapter implements
        SharedPreferences.OnSharedPreferenceChangeListener,
        ResectionBearingDialog.Callback {

    private static final String CHAR_SPACE = "\u200E ";
    private static final String CHAR_SEC = "\"";
    private static final String CHAR_MIN = "\u0027";
    private static final String CHAR_SEC_SPACE = CHAR_SEC + CHAR_SPACE
            + CHAR_SPACE;
    private static final String CHAR_MIN_SPACE = CHAR_MIN + CHAR_SPACE
            + CHAR_SPACE;

    private final MapView _mapView;
    private final Context _context;
    private final SharedPreferences _prefs;
    private final LayoutInflater _inflater;
    private final ResectionMapManager _manager;
    private final List<Marker> _landmarks = new ArrayList<>();
    private TextView _estimatedLoc;

    ResectionLandmarkAdapter(MapView mapView, ResectionMapManager manager) {
        _mapView = mapView;
        _context = mapView.getContext();
        _inflater = LayoutInflater.from(_context);
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        _prefs.registerOnSharedPreferenceChangeListener(this);
        _manager = manager;
    }

    void setEstimatedTV(TextView est) {
        _estimatedLoc = est;
    }

    @Override
    public void notifyDataSetChanged() {
        _landmarks.clear();
        _landmarks.addAll(_manager.getLandmarks());
        Collections.sort(_landmarks, LM_SORT);
        if (_estimatedLoc != null) {
            GeoPoint inter = _manager.getIntersectionPoint();
            if (inter != null)
                _estimatedLoc.setText(formatCoord(inter));
            else
                _estimatedLoc.setText("");
        }
        super.notifyDataSetChanged();
    }

    @Override
    public void onBearingEntered(ResectionBearingDialog dialog,
            double oldBearingTrue, double newBearingTrue) {
        Object tag = dialog.getTag();
        if (!(tag instanceof Marker) || Double.compare(oldBearingTrue,
                newBearingTrue) == 0)
            return;
        Marker m = (Marker) tag;
        m.setMetaDouble("landmarkBearing", newBearingTrue);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return _landmarks.size();
    }

    @Override
    public Marker getItem(int position) {
        return _landmarks.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View row, ViewGroup parent) {
        ViewHolder h = row != null ? (ViewHolder) row.getTag() : null;
        if (h == null) {
            row = _inflater.inflate(R.layout.resection_landmark_row,
                    null, false);
            h = new ViewHolder();
            h.name = row.findViewById(R.id.name);
            h.location = row.findViewById(R.id.location);
            h.bearing = row.findViewById(R.id.bearing);
            row.setTag(h);
        }

        NorthReference ref = getNorthReference();

        Marker m = h.marker = getItem(position);
        GeoPoint point = m.getPoint();

        h.name.setText(m.getTitle());
        h.name.setOnClickListener(h);

        h.location.setText(formatCoord(point));
        h.location.setOnClickListener(h);

        row.findViewById(R.id.panto_location).setOnClickListener(h);

        double bearing = m.getMetaDouble("landmarkBearing", 0);

        if (ref == NorthReference.GRID) {
            GeoPoint endPoint = GeoCalculations.pointAtDistance(point, bearing,
                    1000);
            double gridConvergence = ATAKUtilities
                    .computeGridConvergence(point, endPoint);
            bearing -= gridConvergence;
        } else if (ref == NorthReference.MAGNETIC)
            bearing = ATAKUtilities.convertFromTrueToMagnetic(point, bearing);

        h.bearing.setText(AngleUtilities.format(bearing, Angle.DEGREE, 1)
                + ref.getAbbrev());
        h.bearing.setOnClickListener(h);

        return row;
    }

    private class ViewHolder implements View.OnClickListener {
        Marker marker;
        TextView name, location, bearing;

        @Override
        public void onClick(View v) {
            if (v == name) {
                // Bring up name entry dialog
                showNameDialog(marker);
            } else if (v == location) {
                // Bring up coordinate entry dialog
                AtakBroadcast.getInstance().sendBroadcast(new Intent(
                        "com.atakmap.android.maps.MANUAL_POINT_ENTRY")
                                .putExtra("uid", marker.getUID()));
            } else if (v == bearing) {
                // Set marker bearing
                double bearing = marker.getMetaDouble("landmarkBearing", 0);
                ResectionBearingDialog d = new ResectionBearingDialog(_mapView);
                d.setTitle(_context.getString(R.string.resection_bearing_title,
                        _mapView.getDeviceCallsign(), marker.getTitle()));
                d.setTargetPoint(marker.getPoint());
                d.setTag(marker);
                d.show(bearing, ResectionLandmarkAdapter.this);
            } else if (v.getId() == R.id.panto_location) {
                CameraController.Programmatic.panTo(
                        _mapView.getRenderer3(), marker.getPoint(), true);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {

        if (key == null)
            return;

        if (key.equals("coord_display_pref")
                || key.equals("rab_north_ref_pref"))
            notifyDataSetChanged();
    }

    private void showNameDialog(final Marker marker) {
        String name = marker.getTitle();
        final EditText et = new EditText(_context);
        et.setSingleLine(true);
        et.setText(name);
        et.setSelection(name.length());

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.name);
        b.setView(et);
        b.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                String name = et.getText().toString();
                if (name.isEmpty())
                    return;
                marker.setTitle(name);
                notifyDataSetChanged();
            }
        });
        b.setNegativeButton(R.string.cancel, null);
        AlertDialog d = b.create();
        if (d.getWindow() != null)
            d.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        d.show();
        et.requestFocus();
    }

    private CoordinateFormat getCoordinateFormat() {
        return CoordinateFormat.find(_prefs.getString(
                "coord_display_pref", CoordinateFormat.MGRS.toString()));
    }

    private NorthReference getNorthReference() {
        NorthReference ref = NorthReference.MAGNETIC;
        try {
            int north = Integer.parseInt(_prefs.getString(
                    "rab_north_ref_pref", String.valueOf(ref.getValue())));
            ref = NorthReference.findFromValue(north);
        } catch (Exception ignore) {
        }
        return ref;
    }

    private String formatCoord(GeoPoint point) {
        CoordinateFormat coordFmt = getCoordinateFormat();
        String coord = CoordinateFormatUtilities.formatToString(
                point, coordFmt);
        // Force text to wrap between latitude and longitude strings
        if (coordFmt == CoordinateFormat.DMS)
            coord = coord.replace(CHAR_SEC_SPACE + "E", CHAR_SEC + "\nE")
                    .replace(CHAR_SEC_SPACE + "W", CHAR_SEC + "\nW");
        else if (coordFmt == CoordinateFormat.DM)
            coord = coord.replace(CHAR_MIN_SPACE + "E", CHAR_MIN + "\nE")
                    .replace(CHAR_MIN_SPACE + "W", CHAR_MIN + "\nW");
        return coord;
    }

    private static final Comparator<Marker> LM_SORT = new Comparator<Marker>() {
        @Override
        public int compare(Marker lhs, Marker rhs) {
            String lTitle = lhs.getTitle(), rTitle = rhs.getTitle();
            int lNum = getLandmarkNum(lTitle);
            int rNum = getLandmarkNum(rTitle);
            return lNum == rNum ? lTitle.compareTo(rTitle)
                    : Integer.compare(lNum, rNum);
        }
    };

    private static int getLandmarkNum(String title) {
        String prefix = ResectionMapManager.LANDMARK_PREFIX;
        if (!title.startsWith(prefix))
            return -1;
        try {
            return Integer.parseInt(title.substring(prefix.length()));
        } catch (Exception ignore) {
        }
        return -1;
    }
}
