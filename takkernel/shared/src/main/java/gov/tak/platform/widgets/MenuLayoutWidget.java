
package gov.tak.platform.widgets;

import android.graphics.PointF;

import com.atakmap.coremap.log.Log;

import java.util.List;

import gov.tak.api.widgets.IMapMenuButtonWidget;
import gov.tak.api.widgets.IMapMenuWidget;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.IMenuLayoutWidget;
import gov.tak.api.widgets.IParentWidget;
import gov.tak.platform.ui.MotionEvent;
import gov.tak.platform.widgets.factory.MapMenuWidgetFactory;

public class MenuLayoutWidget extends LayoutWidget implements IMenuLayoutWidget,
        IParentWidget.OnWidgetListChangedListener {

    public static final String TAG = "MenuLayoutWidget";
    private static final int RING_PADDING = 5;

    private Object _mapItem = null;

    @Override
    public Object getMapItem() {
        return _mapItem;
    }

    @Override
    public void onClick(MotionEvent event) {
        super.onClick(event);
        _clearAllMenus();
    }

    private IMapMenuWidget _openMapMenu(PointF p) {
        final IMapMenuWidget widget = createMapMenuWidget();
        if (null != widget) {
            layoutAsMenu(widget);
            if (null != p) {
                widget.setPoint(p.x, p.y);
            }
        }
        return widget;
    }
    private IMapMenuWidget createMapMenuWidget() {
        IMapMenuWidget menuWidget = new MapMenuWidget();
        // only need to add; remove effectively will happen as part of gc
        menuWidget.addOnWidgetListChangedListener(this);
        menuWidget.setZOrder(getZOrder());
        menuWidget.fadeAlpha(0, 255, 150); // Perform 150ms fade-in
        return menuWidget;
    }

    private IMapMenuWidget factoryCreateMapMenuWidget(Object object) {
        IMapMenuWidget menuWidget = MapMenuWidgetFactory.create(object);
        menuWidget.addOnWidgetListChangedListener(this);
        menuWidget.setZOrder(getZOrder());
        menuWidget.fadeAlpha(0, 255, 150); // Perform 150ms fade-in
        return menuWidget;
    }

    protected IMapMenuWidget _openSubmenu(IMapMenuWidget parent,
                                          IMapMenuButtonWidget buttonWidget) {
        if ((null == parent) || (null == buttonWidget))
            return null;

        IMapMenuWidget submenuWidget = buttonWidget.getSubmenu();
        if (null != submenuWidget) {
            submenuWidget.setPoint(parent.getPointX(), parent.getPointY());
            layoutAsSubmenu(submenuWidget, buttonWidget);
        }
        return submenuWidget;
    }

    protected boolean _clearAllMenus() {
        boolean r = false;
        while (getChildWidgetCount() > 0) {
            removeChildWidgetAt(getChildWidgetCount() - 1);
            r = true;
        }
        return r;
    }

    private boolean _clearUntilMenu(IMapMenuWidget menu) {
        boolean r = false;
        while (getChildWidgetCount() > 0 && getChildWidgetAt(getChildWidgetCount() - 1) != menu) {
            removeChildWidgetAt(getChildWidgetCount() - 1);
            r = true;
        }
        return r;
    }

    public void clearMenu() {
        _clearAllMenus();
    }

    /**
     * Controls what actions are to be taken when a MapMenuButtonWidget is pressed.
     */
    protected void _addPressExpanders(final IMapMenuWidget menuWidget) {
        for (int i = 0; i < menuWidget.getChildWidgetCount(); ++i) {
            final IMapMenuButtonWidget button = (IMapMenuButtonWidget) menuWidget
                    .getChildWidgetAt(i);

            button.addOnPressListener(new OnPressListener() {
                @Override
                public void onMapWidgetPress(IMapWidget widget, MotionEvent event) {
                    onMenuPress(button, menuWidget, event);
                }
            });

            button.addOnClickListener(new OnClickListener() {
                @Override
                public void onMapWidgetClick(IMapWidget widget, MotionEvent event) {
                    onMenuClick(button, menuWidget,event);
                }
            });

            button.addOnLongPressListener(new OnLongPressListener() {
                @Override
                public void onMapWidgetLongPress(IMapWidget widget) {
                    onMenuLongPress(button, menuWidget);

                }
            });
        }
    }

    protected void onMenuPress(IMapMenuButtonWidget mapWidget, IMapMenuWidget menuWidget, MotionEvent event) {

    }
    protected void onMenuClick(IMapMenuButtonWidget button, IMapMenuWidget menuWidget, MotionEvent event) {
        if (null != button.getOnButtonClickHandler() && button.getOnButtonClickHandler().isSupported(_mapItem)) {
            button.onButtonClick(_mapItem);
        } else {
            IMapMenuWidget submenuWidget = button.getSubmenu();
            if (null != submenuWidget) {
                _expandPressSubmenu(menuWidget, button);
            } else if (!button.isDisabled()) {
                _clearAllMenus();
            }
        }

    }
    protected void onMenuLongPress(IMapMenuButtonWidget mapWidget, IMapMenuWidget menuWidget) {

    }

    protected void onSubMenuPress(IMapMenuButtonWidget parentButton, IMapMenuButtonWidget childButton, IMapMenuWidget submenuWidget, IParentWidget parent, MotionEvent event) {

    }
    protected void onSubMenuClick(IMapMenuButtonWidget parentButton, IMapMenuButtonWidget childButton, IMapMenuWidget submenuWidget, MotionEvent event)
    {
        if (getMapItem() == null)
            return;
        IMapMenuButtonWidget.OnButtonClickHandler clickAction = childButton.getOnButtonClickHandler();
        if (clickAction != null)
        {
            if (clickAction != null) {
                clickAction.performAction(getMapItem());
            } else {
                _clearAllMenus();
            }
        }
        else if (!childButton.isDisabled()) {
            _clearAllMenus();
        }
        else {
            _expandPressSubmenu(submenuWidget, childButton);
        }
    }
    protected void onSubMenuLongPress(IMapMenuButtonWidget parentButton, IMapMenuButtonWidget childButton, IMapMenuWidget submenuWidget) {
        _expandPressSubmenu(submenuWidget, childButton);
    }

    /**
     * Controls what actions are to be taken when a MapMenuButtonWidget is pressed.
     */
    protected void _addPressExpanders(final IMapMenuWidget submenuWidget,
                                      final IMapMenuButtonWidget parentButton) {
        for (int i = 0; i < submenuWidget.getChildWidgetCount(); ++i) {
            final IMapMenuButtonWidget childButton = (IMapMenuButtonWidget) submenuWidget
                    .getChildWidgetAt(i);

            if (parentButton.isDisabled())
                childButton.setDisabled(true);

            final float radius = getChildMenuRadius(submenuWidget,
                    parentButton);
            childButton.setOrientation(childButton.getOrientationAngle(),
                    radius);

            childButton.addOnPressListener(new OnPressListener() {
                @Override
                public void onMapWidgetPress(IMapWidget widget, MotionEvent event) {
                    onSubMenuClick(parentButton, childButton, submenuWidget, event);

                }
            });

            childButton.addOnClickListener(new OnClickListener() {
                @Override
                public void onMapWidgetClick(IMapWidget widget, MotionEvent event) {
                    onSubMenuClick(parentButton, childButton, submenuWidget, event);

                }
            });

            childButton.addOnLongPressListener(new OnLongPressListener() {
                @Override
                public void onMapWidgetLongPress(IMapWidget widget) {
                    onSubMenuLongPress(parentButton, childButton, submenuWidget);

                }
            });
        }
    }

    protected float getChildMenuRadius(final IMapMenuWidget submenuWidget,
                                       final IMapMenuButtonWidget parentButton) {
        return submenuWidget.getInnerRadius() +
                + parentButton.getButtonWidth()
                + RING_PADDING;
    }

    protected void _expandPressSubmenu(IMapMenuWidget menuWidget,
                                       IMapMenuButtonWidget parentButton) {
        IMapMenuWidget submenuWidget = _openSubmenu(menuWidget, parentButton);
        if (submenuWidget != null) {
            _addPressExpanders(submenuWidget, parentButton);
            _clearUntilMenu(menuWidget);
            addChildWidget(submenuWidget);
        }
    }

    /**
     * Open a IMapMenuWidget for an item. The MapMenuWidgetFactory defines
     * the creation of the IMapMenuWidget given the item instance
     *
     * @param item
     * @param point
     * @return
     */
    @Override
    public IMapMenuWidget openMenuOnItem(Object item, PointF point) {
        _clearAllMenus();
        IMapMenuWidget widget = factoryCreateMapMenuWidget(item);
        if (widget != null) {
            layoutAsMenu(widget);
            if (null != point)
                widget.setPoint(point.x, point.y);
            _mapItem = item;
            _addPressExpanders(widget);
            addChildWidget(widget);
        }
        return widget;
    }


    IMapMenuWidget _openMenuOnMap(PointF p) {
        IMapMenuWidget widget = null;
        if (!_clearAllMenus()) {
            widget = _openMapMenu(p);
            if (widget != null) {
                _addPressExpanders(widget);
                addChildWidget(widget);
            }
        }
        return widget;
    }

    private float normalizeAngle(final float raw) {
        float normalized = raw;
        while (180f < normalized)
            normalized -= 360f;
        while (-180f >= normalized)
            normalized += 360f;
        return normalized;
    }

    private void orientLayout(final IMapMenuWidget menuWidget,
                              final float totalWeight) {
        float accumulatedWeight = 0f;
        final float startAngle = menuWidget.getStartAngle();
        final float coveredAngle = menuWidget.getCoveredAngle();
        for (int index = 0, max = menuWidget
                .getChildWidgetCount(); max > index; ++index) {
            final IMapWidget widget = menuWidget.getChildWidgetAt(index);
            // should not throw any class cast exceptions since we screened earlier
            final IMapMenuButtonWidget button = (IMapMenuButtonWidget) widget;
            final float halfWeight = button.getLayoutWeight() / 2f;
            final float span = coveredAngle * button.getLayoutWeight()
                    / totalWeight;
            button.setButtonSize(span, button.getButtonWidth());
            if (0 == index) {
                button.setOrientation(normalizeAngle(startAngle),
                        button.getOrientationRadius());
                accumulatedWeight = halfWeight;
            } else {
                final float currentOffset = coveredAngle
                        * (accumulatedWeight + halfWeight) / totalWeight;
                final float totalOffset = menuWidget.isClockwiseWinding()
                        ? startAngle - currentOffset
                        : startAngle + currentOffset;
                button.setOrientation(normalizeAngle(totalOffset),
                        button.getOrientationRadius());
                accumulatedWeight += button.getLayoutWeight();
            }
        }
    }

    private float cullAndWeighButtons(final IMapMenuWidget menuWidget,
                                      final boolean cullDisabled) {
        float totalWeight = 0f;
        for (int index = 0, max = menuWidget
                .getChildWidgetCount(); max > index; ++index) {
            IMapWidget widget = menuWidget.getChildWidgetAt(index);
            if (!(widget instanceof IMapMenuButtonWidget) ||
                    (cullDisabled
                            && (((IMapMenuButtonWidget) widget).isDisabled()))) {
                menuWidget.removeChildWidgetAt(index);
                max -= 1;
                // index will be incremented back to original value, and we'll try again
                index -= 1;
            } else {
                totalWeight += ((IMapMenuButtonWidget) widget).getLayoutWeight();
            }
        }
        return totalWeight;
    }

    protected void layoutAsMenu(IMapMenuWidget menuWidget) {
        final float totalWeight = cullAndWeighButtons(menuWidget, false);
        orientLayout(menuWidget, totalWeight);
    }

    protected void layoutAsSubmenu(IMapMenuWidget menuWidget,
                                   IMapMenuButtonWidget parentButton) {

        // get all buttons possible prior to culling, and their unit span
        float submenuSpan = 0f; // an unreasonable default, but defined.
        if (menuWidget.getExplicitSizing()) {
            submenuSpan = menuWidget.getCoveredAngle()
                    / menuWidget.getChildWidgetCount();
        } else {
            submenuSpan = parentButton.getButtonSpan();

            // parentButton's orientation radius appears to be its inner dimension
            // Size the submenu button for nominally equivalent arc length as its parent
            final float parentRadius = parentButton.getOrientationRadius();
            menuWidget.setInnerRadius(parentRadius);

            final float parentWidth = parentButton.getButtonWidth();
            menuWidget.setButtonWidth(parentWidth);
        }

        // cull before we figure our dimensioning
        final float totalWeight = cullAndWeighButtons(menuWidget, true);

        float coveredAngle = submenuSpan * menuWidget.getChildWidgetCount();
        if (360f < coveredAngle) {
            submenuSpan = 360f / menuWidget.getChildWidgetCount();
            coveredAngle = 360f;
        }
        menuWidget.setCoveredAngle(coveredAngle);

        final float parentOrient = parentButton.getOrientationAngle();
        final float startingAngle = normalizeAngle(
                menuWidget.isClockwiseWinding()
                        ? parentOrient + (coveredAngle - submenuSpan) / 2f
                        : parentOrient - (coveredAngle - submenuSpan) / 2f);
        menuWidget.setStartAngle(startingAngle);

        orientLayout(menuWidget, totalWeight);
    }

    private MapMenuButtonWidget getSubmenuButton(
            final IParentWidget widget,
            final IParentWidget root) {
        for (IMapWidget childWidget : root.getChildren()) {
            if (childWidget instanceof IMapMenuWidget) {
                return getSubmenuButton(widget,
                        (AbstractParentWidget) childWidget);
            } else if (childWidget instanceof MapMenuButtonWidget) {
                final MapMenuButtonWidget buttonWidget = (MapMenuButtonWidget) childWidget;
                final IMapMenuWidget submenuWidget = buttonWidget
                        .getSubmenu();
                if (null == submenuWidget)
                    continue;
                if (widget == submenuWidget)
                    return buttonWidget;
                return getSubmenuButton(widget, submenuWidget);
            }
        }
        return null;
    }

    private void onWidgetCardinality(IParentWidget widget) {
        if (widget instanceof IMapMenuWidget) {
            final IMapMenuWidget menuWidget = (IMapMenuWidget) widget;
            MapMenuButtonWidget buttonWidget = getSubmenuButton(widget, this);
            if (null == buttonWidget)
                layoutAsMenu(menuWidget);
            else
                layoutAsSubmenu(menuWidget, buttonWidget);
        } else {
            Log.w(TAG, "Logic error; non menu widget type changed");
        }
    }

    @Override
    public void onWidgetAdded(IParentWidget parent, int index,
                              IMapWidget child) {
        onWidgetCardinality(parent);
    }

    private void removeMenuWidgetFromLayout(IMapMenuWidget menuWidget) {
        for (IMapWidget child : menuWidget.getChildren()) {
            if (child instanceof MapMenuButtonWidget) {
                IMapMenuButtonWidget buttonWidget = (MapMenuButtonWidget) child;
                IMapMenuWidget submenuWidget = buttonWidget.getSubmenu();
                if (null != submenuWidget)
                    removeMenuWidgetFromLayout(submenuWidget);
            }
        }
        removeChildWidget(menuWidget);
    }

    @Override
    public void onWidgetRemoved(IParentWidget parent, int index,
                                IMapWidget child) {
        // need to see if the child had any showing subwidgets
        // showing means that the child descendants are children of this layout widget
        if (child instanceof IMapMenuWidget) {
            removeMenuWidgetFromLayout((IMapMenuWidget) child);
        } else if (child instanceof MapMenuButtonWidget) {
            MapMenuButtonWidget buttonWidget = (MapMenuButtonWidget) child;
            IMapMenuWidget submenuWidget = buttonWidget.getSubmenu();
            if (null != submenuWidget)
                removeMenuWidgetFromLayout(submenuWidget);
        }

        onWidgetCardinality(parent);
    }
    @Override
    public IMapMenuWidget openMenuOnItem(List<IMapMenuButtonWidget> buttonWidgets, Object item, PointF point) {
        _clearAllMenus();
        IMapMenuWidget widget = createMapMenuWidget();
        if (widget != null) {
            for(IMapMenuButtonWidget childWidget : buttonWidgets) {
                widget.addChildWidget(childWidget);
            }
            layoutAsMenu(widget);
            if (null != point)
                widget.setPoint(point.x, point.y);
            _mapItem = item;
            _addPressExpanders(widget);
            addChildWidget(widget);
        }
        return widget;
    }

    @Override
    public void setMenuWidget(IMapMenuWidget menuWidget, gov.tak.platform.graphics.PointF point) {
        _clearAllMenus();
        if (menuWidget != null) {
            layoutAsMenu(menuWidget);
            if (null != point)
                menuWidget.setPoint(point.x, point.y);
            _addPressExpanders(menuWidget);
            addChildWidget(menuWidget);
        }
    }

}
