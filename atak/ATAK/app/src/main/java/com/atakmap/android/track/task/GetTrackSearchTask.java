
package com.atakmap.android.track.task;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.widget.Toast;

import com.atakmap.android.track.maps.TrackPolyline;
import com.atakmap.android.track.crumb.CrumbDatabase;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.kml.KMLUtil;

import java.util.List;

/**
 * Simple background task to query time range from DB and perform some analysis
 * Query based on a time range
 *
 * 
 */
public class GetTrackSearchTask extends AsyncTask<Void, Void, Boolean>
        implements TrackProgress, DialogInterface.OnCancelListener {

    private static final String TAG = "GetTrackSearchTask";

    private final Callback callback;
    private ProgressDialog _progressDialog;
    private final Context _context;
    private final String _uid;
    private final long _startTime;
    private final long _endTime;
    private boolean _cancelled = false;

    /**
     * Queried tracks
     */
    private List<TrackPolyline> tracks;

    public GetTrackSearchTask(Context context, String callsign, String uid,
            long startTime, long endTime, Callback cb) {
        this._context = context;
        this._uid = uid;
        this._startTime = startTime;
        this._endTime = endTime;
        this.callback = cb;
    }

    @Override
    protected void onPreExecute() {
        // Before running code in background/worker thread
        _progressDialog = new ProgressDialog(_context);
        _progressDialog.setTitle(_context
                .getString(R.string.searching_without_space));
        _progressDialog.setIcon(R.drawable.ic_track_search);
        _progressDialog
                .setMessage(_context
                        .getString(R.string.searching_detailed_tracks));
        _progressDialog.setIndeterminate(true);
        _progressDialog.setCancelable(true);
        _progressDialog.show();
        _progressDialog.setOnCancelListener(this);
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        Thread.currentThread().setName("GetTrackSearchTask");
        Log.d(TAG, "Executing GetTrackSearchTask: " + _uid +
                " ["
                + KMLUtil.KMLDateTimeFormatter.get().format(_startTime)
                + ", " +
                KMLUtil.KMLDateTimeFormatter.get().format(_endTime) + "]");

        //TODO any threading issues with DB access? this done from an async task
        CrumbDatabase db = CrumbDatabase.instance();
        tracks = db.getTracks(_uid, _startTime, _endTime, this);
        if (FileSystemUtils.isEmpty(tracks)) {
            Log.w(TAG,
                    "Cannot create tracks w/out tracks for uid: " + _uid);
            return false;
        }

        return true;
    }

    @Override
    public void onProgress(int progress) {
    }

    @Override
    public boolean cancelled() {
        return _cancelled;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        _cancelled = true;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        if (!result) {
            if (cancelled()) {
                Toast.makeText(_context,
                        _context.getString(R.string.search)
                                + _context.getString(R.string.cancelled),
                        Toast.LENGTH_LONG).show();
            } else
                Toast.makeText(_context, R.string.no_logged_points,
                        Toast.LENGTH_LONG).show();
        } else {
            //callback with results
            if (callback != null) {
                callback.onComplete(tracks);
            }
        }

        if (_progressDialog != null) {
            _progressDialog.dismiss();
            _progressDialog = null;
        }
    }

    public interface Callback {
        void onComplete(List<TrackPolyline> tracks);
    }
}
