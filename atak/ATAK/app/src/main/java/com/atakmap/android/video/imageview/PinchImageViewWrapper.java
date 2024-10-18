
package com.atakmap.android.video.imageview;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.View.OnTouchListener;
import android.widget.ImageView;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;

/**
 * Derived from from:
 * http://stackoverflow.com/questions/6650398/android-imageview-zoom-in-and-zoom-out
 */

public class PinchImageViewWrapper implements OnTouchListener,
        OnLayoutChangeListener {

    private static final String TAG = "PinchImageViewWrapper";

    // These matrices will be used to scale points of the image
    private final Matrix matrix = new Matrix();
    private final Matrix savedMatrix = new Matrix();

    // The 3 states (events) which the user is trying to perform
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private int mode = NONE;

    // these PointF objects are used to record the point(s) the user is touching
    private final PointF start = new PointF();
    private final PointF mid = new PointF();
    private float oldDist = 1f;
    private float scale = 1f, minScale = 1f, maxScale = 1f;

    private final ImageView iv;
    private final SingleTapListener singleTapListener;

    /**
     * 8/5/2017 - Used to decouple the single tap from the fmv Component.
     */
    public interface SingleTapListener {
        void onSingleTap();
    }

    private boolean isViewContains(View view, int rx, int ry) {
        int[] l = new int[2];
        view.getLocationOnScreen(l);
        int x = l[0];
        int y = l[1];
        int w = view.getWidth();
        int h = view.getHeight();

        return !(rx < x || rx > x + w || ry < y || ry > y + h);
    }

    /**
     * Construct a PinchImageViewWrapper with the appropriate ImageView and an 
     * optional single tap listener used currently by the FmvComponent to hide 
     * controls.
     * @param iv the image view to wrap
     *           ***IMPORTANT*** - ImageView must have the following setting enabled in xml file
     *           android:longClickable="true"
     * @param stl the single tap listener to use during a single tap event
     */
    public PinchImageViewWrapper(final ImageView iv, SingleTapListener stl) {

        this.iv = iv;
        this.singleTapListener = stl;

        iv.setOnTouchListener(this);

        iv.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top,
                    int right, int bottom,
                    int oldLeft, int oldTop,
                    int oldRight, int oldBottom) {
                // its possible that the layout is not complete in which case
                // we will get all zero values for the positions, so ignore the event

                if (left == 0 && top == 0 && right == 0 && bottom == 0) {
                    return;
                }
                if (left == oldLeft &&
                        top == oldTop &&
                        bottom == oldBottom &&
                        right == oldRight) {
                    return;

                }
                PinchImageViewWrapper.this.reset();
            }
        });

    }

    public void reset() {
        int screen_height = iv.getMeasuredHeight();// height of imageView
        int screen_width = iv.getMeasuredWidth();// width of imageView
        if (iv.getDrawable() == null)
            return;
        int image_height = iv.getDrawable().getIntrinsicHeight();// original height of underlying
                                                                 // image
        int image_width = iv.getDrawable().getIntrinsicWidth();// original width of underlying image

        RectF drawableRect = new RectF(0, 0, image_width, image_height);
        RectF viewRect = new RectF(0, 0, screen_width, screen_height);
        matrix.reset();
        matrix.setRectToRect(drawableRect, viewRect, Matrix.ScaleToFit.CENTER);

        float[] matV = new float[9];
        matrix.getValues(matV);

        scale = matV[0];
        minScale = scale * 0.75f;
        maxScale = scale * 10f;

        savedMatrix.set(matrix);

        MapView mv = MapView.getMapView();
        if (mv == null)
            return;
        mv.post(new Runnable() {
            @Override
            public void run() {
                iv.setImageMatrix(matrix);
            }
        });
    }

    @Override
    public void onLayoutChange(View v, int left, int top,
            int right, int bottom,
            int oldLeft, int oldTop,
            int oldRight, int oldBottom) {
        // its possible that the layout is not complete in which case
        // we will get all zero values for the positions, so ignore the event

        if (left == 0 && top == 0 && right == 0 && bottom == 0) {
            return;
        }
        if (left == oldLeft &&
                top == oldTop &&
                bottom == oldBottom &&
                right == oldRight) {
            return;

        }
        reset();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        // cast exception occurs if v is not an imageview
        if (!(v instanceof ImageView))
            return false;

        ImageView view = (ImageView) v;

        v.cancelLongPress();

        view.setScaleType(ImageView.ScaleType.MATRIX);

        // dumpEvent(event);

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: // first finger down only
                savedMatrix.set(matrix);
                start.set(event.getX(), event.getY());
                mode = NONE;
                // do not handle this event, just let it fall through
                // begin treating as a long press.
                return false;
            case MotionEvent.ACTION_UP: // first finger lifted
            case MotionEvent.ACTION_POINTER_UP: // second finger lifted
                if (mode == NONE) {
                    Log.d("Video", "Fire tap event");
                    if (singleTapListener != null)
                        singleTapListener.onSingleTap();
                    return false;
                } else if (mode == ZOOM) {
                    savedMatrix.set(matrix);
                    int up = event.getActionIndex();
                    if (up == 0) {
                        start.set(event.getX(1), event.getY(1));
                    } else {
                        start.set(event.getX(0), event.getY(0));
                    }
                    mode = DRAG;
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN: // first and second finger down
                oldDist = spacing(event);
                if (oldDist > 5f) {
                    savedMatrix.set(matrix);
                    midPoint(mid, event);
                    mode = ZOOM;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!isViewContains(view, (int) event.getRawX(),
                        (int) event.getRawY())) {
                    // User moved outside bounds
                    return true;
                }
                if (mode == NONE) {
                    if ((Math.abs(event.getX(0) - start.x) > 3)
                            || (Math.abs(event.getY(0) - start.y) > 3)) {
                        mode = DRAG;
                    }
                } else if (mode == DRAG) {
                    matrix.set(savedMatrix);
                    matrix.postTranslate(event.getX() - start.x,
                            event.getY() - start.y); // create the transformation in the matrix of
                                                                                                  // points
                } else if (mode == ZOOM) {
                    // pinch zooming
                    float newDist = spacing(event);
                    if (newDist > 5f) {
                        float scaleFactor = newDist / oldDist;
                        float newScale = this.scale * scaleFactor;
                        if (newScale < this.minScale
                                || newScale > this.maxScale)
                            scaleFactor = 1;
                        else
                            this.scale = newScale;
                        matrix.postScale(scaleFactor, scaleFactor, mid.x,
                                mid.y);
                        this.oldDist = newDist;
                    }
                }
                break;
        }

        view.setImageMatrix(matrix); // display the transformation on screen

        // returning true prevents video touch/click detection in other listeners.
        // tapping on the video will only provide pinch/zoom/and reposition behavior.
        // allowing this to fall through was a bit unstable.
        return true; // indicate event was handled
    }

    /*
     * -------------------------------------------------------------------------- Method: spacing
     * Parameters: MotionEvent Returns: float Description: checks the spacing between the two
     * fingers on touch ----------------------------------------------------
     */

    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    /*
     * -------------------------------------------------------------------------- Method: midPoint
     * Parameters: PointF object, MotionEvent Returns: void Description: calculates the midpoint
     * between the two fingers ------------------------------------------------------------
     */

    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }

    /** Show an event in the LogCat view, for debugging */
    private void dumpEvent(MotionEvent event) {
        String[] names = {
                "DOWN", "UP", "MOVE", "CANCEL", "OUTSIDE", "POINTER_DOWN",
                "POINTER_UP", "7?",
                "8?", "9?"
        };
        StringBuilder sb = new StringBuilder();
        int action = event.getAction();
        int actionCode = action & MotionEvent.ACTION_MASK;
        sb.append("event ACTION_").append(names[actionCode]);

        if (actionCode == MotionEvent.ACTION_POINTER_DOWN
                || actionCode == MotionEvent.ACTION_POINTER_UP) {
            sb.append("(pid ").append(
                    action >> MotionEvent.ACTION_POINTER_INDEX_SHIFT);
            sb.append(")");
        }

        sb.append("[");
        for (int i = 0; i < event.getPointerCount(); i++) {
            sb.append("#").append(i);
            sb.append("(pid ").append(event.getPointerId(i));
            sb.append(")=").append((int) event.getX(i));
            sb.append(",").append((int) event.getY(i));
            if (i + 1 < event.getPointerCount())
                sb.append(";");
        }

        sb.append("]");
        Log.d(TAG, "Touch Events ---------\n" + sb);
    }

}
