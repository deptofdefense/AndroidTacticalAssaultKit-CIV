
package com.atakmap.android.gridlines.graphics;

import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.graphics.GLPolyline;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.Globe;
import com.atakmap.map.RenderSurface;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Rectangle;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLText;
import com.atakmap.util.DirectExecutorService;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executor;

public class GLZonesOverlay implements GLMapRenderable, GLMapRenderable2 {
    protected GLText _glText;
    protected String _text;

    /*
     * Latitude break-down -80 to 0 : 10 8 deg steps [C,D,E,F,G,H,J,K,L,M] 0 to 72 : 9 8 deg zones
     * [N,P,Q,R,S,T,U,V,W] 72 to 84 : 1 12 deg zone [X] Longitude bread-down -180 to 0 : 6 deg steps
     * [01...30] 0 to 180 : 6 deg steps [31...60]
     */

    public GLZonesOverlay() {
    }

    public void setColor(float red, float green, float blue) {
        _red = red;
        _green = green;
        _blue = blue;
        _color = ((int) (0xFF * red) << 16) | ((int) (0xFF * green) << 8)
                | ((int) (0xFF * blue));
    }

    public void setType(String type) {
        _type = type;
    }

    @Override
    public void draw(GLMapView map) {
        draw(map, GLMapView.RENDER_PASS_SURFACE);
    }

    @Override
    public void draw(GLMapView map, int renderPass) {
        if ((renderPass & getRenderPass()) == 0)
            return;

        if (!_type.equals("MGRS"))
            return;
        if (_zones == null) {
            _zones = new GLZoneRegion[20][];
            for (int i = 0; i < 20; ++i) {
                _zones[i] = new GLZoneRegion[60];
            }
        }

        final double drawZoom = convertMapScaleToLegacyZoom(
                map.getRenderSurface(),
                Globe.getMapScale(map.currentScene.scene.dpi,
                        map.currentScene.drawMapResolution));

        float alpha = (float) Math.max(Math.min(drawZoom / 15d, 1d), 0d);
        GLES20FixedPipeline.glColor4f(_red, _green, _blue, alpha);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);

