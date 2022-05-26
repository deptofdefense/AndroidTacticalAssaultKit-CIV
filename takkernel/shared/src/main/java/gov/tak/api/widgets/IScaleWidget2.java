package gov.tak.api.widgets;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.coremap.conversions.Span;

import gov.tak.platform.widgets.opengl.GLScaleWidget;

/**
 * Interface for a scale bar widget
 */
public interface IScaleWidget2 extends IShapeWidget {

    /**
     * Display parameters have changed
     */
    interface OnDisplayChangedListener {
        void onDisplayChanged(IScaleWidget2 widget);
    }

    /**
     * Text content has changed
     */
    interface OnTextChangedListener {
        void onScaleTextChanged(IScaleWidget2 widget);
    }

    /**
     * Remove display changed listener
     * @param listener Listener
     */
    void removeOnDisplayChangedListener(OnDisplayChangedListener listener);

    /**
     * Add display changed listener
     * @param listener Listener
     */
    void addOnDisplayChangedListener(OnDisplayChangedListener listener);

    /**
     * Remove text changed listener
     * @param listener Listener
     */
    void removeOnTextChangedListener(OnTextChangedListener listener);

    /**
     * Add text changed listener
     * @param listener Listener
     */
    void addOnTextChangedListener(OnTextChangedListener listener);

    /**
     * Get the {@link MapTextFormat} used for the text in this widget
     * @return Text format
     */
    MapTextFormat getTextFormat();

    /**
     * Get the text displayed in this widget
     * @return Text
     */
    String getText();

    /**
     * Get the current scale
     * @return Scale value in meters
     */
    double getScale();

    /**
     * Get the display units for the scale
     * @return Display units (see {@link Span#METRIC} and the like)
     */
    int getUnits();

    /**
     * Check if the scale bar always uses a rounded metric value
     * @return True if scale value is always rounded
     */
    boolean isRounded();

    /**
     * Get the minimum allowed width for the scale bar
     * @return Minimum width in pixels
     */
    float getMinWidth();

    /**
     * Get the max allowed width for the scale bar
     * @return Max width in pixels
     */
    float getMaxWidth();

    /**
     * Update the text and metric scale of the widget
     * Note: This is primarily meant for {@link GLScaleWidget}
     * @param text Text content
     * @param scale Scale in meters
     */
    void update(String text, double scale);
}