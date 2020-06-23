
package com.atakmap.android.vehicle.overhead;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.BitSet;
import java.util.Comparator;

import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;

/**
 * An overhead image with matching length and width in meters
 */

public class OverheadImage {

    private static final String TAG = "OverheadImage";

    public static final Comparator<OverheadImage> NAME_COMPARATOR = new Comparator<OverheadImage>() {
        @Override
        public int compare(OverheadImage lhs, OverheadImage rhs) {
            return lhs.name.compareTo(rhs.name);
        }
    };

    public final Hitmap hitmap;
    public final String name, group, imageUri;
    public final double width, length, height;
    public final int resId;

    public OverheadImage(Context context, String name, String group,
            String imagePath, double widthFt, double lengthFt,
            double heightFt) {
        this.name = name;
        this.group = group;
        if (context != null && imagePath.startsWith("res/")) {
            String[] dirs = imagePath.split("/");
            String type = dirs[1].startsWith("drawable") ? "drawable" : dirs[1];
            String id = dirs[dirs.length - 1].substring(0,
                    dirs[dirs.length - 1]
                            .lastIndexOf("."));
            this.resId = context.getResources().getIdentifier(id, type,
                    context.getPackageName());
            this.imageUri = "android.resource://" + context.getPackageName()
                    + "/"
                    + this.resId;
        } else {
            this.resId = 0;
            this.imageUri = "file://" + imagePath;
        }
        this.width = SpanUtilities.convert(widthFt, Span.FOOT, Span.METER);
        this.length = SpanUtilities.convert(lengthFt, Span.FOOT, Span.METER);
        this.height = SpanUtilities.convert(heightFt, Span.FOOT, Span.METER);
        this.hitmap = generateHitmap(this.imageUri);
    }

    public static Hitmap generateHitmap(String uri) {
        Bitmap b = ATAKUtilities.getUriBitmap(uri);
        if (b == null)
            return null;
        int width = b.getWidth(), height = b.getHeight();
        int[] p = new int[width * height];
        b.getPixels(p, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        b.recycle();
        BitSet data = new BitSet(p.length);
        for (int i = 0; i < p.length; i++)
            data.set(i, Color.alpha(p[i]) >= 128);
        return new Hitmap(data, width, height);
    }
}
