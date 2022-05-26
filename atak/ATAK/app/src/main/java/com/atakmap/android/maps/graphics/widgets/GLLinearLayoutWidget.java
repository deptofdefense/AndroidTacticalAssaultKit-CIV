
package com.atakmap.android.maps.graphics.widgets;

import android.util.Pair;
import android.view.Gravity;

import com.atakmap.android.maps.graphics.GLTriangle;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MapWidget2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;

import java.util.List;

import gov.tak.api.annotation.DeprecatedApi;

/** @deprecated use {@link gov.tak.platform.widgets.opengl.GLLinearLayoutWidget} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class GLLinearLayoutWidget extends GLLayoutWidget {

    public final static GLWidgetSpi SPI = new GLWidgetSpi() {
        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public GLWidget create(Pair<MapWidget, GLMapView> arg) {
            final MapWidget subject = arg.first;
            final GLMapView ortho = arg.second;
            if (subject instanceof LinearLayoutWidget) {
                LinearLayoutWidget llw = (LinearLayoutWidget) subject;
                GLLinearLayoutWidget w = new GLLinearLayoutWidget(llw, ortho);
                w.startObserving(llw);
                return w;
            } else {
                return null;
            }
        }
    };

    protected final LinearLayoutWidget _subject;
    protected int _orientation = LinearLayoutWidget.VERTICAL;
    protected int _gravity = Gravity.START | Gravity.TOP;
    protected float _childrenWidth, _childrenHeight;
    private GLTriangle.Fan _cropStencil;

    public GLLinearLayoutWidget(LinearLayoutWidget subject, GLMapView ortho) {
        super(subject, ortho);
        _subject = subject;
        _width = subject.getWidth();
        _height = subject.getHeight();
        _orientation = subject.getOrientation();
        _backingColor = subject.getBackingColor();
    }

    @Override
    public void drawWidgetContent() {
        // Don't draw layout without defined width/height
        if (_width == 0 || _height == 0)
            return;

        // Alpha mod
        GLES20FixedPipeline.glPushAlphaMod(_subject.getAlpha() / 255f);

        // Background color
        if (_backingColor != 0) {
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(0f, -_pHeight, 0f);
            drawBacking();
            GLES20FixedPipeline.glPopMatrix();
        }

        // Create cropping stencil
        if (_sizeChanged || _cropStencil == null) {
            _cropStencil = new GLTriangle.Fan(2, 4);
            _cropStencil.setX(0, 0f);
            _cropStencil.setY(0, 0f);
            _cropStencil.setX(1, 0f);
            _cropStencil.setY(1, _height);
            _cropStencil.setX(2, _width);
            _cropStencil.setY(2, _height);
            _cropStencil.setX(3, _width);
            _cropStencil.setY(3, 0f);
        }
        _sizeChanged = false;

        // Enable stencil for cropping out overflowing children draw operations

        GLES20FixedPipeline.glClear(GLES20FixedPipeline.GL_STENCIL_BUFFER_BIT);
        GLES20FixedPipeline.glStencilMask(0xFFFFFFFF);
        GLES20FixedPipeline.glStencilFunc(GLES20FixedPipeline.GL_ALWAYS, 0x1,
                0x1);
        GLES20FixedPipeline.glStencilOp(GLES20FixedPipeline.GL_KEEP,
                GLES20FixedPipeline.GL_KEEP,
                GLES20FixedPipeline.GL_INCR);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_STENCIL_TEST);
        GLES20FixedPipeline.glColorMask(false, false, false, false);
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(_padding[LEFT], -_pHeight
                + _padding[BOTTOM], 0f);
        GLES20FixedPipeline.glColor4f(1f, 0f, 0f, 1f);
        _cropStencil.draw();
        GLES20FixedPipeline.glPopMatrix();
        GLES20FixedPipeline.glStencilMask(0xFFFFFFFF);
        GLES20FixedPipeline.glStencilFunc(GLES20FixedPipeline.GL_EQUAL, 0x1,
                0x1);
        GLES20FixedPipeline.glStencilOp(GLES20FixedPipeline.GL_KEEP,
                GLES20FixedPipeline.GL_KEEP,
                GLES20FixedPipeline.GL_KEEP);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_STENCIL_TEST);
        GLES20FixedPipeline.glColorMask(true, true, true, true);

        // Draw child widgets
        drawChildren(0, 0);

        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_STENCIL_TEST);
        GLES20FixedPipeline.glPopAlphaMod();

        // Fade in progress - request render of next frame
        if (_subject.isFadingAlpha())
            getSurface().requestRender();
    }

    protected void drawChildren(float xOffset, float yOffset) {
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(xOffset, yOffset, 0f);

        float childTop, childLeft;
        int hGrav = _gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
        int vGrav = _gravity & Gravity.VERTICAL_GRAVITY_MASK;

        // Initial child left offset
        if (hGrav == Gravity.RIGHT)
            childLeft = _pWidth - _padding[RIGHT];
        else if (hGrav == Gravity.CENTER_HORIZONTAL)
            childLeft = _padding[LEFT] + ((_width - _childrenWidth) / 2);
        else
            childLeft = _padding[LEFT];

        // Initial vertical offset
        if (vGrav == Gravity.CENTER_VERTICAL)
            childTop = _padding[TOP] + ((_height - _childrenHeight) / 2);
        else if (vGrav == Gravity.BOTTOM)
            childTop = _pHeight - _padding[BOTTOM];
        else
            childTop = _padding[TOP];

        List<GLWidget> glChildren = getChildren();
        for (GLWidget c : glChildren) {
            if (!c.getSubject().isVisible())
                continue;
            float[] size = MapWidget2.getSize(c.getSubject(), true, false);
            float[] cMargin;
            if (c instanceof GLWidget2)
                cMargin = ((GLWidget2) c)._margin;
            else
                cMargin = new float[] {
                        0, 0, 0, 0
                };

            if (size == null || cMargin == null)
                continue;

            // Pre-draw increment offset
            float left = childLeft, top = childTop;

            // Horizontal offset
            if (hGrav == Gravity.RIGHT)
                left -= size[0] + cMargin[RIGHT];
            else if (_orientation == LinearLayoutWidget.VERTICAL
                    && hGrav == Gravity.CENTER_HORIZONTAL)
                left += (_childrenWidth - size[0]) / 2;
            else
                left += cMargin[LEFT];

            // Vertical offset
            if (vGrav == Gravity.BOTTOM)
                top -= size[1] + cMargin[BOTTOM];
            else if (_orientation == LinearLayoutWidget.HORIZONTAL
                    && vGrav == Gravity.CENTER_VERTICAL)
                top += (_childrenHeight - size[1]) / 2;
            else
                top += cMargin[TOP];

            // Draw child here
            c.setX(left);
            c.setY(top);

            if (c instanceof GLWidget) {
                ((GLWidget) c).drawWidget();
            } else {
                gov.tak.platform.widgets.opengl.GLWidget.DrawState drawState = drawStateFromFixedPipeline(
                        orthoView);
                c.drawWidget(drawState);
            }
            // Update position for click events
            c.getSubject().setPoint(left + xOffset, top - yOffset);

            // Post-draw increment offset
            if (_orientation == LinearLayoutWidget.HORIZONTAL) {
                // Horizontal offset
                childLeft = left;
                if (hGrav == Gravity.RIGHT)
                    childLeft -= cMargin[LEFT];
                else
                    childLeft += size[0] + cMargin[RIGHT];
            } else {
                // Vertical offset
                childTop = top;
                if (vGrav == Gravity.BOTTOM)
                    childTop -= cMargin[TOP];
                else
                    childTop += size[1] + cMargin[BOTTOM];
            }
        }
        GLES20FixedPipeline.glPopMatrix();
    }

    @Override
    public void onWidgetSizeChanged(MapWidget2 w) {
        super.onWidgetSizeChanged(w);
        if (!(w instanceof LinearLayoutWidget))
            return;
        LinearLayoutWidget llw = (LinearLayoutWidget) w;
        final int orientation = llw.getOrientation();
        final int gravity = llw.getGravity();
        final float[] childrenSize = llw.getChildrenSize();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _orientation = orientation;
                _gravity = gravity;
                _childrenWidth = childrenSize[0];
                _childrenHeight = childrenSize[1];
            }
        });
    }
}
