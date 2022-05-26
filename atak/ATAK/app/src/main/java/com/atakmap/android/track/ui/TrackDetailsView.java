
package com.atakmap.android.track.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.gui.ColorPalette;
import com.atakmap.android.gui.HintDialogHelper;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.elevation.RouteElevationBroadcastReceiver;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.track.RouteTrackWrapper;
import com.atakmap.android.track.TrackDetails;
import com.atakmap.android.track.TrackHistoryDropDown;
import com.atakmap.android.track.maps.TrackPolyline;
import com.atakmap.android.track.crumb.Crumb;
import com.atakmap.android.track.crumb.CrumbDatabase;
import com.atakmap.android.track.task.GetTrackDetailsTask;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;

import com.atakmap.coremap.maps.coords.GeoPoint;

import java.text.SimpleDateFormat;
import java.util.Date;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.map.CameraController;

import java.util.TimeZone;

public class TrackDetailsView extends LinearLayout implements
        GetTrackDetailsTask.Callback,
        View.OnClickListener, DropDown.OnStateListener,
        CrumbDatabase.OnCrumbListener {

    public static final String TAG = "TrackDetailsView";

    private MapView _mapView;
    private SharedPreferences _prefs;
    private TextView _curTrack;
    private ImageView _icon;
    private TextView _callsign;
    private ImageButton _trackColor;
    private Button _trackTitle;
    private Button _trackStyle;
    private Button _distUnits;
    private TextView _startDate, _startTime, _timeAgo;
    private TextView _distance, _totalTime;
    private TextView _gain, _loss;
    private TextView _minAlt, _maxAlt;
    private TextView _maxSpeed, _avgSpeed;
    private View _loader, _loaderWheel, _scrollView;
    private TextView _loaderTxt;
    private TrackHistoryDropDown _trackHandler;
    private DropDownReceiver _dropDown;
    private MapGroup _trackMapGroup;
    private TrackDetails _track;
    private boolean _cancelLoad = false, _hideOnClose = true;

    public TrackDetailsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TrackDetails getTrack() {
        return _track;
    }

    public void setStartDateTime(long millis) {
        Date d = new Date(millis);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd",
                LocaleUtil.getCurrent());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        _startDate.setText(sdf.format(d));

        sdf = new SimpleDateFormat("HH:mm:ss'Z'", LocaleUtil.getCurrent());
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        _startTime.setText(sdf.format(d));
    }

    @Override
    public void onComplete(TrackDetails track) {
        Log.d(TAG, "Track analysis complete");
        if (_track != track) {
            removePolyline();
            if (_track == null)
                ATAKUtilities.scaleToFit(track.getPolyline());
            _track = track;
        }
        if (track == null)
            _cancelLoad = true;
        refresh();

        HintDialogHelper.showHint(getContext(),
                getContext().getString(R.string.track_details),
                getContext().getString(R.string.track_details_summ),
                "track.details");
    }

    public void init(MapView mapView, MapGroup mapGroup, TrackDetails track,
            TrackHistoryDropDown trackHandler, DropDownReceiver dropDown,
            boolean hideOnClose) {
        _track = track;
        _trackHandler = trackHandler;
        _dropDown = dropDown;
        _trackMapGroup = mapGroup;
        _mapView = mapView;
        _prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        _hideOnClose = hideOnClose;
        CrumbDatabase.instance().addCrumbListener(this);

        // Navigation bar
        findViewById(R.id.track_details_navbar).setOnClickListener(this);
        _icon = findViewById(R.id.track_details_icon);
        _callsign = findViewById(R.id.track_details_callsign);

        // Current track message
        _curTrack = findViewById(R.id.track_details_currentTrack);

        // Track title
        _trackTitle = findViewById(R.id.track_details_titleBtn);
        _trackTitle.setOnClickListener(this);

        // Create new track
        findViewById(R.id.track_details_create_new).setOnClickListener(this);
        findViewById(R.id.track_details_search).setOnClickListener(this);

        // Style and distance units
        _trackColor = findViewById(R.id.track_details_colorBtn);
        _trackColor.setOnClickListener(this);
        _trackStyle = findViewById(R.id.track_details_styleBtn);
        _trackStyle.setOnClickListener(this);
        _distUnits = findViewById(R.id.track_details_distUnitsBtn);
        _distUnits.setOnClickListener(this);

        // Pan to track start and end
        findViewById(R.id.track_details_startLayout).setOnClickListener(this);
        findViewById(R.id.track_details_distanceLayout)
                .setOnClickListener(this);

        // Start date and time
        _startDate = findViewById(R.id.track_details_startDate);
        _startTime = findViewById(R.id.track_details_startTime);
        _timeAgo = findViewById(R.id.track_details_timeAgo);

        // Distance and total time
        _distance = findViewById(R.id.track_details_distance);
        _totalTime = findViewById(R.id.track_details_elapsedtime);

        // Min and max altitude
        findViewById(R.id.track_details_minAltLayout).setOnClickListener(this);
        findViewById(R.id.track_details_maxAltLayout).setOnClickListener(this);
        _minAlt = findViewById(R.id.track_details_minAlt);
        _maxAlt = findViewById(R.id.track_details_maxAlt);

        // Max and average speed
        findViewById(R.id.track_details_maxSpeedLayout)
                .setOnClickListener(this);
        _maxSpeed = findViewById(R.id.track_details_maxSpeed);
        _avgSpeed = findViewById(R.id.track_details_avgSpeed);

        // Elevation gain and loss
        _gain = findViewById(R.id.track_details_gain);
        _loss = findViewById(R.id.track_details_loss);

        // Loader view
        _loader = findViewById(R.id.track_details_loader);
        _loaderWheel = findViewById(R.id.track_details_loader_wheel);
        _loaderTxt = findViewById(R.id.track_details_loader_txt);
        _scrollView = findViewById(R.id.track_details_scrollView);

        // Footer buttons
        findViewById(R.id.track_details_export).setOnClickListener(this);
        findViewById(R.id.track_details_graph).setOnClickListener(this);
        findViewById(R.id.track_details_delete).setOnClickListener(this);
        findViewById(R.id.track_details_close).setOnClickListener(this);

        refresh();
    }

    public void refresh() {
        Context ctx = getContext();
        if (_track == null || _mapView == null
                || _mapView.getRootGroup() == null) {
            _scrollView.setVisibility(View.GONE);
            _loader.setVisibility(View.VISIBLE);
            _icon.setVisibility(View.GONE);
            if (_cancelLoad) {
                _loaderWheel.setVisibility(View.GONE);
                _loaderTxt.setText(R.string.no_tracks_found_for_user);
                _callsign.setText(R.string.none);
            } else {
                _loaderWheel.setVisibility(View.VISIBLE);
                _loaderTxt.setText(R.string.processing_user_track);
                _callsign.setText(R.string.loading);
            }
            return;
        }

        _loader.setVisibility(View.GONE);
        _scrollView.setVisibility(View.VISIBLE);

        Log.d(TAG, "Refreshing track: " + _track.getTrackUID());

        boolean selfTrack = FileSystemUtils.isEquals(_track.getUserUID(),
                MapView.getDeviceUid());

        if (_track.isCurrentTrack()) {
            _curTrack.setVisibility(TextView.VISIBLE);
            if (selfTrack) {
                _curTrack.setText(R.string.current_active_Track);
            } else {
                String txt = ctx.getString(R.string.most_recent_Track)
                        + _track.getUserCallsign();
                _curTrack.setText(txt);
            }
        } else {
            _curTrack.setVisibility(TextView.GONE);
        }

        _icon.setVisibility(View.VISIBLE);

        ATAKUtilities.SetUserIcon(_mapView, _icon, _track.getUserUID());
        _callsign.setText(_track.getUserCallsign());

        showPolyline();

        _trackTitle.setText(_track.getTitle());

        _trackColor.setColorFilter(_track.getColor(),
                PorterDuff.Mode.MULTIPLY);
        _trackStyle.setText(_track.getSummary());

        setStartDateTime(_track.getStartTime());
        _totalTime.setText(MathUtils.GetTimeRemainingString(_track
                .getTimeElapsedLong()));
        final long millisNow = new CoordinatedTime().getMilliseconds();
        final long millisAgo = millisNow - _track.getStartTime();
        _timeAgo.setText(MathUtils.GetTimeRemainingOrDateString(millisNow,
                millisAgo, false));

        // Update units
        final int spanUnits = Integer.parseInt(_prefs.getString(
                "rab_rng_units_pref", String.valueOf(Span.METRIC)));

        String label = spanUnits == Span.METRIC ? "M/KM"
                : (spanUnits == Span.ENGLISH) ? "Ft/Mi" : "NM";
        _distUnits.setText(label);
        _distance.setText(_track.getDistanceString(spanUnits));
        _gain.setText(_track.getGainString());
        _loss.setText(_track.getLossString());
        _minAlt.setText(_track.getMinAltString());
        _maxAlt.setText(_track.getMaxAltString());
        _maxSpeed.setText(_track.getMaxSpeedString(spanUnits));
        _avgSpeed.setText(_track.getAvgSpeedString(spanUnits));
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        final Context ctx = getContext();

        // Go back to track history
        if (id == R.id.track_details_navbar) {
            closeDropDown(false);
            if (_trackHandler.isClosed())
                _trackHandler.showTrackListView(new TrackUser(
                        _mapView.getDeviceCallsign(),
                        MapView.getDeviceUid(), 0));
        }

        // Create new track
        else if (id == R.id.track_details_create_new)
            TrackHistoryDropDown.promptNewTrack(_prefs, _mapView, true);

        // Search tracks
        else if (id == R.id.track_details_search)
            _trackHandler.showTrackSearchView(null, false);

        // Pan to track start point
        else if (id == R.id.track_details_startLayout) {
            final GeoPointMetaData gpm = _track.getStartPoint();
            if (gpm != null)
                zoomToPoint(gpm.get());
        }

        // Pan to track end point
        else if (id == R.id.track_details_distanceLayout) {
            final GeoPointMetaData gpm = _track.getEndPoint();
            if (gpm != null)
                zoomToPoint(gpm.get());
        }

        // Pan to minimum altitude point
        else if (id == R.id.track_details_minAltLayout) {
            final GeoPointMetaData gpm = _track.getMinAlt();
            if (gpm != null)
                zoomToPoint(gpm.get());
        }

        // Pan to maximum altitude point
        else if (id == R.id.track_details_maxAltLayout) {
            final GeoPointMetaData gpm = _track.getMaxAlt();
            if (gpm != null)
                zoomToPoint(gpm.get());
        }
        // Pan to maximum speed point
        else if (id == R.id.track_details_maxSpeedLayout) {
            final GeoPointMetaData gpm = _track.getMaxSpeedLocation();
            if (gpm != null)
                zoomToPoint(gpm.get());

        }
        // Prompt the user to change track name
        else if (id == R.id.track_details_titleBtn) {
            final EditText editName = new EditText(ctx);
            editName.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editName.setText(_track.getTitle());

            final AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(R.string.enter_track_name);
            b.setView(editName);
            b.setPositiveButton(R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            String name = editName.getText().toString();
                            _track.setTitle(name, ctx);
                            _trackTitle.setText(name);
                            if (_trackHandler != null)
                                _trackHandler.onTrackChanged(_track);
                            refresh();
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();
        }

        // Track line color
        else if (id == R.id.track_details_colorBtn) {
            final AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(R.string.select_track_color);
            ColorPalette palette = new ColorPalette(ctx, _track.getColor());
            b.setView(palette);
            final AlertDialog alert = b.create();
            ColorPalette.OnColorSelectedListener l = new ColorPalette.OnColorSelectedListener() {
                @Override
                public void onColorSelected(int color, String label) {
                    _track.setColor(color, ctx);
                    refresh();
                    if (_trackHandler != null)
                        _trackHandler.onTrackChanged(_track);
                    alert.dismiss();
                }
            };
            palette.setOnColorSelectedListener(l);
            alert.show();
        }

        // Track line style
        else if (id == R.id.track_details_styleBtn) {
            final AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(R.string.select_track_style);
            final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(
                    ctx, android.R.layout.select_dialog_singlechoice);
            arrayAdapter.add(TrackDetails.Style.Solid.toString());
            arrayAdapter.add(TrackDetails.Style.Arrows.toString());
            arrayAdapter.add(TrackDetails.Style.Dashed.toString());

            int trackStyle = arrayAdapter.getPosition(_track.getSummary());

            b.setNegativeButton(R.string.cancel, null);
            b.setSingleChoiceItems(arrayAdapter, trackStyle,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            _track.setStyle(arrayAdapter.getItem(w), ctx);
                            refresh();
                            if (_trackHandler != null)
                                _trackHandler.onTrackChanged(_track);
                            d.dismiss();
                        }
                    });
            b.show();
        }

        // Change distance units
        else if (id == R.id.track_details_distUnitsBtn) {
            final ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx,
                    android.R.layout.select_dialog_singlechoice);
            adapter.add("Ft/Mi");
            adapter.add("M/KM");
            adapter.add("NM");

            int units = Span.METRIC;
            try {
                units = Integer.parseInt(_prefs.getString("rab_rng_units_pref",
                        String.valueOf(Span.METRIC)));
            } catch (Exception ignore) {
            }

            final AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(R.string.select_distance_units);
            b.setNegativeButton(R.string.cancel, null);
            b.setSingleChoiceItems(adapter, units,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            _prefs.edit().putString("rab_rng_units_pref",
                                    String.valueOf(w)).apply();
                            refresh();
                            d.dismiss();
                        }
                    });
            b.show();
        }

        // Export track
        else if (id == R.id.track_details_export && _track != null)
            TrackHistoryDropDown.exportTrack(_mapView, _track);

        // Open elevation profile
        else if (id == R.id.track_details_graph && _track != null) {
            if (_track.getCount() < 2) {
                Toast.makeText(ctx, R.string.track_no_2_points,
                        Toast.LENGTH_LONG).show();
                return;
            }

            boolean validAlt = false;
            GeoPoint[] trackPoints = _track.getPolyline().getPoints();
            for (GeoPoint trackPoint : trackPoints) {
                if (trackPoint.isAltitudeValid()) {
                    validAlt = true;
                    break;
                }
            }
            if (!validAlt) {
                Toast.makeText(ctx, R.string.track_no_elevation,
                        Toast.LENGTH_LONG).show();
            }

            Route route = new RouteTrackWrapper(_mapView, _track);
            RouteElevationBroadcastReceiver.getInstance().setRoute(
                    route);
            RouteElevationBroadcastReceiver.getInstance().setTitle(
                    route.getTitle());
            RouteElevationBroadcastReceiver.getInstance()
                    .openDropDown();
        }

        // Remove track
        else if (id == R.id.track_details_delete && _track != null) {
            final AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle(R.string.confirm_delete);
            b.setMessage(ctx.getString(R.string.delete) + _track.getTitle()
                    + ctx.getString(R.string.question_mark_symbol));
            b.setPositiveButton(R.string.yes,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            removePolyline();
                            CrumbDatabase.instance().deleteSegment(
                                    _track.getTrackDbId());
                            closeDropDown(false);
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();
        }

        // Close the drop-down
        else if (id == R.id.track_details_close)
            closeDropDown(true);
    }

    private void closeDropDown(boolean closeAll) {
        if (closeAll)
            _trackHandler.closeAll();
        else if (_dropDown != null)
            _dropDown.closeDropDown();
    }

    private void showPolyline() {
        if (_track == null || !_dropDown.isVisible())
            return;
        MapItem gi = _trackMapGroup.deepFindUID(_track.getTrackUID());
        if (gi == null || gi != _track.getPolyline()) {
            if (gi != null)
                gi.removeFromGroup();
            int rad = Integer.parseInt(_prefs.getString(
                    "track_crumb_size", "10"));
            _track.showPolyline(_trackMapGroup);
            _track.getPolyline().setCrumbSize(rad);
        }
        if (_track != null && _track.getPolyline() != null)
            _track.getPolyline().setMetaBoolean("detailsOpen", true);
    }

    private void removePolyline() {
        if (_track != null)
            _track.removePolyline();
    }

    private void zoomToPoint(GeoPoint gp) {
        if (gp != null && gp.isValid())
            CameraController.Programmatic.panTo(
                    _mapView.getRenderer3(), gp, true);
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownClose() {
        CrumbDatabase.instance().removeCrumbListener(this);
        if (_track != null && _track.getPolyline() != null)
            _track.getPolyline().removeMetaData("detailsOpen");
        if (_hideOnClose)
            removePolyline();
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (v) {
            ActionBarReceiver.getInstance().setToolView(null);
            showPolyline();
            refresh();
        }
    }

    @Override
    public void onCrumbAdded(int trackId, Crumb c) {
        if (_track == null || _track.getTrackDbId() != trackId
                || c == null || c.getPoint() == null)
            return;
        // Update polyline
        GeoPointMetaData point = c.getGeoPointMetaData();
        TrackPolyline poly = _track.getPolyline();
        int numPoints = poly.getNumPoints();
        GeoPointMetaData lastPoint = poly.getEndPoint();
        if (lastPoint == null)
            lastPoint = point;
        poly.addPoint(point);

        // Update min/max altitude
        double hae = EGM96.getHAE(point.get());

        if (GeoPoint.isAltitudeValid(hae)) {
            GeoPointMetaData minPt = _track.getMinAlt();
            double minHAE = GeoPoint.UNKNOWN;
            if (minPt != null)
                minHAE = EGM96.getHAE(minPt.get());

            GeoPointMetaData maxPt = _track.getMaxAlt();
            double maxHAE = GeoPoint.UNKNOWN;
            if (maxPt != null)
                maxHAE = EGM96.getHAE(maxPt.get());

            if (hae < minHAE)
                _track.setMinAlt(point);
            if (hae > maxHAE)
                _track.setMaxAlt(point);
        }

        // Update max slope, gain, and loss
        double lastHAE = EGM96.getHAE(lastPoint.get());

        if (GeoPoint.isAltitudeValid(lastHAE)) {
            double delta = hae - lastHAE;
            double deltaFt = delta * ConversionFactors.METERS_TO_FEET;
            if (delta > 0)
                _track.setGain(_track.getGain() + deltaFt);
            else if (delta < 0)
                _track.setLoss(_track.getLoss() + Math.abs(deltaFt));
            double s = delta / lastPoint.get().distanceTo(point.get());
            if (!Double.isNaN(s) && Math.abs(s) > Math.abs(
                    _track.getMaxSlope()))
                _track.setMaxSlope(s);
        }

        // Update max/avg speed
        if (c.speed > _track.getMaxSpeed())
            _track.setMaxSpeed(c.speed, point);

        double avg = _track.getAvgSpeed();
        double newAvg;
        if (avg < 0)
            newAvg = c.speed;
        else
            newAvg = (c.speed + (avg * numPoints)) / (numPoints + 1);
        _track.setAvgSpeed(newAvg);

        // Update elapsed time
        long lct = poly.getMetaLong("lastcrumbtime", -1);
        poly.setMetaLong("lastcrumbtime", Math.max(lct, c.timestamp));

        this.post(new Runnable() {
            public void run() {
                refresh();
            }
        });
    }
}
