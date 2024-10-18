
package com.atakmap.android.importexport;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.AsyncTask;
import android.widget.Toast;

import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.io.IOException;
import java.util.List;

/**
 * Task to export data
 * 
 * 
 */
public class ExportMarshalTask extends AsyncTask<Void, Integer, Boolean>
        implements OnCancelListener, ExportMarshal.Progress {
    private static final String TAG = "ExportMarshalTask";

    private ProgressDialog _progressDialog;
    private final Context _context;
    private final ExportMarshal _marshal;
    private final List<Exportable> _exports;

    private final boolean _displayProgress;

    public ExportMarshalTask(Context context, ExportMarshal marshal,
            List<Exportable> exports, boolean displayProgress) {
        _context = context;
        _marshal = marshal;
        _exports = exports;
        _displayProgress = displayProgress;
    }

    @Override
    protected void onPreExecute() {
        if (_displayProgress && _progressDialog == null) {
            // Before running code in background/worker thread
            _progressDialog = new ProgressDialog(_context);
            _progressDialog.setIcon(_marshal.getIconId());
            _progressDialog.setTitle(_context
                    .getString(R.string.exporting_data));
            _progressDialog.setMessage(String.format(
                    _context.getString(R.string.importmgr_generating),
                    _marshal.getContentType()));
            _progressDialog.setIndeterminate(false);
            _progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            _progressDialog.setCancelable(true);
            _progressDialog.setOnCancelListener(this);
            _progressDialog.show();
        }
    }

    @Override
    public void publish(int progress) {
        publishProgress(progress);
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        // set the current progress of the progress dialog UI
        if (_displayProgress && _progressDialog != null)
            _progressDialog.setProgress(progress[0]);
    }

    @Override
    protected Boolean doInBackground(Void... arg0) {
        Thread.currentThread().setName(TAG);
        Log.d(TAG, "Executing...");

        //setup progress reporting, currently assume 90% to marshal
        //10% to finalizeMarshal
        _marshal.setProgress(this);
        publishProgress(1);

        try {
            if (!_marshal.marshal(_exports)) {
                throw new IOException("No items were exported");
            }
            publishProgress(91);
            _marshal.finalizeMarshal();
            publishProgress(99);
            return true;
        } catch (IOException | FormatNotSupportedException e) {
            Log.w(TAG, "Failed to finalize " + _marshal, e);
            return false;
        }
    }

    @Override
    protected void onCancelled(Boolean result) {
        // UI thread being notified that task was cancelled
        Log.d(TAG, "onCancelled");

        if (_progressDialog != null) {
            _progressDialog.dismiss();
            _progressDialog = null;
        }

        Toast.makeText(_context, R.string.export_cancelled, Toast.LENGTH_LONG)
                .show();
    }

    @Override
    public void onCancel(DialogInterface arg0) {
        // task was cancelled, e.g. user pressed back
        Log.d(TAG, "onCancel");

        _marshal.cancelMarshal();

        // user pushed back button or we hit an error, cancel the task, and notify UI thread via
        // onCancelled()
        cancel(true);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        // work to be performed by UI thread after work is complete
        Log.d(TAG, "onPostExecute");

        // close the progress dialog
        if (_progressDialog != null) {
            _progressDialog.dismiss();
            _progressDialog = null;
        }

        if (result) {
            _marshal.postMarshal();
        } else {
            NotificationUtil
                    .getInstance()
                    .postNotification(
                            R.drawable.ic_network_error_notification_icon,
                            NotificationUtil.RED,
                            String.format(
                                    _context.getString(
                                            R.string.importmgr_export_failed),
                                    _marshal.getContentType()),
                            String.format(
                                    _context.getString(
                                            R.string.importmgr_failed_to_export),
                                    _marshal.getContentType()),
                            String.format(
                                    _context.getString(
                                            R.string.importmgr_failed_to_export),
                                    _marshal.getContentType()));
        }

        _exports.clear();
    }
}
