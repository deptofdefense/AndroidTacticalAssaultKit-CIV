
package com.atakmap.android.widgets;

import android.graphics.Color;
import android.graphics.Typeface;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.map.AtakMapView;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.commons.graphics.TextFormat;
import gov.tak.api.widgets.ITextWidget;
import gov.tak.platform.marshal.MarshalManager;

public class TextWidget extends MapWidget2 implements ITextWidget {
    private final ConcurrentLinkedQueue<ITextWidget.OnTextChangedListener> _onTextChanged = new ConcurrentLinkedQueue<>();
    private final Map<TextWidget.OnTextChangedListener, ITextWidget.OnTextChangedListener> _onTextChangedForwarders = new IdentityHashMap<>();
    private final ConcurrentLinkedQueue<ITextWidget.OnColorChangedListener> _onColorChanged = new ConcurrentLinkedQueue<>();
    private final Map<TextWidget.OnColorChangedListener, ITextWidget.OnColorChangedListener> _onColorChangedForwarders = new IdentityHashMap();
    private final ConcurrentLinkedQueue<ITextWidget.OnHasBackgroundChangedListener> _onHasBackgroundChanged = new ConcurrentLinkedQueue<>();
    private final Map<TextWidget.OnHasBackgroundChangedListener, ITextWidget.OnHasBackgroundChangedListener> _onHasBackgroundChangedForwarders = new IdentityHashMap();
    protected String _text;
    protected int _lineCount = 0;
    private int[] _colors;
    protected MapTextFormat _textFormat;
    protected int background;

    public static final int TRANSLUCENT_BLACK = 0x99000000;
    public static final int TRANSLUCENT = 0x00000000;

    public interface OnTextChangedListener {
        void onTextWidgetTextChanged(TextWidget widget);
    }

    public interface OnColorChangedListener {
        void onTextWidgetColorChanged(TextWidget widget);
    }

    public interface OnHasBackgroundChangedListener {
        void onTextWidgetHasBackgroundChanged(TextWidget widget);
    }

    @Override
    public final void addOnTextChangedListener(
            ITextWidget.OnTextChangedListener l) {
        _onTextChanged.add(l);
    }

    public void addOnTextChangedListener(TextWidget.OnTextChangedListener l) {
        registerForwardedListener(_onTextChanged, _onTextChangedForwarders, l,
                new TextChangedForwarder(l));
    }

    @Override
    public final void removeOnTextChangedListener(
            ITextWidget.OnTextChangedListener l) {
        _onTextChanged.remove(l);
    }

    public void removeOnTextChangedListener(
            TextWidget.OnTextChangedListener l) {
        unregisterForwardedListener(_onTextChanged, _onTextChangedForwarders,
                l);
    }

    @Override
    public final void addOnColorChangedListener(
            ITextWidget.OnColorChangedListener l) {
        _onColorChanged.add(l);
    }

    public void addOnColorChangedListener(TextWidget.OnColorChangedListener l) {
        registerForwardedListener(_onColorChanged, _onColorChangedForwarders, l,
                new ColorChangedForwarder(l));
    }

    @Override
    public final void removeOnColorChangedListener(
            ITextWidget.OnColorChangedListener l) {
        _onColorChanged.remove(l);
    }

    public void removeOnColorChangedListener(
            TextWidget.OnColorChangedListener l) {
        unregisterForwardedListener(_onColorChanged, _onColorChangedForwarders,
                l);
    }

    @Override
    public final void addOnHasBackgroundChangedListener(
            ITextWidget.OnHasBackgroundChangedListener l) {
        _onHasBackgroundChanged.add(l);
    }

    public void addOnHasBackgroundChangedListener(
            TextWidget.OnHasBackgroundChangedListener l) {
        registerForwardedListener(_onHasBackgroundChanged,
                _onHasBackgroundChangedForwarders, l,
                new HasBackgroundChangedForwarder(l));
    }

    @Override
    public final void removeOnHasBackgroundChangedListener(
            ITextWidget.OnHasBackgroundChangedListener l) {
        _onHasBackgroundChanged.remove(l);
    }

