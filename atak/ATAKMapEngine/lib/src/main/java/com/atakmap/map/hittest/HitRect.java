package com.atakmap.map.hittest;

import android.graphics.RectF;

/**
 * A copy of {@link RectF} that uses a lower-left display origin for hit-testing
 */
public class HitRect {

    public float left;
    public float bottom;
    public float right;
    public float top;

    public HitRect() {
    }

    public HitRect(float left, float bottom, float right, float top) {
        this.left = left;
        this.bottom = bottom;
        this.right = right;
        this.top = top;
    }

    public HitRect(HitRect other) {
        this.left = other.left;
        this.bottom = other.bottom;
        this.right = other.right;
        this.top = other.top;
    }

    /**
     * Returns true if the rectangle is empty (left >= right or top >= bottom)
     */
    public final boolean isEmpty() {
        return left >= right || bottom >= top;
    }

    /**
     * @return the rectangle's width. This does not check for a valid rectangle
     * (i.e. left <= right) so the result may be negative.
     */
    public final float width() {
        return right - left;
    }

    /**
     * @return the rectangle's height. This does not check for a valid rectangle
     * (i.e. bottom <= top) so the result may be negative.
     */
    public final float height() {
        return top - bottom;
    }

    /**
     * @return the horizontal center of the rectangle. This does not check for
     * a valid rectangle (i.e. left <= right)
     */
    public final float centerX() {
        return (left + right) * 0.5f;
    }

    /**
     * @return the vertical center of the rectangle. This does not check for
     * a valid rectangle (i.e. bottom <= top)
     */
    public final float centerY() {
        return (bottom + top) * 0.5f;
    }

    /**
     * Set the rectangle to (0,0,0,0)
     */
    public void setEmpty() {
        set(0, 0, 0, 0);
    }

    /**
     * Set the rectangle's coordinates to the specified values. Note: no range
     * checking is performed, so it is up to the caller to ensure that
     * left <= right and bottom <= top.
     *
     * @param left   The X coordinate of the left side of the rectangle
     * @param bottom The Y coordinate of the bottom of the rectangle
     * @param right  The X coordinate of the right side of the rectangle
     * @param top    The Y coordinate of the top of the rectangle
     */
    public void set(float left, float bottom, float right, float top) {
        this.left = left;
        this.bottom = bottom;
        this.right = right;
        this.top = top;
    }

    /**
     * Copy the coordinates from src into this rectangle.
     *
     * @param src The rectangle whose coordinates are copied into this
     *           rectangle.
     */
    public void set(HitRect src) {
        this.left = src.left;
        this.bottom = src.bottom;
        this.right = src.right;
        this.top = src.top;
    }

    /**
     * Offset the rectangle by adding dx to its left and right coordinates, and
     * adding dy to its top and bottom coordinates.
     *
     * @param dx The amount to add to the rectangle's left and right coordinates
     * @param dy The amount to add to the rectangle's top and bottom coordinates
     */
    public void offset(float dx, float dy) {
        left += dx;
        top += dy;
        right += dx;
        bottom += dy;
    }

    /**
     * Offset the rectangle to a specific (left, bottom) position,
     * keeping its width and height the same.
     *
     * @param newLeft   The new "left" coordinate for the rectangle
     * @param newBottom    The new "bottom" coordinate for the rectangle
     */
    public void offsetTo(float newLeft, float newBottom) {
        right += newLeft - left;
        top += newBottom - bottom;
        left = newLeft;
        bottom = newBottom;
    }

    /**
     * Inset the rectangle by (dx,dy). If dx is positive, then the sides are
     * moved inwards, making the rectangle narrower. If dx is negative, then the
     * sides are moved outwards, making the rectangle wider. The same holds true
     * for dy and the top and bottom.
     *
     * @param dx The amount to add(subtract) from the rectangle's left(right)
     * @param dy The amount to add(subtract) from the rectangle's bottom(top)
     */
    public void inset(float dx, float dy) {
        left += dx;
        top -= dy;
        right -= dx;
        bottom += dy;
    }

    /**
     * Returns true if (x,y) is inside the rectangle. The left and bottom are
     * considered to be inside, while the right and top are not. This means
     * that for a x,y to be contained: left <= x < right and bottom <= y < top.
     * An empty rectangle never contains any point.
     *
     * @param x The X coordinate of the point being tested for containment
     * @param y The Y coordinate of the point being tested for containment
     * @return true iff (x,y) are contained by the rectangle, where containment
     *              means left <= x < right and bottom <= y < top
     */
    public boolean contains(float x, float y) {
        return this.left < this.right && this.bottom < this.top
                && x >= left && x < right && y >= bottom && y < top;
    }

