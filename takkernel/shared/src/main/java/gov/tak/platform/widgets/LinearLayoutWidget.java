
package gov.tak.platform.widgets;

import gov.tak.platform.ui.MotionEvent;

import com.atakmap.coremap.filesystem.FileSystemUtils;

import gov.tak.api.widgets.IParentWidget;
import gov.tak.api.widgets.ILinearLayoutWidget;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.platform.view.Gravity;
import gov.tak.platform.widget.LinearLayout;
import gov.tak.platform.widget.LinearLayout.LayoutParams;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Parent widget with functions similar to a LinearLayout view
 */
public class LinearLayoutWidget extends LayoutWidget implements
        ILinearLayoutWidget, IMapWidget.OnVisibleChangedListener,
        MapWidget.OnWidgetSizeChangedListener {

    // Dimensions
    public static final int MATCH_PARENT = LayoutParams.MATCH_PARENT;
    public static final int WRAP_CONTENT = LayoutParams.WRAP_CONTENT;

    // Draw order of children
    public static final int HORIZONTAL = LinearLayout.HORIZONTAL;
    public static final int VERTICAL = LinearLayout.VERTICAL;

    protected int _orientation = VERTICAL;
    protected int _gravity = Gravity.START | Gravity.TOP;
    protected int _paramWidth = WRAP_CONTENT, _paramHeight = WRAP_CONTENT;
    protected int _screenWidth = 0, _screenHeight = 0;
    protected float _childrenWidth = 0, _childrenHeight = 0;

    public LinearLayoutWidget() {
        this(WRAP_CONTENT, WRAP_CONTENT, VERTICAL);
    }

    public LinearLayoutWidget(int paramWidth, int paramHeight,
            int orientation) {
        _paramWidth = paramWidth;
        _paramHeight = paramHeight;
        setOrientation(orientation);
    }

    /**
     * Sets the orientation of the linear layout widget to be either VERTICAL or HORIZONTAL.
     * @param orientation the value either HORIZONTAL or VERTICAL
     */
    public void setOrientation(int orientation) {
        if (_orientation != orientation) {
            _orientation = orientation;
            recalcSize();
        }
    }

    /**
     * Returns the orientation of the linear layout widget.
     * @return
     */
    public int getOrientation() {
        return _orientation;
    }

    /**
     * Sets the gravity of the linear layout widget, see Gravity.CENTER_HORIZONTAL, etc.
     * @param gravity
     */
    public void setGravity(int gravity) {
        if (_gravity != gravity) {
            if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0)
                gravity |= Gravity.START;
            if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == 0)
                gravity |= Gravity.TOP;
            _gravity = gravity;
            onSizeChanged();
        }
    }

    public int getGravity() {
        return _gravity;
    }

    /**
     * Set the layout params of this widget
     * Can be WRAP_CONTENT, MATCH_PARENT, or a pixel size value
     * @param pWidth Width parameter
     * @param pHeight Height parameter
     */
    public void setLayoutParams(int pWidth, int pHeight) {
        _paramWidth = pWidth;
        _paramHeight = pHeight;
        recalcSize();
    }

    /**
     * Provide mechanism for setting the screen dimensions of the map view.
     *
     * @param width The width of the map view.
     * @param height The height of the map view.
     */
    public void setScreenSize(int width, int height) {
        _screenWidth = width;
        _screenHeight = height;
    }

    public int[] getLayoutParams() {
        return new int[] {
                _paramWidth, _paramHeight
        };
    }

    /**
     * Get the size in pixels taken up by this layout's children
     * @return [width, height]
     */
    public float[] getChildrenSize() {
        return new float[] {
                _childrenWidth, _childrenHeight
        };
    }

    @Override
    public void onWidgetAdded(int index, IMapWidget w) {
        recalcSize();
        w.addOnVisibleChangedListener(this);
        if (w instanceof MapWidget)
            w.addOnWidgetSizeChangedListener(this);
        super.onWidgetAdded(index, w);
    }

    @Override
    public void onWidgetRemoved(int index, IMapWidget w) {
        recalcSize();
        w.removeOnVisibleChangedListener(this);
        if (w instanceof MapWidget)
            w.removeOnWidgetSizeChangedListener(this);
        super.onWidgetRemoved(index, w);
    }

    @Override
    public void onVisibleChanged(IMapWidget widget) {
        // Child widget visibility changed
        recalcSize();
    }

    @Override
    public void onWidgetSizeChanged(IMapWidget widget) {
        // Child widget size changed
        recalcSize();
    }

    @Override
    public IMapWidget seekWidgetHit(MotionEvent event, float x, float y) {
        // Ensure the hit is within this layout first
        // LayoutWidget does not perform this check...
        if (isVisible() && super.testHit(x, y))
            return super.seekWidgetHit(event, x, y);
        return null;
    }

    /**
     * Get or create a new layout widget in this hierarchy
     * @param path Layout names separated by slashes
     *             where the last letter specifies the orientation (V or H)
     *             i.e. WidgetLayoutV/WidgetLayoutH = horizontal within vertical
     * @return Existing or newly created layout widget
     */
    public LinearLayoutWidget getOrCreateLayout(String path) {
        LinearLayoutWidget ret = null;
        int firstSlash = path.indexOf("/");
        String name = firstSlash > -1 ? path.substring(0, firstSlash) : path;
        int orientation = -1;
        if (name.endsWith("H"))
            orientation = HORIZONTAL;
        else if (name.endsWith("V"))
            orientation = VERTICAL;
        for (IMapWidget c : getChildren()) {
            if (c instanceof LinearLayoutWidget && FileSystemUtils
                    .isEquals(name, c.getName())) {
                ret = (LinearLayoutWidget) c;
                break;
            }
        }
        if (ret == null) {
            ret = new LinearLayoutWidget();
            ret.setName(name);
            ret.setGravity(getGravity());
            addChildWidget(ret);
        }
        if (orientation != -1 && ret.getOrientation() != orientation)
            ret.setOrientation(orientation);
        if (firstSlash > -1)
            ret = ret.getOrCreateLayout(path.substring(firstSlash + 1));
        return ret;
    }

    public void recalcSize() {
        final float pWidth = _paramWidth, pHeight = _paramHeight;
        float width = 0, height = 0;
        IParentWidget parent = getParent();

        // Find the first parent that has an explicit or wrapped width
        float parentWidth = LinearLayoutWidget.MATCH_PARENT;
        while (parent != null
                && parentWidth == LinearLayoutWidget.MATCH_PARENT) {
            parentWidth = parent.getWidth();
            if (parent instanceof LinearLayoutWidget)
                parentWidth = ((LinearLayoutWidget) parent)._paramWidth;
            parent = parent.getParent();
        }
        // If still set to match parent use the screen width
        if (parentWidth == LinearLayoutWidget.MATCH_PARENT)
            parentWidth = _screenWidth;

        // Perform the same for height
        parent = getParent();
        float parentHeight = LinearLayoutWidget.MATCH_PARENT;
        while (parent != null
                && parentHeight == LinearLayoutWidget.MATCH_PARENT) {
            parentHeight = parent.getHeight();
            if (parent instanceof LinearLayoutWidget)
                parentHeight = ((LinearLayoutWidget) parent)._paramHeight;
            parent = parent.getParent();
        }
        if (parentHeight == LinearLayoutWidget.MATCH_PARENT)
            parentHeight = _screenHeight;

        // Explicitly set dimensions
        if (pWidth >= 0)
            width = pWidth;
        else if (pWidth == LinearLayoutWidget.MATCH_PARENT)
            width = parentWidth;

        if (pHeight >= 0)
            height = pHeight;
        else if (pHeight == LinearLayoutWidget.MATCH_PARENT)
            height = parentHeight;

        float maxW = 0, maxH = 0, totalW = 0, totalH = 0;
        Collection<IMapWidget> children = getChildren();
        List<LinearLayoutWidget> matchParentW = new ArrayList<>();
        List<LinearLayoutWidget> matchParentH = new ArrayList<>();
        for (IMapWidget c : children) {
            float[] size = MapWidget.getSize(c, true, true);
            if (c instanceof LinearLayoutWidget) {
                LinearLayoutWidget llw = (LinearLayoutWidget) c;
                if (llw._paramWidth == MATCH_PARENT) {
                    size[0] = 0;
                    matchParentW.add(llw);
                }
                if (llw._paramHeight == MATCH_PARENT) {
                    size[1] = 0;
                    matchParentH.add(llw);
                }
            }
            maxW = Math.max(maxW, size[0]);
            maxH = Math.max(maxH, size[1]);
            totalW += size[0];
            totalH += size[1];
        }
        float cWidth = _orientation == LinearLayoutWidget.HORIZONTAL
                ? totalW
                : maxW;
        float cHeight = _orientation == LinearLayoutWidget.VERTICAL
                ? totalH
                : maxH;

        // Account for children with the match_parent attribute
        boolean childSizeChanged = false;
        if (!matchParentW.isEmpty()) {
            if (pWidth == LinearLayoutWidget.WRAP_CONTENT)
                width = parentWidth;
            float remWidth = Math.max(0, width - cWidth)
                    / matchParentW.size();
            for (MapWidget w : matchParentW) {
                float[] p = w.getPadding();
                float[] m = w.getMargins();
                float childWidth = Math.max(0, remWidth
                        - (m[LEFT] + p[LEFT] + p[RIGHT] + m[RIGHT]));
                childSizeChanged |= w.setWidth(childWidth);
            }
            cWidth = width;
        }
        if (!matchParentH.isEmpty()) {
            if (pHeight == LinearLayoutWidget.WRAP_CONTENT)
                height = parentHeight;
            float remHeight = Math.max(0, height - cHeight)
                    / matchParentH.size();
            for (MapWidget w : matchParentH) {
                float[] p = w.getPadding();
                float[] m = w.getMargins();
                float childHeight = Math.max(0, remHeight
                        - (m[TOP] + p[TOP] + p[BOTTOM] + m[BOTTOM]));
                childSizeChanged |= w.setHeight(childHeight);
            }
            cHeight = height;
        }

        if (!childSizeChanged) {
            if (pWidth == LinearLayoutWidget.WRAP_CONTENT)
                width = cWidth;
            if (pHeight == LinearLayoutWidget.WRAP_CONTENT)
                height = cHeight;
            _childrenWidth = cWidth;
            _childrenHeight = cHeight;
            super.setSize(width, height);
        }
    }
}
