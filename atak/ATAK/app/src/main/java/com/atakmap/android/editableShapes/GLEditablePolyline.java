
package com.atakmap.android.editableShapes;

import com.atakmap.android.maps.graphics.GLImageCache;
import com.atakmap.android.maps.graphics.GLPolyline;
import com.atakmap.app.DeveloperOptions;
import com.atakmap.app.R;
import com.atakmap.map.Globe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.hittest.HitTestResult;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;

class GLEditablePolyline extends GLPolyline implements
        EditablePolyline.OnEditableChangedListener {

    private GLImageCache.Entry _vertexIconEntry;
    private static final String _vertexIconRef = "resource://"
            + R.drawable.polyline_vertex_cropped;
    private boolean _editable;
    private final EditablePolyline _subject;

    private boolean disableVertexPointDrawing = false;

    public GLEditablePolyline(MapRenderer surface, EditablePolyline subject) {
        super(surface, subject);
        _editable = subject.getEditable();
        _subject = subject;
        _verts2Size = 3;

        // working around an issue with the MPU 5 and the GL_POINTS
        // this is temporary and should be removed as soon as possible but for the
        // purposes of 4.1.0.1, this is probably the least intrusive bandaid.
        disableVertexPointDrawing = DeveloperOptions
                .getIntOption("disable-vertex-points", 0) == 1;
    }

    @Override
    public void startObserving() {
        final EditablePolyline polyline = (EditablePolyline) this.subject;
        super.startObserving();
        polyline.addOnEditableChangedListener(this);
    }

    @Override
    public void stopObserving() {
        final EditablePolyline polyline = (EditablePolyline) this.subject;
        super.stopObserving();
        polyline.removeOnEditableChangedListener(this);
    }

    @Override
    protected boolean uses2DPointBuffer() {
        // We need altitudes when rendering vertex handles in editable mode
        // since they're rendered on sprite pass
        return !_editable && super.uses2DPointBuffer();
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        super.draw(ortho, renderPass);

        if (disableVertexPointDrawing || !MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SPRITES))
            return;

        int dragIndex = -1;

        boolean drawVerts = _editable && _verts2 != null
                && currentDraw == ortho.currentScene.drawVersion;

        if (drawVerts) {
            if (_subject.hasMetaValue("dragInProgress"))
                drawVerts = (dragIndex = _subject.getMetaInteger("hit_index",
                        -1)) > -1;
            else
                drawVerts = _subject.shouldDisplayVertices(
                        Globe.getMapResolution(ortho.getSurface().getDpi(),
                                ortho.currentScene.drawMapResolution));
        }

        if (drawVerts) {
            // float xpos = (float)ortho.longitudeToX(_points[1]);
            // float ypos = (float)ortho.latitudeToY(_points[0]);

            GLES20FixedPipeline.glPushMatrix();

            // GLES20FixedPipeline.glTranslatef(ortho.focusx, ortho.focusy, 0f);
            // GLES20FixedPipeline.glRotatef((float)ortho.drawRotation, 0f, 0f, 1f);
            // GLES20FixedPipeline.glTranslatef(xpos - ortho.focusx, ypos - ortho.focusy, 0f);

            if (_vertexIconEntry == null) {
                final GLRenderGlobals glg = GLRenderGlobals
                        .get(context);
                if (glg != null) {
                    GLImageCache imageCache = glg.getImageCache();
                    _vertexIconEntry = imageCache.fetchAndRetain(
                            _vertexIconRef, false);
                }
            }

            if (_vertexIconEntry != null
                    && _vertexIconEntry.getTextureId() != 0) {

                // Set up transparency
                GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
                GLES20FixedPipeline.glBlendFunc(
                        GLES20FixedPipeline.GL_SRC_ALPHA,
                        GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

                // Set up vertex icon texture to use
                GLES20FixedPipeline.glBindTexture(
                        GLES20FixedPipeline.GL_TEXTURE_2D,
                        _vertexIconEntry.getTextureId());

                // Scale icon relative to line width
                int pixelSize = Math.round((Math.max(4, strokeWeight) * 3)
                        / ortho.currentPass.relativeScaleHint);
                GLES20FixedPipeline.glPointSize(pixelSize);

                // Have the vertex "sit" on the ground when the camera is tilted
                // so it doesn't clip into the terrain
                if (getAltitudeMode() == AltitudeMode.ClampToGround
                        && ortho.currentScene.drawTilt > 0)
                    GLES20FixedPipeline.glTranslatef(0f, pixelSize / 2f, 0f);

                // Set up points at which to draw vertexes
                GLES20FixedPipeline.glVertexPointer(_verts2Size,
                        GLES20FixedPipeline.GL_FLOAT, 0, _verts2);
                GLES20FixedPipeline.glEnableClientState(
                        GLES20FixedPipeline.GL_VERTEX_ARRAY);

                if (dragIndex > -1 && dragIndex < this.numPoints) {
                    // Draw single vertex being dragged
                    GLES20FixedPipeline.glDrawArrays(
                            GLES20FixedPipeline.GL_POINTS, dragIndex, 1);
                } else {
                    // Draw all vertices
                    GLES20FixedPipeline.glDrawArrays(
                            GLES20FixedPipeline.GL_POINTS, 0,
                            this.numPoints);
                }

                // Disable features
                GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
                GLES20FixedPipeline
                        .glDisableClientState(
                                GLES20FixedPipeline.GL_VERTEX_ARRAY);
            }

            GLES20FixedPipeline.glPopMatrix();
        }
    }

    @Override
    public void onEditableChanged(EditablePolyline polyline) {
        final boolean editable = polyline.getEditable();
        context.queueEvent(new Runnable() {
            @Override
            public void run() {
                _editable = editable;
                updatePointsImpl();
            }
        });
    }

    @Override
    protected HitTestResult hitTestImpl(MapRenderer3 renderer,
            HitTestQueryParameters params) {
        HitTestResult result = super.hitTestImpl(renderer, params);
        if (result != null) {
            // Use the proper menu based on whether we hit a line or point
            String menu = _subject.getShapeMenu();
            if (_editable)
                menu = result.type == HitTestResult.Type.LINE
                        ? _subject.getLineMenu()
                        : _subject.getCornerMenu();
            _subject.setRadialMenu(menu);
            return result;
        }
        return null;
    }
}
