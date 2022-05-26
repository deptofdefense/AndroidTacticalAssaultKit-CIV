
package com.atakmap.android.maps.graphics.widgets;

import android.util.Pair;

import com.atakmap.android.maps.graphics.GLTriangle;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.ScrollLayoutWidget;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;

import gov.tak.api.annotation.DeprecatedApi;

/** @deprecated use {@link gov.tak.platform.widgets.opengl.GLScrollLayoutWidget} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class GLScrollLayoutWidget extends GLLinearLayoutWidget
        implements ScrollLayoutWidget.OnScrollChangedListener {

    public final static GLWidgetSpi SPI = new GLWidgetSpi() {
        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public GLWidget create(Pair<MapWidget, GLMapView> arg) {
            final MapWidget subject = arg.first;
            final GLMapView ortho = arg.second;
            if (subject instanceof ScrollLayoutWidget) {
                ScrollLayoutWidget llw = (ScrollLayoutWidget) subject;
                GLScrollLayoutWidget w = new GLScrollLayoutWidget(llw, ortho);
                w.startObserving(llw);
                return w;
            } else {
                return null;
            }
        }
    };

    protected final ScrollLayoutWidget _subject;
    protected float _scroll = 0;

    private static final float BAR_THICKNESS = 8f;
    private static final float BAR_COLOR = 95f / 255f;
    private GLTriangle.Fan _scrollBar;

    public GLScrollLayoutWidget(ScrollLayoutWidget subject, GLMapView ortho) {
        super(subject, ortho);
        _subject = subject;
    }

    @Override
    public void startObserving(MapWidget subject) {
        super.startObserving(subject);
        if (subject instanceof ScrollLayoutWidget)
            ((ScrollLayoutWidget) subject).addOnScrollListener(this);
    }

    @Override
    public void stopObserving(MapWidget subject) {
        super.stopObserving(subject);
        if (subject instanceof ScrollLayoutWidget)
            ((ScrollLayoutWidget) subject).removeOnScrollListener(this);
    }

    @Override
    public void drawWidgetContent() {
        boolean sizeChanged = _sizeChanged;
        super.drawWidgetContent();

        // Draw scroll bar on top
        boolean horizontal = _orientation == LinearLayoutWidget.HORIZONTAL;
        float barSize, barScale;
        if (horizontal) {
            barScale = _width / _childrenWidth;
            barSize = _width * barScale;
            if (barSize >= _width)
                return;
        } else {
            barScale = _height / _childrenHeight;
            barSize = _height * barScale;
            if (barSize >= _height)
                return;
        }

        if (sizeChanged || _scrollBar == null) {
            _scrollBar = new GLTriangle.Fan(2, 4);
            _scrollBar.setX(0, 0f);
            _scrollBar.setY(0, 0f);
            _scrollBar.setX(1, 0f);
            _scrollBar.setY(1, horizontal ? BAR_THICKNESS : barSize);
            _scrollBar.setX(2, horizontal ? barSize : BAR_THICKNESS);
            _scrollBar.setY(2, horizontal ? BAR_THICKNESS : barSize);
            _scrollBar.setX(3, horizontal ? barSize : BAR_THICKNESS);
            _scrollBar.setY(3, 0f);
        }

        float barPos = barScale * _scroll;
        GLES20FixedPipeline.glPushMatrix();
        if (horizontal)
            GLES20FixedPipeline.glTranslatef(_padding[LEFT] + barPos,
                    -_pHeight + 1f, 0f);
        else
            GLES20FixedPipeline.glTranslatef(_pWidth - BAR_THICKNESS - 1f,
                    -_pHeight + (_pHeight - barSize) - barPos
                            - _padding[TOP],
                    0f);
        GLES20FixedPipeline.glColor4f(BAR_COLOR, BAR_COLOR, BAR_COLOR, 1f);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        _scrollBar.draw();
        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glPopMatrix();
    }

    @Override
    protected void drawChildren(float xOffset, float yOffset) {
        if (_orientation == LinearLayoutWidget.HORIZONTAL)
            super.drawChildren(-_scroll, 0f);
        else
            super.drawChildren(0f, _scroll);
    }

    @Override
    public void onScrollChanged(ScrollLayoutWidget layout,
            final float scroll) {
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _scroll = scroll;
            }
        });
    }
}
