
package com.atakmap.android.menu;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.action.MapAction;
import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.DataParser;
import com.atakmap.android.config.PhraseParser;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.RadialButtonWidget;
import com.atakmap.coremap.log.Log;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

public class MapMenuButtonWidget extends RadialButtonWidget {

    public final static String TAG = "MapMenuButtonWidget";
    private final Context _context;
    private MapMenuWidget _submenuWidget;
    private String _showSubmenuPref;
    private MapAction _onClickAction;
    private boolean _disableActionSwap = false;
    private boolean _disableIconSwap = false;
    private List<String> _prefKeys = new ArrayList<>();
    private List<String> _prefValues = new ArrayList<>();
    private float _layoutWeight = 1f;

    // allow for up to 5 selection criteria so that is can do a better job at showing the user 
    // that there might be a submenu active (icon swapping is nice)
    private static final String[] possibleSelected = {
            "selected", "selected1", "selected2", "selected3", "selected4"
    };

    /**
     * Button widget children of radial menu widgets.
     * @param context application <a href=#{@link}>{@link Context}</a>
     */
    public MapMenuButtonWidget(Context context) {
        this._context = context;
    }

    /**
     * Get preference keys associated with the button widget
     * @return list of String preference keys
     */
    public List<String> getPrefKeys() {
        return _prefKeys;
    }

    /**
     * Set preference keys associated with the button widget
     * @param keys listing string preferences
     */
    public void setPrefKeys(List<String> keys) {
        _prefKeys = keys;
    }

    /**
     * Get preference values associated with the button widget
     * @return list of String preference values
     */
    public List<String> getPrefValues() {
        return _prefValues;
    }

    /**
     * Set preference values associated with the button widget
     * @param values listing string preferences
     */
    public void setPrefValues(List<String> values) {
        _prefValues = values;
    }

    /**
     * Disable or enable the state of the button
     * @param disabled if the button should be inactive
     */
    public void setDisabled(boolean disabled) {
        if (disabled)
            setState(getState() | STATE_DISABLED);
        else
            setState(getState() & ~STATE_DISABLED);
    }

    /**
     * Get whether the state of the button includes a disabled flag
     * @return true if the button is disabled
     */
    public boolean isDisabled() {
        return (getState() & STATE_DISABLED) == STATE_DISABLED;
    }

    void copyAction(MapMenuButtonWidget other) {
        if (other != null) {
            if (!_disableIconSwap)
                setIcon(other.getIcon());
            if (!_disableActionSwap)
                setOnClickAction(other.getOnClickAction());
        }
    }

    /**
     * Sets an onClick handler to a MapAction instance
     * @param mapAction action to execute upon click
     */
    public void setOnClickAction(MapAction mapAction) {
        _onClickAction = mapAction;
    }

    /**
     * Gets the button's onClick handler object instance
     * @return handler that implements MapAction
     */
    public MapAction getOnClickAction() {
        return _onClickAction;
    }

    /**
     * Set submenu widget to provided fully formed MapMenuWidget instance.
     * @param submenuWidget to be associated with the button
     */
    public void setSubmenuWidget(MapMenuWidget submenuWidget) {
        _submenuWidget = submenuWidget;
    }

    /**
     * Gets the current submenu widget for the button.
     * @return current submenu widget or null if unassigned
     * or submenu preference is false
     */
    public MapMenuWidget getSubmenuWidget() {
        if (_showSubmenuPref != null) {
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(_context);
            try {
                if (!prefs.getBoolean(_showSubmenuPref, false))
                    return null;
            } catch (Exception e) {
                Log.e(TAG, "Failed to convert preference "
                        + _showSubmenuPref + " to boolean", e);
            }
        }
        return _submenuWidget;
    }

    /**
     * Gets the layout weight for the button. Buttons are scaled
     * along their arc dimension in accordance with their weights.
     * @return the dimensionless weight for the button
     */
    public float getLayoutWeight() {
        return _layoutWeight;
    }

    /**
     * Set the layout weight for the button to determine is span
     * dimension upon layout. Weights are positive dimensionless values
     * that modify relative layout size in their parent radial menu.
     * @param weight to be associated with the button instance.
     */
    public void setLayoutWeight(float weight) {
        _layoutWeight = weight;
    }

    static class Factory extends RadialButtonWidget.Factory {
        private final Context _context;
        private final XmlResourceResolver _resolver;

        Factory(Context context, XmlResourceResolver resolver) {
            this._context = context;
            this._resolver = resolver;
        }

