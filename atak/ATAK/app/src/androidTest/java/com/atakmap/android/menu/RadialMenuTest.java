
package com.atakmap.android.menu;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.PointF;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.test.core.view.MotionEventBuilder;
import androidx.test.platform.app.InstrumentationRegistry;

import com.atakmap.android.action.MapAction;
import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.android.maps.Association;
import com.atakmap.android.maps.MapData;
import com.atakmap.android.maps.MapDataRef;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.SimpleRectangle;
import com.atakmap.android.maps.assets.MapAssets;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.WidgetIcon;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.AtakMapView;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import gov.tak.api.widgets.IMapWidget;

public class RadialMenuTest extends ATAKInstrumentedTest {

    private static class NullFactory implements MapMenuFactory {

        boolean reached = false;

        void assertReached() {
            assertTrue(reached);
            reached = false;
        }

        @Override
        public MapMenuWidget create(MapItem item) {
            reached = true;
            return null;
        }
    }

    private static class TestFactory implements MapMenuFactory {

        final MenuResourceFactory defaultFactory;
        boolean reached = false;

        TestFactory(@NonNull AtakMapView mapView, @NonNull MapData mapData,
                @NonNull MapAssets mapAssets, @NonNull MenuMapAdapter adapter) {
            defaultFactory = new MenuResourceFactory(mapView, mapData,
                    mapAssets, adapter);
        }

        void assertReached() {
            assertTrue(reached);
            reached = false;
        }

        @Override
        public MapMenuWidget create(MapItem item) {
            reached = true;
            return defaultFactory.create(item);
        }
    }

    private static class TestDispatcher
            implements MenuLayoutBase.MapActionDispatcher {

        MapAction referenceAction;
        MapItem referenceItem;
        boolean reached;

        void setReferenceAction(MapAction mapAction) {
            reached = false;
            referenceAction = mapAction;
        }

        void setReferenceItem(MapItem mapItem) {
            reached = false;
            referenceItem = mapItem;
        }

        void assertReached() {
            assertTrue(reached);
            reached = false;
        }

        @Override
        public void dispatchAction(MapAction mapAction, MapItem mapItem) {
            reached = true;
            assertEquals(referenceAction, mapAction);
            assertEquals(referenceItem, mapItem);
        }
    }

    private void walkButtonWidget(MotionEvent event,
            TestDispatcher dispatcher,
            MenuLayoutBase layoutBase,
            MapMenuButtonWidget buttonWidget,
            MapMenuButtonWidget parentButton) {
        // save state, because a click on a null clickAction and null
        // submenu will clear the mapItem
        MapItem mapItem = layoutBase.getMapItem();
        // set up the comparison through the dispatcher
        dispatcher.setReferenceAction(buttonWidget.getOnClickAction());
        buttonWidget.setDisabled(!buttonWidget.isDisabled());
        buttonWidget.onClick(event);
        if (!((null == buttonWidget.getOnClickAction()) ||
                buttonWidget.isDisabled() ||
                ((null != parentButton) && parentButton.isDisabled())))
            dispatcher.assertReached();
        buttonWidget.onLongPress();
        if (null == buttonWidget.getSubmenuWidget()) {
            // test pathological cases; save state
            MapAction mapAction = buttonWidget.getOnClickAction();
            // blow it up ...
            buttonWidget.setOnClickAction(null); // clear click handler
            dispatcher.setReferenceAction(null);
            buttonWidget.onClick(event); // clears the menu including item
            // restore state
            try {
                // reflection needed since there is no public mutator for the _mapItem field
                Field mapItemField = MenuLayoutBase.class
                        .getDeclaredField("_mapItem");
                mapItemField.setAccessible(true);
                mapItemField.set(layoutBase, mapItem);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
                assertNotNull(null); // needed?
            }

            buttonWidget.setOnClickAction(mapAction);
            dispatcher.setReferenceAction(mapAction);
        } else {
            // recurse into submenu
            walkMenuWidget(dispatcher, layoutBase,
                    buttonWidget.getSubmenuWidget(), buttonWidget);
        }
    }

    private void walkMenuWidget(TestDispatcher dispatcher,
            MenuLayoutBase layoutBase,
            MapMenuWidget menuWidget,
            MapMenuButtonWidget parentButton) {
        final MotionEventBuilder eventBuilder = MotionEventBuilder.newBuilder();
        final MotionEvent event = eventBuilder.build();
        menuWidget.onLongPress();
        for (int index = 0, max = menuWidget
                .getChildCount(); max > index; ++index) {
            final MapMenuButtonWidget buttonWidget = (MapMenuButtonWidget) menuWidget
                    .getChildAt(index);
            walkButtonWidget(event, dispatcher, layoutBase, buttonWidget,
                    parentButton); // toggles disabled state from default
            walkButtonWidget(event, dispatcher, layoutBase, buttonWidget,
                    parentButton); // toggles disabled state back to default
        }
    }

