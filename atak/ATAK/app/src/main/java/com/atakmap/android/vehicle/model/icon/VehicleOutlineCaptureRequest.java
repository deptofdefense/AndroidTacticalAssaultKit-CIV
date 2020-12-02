
package com.atakmap.android.vehicle.model.icon;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;

import com.atakmap.android.vehicle.model.VehicleModelInfo;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Render the outline for a vehicle model to a bitmap
 * This class is currently only used for testing the 3D model stroke filter
 */
public class VehicleOutlineCaptureRequest extends VehicleModelCaptureRequest {

    private static final String TAG = "VehicleOutlineCaptureRequest";

    public interface Callback {
        void onCaptureFinished(List<PointF> points);
    }

    // Trace of the outline
    private List<PointF> _points = new ArrayList<>();
    private Callback _callback;

    public VehicleOutlineCaptureRequest(VehicleModelInfo vehicle, int size) {
        super(vehicle);
        setOutputSize(size, true);
        setStrokeEnabled(true);
        setStrokeOnly(true);
        setStrokeColor(Color.BLACK);
        setStrokeWidth(1);
    }

    public void setCallback(Callback cb) {
        _callback = cb;
    }

    private static final int[][] SEARCH_OFFSETS = {
            {
                    0, 1
            }, {
                    1, 1
            }, {
                    1, 0
            }, {
                    1, -1
            }, {
                    0, -1
            }, {
                    -1, -1
            }, {
                    -1, 0
            }, {
                    -1, 1
            }
    };

    /**
     * Finished capturing the bitmap - now we need to trace the stroke outline
     * in order to create a polyline
     */
    @Override
    public void onFinished(Bitmap bmp) {
        // Now take the bitmap result and trace the line to a list of points
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] p = new int[w * h];
        bmp.getPixels(p, 0, w, 0, 0, w, h);
        bmp.recycle();

        // Count how many valid pixels there are so we have a failsafe max
        int max = 0;
        for (int pixel : p) {
            if (pixel == Color.BLACK)
                max++;
        }

        // Image is empty
        if (max == 0) {
            Log.e(TAG, "Failed to generate polyline for vehicle " + _vehicle);
            return;
        }

        // Find the nose of the vehicle
        int x = w / 2, y = 0;
        for (; y < _height; y++) {
            if (hit(p, x, y))
                break;
        }

        // Add first point
        int startX = x, startY = y;
        _points.clear();
        _points.add(new PointF(startX, startY));

        // Now begin the search
        int lastX = x, lastY = y;
        Stack<PointF> altRoutes = new Stack<>();
        boolean[][] hit = new boolean[_width][_height];
        for (int m = 0; m < max; m++) {
            int hitX = -1, hitY = -1;
            int numHit = 0;

            // Check each neighboring pixel, starting with adjacent points
            // then diagonal
            for (int[] o : SEARCH_OFFSETS) {

                // Offset point
                int ox = x + o[0], oy = y + o[1];

                // Out of bounds
                if (ox < 0 || oy < 0 || ox >= _width || oy >= _height)
                    continue;

                // Already hit this point before - ignore
                if (hit[ox][oy])
                    continue;

                // Pixel is not empty
                if (hit(p, ox, oy)) {
                    numHit++;
                    if (numHit >= 2) {
                        // Remember this alternative route in case we hit
                        // a dead end
                        altRoutes.push(new PointF(x, y));
                        break;
                    } else {
                        hitX = ox;
                        hitY = oy;
                    }
                }
            }

            // Hit a dead end
            if (hitX == -1 && hitY == -1) {

                // Add the dead end point
                _points.add(new PointF(x, y));

                // Dead end with no alternate route or unused neighboring pixels
                if (altRoutes.isEmpty()) {
                    Log.e(TAG, "Failed to find any neighboring pixels at "
                            + x + "," + y + " when tracing " + _vehicle);
                    break;
                }

                // Jump to the alternate route we remembered
                PointF altRoute = altRoutes.pop();
                x = (int) altRoute.x;
                y = (int) altRoute.y;
                m--;
                continue;
            }

            // Mark pixel as hit so we don't select it again
            hit[hitX][hitY] = true;

            // Test for smooth line
            boolean onLine = checkBresenham(p, lastX, lastY, hitX, hitY);

            // Add next point
            if (!onLine) {
                _points.add(new PointF(x, y));
                lastX = x;
                lastY = y;
            }
            x = hitX;
            y = hitY;

            // Reached the pixel we started with - finish
            if (startX == hitX && startY == hitY)
                break;
        }

