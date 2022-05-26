
package com.atakmap.android.menu;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.graphics.PointF;
import android.preference.PreferenceManager;
import android.view.MotionEvent;

import com.atakmap.android.action.MapAction;
import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.maps.MapData;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.assets.MapAssets;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.android.widgets.AbstractButtonWidget;
import com.atakmap.android.widgets.AbstractParentWidget;
import com.atakmap.android.widgets.LayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.WidgetIcon;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.AtakMapController;
import com.atakmap.map.AtakMapView;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import gov.tak.api.widgets.IMapMenuButtonWidget;
import gov.tak.api.widgets.IMapMenuWidget;
import gov.tak.api.widgets.IMapWidget;

public class MenuLayoutBase extends LayoutWidget implements
        AtakMapController.OnFocusPointChangedListener,
        AbstractParentWidget.OnWidgetListChangedListener {

    public static final String TAG = "MenuLayoutBase";
    private static final int DELAY_TIME = 40;
    private static final int RING_PADDING = 5;

    interface MapActionDispatcher {
        void dispatchAction(MapAction mapAction, MapItem mapItem);
    }

    private final AtakMapView _atakMapView;
    private final Context _context;
    protected MapItem _mapItem;
    private final List<MapMenuFactory> _factories;
    private final SharedPreferences _prefs;
    protected MapActionDispatcher _mapActionDispatcher;

    MenuLayoutBase(final AtakMapView atakMapView, final MapData mapData,
            final MapAssets assets, final MenuMapAdapter adapter,
            final MapActionDispatcher mapActionDispatcher) {
        _atakMapView = atakMapView;
        _context = atakMapView.getContext();
        _mapActionDispatcher = mapActionDispatcher;

        MenuResourceFactory menuResourceFactory = new MenuResourceFactory(
                atakMapView, mapData, assets, adapter);

        _factories = Collections.synchronizedList(new LinkedList<>());
        _factories.add(menuResourceFactory);

        _prefs = PreferenceManager
                .getDefaultSharedPreferences(_atakMapView
                        .getContext());
    }

    @Override
    public void onClick(MotionEvent event) {
        super.onClick(event);
        _clearAllMenus();
    }

    /**
     * Provide the current <a href="#{@link}">{@link MapItem}</a>
     *
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
        while (getChildWidgetCount() > 0) {
            removeChildWidgetAt(getChildWidgetCount() - 1);
            r = true;
        }

        return r;
    }

    private boolean _clearUntilMenu(IMapMenuWidget menu) {
        boolean r = false;
        while (getChildWidgetCount() > 0
                && getChildWidgetAt(getChildWidgetCount() - 1) != menu) {
            removeChildWidgetAt(getChildWidgetCount() - 1);
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
        for (int i = 0; i < getChildWidgetCount(); i++) {
            IMapWidget widget = getChildWidgetAt(i);
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
        for (int i = 0; i < menuWidget.getChildWidgetCount(); ++i) {
            final IMapMenuButtonWidget button = (IMapMenuButtonWidget) menuWidget
                    .getChildWidgetAt(i);

            button.addOnPressListener(new IMapWidget.OnPressListener() {
                @Override
                public void onMapWidgetPress(IMapWidget widget,
                        gov.tak.platform.ui.MotionEvent event) {

                }
            });

            button.addOnClickListener(new IMapWidget.OnClickListener() {
                @Override
                public void onMapWidgetClick(IMapWidget widget,
                        gov.tak.platform.ui.MotionEvent event) {
                    if (null != button.getOnButtonClickHandler() && button
                            .getOnButtonClickHandler().isSupported(_mapItem)) {
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
            });

            button.addOnLongPressListener(new IMapWidget.OnLongPressListener() {
                @Override
                public void onMapWidgetLongPress(IMapWidget widget) {
                    IMapMenuWidget submenuWidget = button.getSubmenu();
                    if (!button.isDisabled() && (null != submenuWidget))
                        _expandPressSubmenu(menuWidget, button);
                }
            });
        }
    }

    /**
     * Controls what actions are to be taken when a MapMenuButtonWidget is pressed.
     */
    protected void _addPressExpanders(final IMapMenuWidget submenuWidget,
            final IMapMenuButtonWidget parentButton) {
        for (int i = 0; i < submenuWidget.getChildWidgetCount(); ++i) {
            final MapMenuButtonWidget childButton = (MapMenuButtonWidget) submenuWidget
                    .getChildWidgetAt(i);
            childButton.delayShow((i + 1) * DELAY_TIME);
            childButton.addOnPressListener(new IMapWidget.OnPressListener() {
                @Override
                public void onMapWidgetPress(IMapWidget widget,
                        gov.tak.platform.ui.MotionEvent event) {

                }
            });

            final String buttonIndex = Integer.toString(i); // must be final for use in handler below ...
            childButton.addOnClickListener(new IMapWidget.OnClickListener() {
                @Override
                public void onMapWidgetClick(IMapWidget widget,
                        gov.tak.platform.ui.MotionEvent event) {
                    if (_mapItem == null)
                        return;

                    if (childButton.isBackButton()) {
                        openMenuOnItem(_mapItem);
                        return;
                    }
                    MapAction clickAction = childButton.getOnClickAction();
                    List<String> prefKeys = parentButton.getPrefKeys();
                    List<String> prefValues = childButton.getPrefValues();
                    boolean prefsValid = !prefKeys.isEmpty()
                            && !prefValues.isEmpty()
                            && prefKeys.size() == prefValues.size();
                    if (clickAction != null || prefsValid) {
                        if (prefsValid) {

                            // Apply preferences
                            SharedPreferences.Editor editor = _prefs.edit();
                            for (int i1 = 0; i1 < prefKeys.size(); i1++) {
                                String k = prefKeys.get(i1);
                                String v = prefValues.get(i1);
                                if (k != null && v != null)
                                    editor.putString(k, v);
                            }
                            editor.apply();
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
        }
    }

    protected float getChildMenuRadius(final IMapMenuWidget submenuWidget,
            final IMapMenuButtonWidget parentButton) {
        return submenuWidget.getInnerRadius() +
                +parentButton.getButtonWidth()
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
                            && (((IMapMenuButtonWidget) widget)
                                    .isDisabled()))) {
                menuWidget.removeChildWidgetAt(index);
                max -= 1;
                // index will be incremented back to original value, and we'll try again
                index -= 1;
            } else {
                totalWeight += ((IMapMenuButtonWidget) widget)
                        .getLayoutWeight();
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

        _clearUntilMenu(menuWidget);

        //create back button
        MapMenuButtonWidget backButton = new MapMenuButtonWidget(_context);
        backButton.setState(AbstractButtonWidget.STATE_SELECTED);
        WidgetIcon backIcon = null;
        ConfigEnvironment.Builder b = new ConfigEnvironment.Builder();
        ConfigEnvironment ce = b.build();
        try {
            backIcon = WidgetIcon.resolveWidgetIcon(ce,
                    "icons/radial_back.png");
        } catch (Exception e) {
            Log.e(TAG,
                    "error resolving radial_back icon, should not happen in production code",
                    e);
            return;
        }
        // Use the current button at index 0 to set back button parameters
        MapMenuButtonWidget paramsButton = (MapMenuButtonWidget) menuWidget
                .getChildWidgetAt(0);
        WidgetIcon currentIcon = paramsButton.getIcon();
        WidgetIcon newIcon = new WidgetIcon(backIcon.getIconRef(0),
                new Point(currentIcon.getAnchorX(),
                        currentIcon.getAnchorY()),
                currentIcon.getIconWidth(),
                currentIcon.getIconHeight());

        backButton.setIcon(newIcon);
        backButton.setIsBackButton(true);
        backButton.setLayoutWeight(paramsButton.getLayoutWeight());
        backButton.setButtonSize(paramsButton.getButtonSpan(),
                parentButton.getButtonWidth());
        backButton.setOrientation(paramsButton.getOrientationAngle(),
                parentButton.getOrientationRadius());

        backButton.setBackground(parentButton.getWidgetBackground());
        backButton.delayShow(DELAY_TIME);
        menuWidget.addChildWidgetAt(0, backButton);

        // cull before we figure our dimensioning
        final float totalWeight = cullAndWeighButtons(menuWidget, true);

        final float parentOrient = parentButton.getOrientationAngle();
        final float parentWidth = parentButton.getButtonWidth();
        // parentButton's orientation radius appears to be its inner dimension
        // Size the submenu button for nominally equivalent arc length as its parent
        final float parentRadius = parentButton.getOrientationRadius();
        float submenuSpan = parentButton.getButtonSpan();
        float coveredAngle = submenuSpan * menuWidget.getChildWidgetCount();
        if (360f < coveredAngle) {
            submenuSpan = 360f / menuWidget.getChildWidgetCount();
            coveredAngle = 360f;
        }
        final float startingAngle = normalizeAngle(parentOrient);

        menuWidget.setCoveredAngle(coveredAngle);
        menuWidget.setStartAngle(startingAngle);
        menuWidget.setInnerRadius(parentRadius);
        menuWidget.setButtonWidth(parentWidth);
        for (IMapWidget w : menuWidget.getChildren()) {
            if (w instanceof MapMenuButtonWidget) {
                MapMenuButtonWidget btn = (MapMenuButtonWidget) w;
                btn.setButtonSize(btn.getButtonSpan(), parentWidth);
            }
        }
        orientLayout(menuWidget, totalWeight);
        layoutAsMenu(menuWidget);
    }

    private MapMenuButtonWidget getSubmenuButton(
            final AbstractParentWidget widget,
            final AbstractParentWidget root) {
        for (IMapWidget childWidget : root.getChildren()) {
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
        for (IMapWidget child : menuWidget.getChildren()) {
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
