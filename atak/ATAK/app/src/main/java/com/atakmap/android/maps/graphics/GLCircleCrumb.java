
package com.atakmap.android.maps.graphics;

import android.graphics.Color;

import com.atakmap.android.maps.CircleCrumb;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.track.crumb.Crumb;
import com.atakmap.android.track.crumb.Crumb.OnCrumbColorChangedListener;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapBatchable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GLCircleCrumb extends GLPointMapItem2 implements
        OnCrumbColorChangedListener, GLMapBatchable2 {
    private final static String TAG = GLCircleCrumb.class.getName();

    private static final int NUM_VERTICES = 7;
    private final int outlineColor;
    private int color;
    private boolean needsUpdate = true;
    private final ByteBuffer _verts;
    private final float radius = 10f;

    private float xpos;
    private float ypos;

    private final Icon icon;
    private final GLIcon _icon;

    public GLCircleCrumb(MapRenderer surface, Crumb subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SURFACE);

        this.color = subject.getColor();

        // NUM_VERTICES (NUM_VERTICES verts) * 2 (x and y) * 4 (floats) + closed
        _verts = com.atakmap.lang.Unsafe
                .allocateDirect((NUM_VERTICES * 2 * 4) + 8);
        _verts.order(ByteOrder.nativeOrder());
        this.icon = new Icon("asset://icons/circlecrumb.png");
        this._icon = new GLIcon(icon.getWidth(), icon.getHeight(),
                icon.getAnchorX(), icon.getAnchorY());
        GLImageCache.Entry iconEntry = GLRenderGlobals.get(context)
                .getImageCache().fetchAndRetain(
                        this.icon.getImageUri(Icon.STATE_DEFAULT), true);
        this._icon.updateCacheEntry(iconEntry);

        FloatBuffer fb = _verts.asFloatBuffer();
        createCircle(fb);

        fb.rewind();

        outlineColor = Color.BLACK;
    }

    private void createCircle(FloatBuffer cfb) {
        int i;
        float theta = 2 * 3.1415926f / NUM_VERTICES;
        float tangetial_factor = (float) Math.tan(theta);
        float radial_factor = (float) Math.cos(theta);

        float x = radius;
        float y = 0;

        for (i = 0; i < NUM_VERTICES; ++i) {
            cfb.put(x);
            cfb.put(y);

            float tanX = -y;
            float tanY = x;

            x += tanX * tangetial_factor;
            y += tanY * tangetial_factor;

            // correct using the radial factor

            x *= radial_factor;
            y *= radial_factor;
        }

        cfb.put(radius);
        cfb.put(0);
    }

    @Override
    public void onCrumbColorChanged(Crumb crumb) {
        this.color = crumb.getColor();
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        if (!MathUtils.hasBits(renderPass, this.renderPass))
            return;

        if (needsUpdate) {
            _updateOrthoVerts(ortho);
        }

        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                _verts);

        drawCircle(ortho);

        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
    }

    private void drawCircle(GLMapView ortho) {
        int alpha = Color.alpha(color);

        GLES20FixedPipeline.glColor4f(Color.red(color) / 255f,
                Color.green(color) / 255f, Color.blue(color) / 255f,
                alpha / 255f);
        GLES20FixedPipeline.glPushMatrix();

        GLES20FixedPipeline.glTranslatef(ortho.focusx, ortho.focusy, 0f);
        GLES20FixedPipeline.glTranslatef(xpos - ortho.focusx, ypos
                - ortho.focusy, 0f);
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_FAN,
                0, NUM_VERTICES);

        alpha = Color.alpha(outlineColor);

        // TODO: possibly do some anti-aliasing for this outline.
        // GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        // GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
        // GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

        GLES20FixedPipeline.glColor4f(Color.red(outlineColor) / 255f,
                Color.green(outlineColor) / 255f,
                Color.blue(outlineColor) / 255f, alpha / 255f);

        GLES20FixedPipeline.glLineWidth(2f);

        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINE_LOOP, 0,
                NUM_VERTICES);

        GLES20FixedPipeline.glPopMatrix();

    }

    private void _updateOrthoVerts(GLMapView ortho) {
        ortho.forward(point, ortho.scratch.pointF);
        xpos = ortho.scratch.pointF.x;
        ypos = ortho.scratch.pointF.y;
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        final GeoPoint p = item.getPoint();
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                point = p;

                final double N = point.getLatitude() + .0001; // about 10m
                final double S = point.getLatitude() - .0001;
                final double E = point.getLongitude() + .0001;
                final double W = point.getLongitude() - .0001;

                bounds.set(N, W, S, E);

                // IF YOU NEED TO, remove it and re-add it
                // if outside bounds of node
                dispatchOnBoundsChanged();

                needsUpdate = true;
            }
        });
    }

    @Override
    public void startObserving() {
        CircleCrumb crumb = (CircleCrumb) this.subject;
        super.startObserving();
        crumb.addCrumbColorListener(this);
    }

    @Override
    public void stopObserving() {
        CircleCrumb crumb = (CircleCrumb) this.subject;
        super.stopObserving();
        crumb.removeCrumbColorListener(this);
    }

    @Override
    public void batch(GLMapView view, GLRenderBatch2 batch, int renderPass) {
        if (!MathUtils.hasBits(renderPass, this.renderPass))
            return;

        view.forward(point, view.scratch.pointF);
        this.xpos = view.scratch.pointF.x;
        this.ypos = view.scratch.pointF.y;

        if (this._icon != null) {
            this._icon.validate();

            GLImage img = this._icon.getImage();
            if (img != null && img.getTexId() != 0) {
                float offx = -this._icon.getAnchorX();
                float offy = -(img.getHeight() - this._icon.getAnchorY() - 1);

                batch.batch(img.getTexId(),
                        this.xpos + offx, this.ypos + offy,
                        this.xpos + offx + this._icon.getWidth(), this.ypos
                                + offy,
                        this.xpos + offx + this._icon.getWidth(), this.ypos
                                + offy + this._icon.getHeight(),
                        this.xpos + offx,
                        this.ypos + offy + this._icon.getHeight(),
                        img.u0, img.v0,
                        img.u1, img.v0,
                        img.u1, img.v1,
                        img.u0, img.v1,
                        Color.red(this.color) / 255f,
                        Color.green(this.color) / 255f,
                        Color.blue(this.color) / 255f,
                        Color.alpha(this.color) / 255f);
            }
        }
    }
}
