
package com.atakmap.android.update.sorters;

import com.atakmap.android.update.ProductInformation;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Comparator;

/**
 * Comparator to support sorting based only on simple name.
 */
public class ProductInformationComparator implements
        Comparator<ProductInformation> {

    @Override
    public int compare(final ProductInformation lhs,
            final ProductInformation rhs) {
        if (lhs == null || lhs.getSimpleName() == null)
            return 1;
        else if (rhs == null || rhs.getSimpleName() == null)
            return -1;

        //sort by lower case name
        final String lhslc = lhs.getSimpleName()
                .toLowerCase(LocaleUtil.getCurrent());
        final String rhslc = rhs.getSimpleName()
                .toLowerCase(LocaleUtil.getCurrent());
        return lhslc.compareTo(rhslc);
    }
}
