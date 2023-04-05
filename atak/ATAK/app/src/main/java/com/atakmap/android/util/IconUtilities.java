
package com.atakmap.android.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.maps.assets.Icon;

import java.io.ByteArrayOutputStream;

/**
 * A convenience set of methods to help convert things to and from encoded images and bitmaps for use
 * for markers and menus.
 */
public class IconUtilities {

    /**
     * Given a drawable resource, create a bitmap and then encode the bitmap as an icon.
     * @param context The context to use
     * @param marker The marker to set the icon
     * @param res The drawable resource to be used.
     * @param adapt True if the icon can be adapted when the type is changed.  This means that the
     *              icon will be replaced with an appropriate icon for the new type.  False if the
     *              icon is considered permanently set and can only be changed by manually calling
     *              this method again.
     */
    public static void setIcon(Context context, Marker marker,
            @DrawableRes int res, boolean adapt) {

        final Bitmap icon = getBitmap(context, res);
        final String encoded = encodeBitmap(icon);

        marker.setMetaBoolean("adapt_marker_icon", adapt);

        if (encoded == null) {
            marker.setIcon(null);
        } else {
            Icon.Builder markerIconBuilder = new Icon.Builder().setImageUri(0,
                    encoded);
            marker.setIcon(markerIconBuilder.build());
        }
    }

    /**
     * Given a drawable resource turn it into a bitmap
     * @param context The context to use for looking up the drawable
     * @param drawableId The resource ID for the drawable
     * @return Bitmap representation of the drawable or null if failed
     */
    @Nullable
    public static Bitmap getBitmap(Context context,
            @DrawableRes int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);

        // Drawable not found
        if (drawable == null)
            return null;

        if (drawable instanceof BitmapDrawable) // Quick lookup
            return ((BitmapDrawable) drawable).getBitmap();
        else
            return getBitmap(drawable);
    }

    /**
     * Given a drawable resource turn it into a bitmap
     * @param drawable Drawable icon
     * @param width Desired bitmap width
     * @param height Desired bitmap height
     * @return Bitmap version of drawable
     */
    @NonNull
    public static Bitmap getBitmap(@NonNull Drawable drawable,
            int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Make sure to maintain the original bounds to avoid disrupting
        // other code that uses this icon
        Rect originalBounds = drawable.copyBounds();
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        drawable.setBounds(originalBounds);
        return bitmap;
    }

    /**
     * Given a drawable resource turn it into a bitmap
     * @param drawable Drawable icon
     * @return Bitmap version of drawable
     */
    @NonNull
    public static Bitmap getBitmap(@NonNull Drawable drawable) {
        return getBitmap(drawable, drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight());
    }

    /**
     * Given a bitmap, return an encoded base64:// asset that can be used in areas where an icon.
     * During this process it uses in memory compression and conversion of the bitmap to png, so be
     * mindful of the size of the input bitmap.
     * @param bitmap the bitmap to encode as base64://
     * @return Base64 string encoded that can be decoded.
     */
    @Nullable
    public static String encodeBitmap(final Bitmap bitmap) {
        if (bitmap == null)
            return null;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        final byte[] b = baos.toByteArray();
        return "base64://" + android.util.Base64.encodeToString(b,
                Base64.NO_WRAP | Base64.URL_SAFE);
    }

    /**
     * Given a base64:// encoded image, decode into a bitmap
     * @param encoded The encoded image to get the bitmap from.  Must start with base64://
     * @return New Bitmap from the encoded image.
     */
    @Nullable
    public static Bitmap decodeBitmap(final String encoded) {
        if (encoded == null || !encoded.startsWith("base64://"))
            return null;

        byte[] data = Base64.decode(encoded.substring("base64://".length()),
                Base64.NO_WRAP | Base64.URL_SAFE);
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }
}
