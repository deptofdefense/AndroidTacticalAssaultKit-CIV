
package com.atakmap.android.maps.graphics.widgets;

import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.ShapeWidget;
import com.atakmap.android.widgets.ShapeWidget.OnStrokeColorChangedListener;
import com.atakmap.android.widgets.ShapeWidget.OnStrokeWeightChangedListener;
import com.atakmap.map.opengl.GLMapView;

import gov.tak.api.annotation.DeprecatedApi;

/** @deprecated use {@link gov.tak.platform.widgets.opengl.GLShapeWidget} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public abstract class GLShapeWidget extends GLWidget2 implements
        OnStrokeColorChangedListener,
        OnStrokeWeightChangedListener {

    public GLShapeWidget(ShapeWidget subject, GLMapView orthoView) {
        super(subject, orthoView);
        strokeColor = subject.getStrokeColor();
        strokeWeight = subject.getStrokeWeight();
    }

    @Override
    public void startObserving(MapWidget subject) {
        super.startObserving(subject);
        if (subject instanceof ShapeWidget)
            ((ShapeWidget) subject).addOnStrokeColorChangedListener(this);
    }

    @Override
    public void stopObserving(MapWidget subject) {
        super.stopObserving(subject);
        if (subject instanceof ShapeWidget)
            ((ShapeWidget) subject).removeOnStrokeColorChangedListener(this);
    }

    @Override
    public void onStrokeColorChanged(ShapeWidget shape) {
        final int color = shape.getStrokeColor();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                strokeColor = color;
            }
        });
    }

    @Override
    public void onStrokeWeightChanged(ShapeWidget shape) {
        final float weight = shape.getStrokeWeight();
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                strokeWeight = weight;
            }
        });
    }

    float strokeWeight;
    int strokeColor;
}
