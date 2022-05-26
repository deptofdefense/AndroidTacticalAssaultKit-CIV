package gov.tak.platform.commons.graphics;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import gov.tak.api.commons.graphics.IIcon;
import gov.tak.platform.graphics.Color;

public class Icon implements IIcon {
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
    public Icon(IIcon ic) {
        _width = ic.getWidth();
        _height = ic.getHeight();
        _ax = ic.getAnchorX();
        _ay = ic.getAnchorY();
        for(int state : ic.getStates()) {
            _uris.put(state, ic.getImageUri(state));
            _colors.put(state, ic.getColor(state));
        }
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

    @Override
    public Set<Integer> getStates() {
        Set<Integer> states = new TreeSet<>();
        states.addAll(_colors.keySet());
        states.addAll(_uris.keySet());
        return states;
    }

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
            _ic._uris.put(state, uri);
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
            _ic._colors.put(state, color);
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
            _ic._width = width;
            _ic._height = height;
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
            _ic._ax = x;
            _ic._ay = y;
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

        public Builder(IIcon upon) {
            _ic = new Icon(upon);
        }

        private final Icon _ic;
    }

    private int _width = SIZE_DEFAULT, _height = SIZE_DEFAULT;
    private int _ax = ANCHOR_CENTER, _ay = ANCHOR_CENTER;
    private Map<Integer, String> _uris = new TreeMap<>();
    private Map<Integer, Integer> _colors = new TreeMap<>();
}
