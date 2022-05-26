
package com.atakmap.android.util;

import android.content.Context;

public class TakDialogAdapter extends MappingAdapter {
    public TakDialogAdapter(Context context) {
        super(context);
        addMapping(TakDialogMapItemVM.class, TakDialogMapItemVH.class);
    }
}
