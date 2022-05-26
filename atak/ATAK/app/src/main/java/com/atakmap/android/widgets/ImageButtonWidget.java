
package com.atakmap.android.widgets;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;

import com.atakmap.android.maps.MapDataRef;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.UriFactory;

import java.io.InputStream;

@Deprecated
@DeprecatedApi(since = "4.4")
public class ImageButtonWidget extends ButtonWidget {

    public static final String TAG = "ImageButtonWidget";

    private boolean _hasBackground = true;
    private WidgetIcon _icon;

    public ImageButtonWidget(Context ignored) {
        this();
    }

    public ImageButtonWidget() {
        setBackground(new WidgetBackground(-1728053248));
        _hasBackground = true;
        setState(0);
    }

    /*
     * Note that you must first set the size of the button before you set the image. It's a center
     * calculation thing.
     */
    public void setImage(String path) {
        Uri uri = Uri.parse("asset:///" + path);
        // Log.e(TAG, uri.toString());
        MapDataRef ref = MapDataRef.parseUri(uri.toString());
        int imgHeight = 32;
        int imgWidth = 32;
        InputStream istr = null;
        try {
            UriFactory.OpenResult result = UriFactory.open(uri.toString());
            if (result != null) {
                istr = result.inputStream;
                BitmapFactory.Options options = new BitmapFactory.Options();
                BitmapFactory.decodeStream(istr, null, options);
                imgHeight = options.outHeight;
                imgWidth = options.outWidth;
            }
        } catch (Throwable e) {
            Log.e(TAG, "error: ", e);
        } finally {
            if (istr != null) {
                try {
                    istr.close();
                } catch (Exception ignore) {
                }
            }
        }
        _icon = new WidgetIcon(ref, new Point(
                (int) (imgWidth - this.getButtonWidth()) / 2,
                (int) (this.getButtonHeight() - imgHeight) / 2 + imgHeight),
                imgWidth, imgHeight);
        setIcon(_icon);
        // Log.e(TAG, "Icon: " + this.getIcon() + "!");

        // onIconChanged();
        // Log.e(TAG, "Color: " + this.getBackground().getColor(this.getState()));
        // this.setState(99); //00000099f to match the color of the other text boxes
        // Log.e(TAG, "Set the icon");
    }

    @Override
    public boolean testHit(float x, float y) {
        return x >= 0 && x < getButtonWidth() &&
                y >= 0 && y < getButtonHeight();
    }

    @Override
    public MapWidget seekHit(float x, float y) {
        MapWidget hit = null;
        if (testHit(x, -y)) { // Not sure why this has to be negated, but doing so produces the
                              // expected result
                              // There might be something backwards in the initial setup of the icon
            hit = this;
        }
        return hit;
    }

    @Override
    public boolean setSize(float width, float height) {
        return super.setSize(width + 4f, height + 4f); // to compensate for the gl bordering with the
        // rounded corners
    }

    public boolean getHasBackground() {
        return _hasBackground;
    }
}
