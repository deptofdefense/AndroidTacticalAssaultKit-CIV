
package com.atakmap.android.maps.hittest;

import android.graphics.RectF;

import com.atakmap.annotations.DeprecatedApi;

import java.util.Objects;

/**
 * @deprecated No longer used
 */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class PartitionRect extends RectF {

    // Start and end point indices
    public int startIndex, endIndex;

    public PartitionRect() {
    }

    public PartitionRect(PartitionRect other) {
        super(other);
        this.startIndex = other.startIndex;
        this.endIndex = other.endIndex;
    }

    public void set(PartitionRect other) {
        super.set(other);
        this.startIndex = other.startIndex;
        this.endIndex = other.endIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), startIndex, endIndex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof RectF))
            return false;

        RectF r = (RectF) o;
        return left == r.left && top == r.top && right == r.right
                && bottom == r.bottom;
    }
}
