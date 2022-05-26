
package gov.tak.api.widgets;

import gov.tak.platform.binding.PropertyInfo;

import java.util.List;

public interface IMapMenuButtonWidget extends IRadialButtonWidget {

    PropertyInfo PROPERTY_BACKGROUND = new PropertyInfo("background", IWidgetBackground.class);

    interface OnButtonClickHandler {
        /**
         * Returns `true` if this handler can perform an action on the specified object.
         * @param opaque  The object that is currently the focus of the widget that this button is part of.
         */
        boolean isSupported(Object opaque);

        /**
         * Performs the action when the button is clicked on the object that the widget is focused on.
         * @param opaque  The object that is currently the focus of the widget that this button is part of.
         */
        void performAction(Object opaque);
    }

    IMapMenuWidget getSubmenu();
    void setLayoutWeight(float weight);
    OnButtonClickHandler getOnButtonClickHandler();
    void setOnButtonClickHandler(OnButtonClickHandler o);
    void onButtonClick(Object opaque);

    void setDisabled(boolean b);
    void setDisableActionSwap(boolean disableActionSwap);
    void setDisableIconSwap(boolean disableIconSwap);
    void setSubmenu(IMapMenuWidget mapMenuWidget);
    void setShowSubmenu(boolean showSubmenuPref);
    List<String> getPrefValues();
    List<String> getPrefKeys();
    void setPrefKeys(List<String> keys);
    void setPrefValues(List<String> values);
    void copyAction(IMapMenuButtonWidget other);

    boolean isDisabled();

    float getLayoutWeight();
}
