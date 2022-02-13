
package com.atakmap.android.widgets;

import gov.tak.api.annotation.DeprecatedApi;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.IShapeWidget;
import gov.tak.platform.marshal.AbstractMarshal;
import gov.tak.platform.marshal.MarshalManager;

/** @deprecated use {@link gov.tak.platform.widgets.CenterBeadWidget} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class CenterBeadWidget extends ShapeWidget {
    static {
        MarshalManager
                .registerMarshal(new AbstractMarshal(CenterBeadWidget.class,
                        gov.tak.platform.widgets.CenterBeadWidget.class) {
                    @Override
                    protected <T, V> T marshalImpl(V in) {
                        if (!(in instanceof CenterBeadWidget))
                            return null;
                        return (T) ((CenterBeadWidget) in).impl;
                    }
                }, CenterBeadWidget.class,
                        gov.tak.platform.widgets.CenterBeadWidget.class);
    }

    private final gov.tak.platform.widgets.CenterBeadWidget impl;

    public CenterBeadWidget() {
        // bind the impl
        this.impl = new gov.tak.platform.widgets.CenterBeadWidget();
        this.addOnStrokeColorChangedListener(
                new IShapeWidget.OnStrokeColorChangedListener() {
                    @Override
                    public void onStrokeColorChanged(IShapeWidget shape) {
                        impl.setStrokeColor(shape.getStrokeColor());
                    }
                });
        this.addOnStrokeWeightChangedListener(
                new OnStrokeWeightChangedListener() {
                    @Override
                    public void onStrokeWeightChanged(ShapeWidget shape) {
                        impl.setStrokeWeight(shape.getStrokeWeight());
                    }
                });
        this.addOnWidgetSizeChangedListener(
                new IMapWidget.OnWidgetSizeChangedListener() {
                    @Override
                    public void onWidgetSizeChanged(IMapWidget widget) {
                        impl.setSize(widget.getWidth(), widget.getHeight());
                    }
                });
        this.addOnWidgetPointChangedListener(
                new IMapWidget.OnWidgetPointChangedListener() {
                    @Override
                    public void onWidgetPointChanged(IMapWidget widget) {
                        impl.setPoint(widget.getPointX(), widget.getPointY());
                    }
                });

        setName("Center Bead");
    }

    @Override
    public boolean testHit(float x, float y) {
        return false;
    }

    @Override
    public MapWidget seekHit(float x, float y) {
        return null;
    }
}
