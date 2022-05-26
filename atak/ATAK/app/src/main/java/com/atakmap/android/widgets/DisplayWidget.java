
package com.atakmap.android.widgets;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;

import androidx.annotation.DrawableRes;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.assets.Icon;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DisplayWidget extends MapWidget2 {

    private final ConcurrentLinkedQueue<DisplayWidget.OnDisplayTextChangedListener> _onDisplayChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<DisplayWidget.OnDisplayColorChangedListener> _onColorChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<DisplayWidget.OnDisplayHasBackgroundChangedListener> _onHasBackgroundChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<DisplayWidget.OnHasIconChangedListener> _onHasIconChanged = new ConcurrentLinkedQueue<>();

    private final MapView _mapView;
    private Icon icon;
    private String _text;
    private int _lineCount = 0;
    private int[] _colors;
    protected MapTextFormat _textFormat;
    protected int background;

    public static final int TRANSLUCENT = 0x00000000;

    private static final int xIconAnchor = 0;
    private static final int yIconAnchor = 0;

    public interface OnDisplayTextChangedListener {
        void onDisplayTextChanged(DisplayWidget widget);
    }

    public interface OnDisplayColorChangedListener {
        void onDisplayWidgetColorChanged(DisplayWidget widget);
    }

    public interface OnDisplayHasBackgroundChangedListener {
        void onDisplayWidgetHasBackgroundChanged(DisplayWidget widget);
    }

    public interface OnHasIconChangedListener {
        void onDisplayWidgetHasIconChanged(DisplayWidget widget);
    }

    public void addOnDisplayChangedListener(OnDisplayTextChangedListener l) {
        _onDisplayChanged.add(l);
    }

    public void removeOnDisplayChangedListener(
            OnDisplayTextChangedListener l) {
        _onDisplayChanged.remove(l);
    }

    public void addOnDisplayColorChangedListener(
            DisplayWidget.OnDisplayColorChangedListener l) {
        _onColorChanged.add(l);
    }

    public void removeOnDisplayColorChangedListener(
            DisplayWidget.OnDisplayColorChangedListener l) {
        _onColorChanged.remove(l);
    }

    public void addOnDisplayHasBackgroundChangedListener(
            DisplayWidget.OnDisplayHasBackgroundChangedListener l) {
        _onHasBackgroundChanged.add(l);
    }

    public void removeOnDisplayHasBackgroundChangedListener(
            DisplayWidget.OnDisplayHasBackgroundChangedListener l) {
        _onHasBackgroundChanged.remove(l);
    }

    public DisplayWidget(String text, MapTextFormat textFormat, final int icon,
            final int background, MapView mapView) {
        _mapView = mapView;
        _padding[LEFT] = _padding[RIGHT] = _padding[TOP] = _padding[BOTTOM] = (background != TRANSLUCENT)
                ? 8f
                : 0f;
        String imageUri = "android.resource://"
                + mapView.getContext().getPackageName()
                + "/" + icon;

        this.icon = createIcon(imageUri, _mapView.getContext());
        this.background = background;
        _textFormat = textFormat;
        setText(text);
        onHasIconChanged();
    }

    public int getLineCount() {
        return _lineCount;
    }

    /**
     * Set the test for the widget.   The text can be multiline if the \n is present.
     * @param text The text to be contained in the Widget.
     */
    public void setText(String text) {
        if (text != null) {
            // short circuit as needed
            if (text != null && _text != null && text.equals(_text))
                return;

            _text = text;
            _lineCount = _text.split("\n").length;

            // Preserve color when changing text.
            if (_colors == null || _colors.length != _lineCount) {
                int oldColor = getColor();
                setColor(oldColor);
            }
            onDisplayTextChanged();
        }
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

    public void setIcon(@DrawableRes int icon) {
        if (!this.icon.getImageUri(0).contains(Integer.toString(icon))) {
            String imageUri = "android.resource://"
                    + _mapView.getContext().getPackageName()
                    + "/" + icon;
            this.icon = createIcon(imageUri, _mapView.getContext());
            onHasIconChanged();
        }
    }

    /**
     * Should be set to true if the DisplayWidget needs to render a background.  If false, the
     * background is transparent.   See TRANSLUCENT BLACK commonly used background.
     * @param background the background to be used for rendering the widget.
     */
    public void setBackground(final int background) {
        if (this.background == background) {
            this.background = background;
            onHasBackgroundChanged();
        }
    }

    public int getBackground() {
        return background;
    }

    /**
     * Get the text for the DisplayWidget.
     * @return String representation of the text currently in the widget.
     */
    public String getText() {
        return _text;
    }

    public Icon getIcon() {
        return icon;
    }

    public void setTextFormat(MapTextFormat textFormat) {
        this._textFormat = textFormat;
        onDisplayTextChanged();
    }

    public MapTextFormat getTextFormat() {
        return _textFormat;
    }

    protected void onDisplayTextChanged() {
        recalcSize();
        for (OnDisplayTextChangedListener l : _onDisplayChanged)
            l.onDisplayTextChanged(this);
    }

    protected void recalcSize() {
        // Update dimensions based on text
        float width, height;
        if (FileSystemUtils.isEmpty(_text))
            width = height = 0;
        else {
            width = _textFormat.measureTextWidth(_text) + icon.getWidth();
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
        for (DisplayWidget.OnDisplayColorChangedListener l : _onColorChanged) {
            l.onDisplayWidgetColorChanged(this);
        }
    }

    private void onHasBackgroundChanged() {
        for (DisplayWidget.OnDisplayHasBackgroundChangedListener l : _onHasBackgroundChanged) {
            l.onDisplayWidgetHasBackgroundChanged(this);
        }
    }

    private void onHasIconChanged() {
        for (DisplayWidget.OnHasIconChangedListener l : _onHasIconChanged) {
            l.onDisplayWidgetHasIconChanged(this);
        }
    }

    private Icon createIcon(final String imageUri, final Context con) {
        Point p = new Point();
        ((Activity) con).getWindowManager().getDefaultDisplay().getSize(p);
        // determine if the screen is small
        Icon.Builder builder = new Icon.Builder();
        builder.setAnchor(xIconAnchor, yIconAnchor);
        builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);

        builder.setSize(Icon.SIZE_DEFAULT, Icon.SIZE_DEFAULT);

        builder.setImageUri(Icon.STATE_DEFAULT, imageUri);

        return builder.build();
    }
}
