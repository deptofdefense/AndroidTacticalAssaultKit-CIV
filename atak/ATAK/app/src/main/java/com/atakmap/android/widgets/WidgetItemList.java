
package com.atakmap.android.widgets;

public class WidgetItemList {

    public WidgetItemList(WidgetItem[] items) {
        _items = items.clone();
    }

    public int getItemCount() {
        return _items.length;
    }

    public WidgetItem getItem(int index) {
        return _items[index];
    }

    private final WidgetItem[] _items;
}
