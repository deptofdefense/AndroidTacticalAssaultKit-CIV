
package gov.tak.api.widgets.opengl;
import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.IMapWidget;

public interface IGLWidgetSpi {
    IGLWidget create(MapRenderer renderer, IMapWidget widget);
    int getPriority();
}
