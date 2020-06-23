
package com.atakmap.android.track.task;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.track.crumb.CrumbDatabase;
import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Async task for deleting one or several tracks
 */
public class DeleteTracksTask extends AsyncTask<Void, Integer, Integer> {

    private final Context _context;
    private final List<Integer> _trackIds = new ArrayList<>();
    private final boolean _showProgress;
    private final ProgressDialog _pd;

    public DeleteTracksTask(MapView mapView, List<Integer> trackIds,
            boolean showProgress) {
        _context = mapView.getContext();
        if (trackIds != null)
            _trackIds.addAll(trackIds);
        _showProgress = showProgress;
        _pd = new ProgressDialog(_context);
    }

    public DeleteTracksTask(MapView mapView, int trackId,
            boolean showProgress) {
        this(mapView, null, showProgress);
        _trackIds.add(trackId);
    }

    @Override
    protected void onPreExecute() {
        if (_showProgress) {
            _pd.setMessage(_context.getString(R.string.delete_tracks_progress));
            _pd.setProgress(0);
            _pd.setMax(_trackIds.size());
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
    }

    @Override
    protected Integer doInBackground(Void... params) {
        int deleted = 0;
        for (int trackId : _trackIds) {
            if (isCancelled())
                break;
            CrumbDatabase.instance().deleteSegment(trackId);
            deleted++;
            publishProgress(deleted);
        }
        return deleted;
    }

    @Override
    protected void onProgressUpdate(Integer... v) {
        if (_pd.isShowing())
            _pd.setProgress(v[0]);
    }

    @Override
    protected void onCancelled(Integer deleted) {
        onPostExecute(deleted);
    }

    @Override
    protected void onPostExecute(Integer deleted) {
        dismiss();
        if (deleted > 0 && _showProgress) {
            Toast.makeText(_context, _context.getString(
                    R.string.delete_tracks_toast, deleted),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void dismiss() {
        if (_pd.isShowing())
            _pd.dismiss();
    }
}