        @Override
        public MapWidget createFromElem(ConfigEnvironment config,
                Node elemNode) {
            MapMenuButtonWidget button = new MapMenuButtonWidget(_context);
            configAttributes(button, config, elemNode.getAttributes());
            return button;
        }

        void configAttributes(MapMenuButtonWidget widget,
                ConfigEnvironment config,
                NamedNodeMap attrs) {
            super.configAttributes(widget, config, attrs);

            // submenu
            String submenu = DataParser.parseStringText(
                    attrs.getNamedItem("submenu"), null);

            if (null != submenu) {
                if (null != config.getPhraseParserParameters()) {
                    submenu = PhraseParser.expandPhrase(submenu,
                            config.getPhraseParserParameters());
                }

                widget._submenuWidget = _resolver.resolveMenu(submenu, config);

                widget._disableActionSwap = Boolean.parseBoolean(DataParser
                        .parseStringText(
                                attrs.getNamedItem("disableSwap"), "false"));
                widget._disableIconSwap = Boolean.parseBoolean(DataParser
                        .parseStringText(
                                attrs.getNamedItem("disableIconSwap"),
                                "false"));
                widget._showSubmenuPref = DataParser.parseStringText(
                        attrs.getNamedItem("showSubmenuPref"), null);
            }

            String prefKey = DataParser.parseStringText(
                    attrs.getNamedItem("prefKey"), null);

            String prefValue = DataParser.parseStringText(
                    attrs.getNamedItem("prefValue"), null);

            // Set selected based on matching preference key and value
            if (prefKey != null && prefValue != null) {
                SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(_context);
                String actualValue = prefs.getString(prefKey, null);
                if (prefValue.equals(actualValue))
                    widget.setState(widget.getState() | STATE_SELECTED);
            }

            // Add preferences keys
            widget._prefKeys.clear();
            if (prefKey != null) {
                // Add single preference key
                widget._prefKeys.add(prefKey);
            } else {
                // Add all preferences keys (arbitrary max is 10)
                for (int i = 1; i < 10; i++) {
                    prefKey = DataParser.parseStringText(
                            attrs.getNamedItem("prefKey" + i), null);
                    if (prefKey != null)
                        widget._prefKeys.add(prefKey);
                    else
                        break;
                }
            }

            // Add preferences values
            widget._prefValues.clear();
            if (prefValue != null) {
                // Add single preference value
                widget._prefValues.add(prefValue);
            } else {
                // Add all preferences values (arbitrary max is 10)
                for (int i = 1; i < 10; i++) {
                    prefValue = DataParser.parseStringText(
                            attrs.getNamedItem("prefValue" + i), null);
                    if (prefValue != null)
                        widget._prefValues.add(prefValue);
                    else
                        break;
                }
            }

            // selected

            for (String s : possibleSelected) {
                String selectedText = DataParser.parseStringText(
                        attrs.getNamedItem(s), null);
                if (selectedText != null) {
                    selectedText = PhraseParser.expandPhrase(selectedText,
                            config.getPhraseParserParameters());
                    try {
                        boolean selected = Boolean.parseBoolean(selectedText);
                        if (selected) {
                            widget.setState(widget.getState() | STATE_SELECTED);
                        }
                    } catch (Exception ignored) {
                        // ignore
                    }
                }
            }

            // disabled
            String disabledText = DataParser.parseStringText(
                    attrs.getNamedItem("disabled"), "false");
            disabledText = PhraseParser.expandPhrase(disabledText,
                    config.getPhraseParserParameters());

            try {
                boolean disabled = Boolean.parseBoolean(disabledText);
                if (disabled)
                    widget.setDisabled(true);
            } catch (Exception ex) {
                Log.e(TAG, "error: ", ex);
            }

            String dependsOn = DataParser.parseStringText(
                    attrs.getNamedItem("dependsOnCapability"), "");
            if (dependsOn.length() > 0) {
                if (!MenuCapabilities.contains(dependsOn)) {
                    widget.setDisabled(true);
                    widget.setSelectable(false);
                    widget.setIcon(
                            new com.atakmap.android.widgets.WidgetIcon.Builder()
                                    .build());
                }
            }

            // onClick
            String onClick = DataParser.parseStringText(
                    attrs.getNamedItem("onClick"), null);
            if (null != onClick
                    && config.getPhraseParserParameters() != null) {
                onClick = PhraseParser.expandPhrase(onClick,
                        config.getPhraseParserParameters());
            }
            if (null != onClick) {
                widget._onClickAction = _resolver.resolveAction(onClick);
            }
        }
    }
}
