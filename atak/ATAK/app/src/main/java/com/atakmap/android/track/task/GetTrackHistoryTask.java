
package com.atakmap.android.track.task;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;

import com.atakmap.android.track.maps.TrackPolyline;
import com.atakmap.android.track.crumb.CrumbDatabase;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.List;

/**
 * Simple background task to query track history from DB
 * Query by user UID, or by list of tracks
 *
 * 
 */
public class GetTrackHistoryTask extends AsyncTask<Void, Integer, Void>
        implements TrackProgress, DialogInterface.OnCancelListener {

    private static final String TAG = "GetTrackHistoryTask";

    private final String uid;
    private final Callback callback;
    private final int[] _trackIds;
    private ProgressDialog _progressDialog;
    private final Context _context;
    private final boolean _bHideTemp;
    private final boolean _bDisplayAll;
    private boolean _cancelled = false;

    /**
     * List of queried tracks
     */
    private List<TrackPolyline> tracks;

    public GetTrackHistoryTask(Context context, String uid,
            final boolean bHideTemp, final boolean bDisplayAll,
            Callback cb) {
        this.uid = uid;
        this.callback = cb;
        this._context = context;
        this._bHideTemp = bHideTemp;
        this._bDisplayAll = bDisplayAll;
        this._trackIds = null;
    }

    public GetTrackHistoryTask(Context context, String uid,
            final int[] trackIds, Callback cb) {
        this.uid = uid;
        this.callback = cb;
        this._context = context;
        this._bHideTemp = true;
        this._bDisplayAll = false;
        this._trackIds = trackIds;
    }

    @Override
    protected void onPreExecute() {
        // Before running code in background/worker thread
        _progressDialog = new ProgressDialog(_context);
        _progressDialog.setTitle(_context
                .getString(R.string.searching_without_space));
        _progressDialog.setIcon(R.drawable.ic_track_search);
        _progressDialog
                .setMessage(_context.getString(R.string.loading_tracks));
        _progressDialog.setIndeterminate(false);
        _progressDialog.setCancelable(true);
        _progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        _progressDialog.show();
        _progressDialog.setOnCancelListener(this);
    }

    @Override
    protected Void doInBackground(Void... params) {
        Thread.currentThread().setName("GetTrackHistoryTask");

        CrumbDatabase db = CrumbDatabase.instance();
        if (db == null) {
            Log.w(TAG, "Crumb DB not available, cannot get track history");
            return null;
        }

        if (_trackIds != null && _trackIds.length > 0) {
            Log.d(TAG, "Executing GetTrackHistoryTask track Ids: " + uid);
            tracks = db.getTracks(_trackIds, this);
        } else {
            Log.d(TAG, "Executing GetTrackHistoryTask by UID: " + uid);
            tracks = db.getTracks(uid, _bHideTemp, _bDisplayAll, this);
        }

        onProgress(99);
        return null;
    }

    @Override
    public void onProgress(int p) {
        publishProgress(p);
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        if (_progressDialog != null && values != null
                && values.length > 0) {
            _progressDialog.setProgress(values[0]);
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        _cancelled = true;
    }

    @Override
    public boolean cancelled() {
        return _cancelled;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        if (callback != null) {
            callback.onComplete(uid, tracks);
        }

        if (_progressDialog != null) {
            _progressDialog.dismiss();
            _progressDialog = null;
        }
    }

    public interface Callback {
        void onComplete(String uid, List<TrackPolyline> tracks);
    }
}
