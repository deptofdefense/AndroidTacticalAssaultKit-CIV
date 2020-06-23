
package com.atakmap.android.widgets;

import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class FixedItemCountWidget extends MapWidget {

    public interface OnItemChangedListener {
        void onWidgetItemChanged(FixedItemCountWidget widget, int index,
                WidgetItem item);
    }

    public interface OnItemStateChangedListener {
        void onWidgetItemStateChanged(FixedItemCountWidget widget,
                int index);
    }

    public FixedItemCountWidget(int itemCount) {
        _itemCount = itemCount;
        _items = new WidgetItem[itemCount];
        _itemStates = new int[itemCount];
    }

    public void addOnItemChangedListener(OnItemChangedListener l) {
        _onItemChanged.add(l);
    }

    public void removeOnItemChangedListener(OnItemChangedListener l) {
        _onItemChanged.remove(l);
    }

    public void addOnItemStateChangedListener(OnItemStateChangedListener l) {
        _onItemStateChanged.add(l);
    }

    public void removeOnItemStateChangedListener(OnItemStateChangedListener l) {
        _onItemStateChanged.remove(l);
    }

    public int getItemCount() {
        return _itemCount;
    }

    public void setItem(int index, WidgetItem item) {
        if (_items[index] != item) {
            _items[index] = item;
            onRadialMenuItemChanged(index, item);
        }
    }

    public void setItemState(int index, int state) {
        if (_itemStates[index] != state) {
            _itemStates[index] = state;
            onRadialMenuItemStateChanged(index);
        }
    }

    public void setItemsFromList(WidgetItemList list) {
        int count = Math.min(list.getItemCount(), getItemCount());
        for (int i = 0; i < count; ++i) {
            setItem(i, list.getItem(i));
        }
    }

    public void removeItemState(int index, int state) {
        setItemState(index, getItemState(index) & ~state);
    }

    public void addItemState(int index, int state) {
        setItemState(index, getItemState(index) | state);
    }

    public WidgetItem[] getItems() {
        return _items.clone();
    }

    public int[] getItemStates() {
        return _itemStates.clone();
    }

    public WidgetItem getItem(int index) {
        return _items[index];
    }

    public int getItemState(int index) {
        return _itemStates[index];
    }

    protected void onRadialMenuItemChanged(int index, WidgetItem item) {
        for (OnItemChangedListener l : _onItemChanged) {
            l.onWidgetItemChanged(this, index, item);
        }
    }

    protected void onRadialMenuItemStateChanged(int index) {
        for (OnItemStateChangedListener l : _onItemStateChanged) {
            l.onWidgetItemStateChanged(this, index);
        }
    }

    private final int _itemCount;
    private final WidgetItem[] _items;
    private final int[] _itemStates;
    private final ConcurrentLinkedQueue<OnItemChangedListener> _onItemChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnItemStateChangedListener> _onItemStateChanged = new ConcurrentLinkedQueue<>();
}
