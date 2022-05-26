
package gov.tak.platform.widgets;

import gov.tak.api.util.Visitor;
import gov.tak.api.widgets.IMapMenuWidget;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;

import gov.tak.api.widgets.IMapMenuButtonWidget;
import gov.tak.api.widgets.IWidgetBackground;
import gov.tak.platform.binding.PropertyInfo;

public class MapMenuButtonWidget extends RadialButtonWidget implements IMapMenuButtonWidget {

    public final static String TAG = "MapMenuButtonWidget";

    private IMapMenuWidget _submenuWidget;
    private boolean _showSubmenuPref;
    private boolean _disableActionSwap = false;
    private boolean _disableIconSwap = false;
    private OnButtonClickHandler _onClickAction = null;
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
     */
    public MapMenuButtonWidget() {}

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

    public void copyAction(IMapMenuButtonWidget other) {
        if (other != null) {
            if (!_disableIconSwap)
                setWidgetIcon(other.getWidgetIcon());
            if (!_disableActionSwap)
                setOnButtonClickHandler(other.getOnButtonClickHandler());
        }
    }

    /**
     * Sets the button click handler
     * @param mapAction action to execute upon click
     */
    @Override
    public void setOnButtonClickHandler(OnButtonClickHandler mapAction) {
        _onClickAction = mapAction;
    }

    /**
     * Gets the button's onClick handler object instance
     * @return button click handler
     */
    @Override
    public OnButtonClickHandler getOnButtonClickHandler() {
        return _onClickAction;
    }

    @Override
    public void onButtonClick(Object opaque) {
        if(_onClickAction != null && _onClickAction.isSupported(opaque)) {
            _onClickAction.performAction(opaque);
        }
    }

    /**
     * Set submenu widget to provided fully formed MapMenuWidget instance.
     * @param submenuWidget to be associated with the button
     */
    @Override
    public void setSubmenu(IMapMenuWidget submenuWidget) {
        _submenuWidget = submenuWidget;
    }

    /**
     * Gets the current submenu widget for the button.
     * @return current submenu widget or null if unassigned
     * or submenu preference is false
     */
    @Override
    public IMapMenuWidget getSubmenu() {
        return _showSubmenuPref ? _submenuWidget : null;
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

    public void setDisableActionSwap(boolean disableActionSwap) {
        _disableActionSwap = disableActionSwap;
    }
    public void setDisableIconSwap(boolean disableIconSwap) {
        _disableIconSwap = disableIconSwap;
    }

    @Override
    public void setShowSubmenu(boolean showSubmenuPref) {
        _showSubmenuPref = showSubmenuPref;
    }

    @Override
    public void visitPropertyInfos(Visitor<PropertyInfo> visitor) {
        super.visitPropertyInfos(visitor);
        visitor.visit(PROPERTY_BACKGROUND);
    }

    @Override
    protected void applyPropertyChange(String propertyName, Object newValue) {
        if (PROPERTY_BACKGROUND.canAssignValue(propertyName, newValue)) {
            if (newValue != null)
                this.setWidgetBackground((IWidgetBackground) newValue);
            else
                this.setWidgetBackground(null);
        } else {
            super.applyPropertyChange(propertyName, newValue);
        }

    }
}
