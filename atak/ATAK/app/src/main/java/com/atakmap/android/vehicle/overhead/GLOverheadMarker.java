
package com.atakmap.android.vehicle.overhead;

import android.graphics.Color;
import android.util.Pair;

import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.graphics.GLIcon;
import com.atakmap.android.maps.graphics.GLImage;
import com.atakmap.android.maps.graphics.GLImageCache;
import com.atakmap.android.maps.graphics.GLMapItem2;
import com.atakmap.android.maps.graphics.GLMapItemSpi3;
import com.atakmap.android.maps.graphics.GLPointMapItem2;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLText;

public class GLOverheadMarker extends GLPointMapItem2 implements
        OverheadMarker.OnChangedListener {

    public final static GLMapItemSpi3 SPI = new GLMapItemSpi3() {
        @Override
        public int getPriority() {
            // this SPI will be the fall-through if all else fails
            return -1;
        }

        @Override
        public GLMapItem2 create(Pair<MapRenderer, MapItem> arg) {
            if (arg.second instanceof OverheadMarker)
                return new GLOverheadMarker(arg.first,
                        (OverheadMarker) arg.second);
            return null;
        }
    };

    private static final String ICON_URI = "asset://icons/reference_point.png";
    private static final int ICON_SIZE = 32;

    private int _currentDraw = 0;
    private boolean _recompute = true;
    private float _spriteX, _spriteY, _spriteZ;
    private float _surfaceX, _surfaceY;
    private float _scaleX, _scaleY;
    private double _sizeX, _sizeY;
    private OverheadMarker _subject;

    private OverheadImage _image;
    private GLImage _glImage;
    private GLImageCache.Entry _imageEntry;
    private int _textureId = 0;
    private float _red, _green, _blue, _alpha;
    private GLText _glText;
    private String _label;
    private float _labelWidth, _labelHeight;
    private GLIcon _icon;
    private int _iconColor;

    public GLOverheadMarker(MapRenderer surface, OverheadMarker subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SURFACE
                | GLMapView.RENDER_PASS_SPRITES);
        _subject = subject;
        onChanged(_subject);
    }

    @Override
    public void startObserving() {
        super.startObserving();
        _subject.addOnChangedListener(this);
    }

    @Override
    public void stopObserving() {
        super.stopObserving();
        _subject.removeOnChangedListener(this);
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        if (_image != _subject.getImage()) {
            _image = _subject.getImage();
            reloadImage(ortho);
        }
        boolean renderSprites = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SPRITES);
        boolean renderSurface = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SURFACE);
        _recompute = _currentDraw != ortho.drawVersion
                || (_imageEntry != null && _textureId != _imageEntry
                        .getTextureId());
        _textureId = _imageEntry == null ? 0 : _imageEntry.getTextureId();
        _currentDraw = ortho.drawVersion;

        if (renderSurface)
            projectSurface(ortho);
        if (renderSprites)
            projectSprite(ortho);

        boolean minimize = _imageEntry == null || (Math.hypot(
                _imageEntry.getImageWidth() * _scaleX,
                _imageEntry.getImageHeight() * _scaleY) / 2d) < ICON_SIZE / 2;

        // Minimized dot view
        if (minimize) {
            if (!renderSprites)
                return;
            float spriteY = _spriteY;
            if (ortho.drawTilt > 0)
                spriteY += ICON_SIZE;

            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(_spriteX, spriteY, _spriteZ);

            // Draw generic point
            drawIcon();

            if (GLMapSurface.SETTING_displayLabels
                    && ortho.drawMapScale >= this.subject.getMetaDouble(
                            "minRenderScale",
                            CapturePP.DEFAULT_MIN_RENDER_SCALE)) {
                // Draw label text
                drawLabel(ortho);
            }

            GLES20FixedPipeline.glPopMatrix();
        }

        // Full vehicle view
        else if (_textureId != 0) {
            if (!renderSurface)
                return;
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(_surfaceX, _surfaceY, 0f);
            GLES20FixedPipeline.glRotatef((float) (360 - _subject.getAzimuth()
                    + ortho.drawRotation), 0, 0, 1f);
            GLES20FixedPipeline.glScalef(_scaleX, _scaleY, 1f);
            if (_glImage == null)
                _glImage = new GLImage(_imageEntry.getTextureId(),
                        _imageEntry.getTextureWidth(),
                        _imageEntry.getTextureHeight(),
                        _imageEntry.getImageTextureX(),
                        _imageEntry.getImageTextureY(),
                        _imageEntry.getImageTextureWidth(),
                        _imageEntry.getImageTextureHeight(),
                        -_imageEntry.getImageWidth() / 2,
                        -_imageEntry.getImageHeight() / 2,
                        _imageEntry.getImageWidth(),
                        _imageEntry.getImageHeight());
            _glImage.draw(_red, _green, _blue, _alpha);
            GLES20FixedPipeline.glPopMatrix();
        }
    }

    private void reloadImage(GLMapView ortho) {
        // XXX - See ATAK-11817 - release() is not safe to call if there's
        // a chance we may use this resource later on
        /*if (_imageEntry != null)
            _imageEntry.release();*/
        GLRenderGlobals globals = GLRenderGlobals.get(ortho.getSurface());
        _imageEntry = _image == null || globals == null ? null
                : globals
                        .getImageCache().fetchAndRetain(_image.imageUri, false);
        _glImage = null;
    }

    private void projectSurface(GLMapView ortho) {
        if (!_recompute)
            return;
        GeoPoint p = _subject.getPoint();
        double rot = _subject.getAzimuth();
        ortho.scratch.geo.set(p);
        double unwrap = ortho.idlHelper.getUnwrap(_subject.getBounds());
        forward(ortho, ortho.scratch.geo, ortho.scratch.pointD, unwrap);
        _surfaceX = (float) ortho.scratch.pointD.x;
        _surfaceY = (float) ortho.scratch.pointD.y;
        //_posZ = (float) ortho.scratch.pointD.z;
        if (_imageEntry != null && _textureId != 0) {
            ortho.scratch.geo.set(DistanceCalculations.computeDestinationPoint(
                    p, rot + 90, _image.width / 2));
            forward(ortho, ortho.scratch.geo, ortho.scratch.pointD, unwrap);
            _sizeX = Math.hypot(ortho.scratch.pointD.x - _surfaceX,
                    ortho.scratch.pointD.y - _surfaceY) * 2;

            ortho.scratch.geo.set(DistanceCalculations.computeDestinationPoint(
                    p, rot, _image.length / 2));
            forward(ortho, ortho.scratch.geo, ortho.scratch.pointD, unwrap);
            _sizeY = Math.hypot(ortho.scratch.pointD.x - _surfaceX,
                    ortho.scratch.pointD.y - _surfaceY) * 2;

            _scaleX = (float) (_sizeX / _imageEntry.getImageWidth());
            _scaleY = (float) (_sizeY / _imageEntry.getImageHeight());
        } else {
            _scaleX = 0;
            _scaleY = 0;
        }
        int color = _subject.getColor();
        _iconColor = (color & 0xFFFFFF) + 0xFF000000;
        _red = Color.red(color) / 255f;
        _green = Color.green(color) / 255f;
        _blue = Color.blue(color) / 255f;
        _alpha = Color.alpha(color) / 255f;
        _spriteX = _spriteY = _spriteZ = Float.NaN;
    }

    private void projectSprite(GLMapView ortho) {
        if (!Float.isNaN(_spriteX))
            return;
        GeoPoint p = _subject.getPoint();
        ortho.scratch.geo.set(p);
        double unwrap = ortho.idlHelper.getUnwrap(_subject.getBounds());
        forward(ortho, ortho.scratch.geo, ortho.scratch.pointD, unwrap);
        _spriteX = (float) ortho.scratch.pointD.x;
        _spriteY = (float) ortho.scratch.pointD.y;
        _spriteZ = (float) ortho.scratch.pointD.z;
    }

    private void drawIcon() {
        GLRenderGlobals globals = GLRenderGlobals.get(this.context);
        if (globals == null)
            return;
        if (_icon == null) {
            _icon = new GLIcon(ICON_SIZE, ICON_SIZE,
                    ICON_SIZE / 2, ICON_SIZE / 2);
            GLImageCache.Entry iconEntry = globals.getImageCache()
                    .fetchAndRetain(ICON_URI, true);
            _icon.updateCacheEntry(iconEntry);
        }
        _icon.validate();
        if (_icon.isEntryInvalid())
            return;
        _icon.drawColor(_iconColor);
    }

    private void updateLabel(String newLabel) {
        _label = newLabel;
        if (_glText == null)
            _glText = GLText.getInstance(MapView.getDefaultTextFormat());
        _labelWidth = _glText.getStringWidth(_label);
        _labelHeight = _glText.getStringHeight();
    }

    private void drawLabel(GLMapView ortho) {
        GLRenderGlobals globals = GLRenderGlobals.get(this.context);
        if (globals == null)
            return;
        GLNinePatch smallNinePatch = globals.getSmallNinePatch();
        if (smallNinePatch == null)
            return;
        String label = _subject.getTitle();
        if (FileSystemUtils.isEmpty(label))
            return;
        if (!label.equals(_label))
            updateLabel(label);
        GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.6f);
        GLES20FixedPipeline.glPushMatrix();

        float y = _icon.getHeight() - _icon.getAnchorY() - 1;
        if (ortho.drawTilt <= 0)
            y = -(y + _labelHeight);
        GLES20FixedPipeline.glTranslatef(-_labelWidth / 2, y, 0f);
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(-4f, -_glText.getDescent(), 0f);
        smallNinePatch.draw(_labelWidth + 8f, _labelHeight);
        GLES20FixedPipeline.glPopMatrix();
        _glText.draw(_label, 1.0f, 1.0f, 1.0f, 1.0f);
        GLES20FixedPipeline.glPopMatrix();
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        // Handled by listener below
    }

    @Override
    public void onChanged(OverheadMarker marker) {
        _currentDraw = 0;
        final GeoPoint p = _subject.getPoint();
        final GeoBounds b = _subject.getBounds();
        if (b != null) {
            context.queueEvent(new Runnable() {
                @Override
                public void run() {
                    point = p;
                    synchronized (bounds) {
                        bounds.set(b);
                    }
                    dispatchOnBoundsChanged();
                }
            });
        }
    }
}
