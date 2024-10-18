
package com.atakmap.android.tilecapture.reader;

import android.graphics.Point;

import com.atakmap.math.PointD;
import gov.tak.api.util.Disposable;

/**
 * Interface for reading map tiles into a bitmap
 */
public interface BitmapReader extends Disposable {

    /**
     * Get tile bitmap
     * @param level Resolution level
     * @param column Column
     * @param row Row
     * @return Bitmap tile
     */
    TileBitmap getTile(int level, int column, int row);

    /**
     * Get tile point
     * @param level Resolution level
     * @param src Source point
     * @param dst Destination point
     */
    void getTilePoint(int level, PointD src, Point dst);

    /**
     * Get source point
     * @param level Resolution level
     * @param column Column
     * @param row Row
     * @param dst Destination point
     */
    void getSourcePoint(int level, int column, int row, PointD dst);
}
