
package com.atakmap.android.maps.graphics;

import android.graphics.Color;

import com.atakmap.map.AtakMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.coremap.maps.assets.Icon;

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

            _iconImage = new GLImage(_currentEntry.getTextureId(),
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

    public void drawColor(int color) {
        _updateImage();
        if (_iconImage != null) {
            GLES20FixedPipeline.glPushMatrix();
            float offx;
            float offy;
            float scale = 1f;

            offx = scale * -_anchorx;
            offy = scale * -(_iconImage.getHeight() - _anchory - 1);

            GLES20FixedPipeline.glTranslatef(offx, offy, 0f);
            GLES20FixedPipeline.glScalef(scale, scale, 1f);
            _iconImage.draw(Color.red(color) / 255f, Color.green(color) / 255f,
                    Color.blue(color) / 255f, Color.alpha(color) / 255f);
            GLES20FixedPipeline.glPopMatrix();
        }
    }

    public void draw() {
        _updateImage();
        if (_iconImage != null) {
            GLES20FixedPipeline.glPushMatrix();
            float offx = -_anchorx;
            float offy = -(_iconImage.getHeight() - _anchory - 1);

            GLES20FixedPipeline.glTranslatef(offx, offy, 0f);
            _iconImage.draw();
            GLES20FixedPipeline.glPopMatrix();
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
}
