
package com.atakmap.map.layer.feature.geometry.opengl;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;

import android.graphics.Color;
import android.opengl.GLES30;

import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.Tessellate;

public class GLBatchPolygon extends GLBatchLineString {

    private final static String TAG = "GLBatchPolygon";

    protected boolean _hasInnerPolygon;

    boolean drawStroke;
    boolean drawFill;

    public GLBatchPolygon(GLMapSurface surface) {
        this(surface.getGLMapView());
    }

    public GLBatchPolygon(MapRenderer surface) {
        super(surface, 2);

        this.hasBeenExtruded = false;
        _hasInnerPolygon = false;
    }

    @Override
    public void setStyle(final Style style) {
        if(renderContext.isRenderThread())
            setStyleImpl(style);
        else
            renderContext.queueEvent(new Runnable() {
                public void run() {
                    setStyleImpl(style);
                }
            });
    }
    private void setStyleImpl(Style style) {
        super.setStyle(style, false);

        drawFill = hasFill();

        // validate the geometry to update polygon tessellation for fill
        this.validateGeometry();

        this.drawStroke = !drawFill;
        final RenderState[] rs = this.renderStates;
        if(!this.drawStroke && rs != null) {
            for(RenderState s : rs) {
                this.drawStroke = (s.strokeColorA != 0f);
                if(this.drawStroke)
                    break;
            }
        }

        // if extrusion is being performed, ensure there is an outline for
        // consistency with legacy implementation
        if(isExtruded())
            ensureOutline();
    }

    @Override
    public void setExtrude(double value) {
        super.setExtrude(value);
        // if extrusion is being performed, ensure there is an outline for
        // consistency with legacy implementation
        if(value != 0d)
            ensureOutline();
    }

    private void ensureOutline() {
        int fillColor = 0;
        boolean hasStroke = false;
        final int numRenderStates = (renderStates == null) ? 0 : renderStates.length;
        RenderState[] rs = new RenderState[numRenderStates+1];
        for(int i = 0; i < numRenderStates; i++) {
            rs[i] = renderStates[i];
            hasStroke |= (rs[i].strokeColorA+rs[i].outlineColorA) > 0f;
            if(hasStroke)
                break;
            if(fillColor == 0 && rs[i].fillColor != 0)
                fillColor = rs[i].fillColor;
        }
        if(hasStroke)
            return;
        final float fillColorR = Color.red(fillColor) / 255f;
        final float fillColorG = Color.red(fillColor) / 255f;
        final float fillColorB = Color.red(fillColor) / 255f;
        rs[numRenderStates] = new RenderState();
        rs[numRenderStates].strokeColorR = fillColorR*0.9f;
        rs[numRenderStates].strokeColorG = fillColorG*0.9f;
        rs[numRenderStates].strokeColorB = fillColorB*0.9f;
        rs[numRenderStates].strokeColorA = 1f;
        rs[numRenderStates].strokeColor = Color.argb(
                (int)(rs[numRenderStates].strokeColorA*255f),
                (int)(rs[numRenderStates].strokeColorR*255f),
                (int)(rs[numRenderStates].strokeColorG*255f),
                (int)(rs[numRenderStates].strokeColorB*255f)
        );
        rs[numRenderStates].strokeWidth = 4f;
        renderStates = rs;

        // always have stroke to draw when extrusion outline is applied
        this.drawStroke = true;
    }

    @Override
    protected void setGeometryImpl(final ByteBuffer blob, final int type) {
        hasBeenExtruded = false;
        final int numRings = blob.getInt();
        if (numRings == 0) {
            this.numRenderPoints = 0;
        } else {
            super.setGeometryImpl(blob, type, null, numRings);
        }
    }

    @Override
    protected boolean validateGeometry() {
        final boolean updated = super.validateGeometry();

        // if extruded, we'll handle in `projectVertices` as we need the
        // terrain
        if(isExtruded())
            return updated;

        // if the polygon has a fill and has at least 3 points then we need to
        // construct the polygon render geometry
        final boolean needsFill = this.drawFill && this.numRenderPoints > 3;
        // polygon render geometry is valid if there was no update to the
        // linestring render points and we already have polygon render
        // triangles
        final boolean hasFill = !updated && (this.polyTriangles != null);

        if(needsFill != hasFill) {
            Unsafe.free(this.polyTriangles);
            this.polyTriangles = null;
            try {
                if (needsFill) {
                    // XXX - we currently always pass in a threshold if tessellated
                    //       rather than also checking 'needsTessellate'. this is
                    //       because the vertex data in the source exterior ring
                    //       could be sufficient tessellated already, but the
                    //       triangles derived from tessellating the polygon may
                    //       exceed the threshold. should look at computing an MBB
                    //       and comparing diagonal length with threshold for a
                    //       short circuit
                    if (_hasInnerRings) {
                        this.polyTriangles = Tessellate.polygon(this.renderPoints,
                                                            24,
                                                            3,
                                    _numVertices,
                                    _startIndices,
                                    _numPolygons,
                                    this.tessellated ? this.threshold : 0d,
                                    true);
                    } else {
                        this.polyTriangles = Tessellate.polygon(this.renderPoints,
                                24,
                                3,
                                                            this.numRenderPoints-1,
                                this.tessellated ? this.threshold : 0d,
                                                            true);

                    }
                }
            } catch (Exception e) {
                // XXX - If release is called while the validateGeomtry is being
                // called which seems to be the case with both ATAK-11161 and
                // ATAK-11275, protect against a hard crash but note the exception.
                com.atakmap.coremap.log.Log.e(TAG, "XXX - freed / null renderPoint", e);
                return false;
            }
        }
        if(this.polyTriangles == null) {
            Unsafe.free(this.polyVertices);
            this.polyVertices = null;
        }

        return updated;
    }

