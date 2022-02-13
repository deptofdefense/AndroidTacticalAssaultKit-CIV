package gov.tak.api.widgets;

/**
 * A controller for a IMapWidget to enable the Model, View, Controller design pattern.
 * The Model is assumed to be encapsulated within the controller and the View is the widget.
 */
public interface IWidgetController {

    /**
     * Get the controlled widget
     *
     * @return
     */
    IMapWidget getWidget();

    /**
     * Refresh the widget (View) properties
     */
    void refreshProperties();

    /**
     * Refresh a specific property
     *
     * @param propertyName
     */
    void refreshProperty(String propertyName);
}