        // Close the shape
        PointF first = _points.get(0);
        _points.add(new PointF(first.x, first.y));

        // DEBUG - Preview result by drawing the polyline to a canvas
        /*Path path = new Path();
        path.moveTo(startX, startY);
        for (int i = 1; i < _points.size(); i++) {
            PointF point = _points.get(i);
            path.lineTo(point.x, point.y);
        }
        path.close();
        
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        
        Bitmap bmp2 = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp2);
        c.drawPath(path, paint);
        
        c = new Canvas(bmp);
        paint.setColor(Color.RED);
        for (PointF point : _points)
            c.drawPoint(point.x, point.y, paint);
        
        Log.d(TAG, "Total points = " + _points.size());*/

        // Scale the vehicle down to its actual metric size and offset so
        // each point is relative to the center
        float vw = (float) (_aabb.maxX - _aabb.minX);
        float vh = (float) (_aabb.maxY - _aabb.minY);
        float vw2 = vw / 2f, vh2 = vh / 2f;
        for (PointF point : _points) {
            point.x = ((point.x / _width) * vw) - vw2;
            point.y = vh2 - ((point.y / _height) * vh);
        }

        if (_callback != null)
            _callback.onCaptureFinished(_points);
    }

    /**
     * Check if a pixel at a given coordinate is empty or not
     * @param pixels Pixel array
     * @param x X-coordinate
     * @param y Y-coordinate
     * @return True if the pixel is not empty
     */
    private boolean hit(int[] pixels, int x, int y) {
        return pixels[(y * _width) + x] == Color.BLACK;
    }

    /**
     * Check if a pixelated line in the image can be simplified into a smooth line
     * using the Bresenham line algorithm to match against
     * @param p Pixel array
     * @param x1 Start X
     * @param y1 Start Y
     * @param x2 End Y
     * @param y2 End Y
     * @return True if the line passes the test
     */
    private boolean checkBresenham(int[] p, int x1, int y1, int x2, int y2) {

        // Check if this is a horizonal line or not
        boolean horiz = Math.abs(y2 - y1) < Math.abs(x2 - x1);

        // Need to reverse the order so our major increments positively
        if (horiz && x1 > x2 || !horiz && y1 > y2)
            return checkBresenham(p, x2, y2, x1, y1);

        // Setup major and minor values to simplify the code below
        int major, minor, dMajor, dMinor, end;
        if (horiz) {
            major = x1;
            minor = y1;
            dMajor = x2 - x1;
            dMinor = y2 - y1;
            end = x2;
        } else {
            major = y1;
            minor = x1;
            dMajor = y2 - y1;
            dMinor = x2 - x1;
            end = y2;
        }

        int inc = 1;
        if (dMinor < 0) {
            inc = -1;
            dMinor *= -1;
        }
        int D = 2 * dMinor - dMajor;

        int numHit = 0, numMissed = 0;
        while (major <= end) {
            // Track how many pixels match the line plot
            if (horiz && hit(p, major, minor) || !horiz && hit(p, minor, major))
                numHit++;
            else
                numMissed++;
            if (D > 0) {
                minor += inc;
                D -= 2 * dMajor;
            }
            D += 2 * dMinor;
            major++;
        }

        // Error threshold
        return numHit * 2 > numMissed * 3;
    }
}
