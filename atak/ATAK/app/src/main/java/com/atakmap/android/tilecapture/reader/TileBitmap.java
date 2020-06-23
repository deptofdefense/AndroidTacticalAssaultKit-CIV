
package com.atakmap.android.tilecapture.reader;

import android.graphics.Bitmap;

/**
 * A bitmap with an associated level, column, and row
 */
public class TileBitmap {

    public Bitmap bmp;
    public int level, column, row;

    public TileBitmap(Bitmap bmp, int level, int column, int row) {
        this.bmp = bmp;
        this.level = level;
        this.column = column;
        this.row = row;
    }
}
