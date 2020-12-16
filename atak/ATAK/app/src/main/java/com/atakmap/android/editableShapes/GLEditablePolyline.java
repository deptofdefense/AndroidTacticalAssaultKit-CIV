
package com.atakmap.android.editableShapes;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLImageCache;
import com.atakmap.android.maps.graphics.GLPolyline;
import com.atakmap.app.DeveloperOptions;
import com.atakmap.app.R;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLES20FixedPipeline;

class GLEditablePolyline extends GLPolyline implements
        EditablePolyline.OnEditableChangedListener {

    private GLImageCache.Entry _vertexIconEntry;
    private static final String _vertexIconRef = "resource://"
            + R.drawable.polyline_vertex;
    private boolean _editable;
    private final EditablePolyline _subject;

    private boolean disableVertexPointDrawing = false;

    public GLEditablePolyline(MapRenderer surface, EditablePolyline subject) {
        super(surface, subject);
        _editable = subject.getEditable();
        _subject = subject;

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
    public void draw(GLMapView ortho, int renderPass) {

        super.draw(ortho, renderPass);
        int dragIndex = -1;
        if (_editable && _verts2 != null
                && (_subject.shouldDisplayVertices(ortho.drawMapScale)
                        || _subject.hasMetaValue("dragInProgress")
                                && (dragIndex = _subject.getMetaInteger(
                                        "hit_index", -1)) > -1)) {
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
                // // HACK; don't actually need texture coords since these are points
                GLES20FixedPipeline.glPointSize(Math
                        .round(32 * MapView.DENSITY));

                // Set up points at which to draw vertexes
                GLES20FixedPipeline.glVertexPointer(_verts2Size,
                        GLES20FixedPipeline.GL_FLOAT, 0, _verts2);
                GLES20FixedPipeline.glEnableClientState(
                        GLES20FixedPipeline.GL_VERTEX_ARRAY);

                if (!disableVertexPointDrawing) {
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
        _editable = polyline.getEditable();
    }

    @Override
    protected void recomputeScreenRectangles(final GLMapView ortho) {
        super.recomputeScreenRectangles(ortho);

        // Update for polyline hit test
        _subject.updateScreenBounds(_screenRect, _partitionRects);
    }
}