    @Override
    void extrudeGeometryImpl(GLMapView view, DoubleBuffer extPoints, double[] heights, boolean closed) {
        if(_numPolygons == 1) {
            // extrude the outline
            super.extrudeGeometryImpl(view, extPoints, heights, closed);

            // if a single polygon, simply extrude the source points
            Unsafe.free(polyTriangles);
            polyTriangles = GLExtrude.extrudeRelative(Double.NaN, extPoints, 3, closed, heights);
            polyTriangles.rewind();
        } else {
            // if a polygon with holes, we need to extrude each ring and then aggregate into a single buffer
            // Fetch terrain elevation for each point and find the minimum
            double minAlt = heights[0];
            double maxAlt = heights[0];
            for (int i = 1; i < heights.length; i++) {
                double alt = heights[i];
                minAlt = Math.min(minAlt, alt);
                maxAlt = Math.max(maxAlt, alt);
            }

            final double centerAlt = (maxAlt + minAlt) / 2;

            // Calculate relative height per point
            DoubleBuffer[] extrudedWalls = new DoubleBuffer[_numPolygons];
            DoubleBuffer[] extrudedOutlines = new DoubleBuffer[_numPolygons];
            int totalWallData = 0;
            int totalOutlineData = 0;
            for (int p = 0; p < _numPolygons; p++) {
                double[] ringHeights = new double[_numVertices[p]];
                int h = 0;
                double[] tmp = new double[_numVertices[p] * 3];
                for (int i = _startIndices[p] * 3; (i - _startIndices[p] * 3) < _numVertices[p] * 3; i += 3) {
                    // Height/top altitude (depending on altitude mode)
                    double height = points.get(i + 2);

                    // (Minimum altitude + height) - point elevation
                    double alt = extPoints.get(i + 2);
                    ringHeights[h++] = altitudeMode == Feature.AltitudeMode.Absolute
                            ? height - alt : (centerAlt + height) - alt;
                    tmp[i - (_startIndices[p] * 3)] = extPoints.get(i);
                    tmp[i - (_startIndices[p] * 3) + 1] = extPoints.get(i + 1);
                    tmp[i - (_startIndices[p] * 3) + 2] = extPoints.get(i + 2);
                }
                DoubleBuffer tmpBuffer = DoubleBuffer.wrap(tmp);
                // Extrude rings individually to avoid any lines connecting multiple rings from being formed
                extrudedWalls[p] = GLExtrude.extrudeRelative(Double.NaN, tmpBuffer, 3, closed, false, ringHeights);
                extrudedOutlines[p] = GLExtrude.extrudeOutline(Double.NaN, tmpBuffer, 3, closed, false, ringHeights);
                totalWallData += extrudedWalls[p].limit();
                totalOutlineData += extrudedOutlines[p].limit();
            }

            if(renderPoints != points)
                Unsafe.free(renderPoints);
            renderPoints = Unsafe.allocateDirect(totalOutlineData, DoubleBuffer.class);
            if(drawFill) {
                // ensure that the polyTriangles buffer contains the correct heights
                if (getAltitudeMode() == AltitudeMode.Relative) {
                    // XXX - can we avoid having to tessellate multiple times?
                    Unsafe.free(polyTriangles);
                    this.polyTriangles = Tessellate.polygon(this.points,
                                                        24,
                                                        3,
                                _numVertices,
                                _startIndices,
                                _numPolygons,
                                this.tessellated ? this.threshold : 0d,
                                true);
                    for (int i = 2; i < this.polyTriangles.limit(); i += 3) {
                        this.polyTriangles.put(i, centerAlt + this.polyTriangles.get(i));
                    }
                }

                int newPolyTrianglesCapacity = totalWallData;
                if(polyTriangles != null)
                    newPolyTrianglesCapacity += polyTriangles.limit();
                DoubleBuffer extrudedPolygon = Unsafe.allocateDirect(newPolyTrianglesCapacity, DoubleBuffer.class);
                if(polyTriangles != null)
                    extrudedPolygon.put(polyTriangles);
                Unsafe.free(polyTriangles);
                polyTriangles = extrudedPolygon;
            } else {
                Unsafe.free(polyTriangles);
                polyTriangles = null;
            }

            for(int i = 0; i < _numPolygons; i++) {
                extrudedOutlines[i].rewind();
                renderPoints.put(extrudedOutlines[i]);
                Unsafe.free(extrudedOutlines[i]);

                if(polyTriangles != null) {
                    extrudedWalls[i].rewind();
                    polyTriangles.put(extrudedWalls[i]);
                    Unsafe.free(extrudedWalls[i]);
                }
            }

            renderPoints.flip();
            numRenderPoints = renderPoints.limit() / 3;
            renderPointsDrawMode = GLES30.GL_LINES;
            if(polyTriangles != null)
                polyTriangles.flip();
        }
    }

    public void setGeometry(final Polygon polygon) {
        this.setGeometry(polygon, -1);
    }

    @Override
    protected void setGeometryImpl(Geometry geometry) {
        hasBeenExtruded = false;
        Polygon polygon = (Polygon)geometry;

        final int numRings = ((polygon.getExteriorRing() != null) ? 1 : 0) + polygon.getInteriorRings().size();
        if (numRings == 0) {
            this.numRenderPoints = 0;
        } else {
            super.setGeometryImpl(polygon.getExteriorRing());
        }
    }
}
