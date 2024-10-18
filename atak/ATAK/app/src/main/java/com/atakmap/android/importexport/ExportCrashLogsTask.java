
package com.atakmap.android.importexport;

import android.content.Context;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.importexport.http.ErrorLogsClient;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.spatial.kml.KMLUtil;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

/**
 * Task to export crash logs

 */
public class ExportCrashLogsTask extends AsyncTask<Void, Integer, Boolean>
        implements OnCancelListener {
    private static final String TAG = "ExportCrashLogsTask";

    private ProgressDialog _progressDialog;
    private final MapView _mapView;
    private final String _autoUploadLogServer;
    private File _logz;
    private String _error;
    private final String _callsign;
    private final ErrorLogsClient _errorLogsClient;
    private final Context _context;

    /**
     * Used to filter out debug logs so they are not uploaded, when debug uploads are disabled
     * Filters out non JSON files, with exception for native crash TXT file
     */
    public static final FilenameFilter DebuLogFileFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir,
                String filename) {
            filename = filename.toLowerCase(LocaleUtil.getCurrent());

            return !filename.endsWith("." + ResourceFile.MIMEType.FPKG.EXT)
                    && !filename.endsWith(".json")
                    && !filename.contains("ataknativecrash");
        }
    };

    public ExportCrashLogsTask(final MapView mapView,
            final String autoUploadLogServer) {
        _mapView = mapView;
        _context = mapView.getContext();
        _autoUploadLogServer = autoUploadLogServer;
        _logz = null;
        _error = null;
        _callsign = _mapView.getDeviceCallsign();
        _errorLogsClient = new ErrorLogsClient(_context);
    }

    @Override
    protected void onPreExecute() {
        // only show progress dialog if we're not auto-uploading logs
        if (_progressDialog == null && _autoUploadLogServer == null) {
            // Before running code in background/worker thread
            _progressDialog = new ProgressDialog(_context);
            _progressDialog.setIcon(
                    com.atakmap.android.util.ATAKConstants.getIconId());
            _progressDialog.setTitle(_context.getString(
                    R.string.exporting_logs));
            _progressDialog.setMessage(
                    _context.getString(
                            R.string.importmgr_compressing_logs));
            _progressDialog.setIndeterminate(false);
            _progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            _progressDialog.setCancelable(true);
            _progressDialog.setOnCancelListener(this);
            _progressDialog.show();
        }
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        // set the current progress of the progress dialog UI
        if (_progressDialog != null)
            _progressDialog.setProgress(progress[0]);
    }

    @Override
    protected Boolean doInBackground(Void... arg0) {
        final String NO_LOGS = _context.getString(
                R.string.importmgr_no_logs_to_export);
        Thread.currentThread().setName(TAG);
        Log.d(TAG, "Executing...");

        publishProgress(1);

        File exportDir = FileSystemUtils
                .getItem(FileSystemUtils.EXPORT_DIRECTORY);
        if (!IOProviderFactory.exists(exportDir)) {
            Log.d(TAG, "Creating export dir: " + exportDir.getAbsolutePath());
            if (!IOProviderFactory.mkdirs(exportDir)) {
                Log.d(TAG,
                        "Failed to create export dir at "
                                + exportDir.getAbsolutePath());
            }
        }

        File logsDir = FileSystemUtils
                .getItem(FileSystemUtils.SUPPORT_DIRECTORY + File.separatorChar
                        + "logs");
        if (!IOProviderFactory.isDirectory(logsDir)) {
            Log.d(TAG, "Logs directory does not exist: " + logsDir);
            _error = NO_LOGS;
            return false;
        }

        String[] files = IOProviderFactory.list(logsDir);
        if (files == null || files.length < 1) {
            Log.d(TAG, "No logs to export: " + logsDir.getAbsolutePath());
            _error = NO_LOGS;
            return true;
        }

        publishProgress(10);

        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(_context);
        FilenameFilter filter = prefs.getBoolean("loggingfile_upload_debug",
                false)
                        ? null
                        : DebuLogFileFilter;

        try {
            File dest = new File(exportDir, "logs_"
                    +
                    _callsign
                    + "_"
                    +
                    KMLUtil.KMLDateTimeFormatter.get().format(
                            CoordinatedTime.currentDate())
                            .replace(':', '-')
                    +
                    ".zip");
            File logz = FileSystemUtils.zipDirectory(
                    logsDir, dest, true, filter);
            if (!FileSystemUtils.isFile(logz)) {
                Log.w(TAG, "Failed to zip logs");
                _error = _context.getString(
                        R.string.importmgr_failed_to_compress_logs);
                return false;
            }
            publishProgress(90);
            _logz = logz;

            //now delete the logs that have been zipped
            FileSystemUtils.deleteDirectory(logsDir, filter);

            // now reset the logging to a file otherwise that will begin failing
            Intent i = new Intent("com.atakmap.app.ExportCrashLogsTask");
            AtakBroadcast.getInstance().sendBroadcast(i);

            publishProgress(99);
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Failed to compress logs " + logsDir.getAbsolutePath(),
                    e);
            _error = _context.getString(
                    R.string.importmgr_failed_to_compress_logs);
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

        Toast.makeText(_context, R.string.export_cancelled,
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onCancel(DialogInterface arg0) {
        // task was cancelled, e.g. user pressed back
        Log.d(TAG, "cancelling the export crash log task");

        // user pushed back button or we hit an error, cancel the task, and notify UI thread via
        // onCancelled()
        cancel(true);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        final String NO_LOGS = _context.getString(
                R.string.importmgr_no_logs_to_export);
        // work to be performed by UI thread after work is complete
        Log.d(TAG, "onPostExecute");

        // close the progress dialog
        if (_progressDialog != null) {
            _progressDialog.dismiss();
            _progressDialog = null;
        }

        if (result && _logz != null) {

            if (_autoUploadLogServer != null) {
                _errorLogsClient.sendLogsToServer(
                        _logz, _autoUploadLogServer, true,
                        _mapView.getSelfMarker().getUID(),
                        _mapView.getDeviceCallsign());
            } else {

                final CharSequence[] items = {
                        _context.getString(
                                R.string.MARTI_sync_server),
                        _context.getString(R.string.choose_app)
                };

                new AlertDialog.Builder(_context)
                        .setTitle(R.string.importmgr_send_compressed_logs)
                        .setIcon(R.drawable.send_square)
                        .setItems(items, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int item) {
                                switch (item) {
                                    case 0: {//TAK Server
                                        dialog.dismiss();

                                        Intent intent = new Intent(
                                                ImportExportMapComponent.SET_EXPORT_LOG_SERVER);
                                        intent.putExtra("logFile",
                                                _logz.getAbsolutePath());
                                        AtakBroadcast.getInstance()
                                                .sendBroadcast(intent);
                                        break;
                                    }
                                    case 1: {//Choose app
                                        ExportFileMarshal.send3rdParty(
                                                _context,
                                                "Logs", "application/zip",
                                                _logz);
                                        break;
                                    }
                                }
                            }
                        })
                        .setNegativeButton(R.string.cancel, null).show();
            }
        } else {
            //if auto uploading, silent if nothing to upload
            if (!FileSystemUtils.isEmpty(_autoUploadLogServer)
                    && NO_LOGS.equals(_error)) {
                Log.d(TAG, _error);
                return;
            }

            NotificationUtil.getInstance()
                    .postNotification(
                            R.drawable.ic_network_error_notification_icon,
                            NotificationUtil.RED,
                            _context.getString(
                                    R.string.log_export_failed),
                            FileSystemUtils.isEmpty(_error)
                                    ? _context.getString(
                                            R.string.failed_to_export_logs)
                                    : _error,
                            FileSystemUtils.isEmpty(_error)
                                    ? _context.getString(
                                            R.string.failed_to_export_logs)
                                    : _error);
        }
    }
}
