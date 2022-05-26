
package gov.tak.platform.widgets.opengl;

import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.IShapeWidget;
import gov.tak.platform.widgets.ShapeWidget;

public abstract class GLShapeWidget extends GLWidget implements
        ShapeWidget.OnStrokeColorChangedListener,
        ShapeWidget.OnStrokeWeightChangedListener {

    public GLShapeWidget(IShapeWidget subject, MapRenderer orthoView) {
        super(subject, orthoView);
        strokeColor = subject.getStrokeColor();
        strokeWeight = subject.getStrokeWeight();
    }

    @Override
    public void start() {
        super.start();
        if (subject instanceof ShapeWidget)
            ((ShapeWidget) subject).addOnStrokeColorChangedListener(this);
    }

    @Override
    public void stop() {
        super.stop();
        if (subject instanceof IShapeWidget)
            ((IShapeWidget) subject).removeOnStrokeColorChangedListener(this);
    }

    @Override
    public void onStrokeColorChanged(IShapeWidget shape) {
        final int color = shape.getStrokeColor();
        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                strokeColor = color;
            }
        });
    }

    @Override
    public void onStrokeWeightChanged(ShapeWidget shape) {
        final float weight = shape.getStrokeWeight();
        runOrQueueEvent(new Runnable() {
            @Override
            public void run() {
                strokeWeight = weight;
            }
        });
    }

    float strokeWeight;
    int strokeColor;
}
