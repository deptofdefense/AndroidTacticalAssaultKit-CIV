
package com.atakmap.android.maps.graphics.widgets;

import android.graphics.Point;

import com.atakmap.android.maps.MapDataRef;
import com.atakmap.android.maps.graphics.GLImage;
import com.atakmap.android.maps.graphics.GLImageCache;
import com.atakmap.android.widgets.FixedItemCountWidget;
import com.atakmap.android.widgets.FixedItemCountWidget.OnItemChangedListener;
import com.atakmap.android.widgets.FixedItemCountWidget.OnItemStateChangedListener;
import com.atakmap.android.widgets.WidgetItem;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;

public abstract class GLFixedItemCountWidget extends GLWidget implements
        OnItemChangedListener,
        OnItemStateChangedListener {

    public GLFixedItemCountWidget(FixedItemCountWidget subject,
            GLMapView orthoView) {
        super(subject, orthoView);

        int itemCount = subject.getItemCount();

        _backingColors = new int[itemCount];
        _imageEntries = new GLImageCache.Entry[itemCount];
        _widgetItems = new WidgetItem[itemCount];
        _icons = new GLImage[itemCount];
        _states = subject.getItemStates();
        for (int i = 0; i < itemCount; ++i) {
            _setItem(i, subject.getItem(i), subject.getItemState(i));
        }
    }

    @Override
    public void onWidgetItemChanged(FixedItemCountWidget widget,
            final int index,
            final WidgetItem item) {
        final int state = widget.getItemState(index);
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _setItem(index, item, state);
            }
        });
    }

    @Override
    public void onWidgetItemStateChanged(FixedItemCountWidget widget,
            final int index) {
        final WidgetItem item = widget.getItem(index);
        final int state = widget.getItemState(index);
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                _setItem(index, item, state);
            }
        });
    }

    public int getBackingColor(int index) {
        return _backingColors[index];
    }

    /** MUST BE INVOKED ON GL THREAD !!! */
    protected GLImage getIconImage(int index) {
        GLImageCache.Entry e = _imageEntries[index];
        GLImage r = _icons[index];
        if (r == null && e != null && e.getTextureId() != 0) {
            WidgetItem item = _widgetItems[index];
            Point anchor = item.getIconAnchor();
            int tx = 0;
            int ty = 0;
            int twidth = item.getIconWidth();
            int theight = item.getIconHeight();
            if (anchor != null) {
                tx = -anchor.x;
                ty = anchor.y - theight + 1;
            }
            r = new GLImage(e.getTextureId(),
                    e.getTextureWidth(),
                    e.getTextureHeight(),
                    e.getImageTextureX(),
                    e.getImageTextureY(),
                    e.getImageTextureWidth(),
                    e.getImageTextureHeight(),
                    tx, ty, twidth, theight);
            _icons[index] = r;
        }
        return r;
    }

    public int getItemState(int index) {
        return _states[index];
    }

    @Override
    public void releaseWidget() {
        getSurface().queueEvent(new Runnable() {
            @Override
            public void run() {
                for (GLImageCache.Entry e : _imageEntries) {
                    if (e != null) {
                        e.release();
                    }
                }
            }
        });
    }

    private void _setItem(int index, WidgetItem item, int itemState) {
        MapDataRef iconRef = item.getIconRef(itemState);
        _backingColors[index] = item.getBackingColor(itemState);
        _widgetItems[index] = item;
        _states[index] = itemState;

        if (iconRef != null) {
            GLImageCache.Entry image = _imageEntries[index];
            if (image != null && !image.getUri().equals(iconRef.toUri())) {
                image.release();
                _imageEntries[index] = null;
            }
            if (_imageEntries[index] == null) {
                _imageEntries[index] = GLRenderGlobals.get(getSurface())
                        .getImageCache()
                        .fetchAndRetain(
                                iconRef.toUri(),
                                false);
            }
        }
    }

    private final int[] _backingColors;
    private final GLImageCache.Entry[] _imageEntries;
    private final WidgetItem[] _widgetItems;
    private final GLImage[] _icons;
    private final int[] _states;
}
