package com.atakmap.map.layer.raster.tilereader.opengl;

import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.math.MathUtils;
import com.atakmap.math.RectD;
import com.atakmap.math.Rectangle;

import java.util.Comparator;

final class TileReadRequestPrioritizer implements Comparator<TileReader.ReadRequest> {
    RectD[] rois;
    int numRois;
    double poiX;
    double poiY;
    int levelPrioritizer;

    /**
     *
     * @param highToLowLevelPrioritization  If <code>true</code>, requests for
 *                                          high resolution tiles are given
     *                                      higher priority than requests for
     *                                      low resolution tiles. If
     *                                      <code>false</code>, requests for
     *                                      low resolution tiles are given
     *                                      higher priority than requests for
     *                                      high resolution tiles.
     */
    TileReadRequestPrioritizer(boolean highToLowLevelPrioritization) {
        levelPrioritizer = highToLowLevelPrioritization ? -1 : 1;
    }

    void setFocus(double poiX, double poiY, RectD[] rois, int numRois) {
        this.poiX = poiX;
        this.poiY = poiY;
        if(this.rois == null || this.rois.length < numRois)
            this.rois = new RectD[numRois];
        for(int i = 0; i < numRois; i++) {
            if(this.rois[i] == null)
                this.rois[i] = new RectD();
            this.rois[i].left = rois[i].left;
            this.rois[i].top = rois[i].top;
            this.rois[i].right = rois[i].right;
            this.rois[i].bottom = rois[i].bottom;
        }
        this.numRois = numRois;
    }

    @Override
    public int compare(TileReader.ReadRequest a, TileReader.ReadRequest b) {
        boolean aIsect = false;
        boolean bIsect = false;
        for(int i = 0; i < numRois; i++) {
            aIsect |= Rectangle.intersects(rois[i].left, rois[i].top, rois[i].right, rois[i].bottom, a.srcX, a.srcY, a.srcX+a.srcW-1, a.srcY+a.srcH-1);
            bIsect |= Rectangle.intersects(rois[i].left, rois[i].top, rois[i].right, rois[i].bottom, b.srcX, b.srcY, b.srcX+b.srcW-1, b.srcY+b.srcH-1);
            if(aIsect && bIsect)
                break;
        }

        // if both requests intersect the ROI(s), prioritize based on level
        if(aIsect && bIsect && a.level != b.level)
            return (a.level-b.level) * levelPrioritizer;
        else if(aIsect && !bIsect)
            return 1; // only A intersects ROI(s)
        else if(!aIsect && bIsect)
            return -1; // only B intersects ROI(s)

        // either A and B both intersect ROIs (at same level) or neither do. In
        // both cases, we're going to prioritize based on distance of the
        // requested tile from the POI
        final boolean aContains = Rectangle.contains(a.srcX, a.srcY, a.srcX+a.srcW-1, a.srcY+a.srcH-1, poiX, poiY);
        final boolean bContains = Rectangle.contains(b.srcX, b.srcY, b.srcX+b.srcW-1, b.srcY+b.srcH-1, poiX, poiY);
        if(aContains && bContains)
            return (a.level-b.level)*levelPrioritizer; // both contains, prioritize based on level
        if(aContains && !bContains)
            return 1; // A contains
        else if(!aContains && bContains)
            return -1; // B contains
        final double aClosestX = MathUtils.clamp(poiX, a.srcX, a.srcX+a.srcW-1);
        final double aClosestY = MathUtils.clamp(poiY, a.srcY, a.srcY+a.srcH-1);
        final double bClosestX = MathUtils.clamp(poiX, b.srcX, b.srcX+b.srcW-1);
        final double bClosestY = MathUtils.clamp(poiY, b.srcY, b.srcY+b.srcH-1);
        final double aDistSq = (aClosestX-poiX)*(aClosestX-poiX) + (aClosestY-poiY)*(aClosestY-poiY);
        final double bDistSq = (bClosestX-poiX)*(bClosestX-poiX) + (bClosestY-poiY)*(bClosestY-poiY);
        if(aDistSq < bDistSq)
            return 1; // A is closer than B
        else if(aDistSq > bDistSq)
            return -1; // B is closer than A

        // prioritize based on level
        return (a.level-b.level)*levelPrioritizer;
    }
}
