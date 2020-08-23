
package com.atakmap.android.model.viewer.processing;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import android.widget.Toast;

import com.atakmap.android.model.viewer.io.FileStorageService;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelFactory;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.ModelInfoFactory;
import com.atakmap.map.layer.model.ModelSpi;
import com.atakmap.util.Collections2;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.Set;

/**
 * Async task to load a model in a background thread
 */
public final class LoadModelAsyncTask extends AsyncTask<Void, Integer, Model>
        implements ModelSpi.Callback, DialogInterface.OnClickListener {

    private static final String TAG = "LoadModelAsyncTask";

    private final WeakReference<Context> contextRef;
    private final Listener listener;
    private final Uri uri;
    private final ProgressDialog progressDialog;

    private long startTime;
    private String failReason;

    /**
     * Interface for callback invocations
     */
    public interface Listener {
        /**
         * Invoked if the model was loaded successfully
         *
         * @param uri Model URI
         * @param model the model; never {@code null}
         */
        void onLoadModelSuccess(String uri, @NonNull Model model);

        /**
         * Invoked if loading the model failed
         *
         * @param uri Model URI
         * @param reason Reason for failure
         */
        void onLoadModelFailure(String uri, String reason);
    }

    /**
     * Creates a new instance of this class
     *
     * @param context the context to display the progress dialog in; may not be {@code null}
     * @param listener listener whose methods will be invoked when events occur; may not be
     *                 {@code null}
     * @throws NullPointerException if {@code context}, {@code listener}, or {@code uri} are
     *                              {@code null}
     */
    public LoadModelAsyncTask(@NonNull Context context,
            @NonNull Listener listener, @NonNull Uri uri) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(listener, "listener must not be null");
        Objects.requireNonNull(uri, "uri must not be null");

        contextRef = new WeakReference<>(context);
        this.listener = listener;
        this.uri = uri;
        progressDialog = new ProgressDialog(context);
        progressDialog.setTitle(R.string.loading_model);
        progressDialog.setMessage(uri.getLastPathSegment());
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        // Require the user tap the "Cancel" button to cancel the task
        progressDialog.setCancelable(false);
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                context.getString(R.string.cancel), this);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        progressDialog.show();
    }

    @Override
    @Nullable
    protected Model doInBackground(Void... voids) {
        startTime = SystemClock.elapsedRealtime();

        String path;
        try {
            File file = FileStorageService.getFile(uri);
            if (file == null)
                throw new Exception("URI is invalid or missing");
            path = file.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get file for URI: " + uri);
            this.failReason = "Invalid file path";
            return null;
        }

        Set<ModelInfo> info = ModelInfoFactory.create(path);
        if (info == null || info.isEmpty()) {
            this.failReason = "File is not recognized as a valid model";
            return null;
        }
        return ModelFactory.create(Collections2.first(info), null, this);
    }

    @Override
    protected void onProgressUpdate(final Integer... values) {
        progressDialog.setProgress(values[0]);
    }

    @Override
    protected void onPostExecute(@Nullable Model model) {
        progressDialog.dismiss();

        if (model != null) {
            float duration = (float) (SystemClock.elapsedRealtime() - startTime)
                    / 1000f;
            Toast.makeText(contextRef.get(),
                    "Loaded in " + duration + " seconds",
                    Toast.LENGTH_LONG).show();
            listener.onLoadModelSuccess(uri.toString(), model);
        } else {
            listener.onLoadModelFailure(uri.toString(), failReason);
        }
    }

    @Override
    public void onClick(DialogInterface d, int which) {
        cancel(false);
    }

    @Override
    public boolean isCanceled() {
        return isCancelled();
    }

    @Override
    public boolean isProbeOnly() {
        return false;
    }

    @Override
    public int getProbeLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void setProbeMatch(boolean match) {
    }

    @Override
    public void errorOccurred(final String msg, Throwable t) {
        Log.e(TAG, "Failed to read model " + uri + " - " + msg, t);
    }

    @Override
    public void progress(int progress) {
        publishProgress(progress);
    }
}
