
package com.atakmap.android.gridlines.graphics;

import android.graphics.PointF;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.graphics.GLPolyline;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLText;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created By Scott Auman 1/17/2019
 *
 * @ParGovernment SOMPE
 * This class creates and constructs the lat and longitude lines used to display lat and longitude sections of
 * the map when enabled. The map with every render is broken up into even rectangles based upon current
 * draw scale. This class runs all methods on the GlThread
 */
public class GLLatLngZoneOverlay extends GLZonesOverlay {

    private final List<GeoPointMetaData> ptsArr = new ArrayList<>();
    GeoPointMetaData[] scratch = new GeoPointMetaData[0];

    @Override
    public void draw(GLMapView map) {
        if (_type.equals("Degrees, Minutes, Seconds")
                || _type.equals("Decimal Degrees")) {
            if (map.drawMapResolution > 10000)
                return;
            drawLinesImpl(map);
        }
    }

    /**
     * Draws and constructs the latitude lines for the current mapview
     *
     * @param map   GlMApView
     * @param east  the double geobounds of the east lat
     * @param west  the double bounds of the west lat
     * @param south the double bounds of the southern long
     * @param north the double bounds of the northern long
     * @param alpha the color transparency of the lines this is inherited from the current color choosen
     */
    void _drawLatLines(GLMapView map, double east, double west,
            double south, double north,
            float alpha) {
        south = Math.max(south, -90);
        north = Math.min(north, 90);
        west = Math.max(west, map.continuousScrollEnabled ? -360 : -180);
        east = Math.min(east, map.continuousScrollEnabled ? 360 : 180);

        double resolutionStep = calcStep(map);
        double lat = roundUp((int) south, (int) resolutionStep);
        GLES20FixedPipeline.glPushMatrix();
        for (; lat <= north;) {
            if (lat >= south || lat <= north) {
                drawLine(map, lat, east, lat, west, alpha, lat == 0.0);
                if (line != null) {
                    if (_glText == null) {
                        _glText = GLText
                                .getInstance(MapView.getDefaultTextFormat());
                    }

                    GeoPoint pt = line.getPoints()[line.getPoints().length - 1];

                    if (_type.equals("Decimal Degrees")) {
                        if (lat == 0.0) {
                            _text = "0";
                        } else {
                            _text = String.valueOf(round(lat));
                        }
                    } else if (_type.equals("Degrees, Minutes, Seconds")) {
                        if (lat == 0) {
                            _text = "Equator";
                        } else {
                            _text = CoordinateFormatUtilities
                                    ._convertToLatDegString(pt, true);
                        }
                    }
                    if (StringUtils.isEmpty(_text))
                        return;
                    map.forward(pt, map.scratch.pointF);
                    // Loop through the line until we find a segment that crosses x = 0, this
                    // prevents the labels from spazzing out when zooming out.
                    if (map.scratch.pointF.x < 0) {
                        PointF prev = map.scratch.pointF;
                        for (int i = line.getPoints().length - 1; i > 1; i--) {
                            PointF curr = map.forward(line.getPoints()[i - 1]);
                            if (prev.x < 0 && curr.x > 0) {
                                float deltaX = curr.x - prev.x;
                                float deltaY = curr.y - prev.y;
                                float slope = deltaY / deltaX;
                                map.scratch.pointF.y = curr.y - slope * curr.x;
                                map.scratch.pointF.x = 0;
                            }
                        }
                    }

                    float yoffset;
                    float xoffset = (map.scratch.pointF.x
                            + (_glText.getStringWidth(_text)) / 2);
                    yoffset = (map.scratch.pointF.y - (_glText.getStringHeight()
                            / 2));

                    GLNinePatch smallNinePatch = GLRenderGlobals.get(map)
                            .getSmallNinePatch();
                    if (smallNinePatch != null) {
                        GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.6f);
                        GLES20FixedPipeline.glPushMatrix();
                        GLES20FixedPipeline.glTranslatef(xoffset, yoffset,
                                0f);
                        smallNinePatch.draw(_glText.getStringWidth(_text) + 16f,
                                Math.max(26f, _glText.getStringHeight()));
                        GLES20FixedPipeline.glPopMatrix();
                    }

                    // draw labels only if too far out
                    GLES20FixedPipeline.glPushMatrix();
                    GLES20FixedPipeline.glTranslatef(xoffset + 9f, yoffset + 4f,
                            0f);
                    _glText.draw(_text, _red, _green, _blue, 1.0f);
                    GLES20FixedPipeline.glPopMatrix();
                }
            }
            lat += resolutionStep;
        }

