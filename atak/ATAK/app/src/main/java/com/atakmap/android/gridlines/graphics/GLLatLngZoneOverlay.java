
package com.atakmap.android.gridlines.graphics;

import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.graphics.GLPolyline;
import com.atakmap.android.maps.graphics.GLSegmentFloatingLabel;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.map.Globe;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Rectangle;
import com.atakmap.opengl.GLES20FixedPipeline;

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
    public void draw(GLMapView map, int renderPass) {
        if (_type.equals("Degrees, Minutes, Seconds")
                || _type.equals("Decimal Degrees")) {
            if (map.currentScene.drawMapResolution > 10000)
                return;
            drawLinesImpl(map, renderPass);
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
            float alpha, int renderPass) {
        south = Math.max(south, -90);
        north = Math.min(north, 90);
        west = Math.max(west, map.continuousScrollEnabled ? -360 : -180);
        east = Math.min(east, map.continuousScrollEnabled ? 360 : 180);

        double resolutionStep = calcStep(map);
        double lat = roundUp((int) south, (int) resolutionStep);
        while (lat <= north) {
            if (lat >= south || lat <= north) {
                if (MathUtils.hasBits(renderPass,
                        GLMapView.RENDER_PASS_SURFACE))
                    drawLine(map, lat, east, lat, west, alpha, lat == 0.0);
                if (MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SPRITES)
                        &&
                        map.currentScene.drawTilt < 40d &&
                        map.currentScene.drawMapResolution <= 1500 &&
                        line != null) {
                    // if surface pass not included, need to validate
                    if (!MathUtils.hasBits(renderPass,
                            GLMapView.RENDER_PASS_SURFACE))
                        validateLine(map, lat, east, lat, west, alpha,
                                lat == 0.0);
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
                    GLSegmentFloatingLabel lbl = new GLSegmentFloatingLabel();
                    lbl.setClampToGround(true);
                    lbl.setTextColor(_red, _green, _blue, 1f);
                    lbl.setBackgroundColor(0f, 0f, 0f, 0.6f);
                    lbl.setRotateToAlign(false);
                    lbl.setInsets(0f, 0f, 0f, 0f);

                    GeoPoint[] pts = line.getPoints();
                    for (int i = 0; i < pts.length; i++) {
                        if (validateLabel(map, lbl, pts[(i + 1) % pts.length],
                                pts[i])) {
                            lbl.setText(_text);
                            lbl.draw(map);
                            break;
                        }
                    }
                }
            }
            lat += resolutionStep;
        }
    }

    /**
     * determines the step of when the grid lines are drawn every n lat/lngs
     * we use static stepping to make sure the grid do not move when we pan the map around
     * @param map GLMapView
     * @return double of step count
     */
    private double calcStep(GLMapView map) {
        if (map.currentScene.drawMapResolution > 6000) {
            return 10d;
        } else if (map.currentScene.drawMapResolution >= 3000) {
            return 6d;
        } else if (map.currentScene.drawMapResolution >= 1000
                || map.currentScene.drawMapResolution > 250) {
            return 3d;
        } else {
            return 1d;
        }
    }

    private void validateLine(GLMapView view, double lat0, double lng0,
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
        if (view.currentScene.drawSrid == 4978 && (dlat * dlng) == 0
                && d > threshold) {
            // tessellate

            ptsArr.clear();

            //final double brg = start.bearingTo(end);
            GeoPoint p;
            ptsArr.add(GeoPointMetaData.wrap(start));
            p = start;

            do {
                d = MathUtils.distance(p.getLongitude(), p.getLatitude(),
                        end.getLongitude(), end.getLatitude());
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
    }

    // TODO - terribly poor efficiency.   Need to look at reworking in the future.
    protected void drawLine(GLMapView view, double lat0, double lng0,
            double lat1, double lng1, float alpha, boolean is0) {

        validateLine(view, lat0, lng0, lat1, lng1, alpha, is0);

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
            float alpha, int renderPass) {
        south = Math.max(south, -90);
        north = Math.min(north, 90);
        west = Math.max(west, map.continuousScrollEnabled ? -360 : -180);
        east = Math.min(east, map.continuousScrollEnabled ? 360 : 180);

        double resolutionStep = calcStep(map);
        double lng = roundUp((int) Math.round(west), (int) resolutionStep);
        for (; lng <= east; lng += resolutionStep) {
            if (lng < -180 || lng > 180 || lng > east) {
                break;
            }
            if (MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SURFACE)) {
                drawLine(map, north, lng, south, lng, alpha, lng == 0.0);
            }
            //only draw every other lng line
            if (MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SPRITES) &&
                    map.currentScene.drawTilt < 40d &&
                    map.currentScene.drawMapResolution <= 1500 &&
                    line != null) {
                // if surface pass not included, line needs to be validated
                if (!MathUtils.hasBits(renderPass,
                        GLMapView.RENDER_PASS_SURFACE))
                    validateLine(map, north, lng, south, lng, alpha,
                            lng == 0.0);

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
                GLSegmentFloatingLabel lbl = new GLSegmentFloatingLabel();
                lbl.setClampToGround(true);
                lbl.setTextColor(_red, _green, _blue, 1f);
                lbl.setBackgroundColor(0f, 0f, 0f, 0.6f);
                lbl.setRotateToAlign(false);
                lbl.setInsets(0f, 0f, 0f, 0f);

                GeoPoint[] pts = line.getPoints();
                for (int i = 0; i < pts.length; i++) {
                    if (validateLabel(map, lbl, pts[i],
                            pts[(i + 1) % pts.length])) {
                        lbl.setText(_text);
                        lbl.draw(map);
                        break;
                    }
                }
            }
        }
    }

    protected void drawLinesImpl(GLMapView map, int renderPass) {
        final double drawZoom = convertMapScaleToLegacyZoom(
                map.getRenderSurface(),
                Globe.getMapScale(map.currentScene.scene.dpi,
                        map.currentScene.drawMapResolution));

        float alpha = (float) Math.max(Math.min(drawZoom / 15d, 1d), 0d);
        GLES20FixedPipeline.glColor4f(_red, _green, _blue, alpha);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);

        if (map.crossesIDL) {
            _drawLatLines(map,
                    map.eastBoundUnwrapped,
                    map.westBoundUnwrapped,
                    map.southBound,
                    map.northBound,
                    alpha, renderPass);
            // west of IDL
            _drawLngLines(map,
                    map.southBound,
                    map.northBound,
                    map.westBound,
                    180d,
                    alpha, renderPass);
            // east of IDL
            _drawLngLines(map,
                    map.southBound,
                    map.northBound,
                    -180d,
                    map.eastBound,
                    alpha, renderPass);
        } else {
            _drawLatLines(map,
                    map.eastBound,
                    map.westBound,
                    map.southBound,
                    map.northBound,
                    alpha, renderPass);
            _drawLngLines(map,
                    map.southBound,
                    map.northBound,
                    map.westBound,
                    map.eastBound,
                    alpha, renderPass);
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

    static boolean validateLabel(GLMapView ortho, GLSegmentFloatingLabel lbl,
            GeoPoint start, GeoPoint end) {
        ortho.scratch.geo.set(start);
        ortho.scratch.geo.set(
                ortho.getTerrainMeshElevation(ortho.scratch.geo.getLatitude(),
                        ortho.scratch.geo.getLongitude()));
        ortho.forward(ortho.scratch.geo, ortho.scratch.pointD);
        if (ortho.scratch.pointD.z >= 1d)
            return false;
        float sx = (float) ortho.scratch.pointD.x;
        float sy = (float) ortho.scratch.pointD.y;
        ortho.scratch.geo.set(end);
        ortho.scratch.geo.set(
                ortho.getTerrainMeshElevation(ortho.scratch.geo.getLatitude(),
                        ortho.scratch.geo.getLongitude()));
        ortho.forward(ortho.scratch.geo, ortho.scratch.pointD);
        float ex = (float) ortho.scratch.pointD.x;
        float ey = (float) ortho.scratch.pointD.y;
        if (ortho.scratch.pointD.z >= 1d)
            return false;

        final float l = ortho.currentScene.left;
        final float r = ortho.currentScene.right;
        final float t = ortho.currentScene.top;
        final float b = ortho.currentScene.bottom;

        // quickly filter out any segments that are contained within the viewport
        final boolean containsStart = Rectangle.contains(l, b, r, t, sx, sy);
        final boolean containsEnd = Rectangle.contains(l, b, r, t, ex, ey);

        if (containsStart && containsEnd)
            return false;

        float weight = 0f;

        // NOTE: bias is always against top if the segment intersects
        // both the left and top edges

        // verify that we intersect the left or top edges
        Vector2D lin = Vector2D.segmentToSegmentIntersection(
                new Vector2D(sx, sy), new Vector2D(ex, ey), new Vector2D(l, t),
                new Vector2D(l, b));
        Vector2D tin = Vector2D.segmentToSegmentIntersection(
                new Vector2D(sx, sy), new Vector2D(ex, ey), new Vector2D(l, t),
                new Vector2D(r, t));

        if (lin == null && tin == null)
            return false;
        if (containsStart != containsEnd) {
            // only one edge intersects
            weight = !containsStart ? 0f : 1f;
        } else {
            // both edges intersect, we will weight towards the
            // top-most or end-most endpoint
            if (tin != null) {
                weight = (sy >= sy) ? 0f : 1f;
            } else if (lin != null) {
                weight = (sx <= ex) ? 0f : 1f;
            }
        }

        lbl.setSegmentPositionWeight(weight);
        lbl.setSegment(new GeoPoint[] {
                start, end
        });
        return true;
    }
}
