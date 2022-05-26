
package com.atakmap.android.menu;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import com.atakmap.android.action.MapAction;
import com.atakmap.android.config.ConfigEnvironment;
import com.atakmap.android.config.DataParser;
import com.atakmap.android.config.PhraseParser;
import com.atakmap.android.widgets.RadialButtonWidget;
import com.atakmap.coremap.log.Log;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.annotation.DeprecatedApi;
import gov.tak.api.commons.graphics.IIcon;
import gov.tak.api.widgets.IMapMenuButtonWidget;
import gov.tak.api.widgets.IMapMenuWidget;
import gov.tak.api.widgets.IMapWidget;
import gov.tak.api.widgets.IWidgetBackground;

public class MapMenuButtonWidget extends RadialButtonWidget
        implements IMapMenuButtonWidget {

    public final static String TAG = "MapMenuButtonWidget";

    /**
     * Radial menu container containing buttons and controlling layout.
     */
    public interface OnBackingColorChangedListener {
        void onBackingColorChanged(MapMenuButtonWidget layout);
    }

    private final Context _context;
    private final IMapMenuButtonWidget _impl;

    private boolean _isBackButton;
    private int _startAlpha = 10;
    private int _endAlpha = 255;
    private long _delayStartMS;
    private int _delayDurationMS;
    private final ConcurrentLinkedQueue<OnBackingColorChangedListener> _onBackingColorChanged = new ConcurrentLinkedQueue<>();

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
        _context = context;
        _impl = new gov.tak.platform.widgets.MapMenuButtonWidget();
    }

    /**
     * Get preference keys associated with the button widget
     * @return list of String preference keys
     */
    @Override
    public List<String> getPrefKeys() {
        return _impl.getPrefKeys();
    }

    /**
     * Set preference keys associated with the button widget
     * @param keys listing string preferences
     */
    @Override
    public void setPrefKeys(List<String> keys) {
        _impl.setPrefKeys(keys);
    }

    /**
     * Get preference values associated with the button widget
     * @return list of String preference values
     */
    @Override
    public List<String> getPrefValues() {
        return _impl.getPrefValues();
    }

    /**
     * Set preference values associated with the button widget
     * @param values listing string preferences
     */
    @Override
    public void setPrefValues(List<String> values) {
        _impl.setPrefValues(values);
    }

    /**
     * Disable or enable the state of the button
     * @param disabled if the button should be inactive
     */
    @Override
    public void setDisabled(boolean disabled) {
        _impl.setDisabled(disabled);
    }

    /**
     * Get whether the state of the button includes a disabled flag
     * @return true if the button is disabled
     */
    @Override
    public boolean isDisabled() {
        return _impl.isDisabled();
    }

    @Override
    public void copyAction(IMapMenuButtonWidget other) {
        _impl.copyAction(other);
    }

    @Override
    public void setWidgetIcon(IIcon icon) {
        _impl.setWidgetIcon(icon);
    }

    @Override
    public IIcon getWidgetIcon() {
        return _impl.getWidgetIcon();
    }

    @Override
    public void setWidgetBackground(IWidgetBackground bg) {
        _impl.setWidgetBackground(bg);
    }

    @Override
    public IWidgetBackground getWidgetBackground() {
        return _impl.getWidgetBackground();
    }

    @Override
    public void setState(int state) {
        _impl.setState(state);
        super.setState(state);
    }

    @Override
    public int getState() {
        return _impl.getState();
    }

    @Override
    public void setSelectable(boolean selectable) {
        _impl.setSelectable(selectable);
    }

    @Override
    public OnButtonClickHandler getOnButtonClickHandler() {
        return _impl.getOnButtonClickHandler();
    }

    @Override
    public void setOnButtonClickHandler(OnButtonClickHandler o) {
        _impl.setOnButtonClickHandler(o);
    }

    @Override
    public void onButtonClick(Object opaque) {
        _impl.onButtonClick(opaque);
    }

    /**
     * Gets the button's onClick handler object instance
     * @return handler that implements MapAction
     *
     * @deprecated  Use {@link IMapMenuButtonWidget#getOnButtonClickHandler()} or
     *              {@link IMapMenuButtonWidget#onButtonClick(Object)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public MapAction getOnClickAction() {
        final OnButtonClickHandler onClickAction = _impl
                .getOnButtonClickHandler();
        return (onClickAction instanceof MapActionAdapter)
                ? ((MapActionAdapter) onClickAction).impl
                : null;
    }

    /**
     * Sets an onClick handler to a MapAction instance
     * @param mapAction action to execute upon click
     *
     * @deprecated Use {@link IMapMenuButtonWidget#setOnButtonClickHandler(OnButtonClickHandler)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void setOnClickAction(MapAction mapAction) {
        _impl.setOnButtonClickHandler(new MapActionAdapter(mapAction));
    }

    /**
     * Gets the current submenu widget for the button.
     * @return current submenu widget or null if unassigned
     * or submenu preference is false
     *
     * @deprecated Use {@link IMapMenuButtonWidget#getSubmenu()}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public MapMenuWidget getSubmenuWidget() {
        final IMapMenuWidget submenu = getSubmenu();
        return (submenu instanceof MapMenuWidget) ? (MapMenuWidget) submenu
                : null;
    }

    @Override
    public IMapMenuWidget getSubmenu() {
        return _impl.getSubmenu();
    }

    /**
     * Gets the layout weight for the button. Buttons are scaled
     * along their arc dimension in accordance with their weights.
     * @return the dimensionless weight for the button
     */
    @Override
    public float getLayoutWeight() {
        return _impl.getLayoutWeight();
    }

    /**
     * Set the layout weight for the button to determine is span
     * dimension upon layout. Weights are positive dimensionless values
     * that modify relative layout size in their parent radial menu.
     * @param weight to be associated with the button instance.
     */
    @Override
    public void setLayoutWeight(float weight) {
        _impl.setLayoutWeight(weight);
    }

    @Override
    public void setDisableActionSwap(boolean disableActionSwap) {
        _impl.setDisableActionSwap(disableActionSwap);
    }

    @Override
    public void setDisableIconSwap(boolean disableIconSwap) {
        _impl.setDisableIconSwap(disableIconSwap);
    }

    /**
     * Set submenu widget to provided fully formed MapMenuWidget instance.
     * @param submenuWidget to be associated with the button
     *
     * @deprecated Use {@link IMapMenuButtonWidget#setSubmenu(IMapMenuWidget)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void setSubmenuWidget(MapMenuWidget submenuWidget) {
        setSubmenu(submenuWidget);
    }

    @Override
    public void setSubmenu(IMapMenuWidget mapMenuWidget) {
        _impl.setSubmenu(mapMenuWidget);
    }

    public void setShowSubmenuPref(String showSubmenuPref) {
        setShowSubmenu(showSubmenu(_context, showSubmenuPref));
    }

    @Override
    public void setShowSubmenu(boolean showSubmenu) {
        _impl.setShowSubmenu(showSubmenu);
    }

    /**
     * How long to delay showing widget, to animate menu
     *
     * @param milliSeconds how long to delay
     */
    public void delayShow(int milliSeconds) {
        _delayDurationMS = milliSeconds;
        _delayStartMS = SystemClock.elapsedRealtime();
        onBackingColorChanged();
    }

    public boolean isDelaying() {
        return _delayStartMS > 0;
    }

    /**
     * Get menu widget alpha based on delay value
     *
     * @return alpha value
     */
    public float getDelayAlpha() {
        if (isDelaying()) {
            long curTime = SystemClock.elapsedRealtime();
            if (curTime >= _delayStartMS + _delayDurationMS) {
                _startAlpha = _endAlpha;
                _delayStartMS = _delayDurationMS = 0;
            } else {
                float t = (float) (curTime - _delayStartMS)
                        / _delayDurationMS;
                return (_startAlpha * (1 - t)) + (_endAlpha * t);
            }
        }
        return _endAlpha;
    }

    public boolean isBackButton() {
        return _isBackButton;
    }

    /**
     * Set this as a back button in a submenu
     *
     * @param _isBackButton back button state
     */
    public void setIsBackButton(boolean _isBackButton) {
        this._isBackButton = _isBackButton;
    }

    static boolean showSubmenu(Context context, String prefKey) {
        if (prefKey != null) {
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(context);
            try {
                if (!prefs.getBoolean(prefKey, false))
                    return false;
            } catch (Exception e) {
                Log.e(TAG, "Failed to convert preference "
                        + prefKey + " to boolean", e);
            }
        }
        return true;
    }

    static class Factory extends RadialButtonWidget.Factory {
        private final Context _context;
        private final XmlResourceResolver _resolver;

        Factory(Context context, XmlResourceResolver resolver) {
            this._context = context;
            this._resolver = resolver;
        }

        @Override
        public IMapWidget createFromElem(ConfigEnvironment config,
                Node elemNode) {
            IMapMenuButtonWidget button = new MapMenuButtonWidget(_context);
            configAttributes(button, config, elemNode.getAttributes());
            return button;
        }

        void configAttributes(IMapMenuButtonWidget widget,
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

                widget.setSubmenu(_resolver.resolveMenu(submenu, config));

                widget.setDisableActionSwap(Boolean.parseBoolean(DataParser
                        .parseStringText(
                                attrs.getNamedItem("disableSwap"), "false")));
                widget.setDisableIconSwap(Boolean.parseBoolean(DataParser
                        .parseStringText(
                                attrs.getNamedItem("disableIconSwap"),
                                "false")));
                final String showSubmenuPref = DataParser.parseStringText(
                        attrs.getNamedItem("showSubmenuPref"), null);
                widget.setShowSubmenu(showSubmenu(_context, showSubmenuPref));
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
            widget.getPrefKeys().clear();
            if (prefKey != null) {
                // Add single preference key
                widget.getPrefKeys().add(prefKey);
            } else {
                // Add all preferences keys (arbitrary max is 10)
                for (int i = 1; i < 10; i++) {
                    prefKey = DataParser.parseStringText(
                            attrs.getNamedItem("prefKey" + i), null);
                    if (prefKey != null)
                        widget.getPrefKeys().add(prefKey);
                    else
                        break;
                }
            }

            // Add preferences values
            widget.getPrefValues().clear();
            if (prefValue != null) {
                // Add single preference value
                widget.getPrefValues().add(prefValue);
            } else {
                // Add all preferences values (arbitrary max is 10)
                for (int i = 1; i < 10; i++) {
                    prefValue = DataParser.parseStringText(
                            attrs.getNamedItem("prefValue" + i), null);
                    if (prefValue != null)
                        widget.getPrefValues().add(prefValue);
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
                    widget.setWidgetIcon(
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
                widget.setOnButtonClickHandler(
                        new MapActionAdapter(_resolver.resolveAction(onClick)));
            }
        }
    }

    protected void onBackingColorChanged() {
        for (OnBackingColorChangedListener l : _onBackingColorChanged) {
            l.onBackingColorChanged(this);
        }
    }
}