        GLES20FixedPipeline.glPopMatrix();
    }

    /**
     * determines the step of when the grid lines are drawn every n lat/lngs
     * we use static stepping to make sure the grid do not move when we pan the map around
     * @param map GLMapView
     * @return double of step count
     */
    private double calcStep(GLMapView map) {
        if (map.drawMapResolution > 6000) {
            return 10d;
        } else if (map.drawMapResolution >= 3000) {
            return 6d;
        } else if (map.drawMapResolution >= 1000
                || map.drawMapResolution > 250) {
            return 3d;
        } else {
            return 1d;
        }
    }

    // TODO - terribly poor efficiency.   Need to look at reworking in the future.
    protected void drawLine(GLMapView view, double lat0, double lng0,
            double lat1, double lng1, float alpha, boolean is0) {
        if (line == null) {
            line = new Polyline(UUID.randomUUID().toString());
            glline = new GLPolyline(view, line);
            glline.startObserving();
        }

        line.setStrokeColor(((int) (0xFF * alpha + 0.5) << 24) | _color);
        line.setStrokeWeight(is0 ? 3.0d : 1.0d);

        GeoPoint start = new GeoPoint(lat0, lng0);
        GeoPoint end = new GeoPoint(lat1, lng1);

        final double threshold = 6d;
        final double dlat = (lat1 - lat0);
        final double dlng = (lng1 - lng0);

        double d = Math.sqrt(dlat * dlat + dlng * dlng);
        if (view.scene.mapProjection.is3D() && (dlat * dlng) == 0
                && d > threshold) {
            // tessellate

            ptsArr.clear();

            //final double brg = start.bearingTo(end);
            GeoPoint p;
            ptsArr.add(GeoPointMetaData.wrap(start));
            p = start;

            do {
                d = dist(p.getLongitude(), p.getLatitude(), end.getLongitude(),
                        end.getLatitude());
                if (d <= threshold)
                    break;
                double dx = (end.getLongitude() - p.getLongitude()) / d;
                double dy = (end.getLatitude() - p.getLatitude()) / d;
                p = new GeoPoint(p.getLatitude() + dy * threshold,
                        p.getLongitude() + dx * threshold);
                ptsArr.add(GeoPointMetaData.wrap(p));
            } while (true);
            ptsArr.add(GeoPointMetaData.wrap(end));
            if (scratch.length != ptsArr.size())
                scratch = ptsArr.toArray(new GeoPointMetaData[0]);
            else
                scratch = ptsArr.toArray(scratch);

        } else {
            if (scratch.length != 2) {
                scratch = new GeoPointMetaData[2];
            }
            scratch[0] = GeoPointMetaData.wrap(start);
            scratch[1] = GeoPointMetaData.wrap(end);
        }

        line.setPoints(scratch);

        // zones have no elevation, just render them as part of the SURFACE pass.
        glline.draw(view, GLMapView.RENDER_PASS_SURFACE);
    }

    /** Rounds the lat/lng to the nearest factor of the draw resolution step
     * @param numToRound the lat/lng
     * @param multiple the draw resolution step
     * @return the nearest lat/lng to start drawing and looping from
     */
    private int roundUp(int numToRound, int multiple) {
        int remainder = Math.abs(numToRound) % multiple;
        if (remainder == 0)
            return numToRound;

        if (numToRound < 0)
            return -(Math.abs(numToRound) - remainder);
        else
            return numToRound + multiple - remainder;
    }

    void _drawLngLines(GLMapView map, double south, double north,
            double west, double east,
            float alpha) {
        south = Math.max(south, -90);
        north = Math.min(north, 90);
        west = Math.max(west, map.continuousScrollEnabled ? -360 : -180);
        east = Math.min(east, map.continuousScrollEnabled ? 360 : 180);

        GLES20FixedPipeline.glPushMatrix();

        double resolutionStep = calcStep(map);
        double lng = roundUp((int) Math.round(west), (int) resolutionStep);
        for (; lng <= east; lng += resolutionStep) {
            if (lng < -180 || lng > 180 || lng > east) {
                break;
            }
            drawLine(map, north, lng, south, lng, alpha, lng == 0.0);
            //only draw every other lng line
            if (line != null) {
                if (_glText == null) {
                    _glText = GLText
                            .getInstance(MapView.getDefaultTextFormat());
                }

                if (_type.equals("Decimal Degrees")) {
                    //0,0  on lng side is the prime no Degree symbol for this so use the known name
                    if (lng == 0.0) {
                        _text = "0";
                    } else {
                        _text = String.valueOf(round(lng));
                    }
                } else if (_type.equals("Degrees, Minutes, Seconds")) {
                    //0,0  on lng side is the prime no Degree symbol for this so use the known name
                    if (lng == 0.0) {
                        _text = "Prime Meridian";
                    } else {
                        _text = CoordinateFormatUtilities
                                ._convertToLngDegString(
                                        line.getPoints()[0],
                                        true);
                    }
                }

                if (StringUtils.isEmpty(_text))
                    return;

                map.forward(line.getPoints()[0], map.scratch.pointF);
                float yoffset;
                if (MapView.getMapView().getActionBarHeight() > 0
                        && (MapView.getMapView().getHeight()
                                - map.scratch.pointF.y <= MapView.getMapView()
                                        .getActionBarHeight())) {
                    map.scratch.pointF.y = MapView.getMapView().getHeight()
                            - MapView.getMapView().getActionBarHeight();
                    yoffset = (map.scratch.pointF.y - (_glText.getStringHeight()
                            / 2) - 8f);
                } else {
                    // Do the same thing as in _drawLatLines() except we treat the y-values as x-values
                    // and vice versa. Essentially rotating a cartesian graph to the right by 90 degrees.
                    int height = MapView.getMapView().getHeight();
                    if (map.scratch.pointF.y > height) {
                        PointF prev = map.scratch.pointF;
                        for (int i = 1; i < line.getPoints().length - 1; i++) {
                            PointF curr = map.forward(line.getPoints()[i + 1]);
                            if (prev.y > height && curr.y < height) {
                                float deltaX = curr.x - prev.x;
                                float deltaY = curr.y - prev.y;
                                float slope = deltaX / deltaY;
                                map.scratch.pointF.y = height;
                                // Have to find the xIntercept since the point we're solving for
                                // isn't at y = 0.
                                float xIntercept = curr.x - slope * curr.y;
                                map.scratch.pointF.x = (height * slope)
                                        + xIntercept;
                            }
                        }

                    }
                    yoffset = (map.scratch.pointF.y - (_glText.getStringHeight()
                            / 2) - 8f);
                }
                float xoffset = (map.scratch.pointF.x
                        - (_glText.getStringWidth(_text)) / 2);

                GLNinePatch smallNinePatch = GLRenderGlobals.get(map)
                        .getSmallNinePatch();
                if (smallNinePatch != null) {
                    GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.6f);
                    GLES20FixedPipeline.glPushMatrix();
                    GLES20FixedPipeline.glTranslatef(xoffset, yoffset,
                            0f);
                    smallNinePatch.draw(_glText.getStringWidth(_text) + 16f,
                            Math.max(16f, _glText.getStringHeight()));
                    GLES20FixedPipeline.glPopMatrix();
                }

                // draw labels only if too far out
                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glTranslatef(xoffset + 9f, yoffset + 4f,
                        0f);
                _glText.draw(_text, _red, _green, _blue, 1.0f);
                GLES20FixedPipeline.glPopMatrix();
            }
        }
        GLES20FixedPipeline.glPopMatrix();
    }

    protected void drawLinesImpl(GLMapView map) {
        final double drawZoom = convertMapScaleToLegacyZoom(
                map.getRenderSurface(),
                map.drawMapScale);

        float alpha = (float) Math.max(Math.min(drawZoom / 15d, 1d), 0d);
        GLES20FixedPipeline.glColor4f(_red, _green, _blue, alpha);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);

        if (map.crossesIDL) {
            _drawLatLines(map,
                    map.eastBoundUnwrapped,
                    map.westBoundUnwrapped,
                    map.southBound,
                    map.northBound,
                    alpha);
            // west of IDL
            _drawLngLines(map,
                    map.southBound,
                    map.northBound,
                    map.westBound,
                    180d,
                    alpha);
            // east of IDL
            _drawLngLines(map,
                    map.southBound,
                    map.northBound,
                    -180d,
                    map.eastBound,
                    alpha);
        } else {
            _drawLatLines(map,
                    map.eastBound,
                    map.westBound,
                    map.southBound,
                    map.northBound,
                    alpha);
            _drawLngLines(map,
                    map.southBound,
                    map.northBound,
                    map.westBound,
                    map.eastBound,
                    alpha);
        }
    }

    /** Rounds a double value to the precision value parameter.
     * Java Math.round only round to nearest integer without decimal
     * @param value decimal value representing lat/lng
     * @return the whole number of the lat/lng
     */
    private double round(double value) {
        int scale = (int) Math.pow(10, 0);
        return (double) Math.round(value * scale) / scale;
    }
}
