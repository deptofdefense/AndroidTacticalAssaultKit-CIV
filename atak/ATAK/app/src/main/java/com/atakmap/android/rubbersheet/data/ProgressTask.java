
package com.atakmap.android.rubbersheet.data;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;

public abstract class ProgressTask extends AsyncTask<Void, Integer, Object>
        implements DialogInterface.OnClickListener {

    protected final MapView _mapView;
    protected final Context _context;
    private final ProgressDialog _pd;
    private int _lastProgress = 0;

    protected ProgressTask(MapView mapView) {
        _mapView = mapView;
        _context = mapView.getContext();
        _pd = new ProgressDialog(_context);
    }

    protected abstract String getProgressMessage();

    protected abstract int getProgressStages();

    @Override
    protected void onPreExecute() {
        _pd.setMessage(getProgressMessage());
        if (getProgressStages() > 0) {
            _pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            _pd.setMax(100);
        }
        // Require the user tap the "Cancel" button to cancel the task
        _pd.setCancelable(false);
        _pd.setButton(DialogInterface.BUTTON_NEGATIVE,
                _context.getString(R.string.cancel), this);
        _pd.show();
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        _pd.setProgress(values[0]);
    }

    @Override
    protected void onPostExecute(Object ret) {
        _pd.dismiss();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_NEGATIVE) {
            _pd.dismiss();
            cancel(false);
        }
    }

    protected void toast(final int strId, final Object... args) {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(_context, _context.getString(strId, args),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    protected void setProgressMessage(final int strId, final Object... args) {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                _pd.setMessage(_context.getString(strId, args));
            }
        });
    }

    protected void progress(int stage, int prog, int max) {
        float stageProg = (float) prog / max;
        float progPerStage = 1f / Math.max(getProgressStages(), 1f);
        int progRounded = Math.round(100 * ((progPerStage * (stage - 1))
                + (stageProg * progPerStage)));
        if (_lastProgress != progRounded)
            publishProgress(_lastProgress = progRounded);
    }

    protected boolean progress(int prog, int max) {
        int progRounded = Math.round(100 * ((float) prog / max));
        if (_lastProgress != progRounded)
            publishProgress(_lastProgress = progRounded);
        return !isCancelled();
    }
}
