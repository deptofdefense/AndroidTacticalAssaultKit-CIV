
package com.atakmap.android.track.task;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.track.BreadcrumbReceiver;
import com.atakmap.android.track.crumb.CrumbDatabase;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.spatial.kml.KMLUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Async task for creating one or several tracks
 */
public class CreateTracksTask extends AsyncTask<Void, Integer, Integer> {

    private final MapView _mapView;
    private final Context _context;
    private final boolean _showProgress;
    private final List<Item> _items;
    private final String _titlePrefix;
    private ProgressDialog _pd;

    public CreateTracksTask(MapView mapView, String title, String callsign,
            String uid, boolean stitch, boolean showProgress) {
        _mapView = mapView;
        _context = mapView.getContext();
        _showProgress = showProgress;

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(_context);
        _titlePrefix = prefs.getString("track_prefix", "track_");
        _items = new ArrayList<>();
        _items.add(new Item(title, BreadcrumbReceiver.getNextColor(prefs),
                callsign, uid, stitch));
    }

    public CreateTracksTask(MapView mapView, Collection<PointMapItem> items,
            boolean showProgress) {
        _mapView = mapView;
        _context = mapView.getContext();
        _showProgress = showProgress;

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(_context);
        _titlePrefix = prefs.getString("track_prefix", "track_");
        int defColor = BreadcrumbReceiver.DEFAULT_LINE_COLOR;
        int[] colors = BreadcrumbReceiver.TRACK_COLORS;
        int trackColor = Integer.parseInt(prefs.getString(
                "track_history_default_color", String.valueOf(defColor)));
        int c = -1;
        if (prefs.getBoolean("toggle_rotate_track_colors", true)) {
            int lastColorUsed = prefs.getInt("track_last_color", defColor);
            for (int i = 0; i < colors.length; i++) {
                if (colors[i] == lastColorUsed) {
                    c = i;
                    break;
                }
            }
            if (c == -1)
                c = 0;
        }
        _items = new ArrayList<>(items.size());
        for (PointMapItem pmi : items) {
            if (c != -1)
                trackColor = colors[++c % colors.length];
            _items.add(new Item(null, trackColor, pmi));
        }
        prefs.edit().putInt("track_last_color", trackColor).apply();
    }

    public CreateTracksTask(MapView mapView, PointMapItem item,
            boolean showProgress) {
        this(mapView, Collections.singleton(item), showProgress);
    }

    @Override
    protected void onPreExecute() {
        if (_showProgress) {
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    _pd = new ProgressDialog(_context);
                    _pd.setMessage(_context.getString(
                            R.string.create_tracks_progress));
                    _pd.setProgress(0);
                    _pd.setMax(_items.size());
                    _pd.setIndeterminate(false);
                    _pd.setCancelable(false);
                    _pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    _pd.setButton(DialogInterface.BUTTON_NEGATIVE,
                            _context.getString(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    cancel(false);
                                }
                            });
                    _pd.show();
                }
            });
        }
    }

    @Override
    protected Integer doInBackground(Void... params) {
        int created = 0;
        for (Item item : _items) {
            if (isCancelled())
                break;
            long now = new CoordinatedTime().getMilliseconds();
            String trackTitle = item.trackTitle;
            if (FileSystemUtils.isEmpty(trackTitle)) {
                String sDate = KMLUtil.KMLDateTimeFormatter.get().format(
                        new Date(now)).replace(':', '-');
                trackTitle = _titlePrefix + sDate;
            }
            CrumbDatabase.instance().createSegment(now, item.trackColor,
                    trackTitle, BreadcrumbReceiver.DEFAULT_LINE_STYLE,
                    item.callsign, item.uid, item.stitch);
            created++;
            publishProgress(created);
        }
        return created;
    }

    @Override
    protected void onProgressUpdate(Integer... v) {
        final int prog = v[0];
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                if (_pd != null && _pd.isShowing()) {
                    if (prog == -1)
                        _pd.dismiss();
                    else
                        _pd.setProgress(prog);
                }
            }
        });
    }

    @Override
    protected void onCancelled(Integer created) {
        onPostExecute(created);
    }

    @Override
    protected void onPostExecute(final Integer created) {
        onProgressUpdate(-1);
        if (created > 0 && _showProgress) {
            _mapView.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(_context, _context.getString(
                            R.string.create_tracks_toast, created),
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    public static class Item {

        private final String trackTitle, callsign, uid;
        private final boolean stitch;
        private final int trackColor;

        public Item(String trackTitle, int trackColor, String callsign,
                String uid, boolean stitch) {
            this.trackTitle = trackTitle;
            this.callsign = callsign;
            this.uid = uid;
            this.stitch = stitch;
            this.trackColor = trackColor;
        }

        public Item(String trackTitle, int trackColor, PointMapItem item) {
            this(trackTitle, trackColor,
                    ATAKUtilities.getDisplayName(item),
                    item.getUID(), false);
        }
    }
}
