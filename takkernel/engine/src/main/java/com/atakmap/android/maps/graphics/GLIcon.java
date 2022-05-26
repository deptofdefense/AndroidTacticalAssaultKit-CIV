
package com.atakmap.android.maps.graphics;

import android.graphics.Color;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.coremap.maps.assets.Icon;

import gov.tak.api.engine.Shader;
import gov.tak.platform.commons.opengl.GLES30;
import gov.tak.platform.commons.opengl.Matrix;

public class GLIcon {

    public GLIcon(int iconWidth, int iconHeight, int anchorx, int anchory) {
        _iconWidth = Math.round(iconWidth * GLRenderGlobals.getRelativeScaling());
        _iconHeight = Math.round(iconHeight * GLRenderGlobals.getRelativeScaling());
        if (anchorx < 0)
            _anchorx = anchorx;
        else
            _anchorx = Math.round(anchorx * GLRenderGlobals.getRelativeScaling());
        if (anchory < 0)
            _anchory = anchory;
        else
            _anchory = Math.round(anchory * GLRenderGlobals.getRelativeScaling());
        this.entryInvalid = false;
    }

    public void updateCacheEntry(GLImageCache.Entry entry) {
        if (_updateEntry != null) {
            _updateEntry.release();
        }
        _updateEntry = entry;
        this.entryInvalid = false;
    }

    public void validate() {
        _updateImage();
    }

    private void _updateImage() {

        if (_updateEntry != null && _updateEntry.getTextureId() != 0) {
            if (_currentEntry != null) {
                _currentEntry.release();
            }
            _currentEntry = _updateEntry;
            _updateEntry = null;

            float iconWidth = _currentEntry.getImageWidth();
            float iconHeight = _currentEntry.getImageHeight();
            if (_iconWidth < 0) {
                _iconWidth = Math.round(iconWidth * GLRenderGlobals.getRelativeScaling());
            }
            if (_iconHeight < 0) {
                _iconHeight = Math.round(iconHeight * GLRenderGlobals.getRelativeScaling());
            }

            _iconImage = new GLImage(
                    _currentEntry.getTextureId(),
                    _currentEntry.getTextureWidth(),
                    _currentEntry.getTextureHeight(),
                    _currentEntry.getImageTextureX() + 0.5f,
                    _currentEntry.getImageTextureY() + 0.5f,
                    _currentEntry.getImageTextureWidth() - 1.0f,
                    _currentEntry.getImageTextureHeight() - 1.0f,
                    0, 0,
                    _iconWidth, _iconHeight);

            if (_anchorx == Icon.ANCHOR_CENTER) {
                _anchorx = (int) (_iconImage.getWidth() / 2);
            }
            if (_anchory == Icon.ANCHOR_CENTER) {
                _anchory = (int) (_iconImage.getHeight() / 2);
            }
        } else if (_updateEntry != null && _updateEntry.isInvalid()) {
            this.entryInvalid = true;
            _updateEntry.release();
            _updateEntry = null;

            // XXX - should _currentEntry be invalidated ???
        }
    }

    public void drawColor(Shader shader, float[] modelView, int color) {
        _updateImage();
        if (_iconImage != null) {
            float offx;
            float offy;
            float scale = 1f;

            offx = scale * -_anchorx;
            offy = scale * -(_iconImage.getHeight() - _anchory - 1);

            float[] localModelView = modelView.clone();

            Matrix.translateM(localModelView, 0, offx, offy, 0);
            Matrix.scaleM(localModelView, 0, scale, scale, 1);

            shader.setModelView(localModelView);

            _iconImage.draw( shader,Color.red(color) / 255f, Color.green(color) / 255f,
                    Color.blue(color) / 255f, Color.alpha(color) / 255f);
            shader.setModelView(modelView);
        }
    }
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void drawColor(int color) {
        _updateImage();
        if (_iconImage != null) {

            Shader shader = Shader.create(Shader.FLAG_2D | Shader.FLAG_TEXTURED);

            int prevProgram = shader.useProgram(true);

            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, SCRATCH_MATRIX, 0);
            shader.setProjection(SCRATCH_MATRIX);
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, SCRATCH_MATRIX, 0);

            float offx;
            float offy;
            float scale = 1f;

            offx = scale * -_anchorx;
            offy = scale * -(_iconImage.getHeight() - _anchory - 1);

            Matrix.translateM(SCRATCH_MATRIX, 0, offx, offy, 0f);
            Matrix.scaleM(SCRATCH_MATRIX, 0, scale, scale, 1f);
            shader.setModelView(SCRATCH_MATRIX);
            _iconImage.draw(shader,Color.red(color) / 255f, Color.green(color) / 255f,
                    Color.blue(color) / 255f, Color.alpha(color) / 255f);

            GLES30.glUseProgram(prevProgram);
        }
    }

    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void draw() {
        _updateImage();
        if (_iconImage != null) {
            Shader shader = Shader.create(Shader.FLAG_2D | Shader.FLAG_TEXTURED);

            int prevProgram = shader.useProgram(true);

            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, SCRATCH_MATRIX, 0);
            shader.setProjection(SCRATCH_MATRIX);
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, SCRATCH_MATRIX, 0);

            float offx;
            float offy;
            float scale = 1f;

            offx = scale * -_anchorx;
            offy = scale * -(_iconImage.getHeight() - _anchory - 1);

            Matrix.translateM(SCRATCH_MATRIX, 0, offx, offy, 0f);
            Matrix.scaleM(SCRATCH_MATRIX, 0, scale, scale, 1f);
            shader.setModelView(SCRATCH_MATRIX);
            _iconImage.draw(shader);

            GLES30.glUseProgram(prevProgram);

        }
    }

    public void release() {
        if (_currentEntry != null) {
            _currentEntry.release();
            _currentEntry = null;
        }
        if (_updateEntry != null) {
            _updateEntry.release();
            _updateEntry = null;
        }
        _iconImage = null;
    }

    public int getWidth() {
        return _iconWidth;
    }

    public int getHeight() {
        return _iconHeight;
    }

    public int getAnchorX() {
        return _anchorx;
    }

    public int getAnchorY() {
        return _anchory;
    }

    public GLImage getImage() {
        return _iconImage;
    }

    public boolean isEntryInvalid() {
        return (_updateEntry != null && _updateEntry.isInvalid()) ||
                (_updateEntry == null && this.entryInvalid);
    }

    private int _iconWidth, _iconHeight;
    private int _anchorx, _anchory;
    private GLImage _iconImage;
    private GLImageCache.Entry _currentEntry;
    private GLImageCache.Entry _updateEntry;
    private boolean entryInvalid;
    private final static float[] SCRATCH_MATRIX = new float[16];

}
