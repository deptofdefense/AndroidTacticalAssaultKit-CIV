
package gov.tak.platform.widgets;
import gov.tak.api.annotation.DeprecatedApi;

import android.graphics.Color;
import android.graphics.Typeface;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.map.opengl.GLRenderGlobals;

import gov.tak.api.commons.graphics.DisplaySettings;
import gov.tak.api.commons.graphics.Font;
import gov.tak.api.commons.graphics.FontMetrics;
import gov.tak.api.commons.graphics.TextFormat;
import gov.tak.api.widgets.ITextWidget;
import gov.tak.platform.marshal.MarshalManager;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TextWidget extends MapWidget implements ITextWidget {
    private final ConcurrentLinkedQueue<OnTextChangedListener> _onTextChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnColorChangedListener> _onColorChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnHasBackgroundChangedListener> _onHasBackgroundChanged = new ConcurrentLinkedQueue<>();
    protected String _text;
    protected int _lineCount = 0;
    private int[] _colors;
    protected TextFormat _textFormat;
    protected int background;

    public static final int TRANSLUCENT_BLACK = 0x99000000;
    public static final int TRANSLUCENT = 0x00000000;

    public void addOnTextChangedListener(OnTextChangedListener l) {
        _onTextChanged.add(l);
    }

    public void removeOnTextChangedListener(OnTextChangedListener l) {
        _onTextChanged.remove(l);
    }

    public void addOnColorChangedListener(OnColorChangedListener l) {
        _onColorChanged.add(l);
    }

    public void removeOnColorChangedListener(OnColorChangedListener l) {
        _onColorChanged.remove(l);
    }

    public void addOnHasBackgroundChangedListener(
            OnHasBackgroundChangedListener l) {
        _onHasBackgroundChanged.add(l);
    }

    public void removeOnHasBackgroundChangedListener(
            OnHasBackgroundChangedListener l) {
        _onHasBackgroundChanged.remove(l);
    }

    public TextWidget(final String text, final TextFormat textFormat,
            final boolean hasBackgound) {
        this(text, textFormat, hasBackgound ? TRANSLUCENT_BLACK : TRANSLUCENT);
    }

    /**
     * The Text widget created with some text, formating and the background set to a specific color.
     * @param text the text which can be broken up by newlines.
     * @param textFormat the format to use when rendering the text
     * @param background the background color.
     */
    public TextWidget(final String text, final TextFormat textFormat,
            final int background) {
        _padding[LEFT] = _padding[RIGHT] = _padding[TOP] = _padding[BOTTOM] = (background != TRANSLUCENT)
                ? 8f
                : 0f;
        this.background = background;
        _textFormat = textFormat;
        setText(text);
    }

    public TextWidget(String text, TextFormat textFormat) {
        this(text, textFormat, TRANSLUCENT_BLACK);
    }
    public TextWidget(String text, int fmtOffset) {
        this(text, MarshalManager.marshal(new MapTextFormat(Typeface.DEFAULT, fmtOffset+(int)DisplaySettings.getDefaultFontSize()), MapTextFormat.class, TextFormat.class));
    }

    public TextWidget(String text) {
        this(text, 0);
    }

    public TextWidget() {
        this("");
    }

    public int getLineCount() {
        return _lineCount;
    }

    /**
     * Set the test for the widget.   The text can be multiline if the \n is present.
     * @param text The text to be contained in the Widget.
     */
    public void setText(String text) {
        if (text == null)
            text = "";
        if (!text.equals(_text)) {
            _text = text;
            _lineCount = _text.split("\n").length;

            // Preserve color when changing text.
            if (_colors == null || _colors.length != _lineCount) {
                int oldColor = getColor();
                setColor(oldColor);
            }
            onTextChanged();
        }
    }

    public void setText(CharSequence text) {
        setText(text == null ? "" : text.toString());
    }

    /**
     * Set the size of the text container
     * To be removed - redundant to setSize and unnecessary since size is
     * now calculated automatically based on text
     * @deprecated Use {@link #setSize(float, float)}
     * @param width Bounds width
     * @param height Bounds height
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public void setTextBounds(float width, float height) {
        setSize(width, height);
    }

    public void setColor(int color) {
        _colors = new int[_lineCount];
        Arrays.fill(_colors, color);
        onColorChanged();
    }

    /**
     * An array of the colors with each postition in the array corresponding to a line in the text.
     * @param colors an integer array that contains the int color values for each line.
     */
    public synchronized void setColors(int[] colors) {
        _colors = colors;
        onColorChanged();
    }

    /**
     * Get the first color in the color array or white if the color is not set.
     * @return the first color in the color array.
     */
    public int getColor() {
        if (_colors != null && _colors.length >= 1)
            return _colors[0];
        else
            return Color.WHITE;
    }

    /**
     * Returns the color of a line.
     *
     * @param lineNumber A zero based index for the line
     * @return The color
     */
    public int getColor(int lineNumber) {
        if (_text == null || lineNumber >= _lineCount)
            return _colors[0];

        return _colors[lineNumber];
    }

    public synchronized int[] getColors() {
        return _colors;
    }

    /**
     * Should be set to true if the TextWidget needs to render a background.  If false, the
     * background is transparent.   See TRANSLUCENT BLACK commonly used background.
     * @param background the background to be used for rendering the widget.
     */
    public void setBackground(final int background) {
        if (this.background != background) {
            this.background = background;
            onHasBackgroundChanged();
        }
    }

    public int getBackground() {
        return background;
    }

    /**
     * Get the text for the TextWidget.
     * @return String representation of the text currently in the widget.
     */
    public String getText() {
        return _text;
    }

    public void setWidgetTextFormat(TextFormat textFormat) {
        this._textFormat = textFormat;
        onTextChanged();
    }

    @Override
    public TextFormat getWidgetTextFormat() {
        return _textFormat;
    }

    protected void onTextChanged() {
        recalcSize();
        for (OnTextChangedListener l : _onTextChanged)
            l.onTextWidgetTextChanged(this);
    }

    protected void recalcSize() {
        // Update dimensions based on text
        float width, height;
        if (FileSystemUtils.isEmpty(_text))
            width = height = 0;
        else {
            final FontMetrics fm = FontMetrics.intern(_textFormat.getFont());
            width = fm.measureTextWidth(_text);
            height = fm.measureTextHeight(_text) -
                    fm.getBaselineOffsetFromBottom();
        }
        setSize(width, height);
    }

    @Override
    public void orientationChanged() {
        recalcSize();
    }

    private void onColorChanged() {
        for (OnColorChangedListener l : _onColorChanged) {
            l.onTextWidgetColorChanged(this);
        }
    }

    private void onHasBackgroundChanged() {
        for (OnHasBackgroundChangedListener l : _onHasBackgroundChanged) {
            l.onTextWidgetHasBackgroundChanged(this);
        }
    }

}
