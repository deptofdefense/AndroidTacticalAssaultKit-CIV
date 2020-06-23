
package com.atakmap.android.update.sorters;

import com.atakmap.android.update.ProductInformation;

import java.util.Comparator;

/**
 * Comparator to support sorting based on validity then by simple name.
 */
public class ProductInformationSorter
        implements Comparator<ProductInformation> {

    public ProductInformationSorter() {
    }

    @Override
    public int compare(final ProductInformation lhs,
            final ProductInformation rhs) {
        if (lhs == null || !lhs.isValid())
            return -1;
        if (rhs == null || !rhs.isValid())
            return 1;

        return lhs.getSimpleName().compareToIgnoreCase(rhs.getSimpleName());
    }
}
