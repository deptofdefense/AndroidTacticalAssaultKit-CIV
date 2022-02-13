
package com.atakmap.android.maps.graphics.widgets;

import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MapWidget2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;

import gov.tak.api.annotation.DeprecatedApi;

/**
 * Map widget with view properties such as width, height, margin, and padding
 * @deprecated use {@link gov.tak.platform.widgets.opengl.GLWidget}
 */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public abstract class GLWidget2 extends GLWidget implements
        MapWidget2.OnWidgetSizeChangedListener {

    public static final int LEFT = 0;
    public static final int TOP = 1;
    public static final int RIGHT = 2;
    public static final int BOTTOM = 3;

    protected final MapWidget2 subject;
    protected float _width = 0, _pWidth = 0, _height = 0, _pHeight = 0;
    protected float[] _margin, _padding;
    protected boolean _sizeChanged = true;

    public GLWidget2(MapWidget2 subject, GLMapView orthoView) {
        super(subject, orthoView);
        this.subject = subject;
        onWidgetSizeChanged(subject);
    }

    @Override
    public void startObserving(MapWidget subject) {
        super.startObserving(subject);
        if (subject instanceof MapWidget2)
            ((MapWidget2) subject).addOnWidgetSizeChangedListener(this);
    }

    @Override
    public void stopObserving(MapWidget subject) {
        super.stopObserving(subject);
        if (subject instanceof MapWidget2)
            ((MapWidget2) subject).removeOnWidgetSizeChangedListener(this);
    }

    @Override
    public void onWidgetSizeChanged(MapWidget2 widget) {
        final float width = widget.getWidth();
        final float height = widget.getHeight();
        final float[] margin = widget.getMargins();
        final float[] padding = widget.getPadding();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _width = width;
                _pWidth = width + padding[LEFT] + padding[RIGHT];
                _height = height;
                _pHeight = height + padding[TOP] + padding[BOTTOM];
                _margin = margin;
                _padding = padding;
                _sizeChanged = true;
            }
        });
    }

    @Override
    public void drawWidget() {
        if (subject != null && subject.isVisible() && _margin != null
                && _padding != null) {
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(x, -y, 0f);
            drawWidgetContent();
            GLES20FixedPipeline.glPopMatrix();
        }
    }
}