    public void removeOnHasBackgroundChangedListener(
            TextWidget.OnHasBackgroundChangedListener l) {
        unregisterForwardedListener(_onHasBackgroundChanged,
                _onHasBackgroundChangedForwarders, l);
    }

    public TextWidget(final String text, final MapTextFormat textFormat,
            final boolean hasBackgound) {
        this(text, textFormat, hasBackgound ? TRANSLUCENT_BLACK : TRANSLUCENT);
    }

    /**
     * The Text widget created with some text, formating and the background set to a specific color.
     * @param text the text which can be broken up by newlines.
     * @param textFormat the format to use when rendering the text
     * @param background the background color.
     */
    public TextWidget(final String text, final MapTextFormat textFormat,
            final int background) {
        _padding[LEFT] = _padding[RIGHT] = _padding[TOP] = _padding[BOTTOM] = (background != TRANSLUCENT)
                ? 8f
                : 0f;
        this.background = background;
        _textFormat = textFormat;
        setText(text);
    }

    public TextWidget(String text, MapTextFormat textFormat) {
        this(text, textFormat, TRANSLUCENT_BLACK);
    }

    public TextWidget(String text, int fmtOffset) {
        this(text, AtakMapView.getTextFormat(Typeface.DEFAULT, fmtOffset));
    }

    public TextWidget(String text) {
        this(text, AtakMapView.getDefaultTextFormat());
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

    public void setTextFormat(MapTextFormat textFormat) {
        this._textFormat = textFormat;
        onTextChanged();
    }

    public MapTextFormat getTextFormat() {
        return _textFormat;
    }

    @Override
    public TextFormat getWidgetTextFormat() {
        return MarshalManager.marshal(_textFormat, MapTextFormat.class,
                TextFormat.class);
    }

    protected void onTextChanged() {
        recalcSize();
        for (ITextWidget.OnTextChangedListener l : _onTextChanged)
            l.onTextWidgetTextChanged(this);
    }

    protected void recalcSize() {
        // Update dimensions based on text
        float width, height;
        if (FileSystemUtils.isEmpty(_text))
            width = height = 0;
        else {
            width = _textFormat.measureTextWidth(_text);
            height = _textFormat.measureTextHeight(_text) -
                    _textFormat.getBaselineOffsetFromBottom();
        }
        setSize(width, height);
    }

    @Override
    public void orientationChanged() {
        recalcSize();
    }

    private void onColorChanged() {
        for (ITextWidget.OnColorChangedListener l : _onColorChanged) {
            l.onTextWidgetColorChanged(this);
        }
    }

    private void onHasBackgroundChanged() {
        for (ITextWidget.OnHasBackgroundChangedListener l : _onHasBackgroundChanged) {
            l.onTextWidgetHasBackgroundChanged(this);
        }
    }

    private final static class TextChangedForwarder
            implements ITextWidget.OnTextChangedListener {
        final TextWidget.OnTextChangedListener _cb;

        TextChangedForwarder(TextWidget.OnTextChangedListener cb) {
            _cb = cb;
        }

        @Override
        public void onTextWidgetTextChanged(ITextWidget widget) {
            if (widget instanceof TextWidget)
                _cb.onTextWidgetTextChanged((TextWidget) widget);
        }
    }

    private final static class ColorChangedForwarder
            implements ITextWidget.OnColorChangedListener {
        final TextWidget.OnColorChangedListener _cb;

        ColorChangedForwarder(TextWidget.OnColorChangedListener cb) {
            _cb = cb;
        }

        @Override
        public void onTextWidgetColorChanged(ITextWidget widget) {
            if (widget instanceof TextWidget)
                _cb.onTextWidgetColorChanged((TextWidget) widget);
        }
    }

    private final static class HasBackgroundChangedForwarder
            implements ITextWidget.OnHasBackgroundChangedListener {
        final TextWidget.OnHasBackgroundChangedListener _cb;

        HasBackgroundChangedForwarder(
                TextWidget.OnHasBackgroundChangedListener cb) {
            _cb = cb;
        }

        @Override
        public void onTextWidgetHasBackgroundChanged(ITextWidget widget) {
            if (widget instanceof TextWidget)
                _cb.onTextWidgetHasBackgroundChanged((TextWidget) widget);
        }
    }
}
