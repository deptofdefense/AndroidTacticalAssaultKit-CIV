
package com.atakmap.android.util;

import androidx.annotation.DrawableRes;

import com.atakmap.android.maps.MapItem;
import com.atakmap.app.R;

public class TakDialogMapItemVM extends MappingVM {
    private final MapItem _mapItem;
    private boolean _selected;

    public TakDialogMapItemVM(MapItem mapItem, boolean selected) {
        this._mapItem = mapItem;
        this._selected = selected;
    }

    public @DrawableRes int getImage() {
        if (_selected) {
            return R.drawable.row_select_checked;
        } else {
            return R.drawable.row_select_empty;
        }
    }

    public void setSelected(boolean selected) {
        _selected = selected;
    }

    public String getText() {
        return _mapItem.getTitle();
    }
}
