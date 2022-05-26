
package gov.tak.platform.widgets.opengl;
import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.opengl.IGLWidget;
import gov.tak.api.widgets.ILinearLayoutWidget;
import gov.tak.api.widgets.opengl.IGLWidgetSpi;
import gov.tak.platform.view.Gravity;

import gov.tak.api.widgets.IMapWidget;
import gov.tak.platform.commons.opengl.Matrix;

import gov.tak.platform.widgets.LinearLayoutWidget;
import gov.tak.platform.widgets.MapWidget;

import java.util.List;

public class GLLinearLayoutWidget extends GLLayoutWidget {

    public final static IGLWidgetSpi SPI = new IGLWidgetSpi() {
        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public GLWidget create(MapRenderer renderContext, IMapWidget subject) {
            if (subject instanceof ILinearLayoutWidget) {
                ILinearLayoutWidget llw = (ILinearLayoutWidget) subject;
                GLLinearLayoutWidget w = new GLLinearLayoutWidget(llw, renderContext);
                return w;
            } else {
                return null;
            }
        }
    };

    protected final ILinearLayoutWidget _subject;
    protected int _orientation = LinearLayoutWidget.VERTICAL;
    protected int _gravity = Gravity.START | Gravity.TOP;
    protected float _childrenWidth, _childrenHeight;

    public GLLinearLayoutWidget(ILinearLayoutWidget subject, MapRenderer ortho) {
        super(subject, ortho);
        _subject = subject;
        _width = subject.getWidth();
        _height = subject.getHeight();
        _orientation = subject.getOrientation();
        _backingColor = subject.getBackingColor();
    }

    @Override
    public void drawWidgetContent(DrawState drawState) {
        // Don't draw layout without defined width/height
        if (_width == 0 || _height == 0)
            return;

        DrawState localDrawState = drawState.clone();
        localDrawState.alphaMod *= _subject.getAlpha() / 255f;


        // Background color
        if (_backingColor != 0) {
            DrawState backDrawState = localDrawState.clone();
            Matrix.translateM(backDrawState.modelMatrix, 0, 0, -_pHeight, 0);
            drawBacking(backDrawState);
        }

        _sizeChanged = false;

        // Draw child widgets
        drawChildren(localDrawState, 0, 0);

        // Fade in progress - request render of next frame
        if (_subject.isFadingAlpha())
            getRenderContext().requestRefresh();

        localDrawState.recycle();
    }

    protected void drawChildren(DrawState drawState, float xOffset, float yOffset) {
        DrawState localDrawState = drawState.clone();
        Matrix.translateM(localDrawState.modelMatrix, 0, xOffset, yOffset, 0);

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

        List<IGLWidget> glChildren = getChildren();
        for (IGLWidget c : glChildren) {
            if (!c.getSubject().isVisible())
                continue;
            float[] size = MapWidget.getSize(c.getSubject(), true, false);
            float[] cMargin;
            if (c instanceof GLWidget)
                cMargin = ((GLWidget) c)._margin;
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
            c.drawWidget(localDrawState);

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

        localDrawState.recycle();
    }

    @Override
    public void onWidgetSizeChanged(IMapWidget w) {
        super.onWidgetSizeChanged(w);
        if (!(w instanceof ILinearLayoutWidget))
            return;
        ILinearLayoutWidget llw = (ILinearLayoutWidget) w;
        final int orientation = llw.getOrientation();
        final int gravity = llw.getGravity();
        final float[] childrenSize = llw.getChildrenSize();
        runOrQueueEvent(new Runnable() {
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
