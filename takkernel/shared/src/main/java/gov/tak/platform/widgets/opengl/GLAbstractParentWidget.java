
package gov.tak.platform.widgets.opengl;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import gov.tak.api.engine.map.MapRenderer;
import gov.tak.api.widgets.IParentWidget;
import gov.tak.api.widgets.opengl.IGLWidget;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.platform.commons.opengl.Matrix;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public abstract class GLAbstractParentWidget extends GLWidget implements
        IParentWidget.OnWidgetListChangedListener {

    private final static String TAG = "GLAbstractParentWidget";

    public GLAbstractParentWidget(IParentWidget subject,
                                  MapRenderer orthoView) {
        super(subject, orthoView);
        List<IMapWidget> children = subject.getChildren();
        int i = 0;
        for (IMapWidget child : children)
            onWidgetAdded(subject, i++, child);
    }

    @Override
    public void start() {
        super.start();
        if (subject instanceof IParentWidget)
            ((IParentWidget) subject)
                    .addOnWidgetListChangedListener(this);
    }

    @Override
    public void stop() {
        super.stop();
        if (subject instanceof IParentWidget)
            ((IParentWidget) subject)
                    .removeOnWidgetListChangedListener(this);
    }

    @Override
    public void drawWidgetContent(DrawState drawState) {

        DrawState localDrawState = drawState.clone();
        Matrix.translateM(localDrawState.modelMatrix, 0, _padding[LEFT], -_padding[TOP], 0f);
        synchronized (_children) {
            for (IGLWidget c : _children.values())
                c.drawWidget(localDrawState);
        }
        localDrawState.recycle();
    }

    @Override
    public void releaseWidget() {
        synchronized (_children) {
            for (IGLWidget w : _children.values())
                w.releaseWidget();
            _children.clear();
        }
    }

    @Override
    public void onWidgetAdded(IParentWidget parent, final int index,
                              IMapWidget child) {
        final IGLWidget glW = GLWidgetFactory.create(this.getMapRenderer(), child);

        if (glW == null) {
            Log.d(TAG, "Failed to create observer for " + child);
            return;
        }

        glW.start();

        synchronized (_children) {
            SortedMap<Integer, IGLWidget> tail = _children.tailMap(index);
            SortedMap<Integer, IGLWidget> updatedTail = new TreeMap<>();

            Iterator<Map.Entry<Integer, IGLWidget>> iter = tail.entrySet()
                    .iterator();
            Map.Entry<Integer, IGLWidget> entry;
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
    public void onWidgetRemoved(IParentWidget parent, final int index,
                                IMapWidget child) {

        synchronized (_children) {
            IGLWidget glWidget = _children.remove(index);
            if (glWidget != null) {
                glWidget.stop();
                glWidget.releaseWidget();
            }

            SortedMap<Integer, IGLWidget> tail = _children.tailMap(index);
            SortedMap<Integer, IGLWidget> updatedTail = new TreeMap<>();

            Iterator<Map.Entry<Integer, IGLWidget>> iter = tail.entrySet()
                    .iterator();
            Map.Entry<Integer, IGLWidget> entry;
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

    protected List<IGLWidget> getChildren() {
        List<IGLWidget> ret = new ArrayList<>();
        synchronized (_children) {
            for (Map.Entry<Integer, IGLWidget> c : _children.entrySet())
                ret.add(c.getValue());
        }
        return ret;
    }

    protected final SortedMap<Integer, IGLWidget> _children = new TreeMap<>();
}
