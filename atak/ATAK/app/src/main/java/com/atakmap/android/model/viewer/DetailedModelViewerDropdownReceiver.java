
package com.atakmap.android.model.viewer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import android.widget.Toast;
import android.view.View;

import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.model.viewer.processing.LoadModelAsyncTask;
import com.atakmap.map.layer.model.Model;

public final class DetailedModelViewerDropdownReceiver extends DropDownReceiver
        implements OnStateListener,
        LoadModelAsyncTask.Listener {
    public static final String SHOW_3D_VIEW = "com.l3t.visor.plugin.SHOW_3D_VIEW";
    public static final String EXTRAS_URI = "com.l3t.visor.plugin.3D_VIEW_URI";

    private double currWidth = HALF_WIDTH;
    private double currHeight = HALF_HEIGHT;

    private static final String TAG = "DetailedModelViewerDropdownReceiver";
    private static final int OPENGL_VERSION = 2;

    private final Context context;

    private final ModelRenderer modelRenderer;
    private final ModelGLSurfaceView modelGLSurfaceView;

    public DetailedModelViewerDropdownReceiver(final MapView mapView,
            final Context context) {
        super(mapView);

        this.context = context;
        this.modelRenderer = new ModelRenderer(context);
        this.modelGLSurfaceView = new ModelGLSurfaceView(context);

        float displayDensity = mapView.getResources()
                .getDisplayMetrics().density;

        modelGLSurfaceView.setEGLContextClientVersion(OPENGL_VERSION);
        // TODO: Create ModelRenderer in modelGLSurfaceView constructor and get density there too
        modelGLSurfaceView.setRenderer(modelRenderer, displayDensity);
    }

    @Override
    public void disposeImpl() {
        Log.d(TAG, "disposeImpl called");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Showing plugin 3D view drop down");
        if (SHOW_3D_VIEW.equals(intent.getAction())) {
            if (!intent.hasExtra(EXTRAS_URI)) {
                Toast.makeText(context, R.string.uri_missing, Toast.LENGTH_LONG)
                        .show();
                return;
            }

            Uri uri = Uri.parse(intent.getStringExtra(EXTRAS_URI));
            String path = uri.getPath();
            if (path == null)
                return;

            loadModel(uri);
        }
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (v)
            modelGLSurfaceView.setVisibility(View.VISIBLE);
        else
            modelGLSurfaceView.setVisibility(View.GONE);

    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
        Log.d(TAG, "resizing width=" + width + " height=" + height);
        currWidth = width;
        currHeight = height;
    }

    @Override
    public void onDropDownClose() {
    }

    @Override
    protected void onStateRequested(int state) {
        if (state == DROPDOWN_STATE_FULLSCREEN) {
            if (!isPortrait()) {
                if (Double.compare(currWidth, HALF_WIDTH) == 0) {
                    resize(FULL_WIDTH - HANDLE_THICKNESS_LANDSCAPE,
                            FULL_HEIGHT);
                }
            } else {
                if (Double.compare(currHeight, HALF_HEIGHT) == 0) {
                    resize(FULL_WIDTH, FULL_HEIGHT - HANDLE_THICKNESS_PORTRAIT);
                }
            }
        } else if (state == DROPDOWN_STATE_NORMAL) {
            if (!isPortrait()) {
                resize(HALF_WIDTH, FULL_HEIGHT);
            } else {
                resize(FULL_WIDTH, HALF_HEIGHT);
            }
        }
    }

    @Override
    public void onLoadModelSuccess(String uri, @NonNull Model model) {
        // TODO: Memory management of recreated views, renderers, onPause, onResume...
        modelRenderer.setModel(model);
        showDropDown(modelGLSurfaceView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                HALF_HEIGHT, false, this);
    }

    @Override
    public void onLoadModelFailure(String uri, String reason) {
        Log.d(TAG, "Failed to view model " + uri + " - " + reason);
        Toast.makeText(context, reason, Toast.LENGTH_LONG).show();
    }

    private void loadModel(Uri uri) {
        Log.d(TAG, "Model being loaded");
        new LoadModelAsyncTask(context, this, uri)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

}