    private void walkMenuWidget(TestDispatcher dispatcher,
            MenuLayoutBase layoutBase,
            MapMenuWidget menuWidget) {
        walkMenuWidget(dispatcher, layoutBase, menuWidget, null);
    }

    private MapMenuButtonWidget findButtonWidget(final MapMenuWidget menuWidget,
            final String iconUri) {
        for (MapWidget child : menuWidget.getChildWidgets()) {
            if (child instanceof MapMenuButtonWidget) {
                MapMenuButtonWidget buttonWidget = (MapMenuButtonWidget) child;
                final WidgetIcon widgetIcon = buttonWidget.getIcon();
                final MapDataRef mapDataRef = widgetIcon.getIconRef(0);
                if ((null != mapDataRef) && iconUri.equals(mapDataRef.toUri()))
                    return buttonWidget;
            }
        }
        return null;
    }

    @Test
    public void testMenuLayoutBase() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(new Runnable() {
                    public void run() {
                        try {
                            Context appContext = InstrumentationRegistry
                                    .getInstrumentation().getTargetContext();
                            AtakMapView atakMapView = new AtakMapView(
                                    appContext, null);

                            MapData mapData = new MapData();
                            MapAssets mapAssets = new MapAssets(appContext);
                            MenuMapAdapter adapter = new MenuMapAdapter();
                            adapter.loadMenuFilters(mapAssets,
                                    "filters/menu_filters.xml");

                            // registering that fires are supported to keep submenu active in hostile marker below
                            MenuCapabilities
                                    .registerSupported("capability.fires");

                            // simple relay factory to test register / unregister functions
                            TestFactory testFactory = new TestFactory(
                                    atakMapView, mapData, mapAssets, adapter);
                            NullFactory nullFactory = new NullFactory();

                            List<MapItem> mapItems = new LinkedList<MapItem>();

                            // Ft. Belvoir'ish geoPoint
                            final GeoPoint geoPoint = new GeoPoint(38.7188716,
                                    -77.1542684);

                            final Marker bullseye = new Marker(
                                    UUID.randomUUID().toString());
                            bullseye.setType("u-r-b-bullseye"); // drives MapMenuItem that will contain submenus
                            bullseye.setTitle("bullseye");
                            mapItems.add(bullseye);

                            final Marker hostile = new Marker(
                                    UUID.randomUUID().toString());
                            hostile.setTitle("hostile");
                            hostile.setType("a-h");
                            mapItems.add(hostile);

                            final SimpleRectangle rectangle = new SimpleRectangle(
                                    UUID.randomUUID().toString());
                            GeoPointMetaData geoPointMetaData = new GeoPointMetaData();
                            geoPointMetaData.set(geoPoint);
                            rectangle.setCenter(geoPointMetaData); // calls setMetaString with "menu_point"
                            rectangle.setTitle("rectangle");
                            rectangle.setType("rectangle"); // not part of filters, but will register handler
                            mapItems.add(rectangle);

                            // non point item and doesn't add "menu_point" metadata
                            final Association association = new Association(
                                    bullseye, hostile,
                                    UUID.randomUUID().toString());
                            association.setTitle("association");
                            mapItems.add(association);

                            final TestDispatcher dispatcher = new TestDispatcher();

                            final MenuLayoutBase layoutBase = new MenuLayoutBase(
                                    atakMapView, mapData, mapAssets, adapter,
                                    dispatcher);
                            assertNotNull(layoutBase);

                            assertTrue(layoutBase
                                    .registerMapMenuFactory(testFactory));
                            assertFalse(layoutBase
                                    .registerMapMenuFactory(testFactory)); // and again; should be idempotent
                            // null factory should take precedence
                            assertTrue(layoutBase
                                    .registerMapMenuFactory(nullFactory));

                            MotionEventBuilder eventBuilder = MotionEventBuilder
                                    .newBuilder();
                            MotionEvent event = eventBuilder.build();
                            layoutBase.onClick(event);

                            MapMenuWidget menuWidget = null;

                            // open on map without a menu to clear
                            menuWidget = layoutBase.openMenuOnMap(geoPoint);
                            assertNotNull(menuWidget);
                            assertFalse(layoutBase.isMapItem(null));
                            dispatcher.setReferenceItem(null);
                            walkMenuWidget(dispatcher, layoutBase, menuWidget);

                            nullFactory.assertReached();
                            testFactory.assertReached();

                            // open on map with a menu to clear
                            // open arbitrary item but don't click so that menu has not been cleared ...
                            menuWidget = layoutBase.openMenuOnItem(hostile);
                            assertNotNull(menuWidget);
                            menuWidget = layoutBase.openMenuOnMap(geoPoint);
                            assertNull(menuWidget);

                            // go back to embedded default factory
                            assertTrue(layoutBase
                                    .unregisterMapMenuFactory(testFactory));
                            assertFalse(layoutBase
                                    .unregisterMapMenuFactory(testFactory));
                            assertTrue(layoutBase
                                    .unregisterMapMenuFactory(nullFactory));

                            // open on items
                            for (MapItem mapItem : mapItems) {
                                menuWidget = layoutBase.openMenuOnItem(mapItem);
                                assertNotNull(menuWidget);
                                assertTrue(layoutBase.isMapItem(mapItem));
                                assertFalse(layoutBase.isMapItem(null));
                                assertEquals(mapItem, layoutBase.getMapItem());
                                dispatcher.setReferenceItem(mapItem);
                                layoutBase.onFocusPointChanged(80, 20);
                                final long time = System.currentTimeMillis();
                                final gov.tak.platform.ui.MotionEvent gEvent = gov.tak.platform.ui.MotionEvent
                                        .obtain(time, time,
                                                gov.tak.platform.ui.MotionEvent.ACTION_DOWN,
                                                80, 20, 0);
                                layoutBase.seekWidgetHit(gEvent, 80, 20);
                                walkMenuWidget(dispatcher, layoutBase,
                                        menuWidget);
                            }

                            // open on items with point
                            final PointF pointF = new PointF(50, 50);
                            for (MapItem mapItem : mapItems) {
                                menuWidget = layoutBase.openMenuOnItem(mapItem,
                                        pointF);
                                assertNotNull(menuWidget);
                                dispatcher.setReferenceItem(mapItem);
                                walkMenuWidget(dispatcher, layoutBase,
                                        menuWidget);
                            }

                            // register a filter
                            // choose menu that has a submenu with one button to test swapping
                            adapter.addFilter("rectangle",
                                    "menus/drawing_shape_line_menu.xml");
                            menuWidget = layoutBase.openMenuOnItem(rectangle);
                            assertNotNull(menuWidget);
                            dispatcher.setReferenceItem(rectangle);
                            walkMenuWidget(dispatcher, layoutBase, menuWidget);
                            adapter.removeFilter("rectangle");

                            // smoke test all menu resources
                            final Marker mapItem = new Marker(
                                    UUID.randomUUID().toString());
                            mapItem.setTitle("generic");
                            AssetManager assetManager = appContext.getAssets();
                            String[] menuResources = assetManager.list("menus");
                            assertNotNull(menuResources);
                            for (String menuResource : menuResources) {
                                mapItem.setMetaString("menu",
                                        "menus/" + menuResource);
                                menuWidget = layoutBase.openMenuOnItem(mapItem);
                                assertNotNull(menuWidget);
                                dispatcher.setReferenceItem(mapItem);
                                walkMenuWidget(dispatcher, layoutBase,
                                        menuWidget);
                            }

                            MapMenuButtonWidget contactButton = null;
                            final String contactIconAsset = "asset://icons/contact.png";
                            final Marker friendly = new Marker(
                                    UUID.randomUUID().toString());
                            friendly.setTitle("friendly");
                            friendly.setType("a-f");
                            menuWidget = layoutBase.openMenuOnItem(friendly);
                            // locate "contact" button by its icon
                            contactButton = findButtonWidget(menuWidget,
                                    contactIconAsset);
                            assertNotNull(contactButton);
                            assertTrue(contactButton.isDisabled());

                            friendly.setMetaString("phoneNumber",
                                    "703-555-1212");
                            menuWidget = layoutBase.openMenuOnItem(friendly);
                            // locate "contact" button by its icon
                            contactButton = findButtonWidget(menuWidget,
                                    contactIconAsset);
                            assertNotNull(contactButton);
                            final MapMenuWidget contactMenu = contactButton
                                    .getSubmenuWidget();
                            assertNotNull(contactMenu);
                            assertFalse(contactButton.isDisabled());
                            int enabledButtonCounter = 0;
                            int disabledButtonCounter = 0;
                            for (MapWidget mapWidget : contactMenu
                                    .getChildWidgets()) {
                                if (mapWidget instanceof MapMenuButtonWidget) {
                                    final MapMenuButtonWidget buttonWidget = (MapMenuButtonWidget) mapWidget;
                                    if (buttonWidget.isDisabled())
                                        ++disabledButtonCounter;
                                    else
                                        ++enabledButtonCounter;
                                }
                            }
                            assertEquals(5, disabledButtonCounter);
                            assertEquals(2, enabledButtonCounter);

                            layoutBase.clearMenu();

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
    }
}
