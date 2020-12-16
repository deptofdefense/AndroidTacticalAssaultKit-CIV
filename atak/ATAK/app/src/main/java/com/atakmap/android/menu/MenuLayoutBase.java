
package com.atakmap.android.menu;

import android.content.SharedPreferences;
import android.graphics.PointF;
import android.preference.PreferenceManager;
import android.view.MotionEvent;

import com.atakmap.android.action.MapAction;
import com.atakmap.android.maps.MapData;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.assets.MapAssets;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.android.widgets.AbstractParentWidget;
import com.atakmap.android.widgets.LayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.AtakMapController;
import com.atakmap.map.AtakMapView;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MenuLayoutBase extends LayoutWidget implements
        AtakMapController.OnFocusPointChangedListener,
        AbstractParentWidget.OnWidgetListChangedListener {

    public static final String TAG = "MenuLayoutBase";
    private static final int RING_PADDING = 5;

    private final AtakMapView _atakMapView;
    protected MapItem _mapItem;
    private final List<MapMenuFactory> _factories;

    private final SharedPreferences prefs;

    interface MapActionDispatcher {
        void dispatchAction(MapAction mapAction, MapItem mapItem);
    }

    protected MapActionDispatcher _mapActionDispatcher;

    @Override
    public void onClick(MotionEvent event) {
        super.onClick(event);
        _clearAllMenus();
    }

    MenuLayoutBase(final AtakMapView atakMapView, final MapData mapData,
            final MapAssets assets, final MenuMapAdapter adapter,
            final MapActionDispatcher mapActionDispatcher) {
        _atakMapView = atakMapView;
        _mapActionDispatcher = mapActionDispatcher;

        MenuResourceFactory menuResourceFactory = new MenuResourceFactory(
                atakMapView, mapData, assets, adapter);

        _factories = Collections
                .synchronizedList(new LinkedList<MapMenuFactory>());
        _factories.add(menuResourceFactory);

        prefs = PreferenceManager
                .getDefaultSharedPreferences(_atakMapView
                        .getContext());
    }

    /**
     * Provide the current <a href="#{@link}">{@link MapItem}</a>
     * @return current MapItem for the displayed menu
     */
    public MapItem getMapItem() {
        return _mapItem;
    }

    private MapMenuWidget _openMapMenu(PointF p) {
        final MapMenuWidget widget = createMapMenuWidget(null);
        if (null != widget) {
            layoutAsMenu(widget);
            if (null != p) {
                widget.setPoint(p.x, p.y);
            }
        }
        return widget;
    }

    private MapMenuWidget _openSubmenu(MapMenuWidget parent,
            MapMenuButtonWidget buttonWidget) {
        if ((null == parent) || (null == buttonWidget))
            return null;

        MapMenuWidget submenuWidget = buttonWidget.getSubmenuWidget();
        if (null != submenuWidget) {
            submenuWidget.setPoint(parent.getPointX(), parent.getPointY());
            layoutAsSubmenu(submenuWidget, buttonWidget);
        }
        return submenuWidget;
    }

    private PointF pointFromItem(MapItem item) {
        PointF point = null;

        if (item.getMetaString("menu_point", null) != null) {
            try {
                GeoPoint gp = GeoPoint.parseGeoPoint(
                        item.getMetaString("menu_point", null));
                // treat the menu point as one and done communication
                item.removeMetaData("menu_point");
                gp = _atakMapView.getRenderElevationAdjustedPoint(gp);
                point = _atakMapView.forward(gp);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing Geo Point");
            }
        }

        // why on this green earth do we call out RangeAndBearingMapItem
        // separate when we are setting the closest touch location in the
        // arrow tool, leave in just in case menu_point is not set in some
        // bizaro world.   shame shame shame.

        if (null == point) {

            if (item instanceof PointMapItem) {
                GeoPoint geo = ((PointMapItem) item).getPoint();
                geo = _atakMapView.getRenderElevationAdjustedPoint(geo);
                point = _atakMapView.forward(geo);
            } else if (item instanceof RangeAndBearingMapItem) {
                Log.e(TAG,
                        "tripping into legacy code for RangeAndBearing center point, please fix");
                GeoPoint geo = ((RangeAndBearingMapItem) item)
                        .getCenter().get();
                geo = _atakMapView.getRenderElevationAdjustedPoint(geo);
                point = _atakMapView.forward(geo);
            }
        }

        if (null == point) {
            point = new PointF(_atakMapView.getMapController().getFocusX(),
                    _atakMapView.getMapController().getFocusY());
        }
        return point;
    }

    boolean _clearAllMenus() {
        boolean r = false;
        if (_mapItem != null) {
            // Clear out any custom selection states
            _mapItem.removeMetaData("overlay_manager_select");
            _mapItem = null;
        }
        while (getChildCount() > 0) {
            removeWidgetAt(getChildCount() - 1);
            r = true;
        }

        return r;
    }

    private boolean _clearUntilMenu(MapMenuWidget menu) {
        boolean r = false;
        while (getChildCount() > 0 && getChildAt(getChildCount() - 1) != menu) {
            removeWidgetAt(getChildCount() - 1);
            r = true;
        }
        return r;
    }

    public MapMenuWidget openMenuOnItem(MapItem item, PointF point) {
        _clearAllMenus();
        if (null == point)
            point = pointFromItem(item);
        MapMenuWidget widget = createMapMenuWidget(item);
        if (widget != null) {
            layoutAsMenu(widget);
            if (null != point)
                widget.setPoint(point.x, point.y);
            _mapItem = item;
            _addPressExpanders(widget);
            addWidget(widget);
        }
        return widget;
    }

    public MapMenuWidget openMenuOnItem(MapItem item) {
        return openMenuOnItem(item, null);
    }

    @Override
    public void onFocusPointChanged(float x, float y) {
        for (int i = 0; i < getChildCount(); i++) {
            MapWidget widget = getChildAt(i);
            if (widget instanceof MapMenuWidget)
                widget.setPoint(x, y);
        }
    }

    public void clearMenu() {
        _clearAllMenus();
    }

    /**
     * Controls what actions are to be taken when a MapMenuButtonWidget is pressed.
     */
    protected void _addPressExpanders(final MapMenuWidget menuWidget) {
        for (int i = 0; i < menuWidget.getChildCount(); ++i) {
            final MapMenuButtonWidget button = (MapMenuButtonWidget) menuWidget
                    .getChildAt(i);

            button.addOnPressListener(new OnPressListener() {
                @Override
                public void onMapWidgetPress(MapWidget widget,
                        MotionEvent event) {

                }
            });

            button.addOnClickListener(new OnClickListener() {
                @Override
                public void onMapWidgetClick(MapWidget widget,
                        MotionEvent event) {
                    if (null != button.getOnClickAction()) {
                        MapAction action = button.getOnClickAction();
                        _mapActionDispatcher.dispatchAction(action, _mapItem);
                    } else {
                        MapMenuWidget submenuWidget = button.getSubmenuWidget();
                        if (null != submenuWidget) {
                            _expandPressSubmenu(menuWidget, button);
                        } else if (!button.isDisabled()) {
                            _clearAllMenus();
                        }
                    }
                }
            });

            button.addOnLongPressListener(new OnLongPressListener() {
                @Override
                public void onMapWidgetLongPress(MapWidget widget) {
                    MapMenuWidget submenuWidget = button.getSubmenuWidget();
                    if (!button.isDisabled() && (null != submenuWidget))
                        _expandPressSubmenu(menuWidget, button);
                }
            });
        }
    }

    /**
     * Controls what actions are to be taken when a MapMenuButtonWidget is pressed.
     */
    private void _addPressExpanders(final MapMenuWidget submenuWidget,
            final MapMenuButtonWidget parentButton) {
        for (int i = 0; i < submenuWidget.getChildCount(); ++i) {
            final MapMenuButtonWidget childButton = (MapMenuButtonWidget) submenuWidget
                    .getChildAt(i);

            if (parentButton.isDisabled())
                childButton.setDisabled(true);

            final float radius = getChildMenuRadius(submenuWidget,
                    parentButton);
            childButton.setOrientation(childButton.getOrientationAngle(),
                    radius);

            childButton.addOnPressListener(new OnPressListener() {
                @Override
                public void onMapWidgetPress(MapWidget widget,
                        MotionEvent event) {
                }
            });

            final String buttonIndex = Integer.toString(i); // must be final for use in handler below ...
            childButton.addOnClickListener(new OnClickListener() {
                @Override
                public void onMapWidgetClick(MapWidget widget,
                        MotionEvent event) {
                    if (_mapItem == null)
                        return;
                    MapAction clickAction = childButton.getOnClickAction();
                    List<String> prefKeys = parentButton.getPrefKeys();
                    List<String> prefValues = childButton.getPrefValues();
                    boolean prefsValid = !prefKeys.isEmpty()
                            && !prefValues.isEmpty()
                            && prefKeys.size() == prefValues.size();
                    if (clickAction != null || prefsValid) {
                        if (prefsValid) {

                            // Apply preferences
                            for (int i1 = 0; i1 < prefKeys.size(); i1++) {
                                String k = prefKeys.get(i1);
                                String v = prefValues.get(i1);
                                if (k != null && v != null)
                                    prefs.edit().putString(k, v).apply();
                            }
                        }

                        if (clickAction != null) {
                            Map<String, Object> itemmap = _mapItem
                                    .getMetaMap("submenu_map");
                            if (itemmap == null)
                                itemmap = new HashMap<>();
                            itemmap.put(buttonIndex, clickAction);
                            _mapItem.setMetaMap("submenu_map", itemmap);
                            _mapActionDispatcher.dispatchAction(clickAction,
                                    _mapItem);
                        } else {
                            _clearAllMenus();
                        }
                    } else if (!childButton.isDisabled()) {
                        _clearAllMenus();
                    } else {
                        _expandPressSubmenu(submenuWidget, childButton);
                    }
                }
            });

            childButton.addOnLongPressListener(new OnLongPressListener() {
                @Override
                public void onMapWidgetLongPress(MapWidget widget) {
                    _expandPressSubmenu(submenuWidget, childButton);
                }
            });
        }
    }

    protected float getChildMenuRadius(final MapMenuWidget submenuWidget,
            final MapMenuButtonWidget parentButton) {
        return submenuWidget._buttonRadius
                + parentButton.getButtonWidth()
                + RING_PADDING;
    }

    protected void _expandPressSubmenu(MapMenuWidget menuWidget,
            MapMenuButtonWidget parentButton) {
        MapMenuWidget submenuWidget = _openSubmenu(menuWidget, parentButton);
        if (submenuWidget != null) {
            _addPressExpanders(submenuWidget, parentButton);
            _clearUntilMenu(menuWidget);
            addWidget(submenuWidget);
        }
    }

    public MapMenuWidget openMenuOnMap(GeoPoint point) {
        PointF p = _atakMapView.forward(point);
        _mapItem = null;
        return _openMenuOnMap(p);
    }

    MapMenuWidget _openMenuOnMap(PointF p) {
        MapMenuWidget widget = null;
        if (!_clearAllMenus()) {
            widget = _openMapMenu(p);
            if (widget != null) {
                _addPressExpanders(widget);
                addWidget(widget);
            }
        }
        return widget;
    }

    boolean isMapItem(MapItem mapItem) {
        return (null != _mapItem) && (mapItem == _mapItem);
    }

    private float normalizeAngle(final float raw) {
        float normalized = raw;
        while (180f < normalized)
            normalized -= 360f;
        while (-180f >= normalized)
            normalized += 360f;
        return normalized;
    }

    private void orientLayout(final MapMenuWidget menuWidget,
            final float totalWeight) {
        float accumulatedWeight = 0f;
        final float startAngle = menuWidget.getStartAngle();
        final float coveredAngle = menuWidget.getCoveredAngle();
        for (int index = 0, max = menuWidget
                .getChildCount(); max > index; ++index) {
            final MapWidget widget = menuWidget.getChildAt(index);
            // should not throw any class cast exceptions since we screened earlier
            final MapMenuButtonWidget button = (MapMenuButtonWidget) widget;
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

    private float cullAndWeighButtons(final MapMenuWidget menuWidget,
            final boolean cullDisabled) {
        float totalWeight = 0f;
        for (int index = 0, max = menuWidget
                .getChildCount(); max > index; ++index) {
            MapWidget widget = menuWidget.getChildAt(index);
            if (!(widget instanceof MapMenuButtonWidget) ||
                    (cullDisabled
                            && (((MapMenuButtonWidget) widget).isDisabled()))) {
                menuWidget.removeWidgetAt(index);
                max -= 1;
                // index will be incremented back to original value, and we'll try again
                index -= 1;
            } else {
                totalWeight += ((MapMenuButtonWidget) widget).getLayoutWeight();
            }
        }
        return totalWeight;
    }

    protected void layoutAsMenu(MapMenuWidget menuWidget) {
        final float totalWeight = cullAndWeighButtons(menuWidget, false);
        orientLayout(menuWidget, totalWeight);
    }

    protected void layoutAsSubmenu(MapMenuWidget menuWidget,
            MapMenuButtonWidget parentButton) {

        // cull before we figure our dimensioning
        final float totalWeight = cullAndWeighButtons(menuWidget, true);
        final float parentOrient = parentButton.getOrientationAngle();
        final float parentWidth = parentButton.getButtonWidth();
        // parentButton's orientation radius appears to be its inner dimension
        // Size the submenu button for nominally equivalent arc length as its parent
        final float parentRadius = parentButton.getOrientationRadius();
        float submenuSpan = parentButton.getButtonSpan();
        float coveredAngle = submenuSpan * menuWidget.getChildCount();
        if (360f < coveredAngle) {
            submenuSpan = 360f / menuWidget.getChildCount();
            coveredAngle = 360f;
        }
        final float startingAngle = normalizeAngle(
                menuWidget.isClockwiseWinding()
                        ? parentOrient + (coveredAngle - submenuSpan) / 2f
                        : parentOrient - (coveredAngle - submenuSpan) / 2f);

        menuWidget.setCoveredAngle(coveredAngle);
        menuWidget.setStartAngle(startingAngle);
        menuWidget.setInnerRadius(parentRadius);
        menuWidget.setButtonWidth(parentWidth);
        orientLayout(menuWidget, totalWeight);
    }

    private MapMenuButtonWidget getSubmenuButton(
            final AbstractParentWidget widget,
            final AbstractParentWidget root) {
        for (MapWidget childWidget : root.getChildWidgets()) {
            if (childWidget instanceof MapMenuWidget) {
                return getSubmenuButton(widget,
                        (AbstractParentWidget) childWidget);
            } else if (childWidget instanceof MapMenuButtonWidget) {
                final MapMenuButtonWidget buttonWidget = (MapMenuButtonWidget) childWidget;
                final MapMenuWidget submenuWidget = buttonWidget
                        .getSubmenuWidget();
                if (null == submenuWidget)
                    continue;
                if (widget == submenuWidget)
                    return buttonWidget;
                return getSubmenuButton(widget, submenuWidget);
            }
        }
        return null;
    }

    private void onWidgetCardinality(AbstractParentWidget widget) {
        if (widget instanceof MapMenuWidget) {
            final MapMenuWidget menuWidget = (MapMenuWidget) widget;
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
    public void onWidgetAdded(AbstractParentWidget parent, int index,
            MapWidget child) {
        onWidgetCardinality(parent);
    }

    private void removeMenuWidgetFromLayout(MapMenuWidget menuWidget) {
        for (MapWidget child : menuWidget.getChildWidgets()) {
            if (child instanceof MapMenuButtonWidget) {
                MapMenuButtonWidget buttonWidget = (MapMenuButtonWidget) child;
                MapMenuWidget submenuWidget = buttonWidget.getSubmenuWidget();
                if (null != submenuWidget)
                    removeMenuWidgetFromLayout(submenuWidget);
            }
        }
        removeWidget(menuWidget);
    }

    @Override
    public void onWidgetRemoved(AbstractParentWidget parent, int index,
            MapWidget child) {
        // need to see if the child had any showing subwidgets
        // showing means that the child descendants are children of this layout widget
        if (child instanceof MapMenuWidget) {
            removeMenuWidgetFromLayout((MapMenuWidget) child);
        } else if (child instanceof MapMenuButtonWidget) {
            MapMenuButtonWidget buttonWidget = (MapMenuButtonWidget) child;
            MapMenuWidget submenuWidget = buttonWidget.getSubmenuWidget();
            if (null != submenuWidget)
                removeMenuWidgetFromLayout(submenuWidget);
        }

        onWidgetCardinality(parent);
    }

    boolean registerMapMenuFactory(MapMenuFactory factory) {
        boolean result = false;
        if (!_factories.contains(factory)) {
            _factories.add(0, factory);
            result = true;
        }
        return result;
    }

    boolean unregisterMapMenuFactory(MapMenuFactory factory) {
        return _factories.remove(factory);
    }

    private MapMenuWidget createMapMenuWidget(MapItem mapItem) {
        MapMenuWidget menuWidget = null;
        for (MapMenuFactory factory : _factories) {
            menuWidget = factory.create(mapItem);
            if (null != menuWidget) {
                // only need to add; remove effectively will happen as part of gc
                menuWidget.addOnWidgetListChangedListener(this);
                menuWidget.setZOrder(getZOrder());
                menuWidget.fadeAlpha(0, 255, 150); // Perform 150ms fade-in
                break;
            }
        }
        return menuWidget;
    }
}
