
package com.atakmap.coremap.maps.assets;

import java.util.Set;

import android.os.Parcel;
import android.os.Parcelable;

import gov.tak.api.annotation.DeprecatedApi;
import gov.tak.api.commons.graphics.IIcon;
import gov.tak.api.marshal.IMarshal;
import gov.tak.platform.marshal.MarshalManager;

/**
 * Image asset with configurable metrics and multiple states
 * 
 * @deprecated use {@link IIcon}
 */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class Icon implements IIcon, Parcelable {
    /**
     * The default state value (zero)
     */
    public static final int STATE_DEFAULT = IIcon.STATE_DEFAULT;

    /**
     * Can be passed to setSize() to signal using the image's natural width or height.
     */
    public static final int SIZE_DEFAULT = IIcon.SIZE_DEFAULT;

    /**
     * Can be passed to setAnchor() to signal using the image center as the anchor point.
     */
    public static final int ANCHOR_CENTER = IIcon.ANCHOR_CENTER;

    /**
     * Create an empty icon
     */
    public Icon() {
        this(new gov.tak.platform.commons.graphics.Icon());
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
        this(new gov.tak.platform.commons.graphics.Icon(imageUri));
    }

    /**
     * Create an Icon that is a copy of another Icon
     * 
     * @param ic the icon to copy
     */
    public Icon(Icon ic) {
        this(new gov.tak.platform.commons.graphics.Icon(ic._impl));
    }

    private Icon(IIcon impl) {
        _impl = impl;
    }

    /**
     * Get the icon width
     * 
     * @return the width of the icon
     */
    public int getWidth() {
        return _impl.getWidth();
    }

    /**
     * Get the icon height
     * 
     * @return the height of the icon
     */
    public int getHeight() {
        return _impl.getHeight();
    }

    /**
     * Get the icon x anchor point
     * 
     * @return the x anchor point
     */
    public int getAnchorX() {
        return _impl.getAnchorX();
    }

    /**
     * Get the icon y anchor point
     * 
     * @return the y anchor point
     */
    public int getAnchorY() {
        return _impl.getAnchorY();
    }

    /**
     * Get the image URI for a given state
     * 
     * @param state the state value
     * @return the uri for a given state, if the uri is null, the state returned is 0
     */
    public String getImageUri(final int state) {
        return _impl.getImageUri(state);
    }

    /**
     * Get the color for a given state
     * 
     * @param state the state value
     * @return a packed color created with android.graphics.Color
     */
    public int getColor(int state) {
        return _impl.getColor(state);
    }

    @Override
    public final Set<Integer> getStates() {
        return _impl.getStates();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(getWidth());
        dest.writeInt(getHeight());
        dest.writeInt(getAnchorX());
        dest.writeInt(getAnchorY());

        final Set<Integer> states = _impl.getStates();

        dest.writeInt(states.size());
        for (int state : states) {
            dest.writeInt(state);
            dest.writeString(_impl.getImageUri(state));
        }

        dest.writeInt(states.size());
        for (int state : states) {
            dest.writeInt(state);
            dest.writeInt(_impl.getColor(state));
        }
    }

    public static final Parcelable.Creator<Icon> CREATOR = new Parcelable.Creator<Icon>() {
        @Override
        public Icon createFromParcel(Parcel source) {
            Icon.Builder ic = new Icon.Builder();
            final int width = source.readInt();
            final int height = source.readInt();
            ic.setSize(width, height);
            final int ax = source.readInt();
            final int ay = source.readInt();
            ic.setAnchor(ax, ay);
            int uriCount = source.readInt();
            while (uriCount-- > 0) {
                int state = source.readInt();
                String uri = source.readString();
                ic.setImageUri(state, uri);
            }
            int colorCount = source.readInt();
            while (colorCount-- > 0) {
                int state = source.readInt();
                int color = source.readInt();
                ic.setColor(state, color);
            }
            return ic.build();
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
            _ic.setImageUri(state, uri);
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
            _ic.setColor(state, color);
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
            _ic.setSize(width, height);
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
            _ic.setAnchor(x, y);
            return this;
        }

        /**
         * Build the icon
         * 
         * @return a new Icon
         */
        public Icon build() {
            return new Icon(_ic.build());
        }

        public Builder() {
            _ic = new gov.tak.platform.commons.graphics.Icon.Builder();
        }

        public Builder(Icon upon) {
            _ic = new gov.tak.platform.commons.graphics.Icon.Builder(upon._impl);
        }

        private final gov.tak.platform.commons.graphics.Icon.Builder _ic;
    }

    public Builder buildUpon() {
        return new Builder(this);
    }

    private final IIcon _impl;
}