        if (MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SURFACE)) {
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

        // mark all zones that will be drawn in this pass
        if (MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SPRITES))
            _markZones(map);
        // draw the zones for this pass
        _drawZones(map.getSurface(), map, renderPass);
        // perform culling
        if (MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SPRITES)
                && !map.multiPartPass) {
            for (GLZoneRegion z = zoneDrawList; z != null; z = z.next) {
                if (z.mark) {
                    // unmark for next pump
                    z.mark = false;
                } else {
                    // cull all that were not marked on this pump
                    if (z.next != null) {
                        z.next.prev = z.prev;
                    }
                    if (z.prev != null) {
                        z.prev.next = z.next;
                    }
                    if (zoneDrawList == z) {
                        zoneDrawList = z.next;
                    }
                    _zones[z.gridY][z.gridX] = null;
                }
            }
        }
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SURFACE | GLMapView.RENDER_PASS_SPRITES;
    }

    @Override
    public void release() {
        if (_zones != null)
            // XXX - release?
            _zones = null;

        if (this.line != null)
            this.line = null;
        if (this.glline != null) {
            this.glline.release();
            this.glline = null;
        }
    }

    protected static double convertMapScaleToLegacyZoom(RenderSurface mapView,
            double scale) {
        return (scale * Globe.getFullEquitorialExtentPixels(mapView.getDpi()))
                / mapView.getWidth();
    }

    private void _drawLngLines(GLMapView map, double south, double north,
            double west, double east,
            float alpha, int renderPass) {
        south = Math.max(south, -80);
        north = Math.min(north, 84);
        west = Math.max(west, -180);
        east = Math.min(east, 180);

        GLES20FixedPipeline.glPushMatrix();

        for (int lng = ((int) west / 6) * 6; lng <= east; lng += 6) {
            switch (lng) {
                case 6:
                case 12:
                case 18:
                case 24:
                case 30:
                case 36:
                    _drawSpecialLong(map, alpha, south, north, lng);
                    break;
                default:
                    drawLine(map, north, lng, south, lng, alpha);
                    break;
            }
        }

        GLES20FixedPipeline.glPopMatrix();
    }

    private void _draw6east(GLMapView map, float alpha, double south,
            double north) {
        if (north < 56) {
            drawLine(map, north, 6, south, 6, alpha);
        } else {
            if (south < 56) {
                drawLine(map, 56, 6, south, 6, alpha);
                south = 56;
            }
            if (north < 64) {
                drawLine(map, north, 3, south, 3, alpha);
                return;
            } else {
                drawLine(map, 64, 3, south, 3, alpha);
                south = 64;
            }
            if (north < 72) {
                drawLine(map, north, 6, south, 6, alpha);
                return;
            } else {
                drawLine(map, 72, 6, south, 6, alpha);
                south = 72;
            }
            drawLine(map, north, 9, south, 9, alpha);
        }
    }

    private void _drawSpecialLong(GLMapView map, float alpha, double south,
            double north, int lng) {
        GLPolyline glpoly;
        if (lng == 6) {
            _draw6east(map, alpha, south, north);
        } else {
            double n = Math.min(north, 72);
            drawLine(map, n, lng, south, lng, alpha);
            if (north > 72 && lng % 12 != 0) {
                south = Math.max(72, south);
                drawLine(map, north, lng + 3, south, lng + 3, alpha);
            }
        }
    }

    private void _drawLatLines(GLMapView map, double east, double west,
            double south, double north,
            float alpha, int renderPass) {
        south = Math.max(south, -80);
        north = Math.min(north, 84);
        west = Math.max(west, map.continuousScrollEnabled ? -360 : -180);
        east = Math.min(east, map.continuousScrollEnabled ? 360 : 180);

        double lat = -80d;

        for (int i = 0; i < 21 && lat <= north; ++i) {
            if (lat >= south) {
                drawLine(map, lat, east, lat, west, alpha);
            }

            lat += 8d;
            if (i == 19) {
                lat += 4d;
            }
        }
    }

    private void _markZones(GLMapView map) {
        if (map.eastBoundUnwrapped - map.westBoundUnwrapped > 60d)
            return;
        if (map.crossesIDL) {
            // west of IDL
            _markZonesImpl(map.northBound,
                    map.westBound,
                    map.southBound,
                    180d);
            // east of IDL
            _markZonesImpl(map.northBound,
                    -180d,
                    map.southBound,
                    map.eastBound);
        } else {
            _markZonesImpl(map.northBound,
                    map.westBound,
                    map.southBound,
                    map.eastBound);
        }
    }

    private void _markZonesImpl(double northBound, double westBound,
            double southBound, double eastBound) {
        int ystart = (int) ((southBound + 90d - 10d) / 8d);
        int xstart = (int) ((westBound + 180d) / 6d);

        int yend = (int) ((northBound + 90d - 10) / 8d);
        int xend = (int) ((eastBound + 180d) / 6d);

        if (yend >= 17 && xend >= 30 && xstart <= 36) {
            xstart -= 2;
            xend += 2;
        }

        ystart = MathUtils.clamp(ystart, 0, 19);
        xstart = MathUtils.clamp(xstart, 0, 59);

        yend = MathUtils.clamp(yend, 0, 19);
        xend = MathUtils.clamp(xend, 0, 59);

        double lat = -80d + (ystart * 8d);// + 4d;
        double lngStart = -180d + (xstart * 6d);// + 3d;

        for (int y = ystart; y <= yend; ++y) {

            double lng = lngStart;

            for (int x = xstart; x <= xend; ++x) {

                boolean shouldMark = true;
                GLZoneRegion z = _zones[y][x];
                // non-null implies in draw list
                if (z == null) {
                    double s = lat;
                    double n = s + 8;
                    double w = lng;
                    double e = w + 6;
                    if (s == 56) {
                        if (w == 0) {
                            e = 3;
                        } else if (w == 6) {
                            w = 3;
                        }
                    } else if (s == 72) {
                        n = 84;
                        if (w == 0) {
                            e = 9;
                        } else if (w == 12) {
                            w = 9;
                            e = 21;
                        } else if (w == 24) {
                            w = 21;
                            e = 33;
                        } else if (w == 36) {
                            w = 33;
                        } else if (w == 6 || w == 18 || w == 30) {
                            w += 3;
                            e = w;
                            shouldMark = false;
                        }
                    }
                    z = new GLZoneRegion(s, w, n, e);
                    z._genGridTileExecutor = _genGridTileExecutor;
                    z._segmentLabelsExecutor = _segmentLabelsExecutor;
                    _zones[y][x] = z;
                    z.next = zoneDrawList;
                    if (zoneDrawList != null) {
                        zoneDrawList.prev = z;
                    }
                    zoneDrawList = z;
                    z.gridX = x;
                    z.gridY = y;
                }
                if (shouldMark) {
                    z.mark = true;
                }

                lng += 6d;
            }

            lat += 8d;
        }
    }

    private void _drawZones(GLMapSurface surface, GLMapView ortho,
            int renderPass) {
        GLGridTile.debugDrawCount = 0;
        for (GLZoneRegion z = zoneDrawList; z != null; z = z.next) {
            if (Rectangle.intersects(ortho.westBound, ortho.southBound,
                    ortho.eastBound, ortho.northBound,
                    z._west, z._south,
                    z._east, z._north,
                    false)) {

                z.draw(surface, ortho, _red, _green, _blue, renderPass);
            }
        }
    }

    private void drawLine(GLMapView view, double lat0, double lng0,
            double lat1, double lng1, float alpha) {
        if (line == null) {
            line = new Polyline(UUID.randomUUID().toString());
            line.setStrokeWeight(1.0d);
            glline = new GLPolyline(view, line);
            glline.startObserving();
        }

        line.setStrokeColor(((int) (0xFF * alpha + 0.5) << 24) | _color);

        GeoPoint start = new GeoPoint(lat0, lng0);
        GeoPoint end = new GeoPoint(lat1, lng1);
        GeoPoint[] pts;

        final double threshold = 6d;
        final double dlat = (lat1 - lat0);
        final double dlng = (lng1 - lng0);

        double d = Math.sqrt(dlat * dlat + dlng * dlng);
        if (view.currentScene.scene.mapProjection
                .getSpatialReferenceID() == 4978
                && (dlat * dlng) == 0
                && d > threshold) {
            // tessellate
            ArrayList<GeoPoint> ptsArr = new ArrayList<>(
                    (int) Math.ceil(d / threshold) + 1);

            //final double brg = start.bearingTo(end);
            GeoPoint p;
            ptsArr.add(start);
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
                ptsArr.add(p);
            } while (true);
            ptsArr.add(end);
            pts = ptsArr.toArray(new GeoPoint[0]);
        } else {
            pts = new GeoPoint[] {
                    start, end
            };
        }

        line.setPoints(GeoPointMetaData.wrap(pts));

        // zones have no elevation, just render them as part of the SURFACE pass.
        glline.draw(view, GLMapView.RENDER_PASS_SURFACE);
    }

    protected int _color;
    protected String _type;
    float _red = 1f;
    float _green = 1f;
    float _blue = 1f;
    private GLZoneRegion zoneDrawList;
    private GLZoneRegion[][] _zones;
    protected Polyline line;
    GLPolyline glline;
    Executor _genGridTileExecutor;
    Executor _segmentLabelsExecutor = new DirectExecutorService();
}