    /**
     * Returns true iff the 4 specified sides of a rectangle are inside or equal
     * to this rectangle. i.e. is this rectangle a superset of the specified
     * rectangle. An empty rectangle never contains another rectangle.
     *
     * @param left The left side of the rectangle being tested for containment
     * @param bottom The bottom of the rectangle being tested for containment
     * @param right The right side of the rectangle being tested for containment
     * @param top The top of the rectangle being tested for containment
     * @return true iff the the 4 specified sides of a rectangle are inside or
     *              equal to this rectangle
     */
    public boolean contains(float left, float bottom, float right, float top) {
        return this.left < this.right && this.bottom < this.top
                && this.left <= left && this.top >= top
                && this.right >= right && this.bottom <= bottom;
    }

    /**
     * Returns true iff the specified rectangle r is inside or equal to this
     * rectangle. An empty rectangle never contains another rectangle.
     *
     * @param r The rectangle being tested for containment.
     * @return true iff the specified rectangle r is inside or equal to this
     *              rectangle
     */
    public boolean contains(HitRect r) {
        return contains(r.left, r.bottom, r.right, r.top);
    }

    /**
     * Check if two rectangles intersect each other
     *
     * @param other Other rectangle
     * @return True if this rectangle intersects/overlaps the other rectangle
     */
    public boolean intersects(HitRect other) {
        return intersects(other.left, other.bottom, other.right, other.top);
    }

    /**
     * If the rectangle specified by left,bottom,right,top intersects this
     * rectangle, return true and set this rectangle to that intersection,
     * otherwise return false and do not change this rectangle. No check is
     * performed to see if either rectangle is empty. Note: To just test for
     * intersection, use intersects()
     *
     * @param left The left side of the rectangle being intersected with this
     *             rectangle
     * @param bottom The bottom of the rectangle being intersected with this rectangle
     * @param right The right side of the rectangle being intersected with this
     *              rectangle.
     * @param top The top of the rectangle being intersected with this
     *             rectangle.
     * @return true if the specified rectangle and this rectangle intersect
     *              (and this rectangle is then set to that intersection) else
     *              return false and do not change this rectangle.
     */
    public boolean intersects(float left, float bottom, float right,
                              float top) {
        return this.left < right && left < this.right
                && this.top > bottom && top > this.bottom;
    }

    /**
     * If the specified rectangle intersects this rectangle, return true and set
     * this rectangle to that intersection, otherwise return false and do not
     * change this rectangle. No check is performed to see if either rectangle
     * is empty. To just test for intersection, use intersects()
     *
     * @param r The rectangle being intersected with this rectangle.
     * @return true if the specified rectangle and this rectangle intersect
     *              (and this rectangle is then set to that intersection) else
     *              return false and do not change this rectangle.
     */
    public boolean intersect(HitRect r) {
        return intersect(r.left, r.bottom, r.right, r.top);
    }

    public boolean intersect(float left, float bottom, float right, float top) {
        if (intersects(left, bottom, right, top)) {
            if (this.left < left) this.left = left;
            if (this.top > top) this.top = top;
            if (this.right > right) this.right = right;
            if (this.bottom < bottom) this.bottom = bottom;
            return true;
        }
        return false;
    }

    public boolean setIntersect(HitRect a, HitRect b) {
        if (a.intersects(b)) {
            left = Math.max(a.left, b.left);
            top = Math.min(a.top, b.top);
            right = Math.min(a.right, b.right);
            bottom = Math.max(a.bottom, b.bottom);
            return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HitRect r = (HitRect) o;
        return left == r.left && top == r.top && right == r.right && bottom == r.bottom;
    }

    @Override
    public int hashCode() {
        int result = (left != +0.0f ? Float.floatToIntBits(left) : 0);
        result = 31 * result + (bottom != +0.0f ? Float.floatToIntBits(bottom) : 0);
        result = 31 * result + (right != +0.0f ? Float.floatToIntBits(right) : 0);
        result = 31 * result + (top != +0.0f ? Float.floatToIntBits(top) : 0);
        return result;
    }

    @Override
    public String toString() {
        return "GLRectF(" + left + ", " + bottom + ", "
                + right + ", " + top + ")";
    }
}
