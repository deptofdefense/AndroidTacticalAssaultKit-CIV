
package com.atakmap.android.image;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.opengl.GLSurfaceView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;

import com.atakmap.android.image.equirectangular.ImmersiveGLRenderer;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;

import com.atakmap.annotations.ModifierApi;
import com.atakmap.app.R;

public class SphereImageViewerDropDownReceiver extends DropDownReceiver
        implements
        OnStateListener, View.OnTouchListener {

    public static final String TAG = "SphereImageViewerDropDownReceiver";

    public static final String SHOW_IMAGE = "com.atakmap.android.image.SHOW_EQUIRECTANGULAR";
    private final View view;

    private GLSurfaceView glv;
    private ImmersiveGLRenderer glRenderer;
    private double currentRotationX, currentRotationY;
    private double lastx, lasty;

    private static final int DRAG_RESOLUTION = 50;
    private final PointF dragStart = new PointF();

    /**************************** CONSTRUCTOR *****************************/

    @ModifierApi(since = "4.5", target = "4.8", modifiers = {})
    public SphereImageViewerDropDownReceiver(final MapView mapView) {
        super(mapView);

        view = LayoutInflater.from(mapView.getContext()).inflate(
                R.layout.equirectangular_view, null);

        glRenderer = new ImmersiveGLRenderer();
        glv = view.findViewById(R.id.glView);
        glv.setEGLContextClientVersion(2);
        glv.setRenderer(glRenderer);
        glv.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        glv.setOnTouchListener(this);

    }

    /**************************** PUBLIC METHODS *****************************/

    public void disposeImpl() {
    }

    /**************************** INHERITED METHODS *****************************/

    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(SHOW_IMAGE)) {

            final String fileToLoad = intent.getStringExtra("filepath");

            // Load image
            BitmapFactory.Options op = new BitmapFactory.Options();
            op.inPreferredConfig = Bitmap.Config.ARGB_8888;
            final Bitmap sphereImage = BitmapFactory.decodeFile(fileToLoad, op);

            currentRotationX = currentRotationY = 0;
            lasty = lastx = 0;

            if (glRenderer.loadNewImage(sphereImage)) {
                glRenderer.setRotation(0, 0);
                glv.requestRender();
            }

            showDropDown(view, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, false, this);

        }
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
        if (v)
            glv.onResume();
        else
            glv.onPause();
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
        glv.onPause();
        glRenderer.dispose();
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: // first finger down only
                dragStart.set(event.getX(), event.getY());
                return false;
            case MotionEvent.ACTION_MOVE:
                effectRotation(
                        (event.getX(0) - dragStart.x) / (DRAG_RESOLUTION * 6),
                        (event.getY(0) - dragStart.y) / (DRAG_RESOLUTION * 6));
                break;
        }
        return true;
    }

    private void effectRotation(double x, double y) {
        double targetRotationX, targetRotationY;
        lastx = x + lastx;
        lasty = y + lasty;

        // 0.5 value here different than other render
        targetRotationX = (lasty - (0.5)) * 0.025;
        targetRotationY = (lastx - (0.5)) * 0.025;
        currentRotationX += (targetRotationX - currentRotationX) * 0.25;
        currentRotationY += (targetRotationY - currentRotationY) * 0.25;

        glRenderer.setRotation(currentRotationX, currentRotationY);
        glv.requestRender();
    }

}
