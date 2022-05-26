
package com.atakmap.android.maps.graphics.widgets;

import com.atakmap.android.widgets.AbstractParentWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import gov.tak.api.annotation.DeprecatedApi;

/** @deprecated use {@link gov.tak.platform.widgets.opengl.GLAbstractParentWidget} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public abstract class GLAbstractParentWidget extends GLWidget2 implements
        AbstractParentWidget.OnWidgetListChangedListener {

    private final static String TAG = "GLAbstractParentWidget";

    public GLAbstractParentWidget(AbstractParentWidget subject,
            GLMapView orthoView) {
        super(subject, orthoView);
        List<MapWidget> children = subject.getChildWidgets();
        int i = 0;
        for (MapWidget child : children)
            onWidgetAdded(subject, i++, child);
    }

    @Override
    public void startObserving(MapWidget subject) {
        super.startObserving(subject);
        if (subject instanceof AbstractParentWidget)
            ((AbstractParentWidget) subject)
                    .addOnWidgetListChangedListener(this);
    }

    @Override
    public void stopObserving(MapWidget subject) {
        super.stopObserving(subject);
        if (subject instanceof AbstractParentWidget)
            ((AbstractParentWidget) subject)
                    .removeOnWidgetListChangedListener(this);
    }

    @Override
    public void drawWidgetContent() {
        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(_padding[LEFT], -_padding[TOP], 0f);
        synchronized (_children) {
            for (GLWidget c : _children.values())
                c.drawWidget();
        }
        GLES20FixedPipeline.glPopMatrix();
    }

    @Override
    public void releaseWidget() {
        synchronized (_children) {
            for (GLWidget w : _children.values())
                w.releaseWidget();
            _children.clear();
        }
        stopObserving(subject);
    }

    @Override
    public void onWidgetAdded(AbstractParentWidget parent, final int index,
            MapWidget child) {
        final GLWidget glW = GLWidgetFactory.create(child, this.orthoView);
        if (glW == null) {
            Log.e(TAG, "Failed to create observer for " + child);
            return;
        }
        // surface.queueEvent(new Runnable() {
        // @Override
        // public void run() {

        synchronized (_children) {
            SortedMap<Integer, GLWidget> tail = _children.tailMap(index);
            SortedMap<Integer, GLWidget> updatedTail = new TreeMap<>();

            Iterator<Map.Entry<Integer, GLWidget>> iter = tail.entrySet()
                    .iterator();
            Map.Entry<Integer, GLWidget> entry;
            while (iter.hasNext()) {
                entry = iter.next();
                iter.remove();

                updatedTail.put(entry.getKey() + 1,
                        entry.getValue());
            }

            _children.put(index, glW);
            if (updatedTail.size() > 0)
                _children.putAll(updatedTail);
        }
    }

    @Override
    public void onWidgetRemoved(AbstractParentWidget parent, final int index,
            MapWidget child) {

        synchronized (_children) {
            GLWidget glWidget = _children.remove(index);
            if (glWidget != null)
                glWidget.releaseWidget();

            SortedMap<Integer, GLWidget> tail = _children.tailMap(index);
            SortedMap<Integer, GLWidget> updatedTail = new TreeMap<>();

            Iterator<Map.Entry<Integer, GLWidget>> iter = tail.entrySet()
                    .iterator();
            Map.Entry<Integer, GLWidget> entry;
            while (iter.hasNext()) {
                entry = iter.next();
                iter.remove();

                updatedTail.put(entry.getKey() - 1,
                        entry.getValue());
            }
            if (updatedTail.size() > 0)
                _children.putAll(updatedTail);
        }
    }

    protected List<GLWidget> getChildren() {
        List<GLWidget> ret = new ArrayList<>();
        synchronized (_children) {
            for (Map.Entry<Integer, GLWidget> c : _children.entrySet())
                ret.add(c.getValue());
        }
        return ret;
    }

    protected final SortedMap<Integer, GLWidget> _children = new TreeMap<>();
}
