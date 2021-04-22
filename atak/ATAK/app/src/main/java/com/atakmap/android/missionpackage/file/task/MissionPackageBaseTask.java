
package com.atakmap.android.missionpackage.file.task;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.AsyncTask;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.file.MissionPackageBuilder;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

/**
 * Common async Mission Package related tasks
 * 
 * 
 */
public abstract class MissionPackageBaseTask extends
        AsyncTask<Void, MissionPackageBaseTask.ProgressDialogUpdate, Boolean>
        implements
        OnCancelListener, MissionPackageBuilder.Progress {
    private static final String TAG = "MissionPackageBaseTask";

    /**
     * Container with progress update
     */
    public static class ProgressDialogUpdate {

        final String message;
        final int progress;
        final int max;

        public ProgressDialogUpdate(int progress, int max, String msg) {
            this.progress = progress;
            this.max = max;
            this.message = msg;
        }

        public ProgressDialogUpdate(int progress, String msg) {
            this(progress, 100, msg);
        }
    }

    /**
     * Callback interface for when a task completes
     * 
     * 
     */
    public interface Callback {
        void onMissionPackageTaskComplete(MissionPackageBaseTask task,
                boolean success);
    }

    protected ProgressDialog _progressDialog;
    protected final MissionPackageReceiver _receiver;
    protected MissionPackageManifest _manifest;
    protected String _cancelReason;
    private final boolean _bShowProgress;
    protected final Callback _callback;

    public MissionPackageBaseTask(MissionPackageManifest manifest,
            MissionPackageReceiver receiver,
            boolean bShowProgress, Callback callback) {
        _manifest = manifest;
        _receiver = receiver;
        _bShowProgress = bShowProgress;
        _callback = callback;
        _cancelReason = null;
    }

    public abstract String getProgressDialogMessage();

    public void setProgressDialog(ProgressDialog progressDialog) {
        _progressDialog = progressDialog;
    }

    public MissionPackageManifest getManifest() {
        return _manifest;
    }

    @Override
    protected void onPreExecute() {
        if (!_bShowProgress)
            return;
        _receiver.getMapView().post(new Runnable() {
            @Override
            public void run() {
                if (_progressDialog == null) {
                    // Before running code in background/worker thread
                    Context ctx = _receiver.getMapView().getContext();
                    _progressDialog = new ProgressDialog(ctx);
                    _progressDialog.setTitle(getContext().getString(
                            R.string.processing_mission_package));
                    _progressDialog.setMessage(getProgressDialogMessage());
                    _progressDialog.setIndeterminate(false);
                    _progressDialog.setCancelable(false);
                    _progressDialog
                            .setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    _progressDialog
                            .setOnCancelListener(MissionPackageBaseTask.this);
                    _progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                            ctx.getText(R.string.cancel),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    _progressDialog.cancel();
                                }
                            });
                    _progressDialog.show();
                } else {
                    // update title as progress dialog may have been passed off from another aysnc task
                    _progressDialog.setMessage(getProgressDialogMessage());
                }
            }
        });
    }

    @Override
    protected void onProgressUpdate(ProgressDialogUpdate... progress) {
        // set the current progress of the progress dialog UI
        if (_progressDialog != null && progress != null) {
            _progressDialog.setProgress(progress[0].progress);
            _progressDialog.setMax(progress[0].max);
            if (!FileSystemUtils.isEmpty(progress[0].message))
                _progressDialog.setMessage(progress[0].message);
        }
    }

    @Override
    protected void onCancelled() {
        // UI thread being notified that task was cancelled
        Log.d(TAG, "onCancelled: " + _cancelReason);

        AlertDialog.Builder b = new AlertDialog.Builder(_receiver
                .getMapView().getContext());
        b.setTitle(R.string.failed_to_process_mission_package);
        if (!FileSystemUtils.isEmpty(_cancelReason))
            b.setMessage(_cancelReason);
        else
            b.setMessage(R.string.mission_package_task_cancelled);
        b.setPositiveButton(R.string.ok, null);
        b.show();

        if (_callback != null)
            _callback.onMissionPackageTaskComplete(this, false);
    }

    @Override
    public void onCancel(DialogInterface arg0) {
        // task was cancelled, e.g. user pressed back on compression progress dialog
        // Also come in here if creation of .zip fails
        Log.d(TAG, "onCancel");

        // user pushed back button or we hit an error, cancel the task, and notify UI thread via
        // onCancelled()
        cancel(true);

        dismissProgressDialog();
    }

    @Override
    public void publish(int progress) {
        publishProgress(new ProgressDialogUpdate(progress, null));
    }

    @Override
    public void cancel(String reason) {
        if (!FileSystemUtils.isEmpty(reason)) {
            _cancelReason = reason;
            Log.e(TAG, "Cancelling due to: " + reason);
        }
        onCancel(_progressDialog);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " - "
                + (_manifest == null ? "" : _manifest.toString());
    }

    public void dismissProgressDialog() {
        if (_progressDialog != null) {
            _progressDialog.dismiss();
            _progressDialog = null;
        }
    }

    public Context getContext() {
        if (_receiver == null)
            return null;

        return _receiver.getMapView().getContext();
    }

    public MapView getMapView() {
        if (_receiver == null)
            return null;

        return _receiver.getMapView();
    }
}
