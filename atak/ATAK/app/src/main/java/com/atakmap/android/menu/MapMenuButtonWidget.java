
package com.atakmap.android.menu;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.DataParser;
import com.atakmap.android.config.PhraseParser;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.RadialButtonWidget;
import com.atakmap.coremap.log.Log;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

public class MapMenuButtonWidget extends RadialButtonWidget {

    public final static String TAG = "MapMenuButtonWidget";
    private String _submenu;
    private String _showSubmenuPref;
    private String _onClick;
    private boolean _disableActionSwap = false;
    private boolean _disableIconSwap = false;
    private List<String> _prefKeys = new ArrayList<>();
    private List<String> _prefValues = new ArrayList<>();

    // allow for up to 5 selection criteria so that is can do a better job at showing the user 
    // that there might be a submenu active (icon swapping is nice)
    private static String[] possibleSelected = {
            "selected", "selected1", "selected2", "selected3", "selected4"
    };

    public static class Factory extends RadialButtonWidget.Factory {
        @Override
        public MapWidget createFromElem(ConfigEnvironment config,
                Node elemNode) {
            MapMenuButtonWidget button = new MapMenuButtonWidget();
            configAttributes(button, config, elemNode.getAttributes());
            return button;
        }

        protected void configAttributes(MapMenuButtonWidget widget,
                ConfigEnvironment config,
                NamedNodeMap attrs) {
            super.configAttributes(widget, config, attrs);

            // submenu
            widget._submenu = DataParser.parseStringText(
                    attrs.getNamedItem("submenu"), null);

            if (widget._submenu != null) {
                if (config.getPhraseParserParameters() != null) {
                    widget._submenu = PhraseParser.expandPhrase(widget._submenu,
                            config.getPhraseParserParameters());
                }

                widget._disableActionSwap = Boolean.valueOf(DataParser
                        .parseStringText(
                                attrs.getNamedItem("disableSwap"), "false"));
                widget._disableIconSwap = Boolean
                        .valueOf(DataParser
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
                        .getDefaultSharedPreferences(
                                MapView.getMapView().getContext());
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
            widget._onClick = DataParser.parseStringText(
                    attrs.getNamedItem("onClick"), null);
            if (widget._onClick != null
                    && config.getPhraseParserParameters() != null) {
                widget._onClick = PhraseParser.expandPhrase(widget._onClick,
                        config.getPhraseParserParameters());
            }
        }
    }

    /**
     * Obtain the submenu for a menu button widget.
     * @return the submenu in string format.
     */
    public String getSubmenu() {
        if (_showSubmenuPref != null) {
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(
                            MapView.getMapView().getContext());
            try {
                if (!prefs.getBoolean(_showSubmenuPref, false))
                    return null;
            } catch (Exception e) {
                Log.e(TAG, "Failed to convert preference "
                        + _showSubmenuPref + " to boolean", e);
            }
        }
        return _submenu;
    }

    public String getOnClick() {
        return _onClick;
    }

    public void setOnClick(String oc) {
        _onClick = oc;
    }

    public List<String> getPrefKeys() {
        return _prefKeys;
    }

    public List<String> getPrefValues() {
        return _prefValues;
    }

    public void setDisabled(boolean disabled) {
        if (disabled)
            setState(getState() | STATE_DISABLED);
        else
            setState(getState() & ~STATE_DISABLED);
    }

    public boolean isDisabled() {
        return (getState() & STATE_DISABLED) == STATE_DISABLED;
    }

    public void copyAction(MapMenuButtonWidget other) {
        if (other != null) {
            if (!_disableIconSwap)
                setIcon(other.getIcon());
            if (!_disableActionSwap)
                setOnClick(other.getOnClick());
        }
    }

}
