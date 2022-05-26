
package com.atakmap.android.menu;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

import com.atakmap.android.action.MapAction;
import com.atakmap.android.action.MapActionFactory;
import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.ConfigLoader;
import com.atakmap.android.config.PhraseParser;
import com.atakmap.android.maps.MapData;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.assets.MapAssets;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.AtakMapView;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import gov.tak.api.widgets.IMapMenuButtonWidget;
import gov.tak.api.widgets.IMapMenuWidget;
import gov.tak.api.widgets.IMapWidget;

public class MenuResourceFactory
        implements MapMenuFactory, XmlResourceResolver {

    private static final String TAG = "MenuResourceFactory";

    private final AtakMapView _mapView;
    private final MenuMapAdapter _adapter;

    private final PhraseParser.Parameters _phraseParams;
    private final ConfigEnvironment _baseConfigEnviron;
    private final ConfigLoader<IMapWidget> _menuLoader;

    private final Map<String, MapAction> _mapActions;

    /**
     * A <a href="#{@link}">{@link MapMenuFactory}</a>
     * implementation that resolves asset based XML descriptions
     * of <a href="#{@link}">{@link MapMenuWidget}</a>.
     * @param mapView representing the application's main map view superclass
     * @param mapData associated with the main map view
     * @param mapAssets associated with the main map view
     * @param adapter providing associations between XML configuration and MapItem types
     */
    public MenuResourceFactory(@NonNull AtakMapView mapView,
            @NonNull MapData mapData,
            @NonNull MapAssets mapAssets, @NonNull MenuMapAdapter adapter) {
        _mapView = mapView;

        _adapter = adapter;

        ConfigEnvironment.Builder configBuilder = new ConfigEnvironment.Builder();
        _baseConfigEnviron = configBuilder.setMapAssets(mapAssets).build();

        _menuLoader = new ConfigLoader<>();
        _menuLoader.addFactory(
                "menu",
                new MapMenuWidget.Factory(mapView.getContext(), this,
                        Math.min(mapView.getWidth(),
                                mapView.getHeight()) * 0.475f));

        _phraseParams = new PhraseParser.Parameters();

        // Replaces metadata keys with their corresponding values in the map metadata
        _phraseParams.setResolver('@',
                new PhraseParser.BundleResolver(mapData));

        // If the value is non-empty, return true
        _phraseParams.setResolver('?', new PhraseParser.NotEmptyResolver());

        // If the value is a boolean, invert the boolean value
        // Otherwise return true if the value is empty
        _phraseParams.setResolver('!',
                new PhraseParser.InvertBooleanResolver());

        _mapActions = new HashMap<>();
    }

    private MapMenuWidget createForMap() {

        ConfigEnvironment config = _baseConfigEnviron.buildUpon()
                .setPhraseParserParameters(_phraseParams)
                .build();

        return resolveMenu("menus/default.xml", config);
    }

    private MapMenuWidget createForItemOrSub(@NonNull String menuResource,
            @NonNull MapItem mapItem) {

        // copy so that map item specialization in this method won't affect the class member params
        final PhraseParser.Parameters params = new PhraseParser.Parameters(
                _phraseParams);

        // Replaces metadata keys with their corresponding values in the item metadata
        PhraseParser.BundleResolver itemParser = new PhraseParser.BundleResolver(
                mapItem);
        itemParser.setDefault("removable", true);
        itemParser.setDefault("editable", MapItem.EDITABLE_DEFAULT);
        itemParser.setDefault("movable", MapItem.MOVABLE_DEFAULT);
        params.setResolver('$', itemParser);

        ConfigEnvironment config = _baseConfigEnviron.buildUpon()
                .setPhraseParserParameters(params)
                .build();

        final MapMenuWidget widget = resolveMenu(menuResource, config);
        if (widget != null)
            processMenuButtons(widget, mapItem);
        return widget;
    }

    /**
     * Implements the <a href"#{@link}">{@link MapMenuFactory}</a>
     * interface to supply MapMenuWidget instances as associated
     * with MapItem types.
     * @param item key to factory logic providing MapMenuWidget instances
     * @return fully formed MapMenuWidget instance
     */
    @Override
    public MapMenuWidget create(MapItem item) {
        String xmlResource = null;
        if (null != item) {
            xmlResource = item.getMetaString("menu", null);
            if (null == xmlResource) {
                xmlResource = _adapter.lookup(item);
            }
        }

        return null == xmlResource ? createForMap()
                : createForItemOrSub(xmlResource, item);
    }

    /**
     * Convenience function to resolve asset based XML configurations
     * into fully formed MapAction instances.
     * @param xmlResource path to asset describing a MapAction
     * @return MapAction instance.
     */
    @Override
    public MapAction resolveAction(final String xmlResource) {

        ConfigEnvironment config = _baseConfigEnviron.buildUpon()
                .setPhraseParserParameters(_phraseParams)
                .build();
        return resolveAction(xmlResource, config);
    }

    /**
     * Convenience function to resolve asset based XML configurations
     * into fully formed MapAction instances with custom configurations.
     * @param xmlResource path to asset describing a MapAction
     * @param config container for parser parameters and resolvers
     * @return MapAction instance.
     */
    @Override
    public MapAction resolveAction(final String xmlResource,
            final ConfigEnvironment config) {
        if (null == xmlResource)
            return null;

        // Caching and re-using MapActions because they will be tested against each other for equivalence
        // MapAction interface does not mandate the equals method, so two actions will only be considered
        // equal if they are the same instance. Assume that a given string action means that the marshalled
        // MapAction will be functionally equivalent.
        MapAction mapAction = _mapActions.get(xmlResource);
        if (null == mapAction) {
            try {
                mapAction = MapActionFactory.loadFromConfigResource(xmlResource,
                        config);
                if (null != mapAction)
                    _mapActions.put(xmlResource, mapAction);
            } catch (SAXException | ParserConfigurationException
                    | IllegalArgumentException | IOException e) {
                Log.e(TAG, "error: " + e, e);
            }

        }
        return mapAction;
    }

    /**
     * Convenience function to resolve asset based XML configurations
     * into fully formed MapMenuWidget instances.
     * @param xmlResource path to asset describing a MapMenuWidget
     * @return MapMenuWidget instance
     */
    @Override
    public MapMenuWidget resolveMenu(final String xmlResource) {

        ConfigEnvironment config = _baseConfigEnviron.buildUpon()
                .setPhraseParserParameters(_phraseParams)
                .build();

        return resolveMenu(xmlResource, config);
    }

    /**
     * Convenience function to resolve asset based XML configurations
     * into fully formed MapMenuWidget instances with custom configurations.
     * @param resource path to asset describing a MapMenuWidget
     * @param config container for parser parameters and resolvers
     * @return MapMenuWidget instance
     */
    @Override
    public MapMenuWidget resolveMenu(final String resource,
            final ConfigEnvironment config) {
        IMapWidget widget = null;

        if (resource == null)
            return null;

        try {
            widget = _menuLoader.loadFromConfigResource(resource, config);
        } catch (IOException | ParserConfigurationException | SAXException e) {
            Log.e(TAG, "error: ", e);
        }

        MapMenuWidget menuWidget = null;
        if (widget instanceof MapMenuWidget)
            menuWidget = (MapMenuWidget) widget;

        return menuWidget;
    }

    private void processMenuButtons(IMapMenuWidget widget, MapItem item) {

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(_mapView.getContext());

        for (int buttonIndex = 0; buttonIndex < widget
                .getChildWidgetCount(); ++buttonIndex) {
            IMapWidget wid = widget.getChildWidgetAt(buttonIndex);

            // Expecting all be menu buttons
            if (!(wid instanceof IMapMenuButtonWidget))
                continue;
            IMapMenuButtonWidget menuButton = (IMapMenuButtonWidget) wid;

            //peek at submenu, get list of enabled sub buttons
            IMapMenuWidget submWidget = menuButton.getSubmenu();

            // Widget failed to load
            if (submWidget == null)
                continue;

            // descend into submenu
            processMenuButtons(submWidget, item);

            // store resolved submenu in button widget
            menuButton.setSubmenu(submWidget);

            ArrayList<IMapWidget> subbuttons = (ArrayList<IMapWidget>) submWidget
                    .getChildren();

            ArrayList<IMapWidget> subbuttonsToRem = new ArrayList<>();
            for (IMapWidget wd : subbuttons) {
                if (wd instanceof IMapMenuButtonWidget) {
                    IMapMenuButtonWidget subButton = ((IMapMenuButtonWidget) wd);
                    if (menuButton.isDisabled() || subButton.isDisabled()) {
                        subButton.setDisabled(true);
                        subbuttonsToRem.add(wd);
                    }
                }
            }
            // Remove disabled sub-buttons from processing below
            for (IMapWidget mwtr : subbuttonsToRem) {
                subbuttons.remove(mwtr);
            }

            // Disable button if sub-menu is empty
            if (subbuttons.isEmpty()) {
                menuButton.setState(MapMenuButtonWidget.STATE_DISABLED);
                continue;
            }

            // Set default option to preference value
            List<String> prefKeys = menuButton.getPrefKeys();
            List<String> prefValues = new ArrayList<>();
            for (String k : prefKeys)
                prefValues.add(prefs.getString(k, null));

            //if only one available, swap to that
            if (subbuttons.size() == 1) {
                IMapMenuButtonWidget thesubb = (IMapMenuButtonWidget) subbuttons
                        .get(0);
                menuButton.copyAction(thesubb);
            } else {
                Map<String, Object> submenuMap = item.getMetaMap("submenu_map");
                for (IMapWidget swid : subbuttons) {
                    IMapMenuButtonWidget swidb = (IMapMenuButtonWidget) swid;
                    //swap out button for chosen sub button
                    if (!prefValues.isEmpty()) {
                        // Set default based on matching pref value
                        List<String> subPrefValues = swidb.getPrefValues();
                        if (!subPrefValues.isEmpty() &&
                                subPrefValues.size() == prefValues.size()) {
                            boolean same = true;
                            for (int i = 0; i < prefValues.size(); i++) {
                                String v1 = prefValues.get(i);
                                String v2 = subPrefValues.get(i);
                                if (v1 == null || !v1.equals(v2)) {
                                    same = false;
                                    break;
                                }
                            }
                            if (same) {
                                menuButton.copyAction(swidb);
                                break;
                            }
                        }
                    } else if (submenuMap != null) {
                        // Set default based on matching click action
                        Object submenuAction = submenuMap
                                .get(Integer.toString(buttonIndex));
                        if ((null != submenuAction) &&
                                (submenuAction == swidb
                                        .getOnButtonClickHandler())) {
                            menuButton.copyAction(swidb);
                            break;
                        }
                    }
                }
            }
        }
    }
}
