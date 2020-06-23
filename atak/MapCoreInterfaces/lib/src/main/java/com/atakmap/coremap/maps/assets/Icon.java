
package com.atakmap.coremap.maps.assets;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Image asset with configurable metrics and multiple states
 * 
 * 
 */
public class Icon implements Parcelable {

    /**
     * The default state value (zero)
     */
    public static final int STATE_DEFAULT = 0;

    /**
     * Can be passed to setSize() to signal using the image's natural width or height.
     */
    public static final int SIZE_DEFAULT = -1;

    /**
     * Can be passed to setAnchor() to signal using the image center as the anchor point.
     */
    public static final int ANCHOR_CENTER = Integer.MIN_VALUE;

    /**
     * Create an empty icon
     */
    public Icon() {
        _colors.put(0, Color.WHITE);
    }

    /**
     * Create a simple single state Icon from a URI string referencing an image.
     * <p>
     * Acceptable URIs:
     * <li>android.resource://<package_name>/<resource_id></li>
     * <li>file://<file_path></li>
     * <li>arc:<arc_path>!/<entry_path></li>
     * <li>asset:/<filepath></li>
     * </p>
     * 
     * @param imageUri the URI string to the image
     */
    public Icon(String imageUri) {
        this();
        _uris.put(0, imageUri);
    }

    /**
     * Create an Icon that is a copy of another Icon
     * 
     * @param ic the icon to copy
     */
    public Icon(Icon ic) {
        _width = ic._width;
        _height = ic._height;
        _ax = ic._ax;
        _ay = ic._ay;
        _uris = ic.getImageUriStates();
        _colors = ic.getColorStates();
    }

    /**
     * Get the icon width
     * 
     * @return the width of the icon
     */
    public int getWidth() {
        return _width;
    }

    /**
     * Get the icon height
     * 
     * @return the height of the icon
     */
    public int getHeight() {
        return _height;
    }

    /**
     * Get the icon x anchor point
     * 
     * @return the x anchor point
     */
    public int getAnchorX() {
        return _ax;
    }

    /**
     * Get the icon y anchor point
     * 
     * @return the y anchor point
     */
    public int getAnchorY() {
        return _ay;
    }

    /**
     * Get the image URI for a given state
     * 
     * @param state the state value
     * @return the uri for a given state, if the uri is null, the state returned is 0
     */
    public String getImageUri(final int state) {
        String uri = _uris.get(state);
        if (uri == null) {
            uri = _uris.get(0);
        }
        return uri;
    }

    /**
     * Get the color for a given state
     * 
     * @param state the state value
     * @return a packed color created with android.graphics.Color
     */
    public int getColor(int state) {
        Integer color = _colors.get(state);
        if (color == null) {
            color = _colors.get(0);
        }
        return color;
    }

    /**
     * Get a copy of the color states map
     * 
     * @return the copy of the colors states map
     */
    private Map<Integer, Integer> getColorStates() {
        return new TreeMap<>(_colors);
    }

    /**
     * Get a copy of the image URI states Map
     * 
     * @return the copy of the image uri states
     */
    private Map<Integer, String> getImageUriStates() {
        return new TreeMap<>(_uris);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(_width);
        dest.writeInt(_height);
        dest.writeInt(_ax);
        dest.writeInt(_ay);

        Set<Entry<Integer, String>> uris = _uris.entrySet();
        dest.writeInt(uris.size());
        for (Entry<Integer, String> uri : uris) {
            dest.writeInt(uri.getKey());
            dest.writeString(uri.getValue());
        }

        Set<Entry<Integer, Integer>> colors = _colors.entrySet();
        dest.writeInt(colors.size());
        for (Entry<Integer, Integer> color : colors) {
            dest.writeInt(color.getKey());
            dest.writeInt(color.getValue());
        }
    }

    public static final Parcelable.Creator<Icon> CREATOR = new Parcelable.Creator<Icon>() {
        @Override
        public Icon createFromParcel(Parcel source) {
            Icon ic = new Icon();
            ic._width = source.readInt();
            ic._height = source.readInt();
            ic._ax = source.readInt();
            ic._ay = source.readInt();
            int uriCount = source.readInt();
            while (uriCount-- > 0) {
                int state = source.readInt();
                String uri = source.readString();
                ic._uris.put(state, uri);
            }
            int colorCount = source.readInt();
            while (colorCount-- > 0) {
                int state = source.readInt();
                int color = source.readInt();
                ic._colors.put(state, color);
            }
            return ic;
        }

        @Override
        public Icon[] newArray(int size) {
            return new Icon[size];
        }
    };

    /**
     * Builds a Icon that is too detailed for a simple constructor
     * 
     * 
     */
    public static class Builder {

        /**
         * Set the image URI of a state
         * 
         * @param state the state value
         * @param uri the image URI
         * @return this Builder
         */
        public Builder setImageUri(int state, String uri) {
            _ic._setImageUri(state, uri);
            return this;
        }

        /**
         * Set the color of a given state
         * 
         * @param state the state value
         * @param color the rgba packed color (use android.graphics.Color)
         * @return this Builder
         */
        public Builder setColor(int state, int color) {
            _ic._setColor(state, color);
            return this;
        }

        /**
         * Set the icon size. All images states will be scaled to this size. If SIZE_DEFAULT is set
         * for width or height, all images will be scaled to the natural width and height of the
         * default state image.
         * 
         * @param width the width in pixels (use SIZE_DEFAULT to use natural image width)
         * @param height the hieght in pixels (use SIZE_DEFAULT to use natural image height)
         * @return this Builder
         */
        public Builder setSize(int width, int height) {
            _ic._setSize(width, height);
            return this;
        }

        /**
         * Set the point that is to be mapped to the on screen destination point. The values are an
         * offset in pixels from the upper left corner (0, 0) of the final image size. Negative
         * values are permitted.
         * 
         * @param x the offset in pixels (use ANCHOR_CENTER to always map to the center)
         * @param y the offset in pixels (use ANCHOR_CENTER to always map to the center)
         * @return this Builder
         */
        public Builder setAnchor(int x, int y) {
            _ic._setAnchor(x, y);
            return this;
        }

        /**
         * Build the icon
         * 
         * @return a new Icon
         */
        public Icon build() {
            return new Icon(_ic);
        }

        public Builder() {
            _ic = new Icon();
        }

        public Builder(Icon upon) {
            _ic = new Icon(upon);
        }

        private final Icon _ic;
    }

    public Builder buildUpon() {
        return new Builder(this);
    }

    private void _setImageUri(int state, String uri) {
        _uris.put(state, uri);
    }

    private void _setColor(int state, int color) {
        _colors.put(state, color);
    }

    private void _setSize(int width, int height) {
        _width = width;
        _height = height;
    }

    private void _setAnchor(int x, int y) {
        _ax = x;
        _ay = y;
    }

    private int _width = SIZE_DEFAULT, _height = SIZE_DEFAULT;
    private int _ax = ANCHOR_CENTER, _ay = ANCHOR_CENTER;
    private Map<Integer, String> _uris = new TreeMap<>();
    private Map<Integer, Integer> _colors = new TreeMap<>();
}
