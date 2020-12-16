
package com.atakmap.android.model.viewer;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

public class ModelGLSurfaceView extends GLSurfaceView {
    private static final String TAG = "ModelGLSurfaceView";

    private ModelRenderer modelRenderer;

    private float previousXp1;
    private float previousYp1;
    private float previousXp2;
    private float previousYp2;
    private float density;

    private final ScaleGestureDetector zoomDetector;

    public ModelGLSurfaceView(Context context) {
        super(context);
        zoomDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    public ModelGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        zoomDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    @Override
    public synchronized boolean onTouchEvent(MotionEvent event) {
        float x1 = event.getX(0);
        float y1 = event.getY(0);
        float x2 = x1;
        float y2 = y1;
        if (event.getPointerCount() >= 2) {
            x2 = event.getX(1);
            y2 = event.getY(1);
        }

        // reset the previous pointer position(s) on down events or if one of the secondary pointers goes up
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            previousXp1 = x1;
            previousYp1 = y1;
            previousXp2 = x2;
            previousYp2 = y2;
        } else if (event.getAction() == MotionEvent.ACTION_POINTER_UP) {
            if (event.getActionIndex() == 0) {
                // pointer 1 up
                x1 = x2;
                y1 = y2;
            } else if (event.getPointerCount() > 2
                    && event.getActionIndex() == 1) {
                // pointer 2 up, but more than 2 pointers
                x2 = event.getX(2);
                y2 = event.getY(2);
            } else {
                // pointer 2 up
                x2 = x1;
                y2 = y1;
            }
            previousXp1 = x1;
            previousYp1 = y1;
            previousXp2 = x2;
            previousYp2 = y2;
        }

        if (modelRenderer != null) {
            zoomDetector.onTouchEvent(event);

            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                if (isRotateEvent(event)) {
                    float deltaX = (x1 - previousXp1) / density / 2f;
                    float deltaY = (y1 - previousYp1) / density / 2f;

                    modelRenderer.deltaX += deltaX;
                    modelRenderer.deltaY += deltaY;
                } else if (isPanEvent(event)) {
                    modelRenderer.getCamera().moveCameraXY(previousXp1 - x1,
                            previousYp1 - y1);
                }
            }
        }
        previousXp1 = x1;
        previousYp1 = y1;
        previousXp2 = x2;
        previousYp2 = y2;

        return true;
    }

    private boolean isMultiFingerScroll(MotionEvent event) {
        if (event.getPointerCount() < 2)
            return false;
        float x1 = event.getX(0);
        float y1 = event.getY(0);
        float x2 = x1;
        float y2 = y1;
        if (event.getPointerCount() >= 2) {
            x2 = event.getX(1);
            y2 = event.getY(1);
        }
        return ((((x1 - previousXp1) * (x2 - previousXp2)) > 0) ||
                (((y1 - previousYp1) * (y2 - previousYp2)) > 0));
    }

    private boolean isPanEvent(MotionEvent event) {
        return event.getPointerCount() == 1;
    }

    private boolean isRotateEvent(MotionEvent event) {
        return event.getPointerCount() >= 2 && isMultiFingerScroll(event);
    }

    //Use this overloaded method instead of super class method
    public void setRenderer(ModelRenderer renderer, float density) {
        this.modelRenderer = renderer;
        this.density = density;
        super.setRenderer(renderer);
    }

    private class ScaleListener
            extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            modelRenderer.getCamera()
                    .moveCameraZ(1f / detector.getScaleFactor());

            return true;
        }
    }
}
