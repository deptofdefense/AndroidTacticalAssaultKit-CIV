
package com.atakmap.android.helloworld.menu;

import android.content.Context;
import android.graphics.Color;

import com.atakmap.android.action.MapAction;
import com.atakmap.android.maps.MapDataRef;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.assets.MapAssets;
import com.atakmap.android.menu.MapMenuButtonWidget;
import com.atakmap.android.menu.MapMenuFactory;
import com.atakmap.android.menu.MapMenuWidget;
import com.atakmap.android.menu.MenuMapAdapter;
import com.atakmap.android.menu.MenuResourceFactory;
import com.atakmap.android.menu.PluginMenuParser;
import com.atakmap.android.widgets.AbstractButtonWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.WidgetBackground;
import com.atakmap.android.widgets.WidgetIcon;

import java.io.IOException;

/*
Nonsensical logic to demonstrate the creation of MapMenuWidgets,
MapMenuButtonWidgets, MapActions, and submenus using a custom
MapMenuFactory along with the MenuResourceFactory.
 */
public class MenuFactory implements MapMenuFactory {

    final private Context pluginContext;
    final private Context appContext;
    final private MenuResourceFactory resourceFactory;

    final static private String[] iconFiles = {
            "icons/blast_rings.png",
            "icons/details.png",
            "icons/redlight.png",
            "icons/redtriangle.png"
    };

    /**
     * Demonstrates a MapMenuFactory that mixes in the default
     * implementation with custom handling
     */
    public MenuFactory(final Context context) {
        pluginContext = context;
        final MapView mapView = MapView.getMapView();
        appContext = mapView.getContext();
        // using application, not plugin, assets, hence the application context
        final MapAssets mapAssets = new MapAssets(appContext);
        final MenuMapAdapter adapter = new MenuMapAdapter();
        try {
            adapter.loadMenuFilters(mapAssets, "filters/menu_filters.xml");
        } catch (IOException e) {
            // do something better here
        }
        resourceFactory = new MenuResourceFactory(mapView, mapView.getMapData(),
                mapAssets, adapter);
    }

    @Override
    public MapMenuWidget create(MapItem mapItem) {
        MapMenuWidget menuWidget = null;
        final String type = mapItem.getType();
        if (type.contains("a-n")) {
            menuWidget = createSimpleDark();
        } else if (type.contains("a-u")) {
            menuWidget = createHighlyCustom();
        } else if (type.contains("a-h")) {
            menuWidget = addedSubmenuButton(mapItem);
        } // else fall through and return null
        return menuWidget;
    }

    /**
     Builds a custom WidgetBackground.
     */
    private WidgetBackground createDarkWidget() {
        WidgetBackground.Builder builder = new WidgetBackground.Builder();
        return builder
                .setColor(0, Color.parseColor("#ff383838"))
                .setColor(AbstractButtonWidget.STATE_DISABLED,
                        Color.parseColor("#7f7f7f7f"))
                .setColor(AbstractButtonWidget.STATE_DISABLED
                        | AbstractButtonWidget.STATE_PRESSED,
                        Color.parseColor("#7f7f7f7f"))
                .setColor(AbstractButtonWidget.STATE_PRESSED,
                        Color.parseColor("#ff7fff7f"))
                .setColor(AbstractButtonWidget.STATE_SELECTED,
                        Color.parseColor("#ff7fffff"))
                .setColor(AbstractButtonWidget.STATE_SELECTED |
                        AbstractButtonWidget.STATE_PRESSED,
                        Color.parseColor("#ff7fff7f"))
                .build();
    }

