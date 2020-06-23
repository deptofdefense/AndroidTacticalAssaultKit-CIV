package transapps.mapi.canvas;

/**
 * This is a simple structure type class that provides measurement details for text that corresponds
 * to rendering text to the screen.
 * 
 * @author SRA
 * @since NW SDK 1.0.45
 */
public class TextMeasurement
{
    /**
     * This is the width of the text in pixels which will be rendered to the right of the drawing
     * point for the text
     */
    public float width;

    /**
     * This is the height of the text in pixels which will be rendered above the drawing point for
     * the text
     */
    public float height;

    /**
     * This is the height of the text which can be displayed below the drawing point for the text.
     * This is for things like the lower part of a "g" and will always be set, but might not be used
     * for the measured text string.
     */
    public float baselineOffsetFromBottom;

    /**
     * This is the default constructor for the TextMeasurement
     */
    public TextMeasurement( )
    {
        this( 0, 0, 0 );
    }

    /**
     * This is the main constructor for the TextMeasurement
     * 
     * @param width
     *            The width of the text in pixels
     * @param height
     *            The height of the text in pixels above the baseline
     * @param baselineOffsetFromBottom
     *            The height which can be used for text that dips under the baseline
     */
    public TextMeasurement( float width, float height, float baselineOffsetFromBottom )
    {
        this.width = width;
        this.height = height;
        this.baselineOffsetFromBottom = baselineOffsetFromBottom;
    }

    /**
     * This is a helper method which will allow setting all the values in a single call
     * 
     * @param width
     *            The width of the text in pixels
     * @param height
     *            The height of the text in pixels above the baseline
     * @param baselineOffsetFromBottom
     *            The height which can be used for text that dips under the baseline
     */
    public void set( float width, float height, float baselineOffsetFromBottom )
    {
        this.width = width;
        this.height = height;
        this.baselineOffsetFromBottom = baselineOffsetFromBottom;
    }
}