    /**
    Arbitrary addition of a submenu button on an existing menu.
    Demonstrates ability to extend current menu / submenus dynamically.
     */
    private MapMenuWidget addedSubmenuButton(final MapItem mapItem) {
        final MapMenuWidget menuWidget = resourceFactory.create(mapItem);
        if (null != menuWidget) {
            // navigate to find button with submenu
            MapMenuWidget submenuWidget = null;
            for (MapWidget child : menuWidget.getChildWidgets()) {
                if (child instanceof MapMenuButtonWidget) {
                    MapMenuButtonWidget buttonWidget = (MapMenuButtonWidget) child;
                    submenuWidget = buttonWidget.getSubmenuWidget();
                    // other logic here to determine what submenu has been
                    // found. For our example, we're looking for the cff
                    // submenu which is associated with the button using the
                    // "icon/cas.png" image for its WidgetIcon
                    if (null != submenuWidget) {
                        final WidgetIcon widgetIcon = buttonWidget.getIcon();
                        final MapDataRef mapDataRef = widgetIcon.getIconRef(0);
                        if (mapDataRef.toUri()
                                .contentEquals("asset://icons/cas.png"))
                            break;
                    }
                }
            }
            if (null != submenuWidget) {
                // add a button, arbitrary choice of plugin
                // scoped remove icon image with prompt action
                final MapMenuButtonWidget buttonWidget = createButton(
                        "details.png");
                // size our button to look like the others ...
                float span = 0;
                float width = 0;
                for (MapWidget child : submenuWidget.getChildWidgets()) {
                    if (child instanceof MapMenuButtonWidget) {
                        final MapMenuButtonWidget submenuButton = (MapMenuButtonWidget) child;
                        span += submenuButton.getButtonSpan();
                        width += submenuButton.getButtonWidth();
                    }
                }

                final int count = submenuWidget.getChildCount();
                span /= count;
                width /= count;
                // span is used as weight for current XML defined menus
                buttonWidget.setLayoutWeight(span);
                // have to add width to be consistent
                buttonWidget.setButtonSize(span, width);

                // arbitrary location in the middle, change to suit ...
                final int index = submenuWidget.getChildCount() / 2;
                submenuWidget.addWidgetAt(index, buttonWidget);
            }
        }
        return menuWidget;
    }

    /**
    Arbitrary composition of MapMenuWidget for demonstration.
    Demonstrates start and coverage angles along with submenus
     */
    private MapMenuWidget createHighlyCustom() {
        final MapMenuWidget menuWidget = new MapMenuWidget();
        // arbitrary composition
        final MapMenuWidget submenuWidget = new MapMenuWidget();
        for (int index = 2, max = iconFiles.length; max > index; ++index) {
            final MapMenuButtonWidget buttonWidget = createButton(
                    iconFiles[index]);
            submenuWidget.addWidget(buttonWidget);
            // weight the first button span 1.5 times the other buttons
            if (2 == index) {
                buttonWidget.setLayoutWeight(1.5f);
            }
        }
        for (int index = 0, max = 3; max > index; ++index) {
            final MapMenuButtonWidget buttonWidget = 1 == index
                    ? createButton(iconFiles[index], submenuWidget)
                    : createButton(iconFiles[index]);
            menuWidget.addWidget(buttonWidget);
        }
        // parenting
        submenuWidget.setParent(menuWidget);
        // locate first button -55 degrees
        menuWidget.setStartAngle(-55f);
        // only go 330 degrees
        menuWidget.setCoveredAngle(330f);

        return menuWidget;
    }

    /**
    Arbitrary composition of MapMenuWidget for demonstration.
    Demonstrates a custom background
     */
    private MapMenuWidget createSimpleDark() {
        MapMenuWidget menuWidget = new MapMenuWidget();
        for (String icon : iconFiles) {
            final MapMenuButtonWidget buttonWidget = createButton(icon);
            buttonWidget.setBackground(createDarkWidget());
            menuWidget.addWidget(buttonWidget);
        }

        return menuWidget;
    }

    /**
     Create a WidgetIcon from an image file path
     */
    private WidgetIcon createWidgetIcon(String path) {
        String asset = PluginMenuParser.getItem(pluginContext, path);
        if (0 == asset.length()) {
            asset = "asset:///" + path;
        }
        final MapDataRef mapDataRef = MapDataRef.parseUri(asset);
        final WidgetIcon.Builder builder = new WidgetIcon.Builder();
        return builder
                .setImageRef(0, mapDataRef)
                .setAnchor(16, 16)
                .setSize(32, 32)
                .build();
    }

    /**
    Uses default factory to create action from asset
     */
    private MapMenuButtonWidget createButton(String iconFile,
            MapMenuWidget submenu) {
        MapMenuButtonWidget buttonWidget = new MapMenuButtonWidget(appContext);
        final MapAction action = resourceFactory
                .resolveAction("actions/cancel.xml");
        buttonWidget.setOnClickAction(action);
        buttonWidget.setIcon(createWidgetIcon(iconFile));
        buttonWidget.setSubmenuWidget(submenu);
        return buttonWidget;
    }

    /**
    Same as prior button method, but uses a MapAction created from scratch
     */
    private MapMenuButtonWidget createButton(String iconFile) {
        MapMenuButtonWidget buttonWidget = new MapMenuButtonWidget(appContext);
        buttonWidget.setOnClickAction(new CancelAction());
        buttonWidget.setIcon(createWidgetIcon(iconFile));
        return buttonWidget;
    }
}
