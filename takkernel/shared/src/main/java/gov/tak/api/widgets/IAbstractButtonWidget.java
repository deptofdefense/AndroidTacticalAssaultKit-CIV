
package gov.tak.api.widgets;

import gov.tak.api.commons.graphics.IIcon;
import gov.tak.platform.binding.PropertyInfo;

public interface IAbstractButtonWidget extends IMapWidget {

    PropertyInfo PROPERTY_STATE = new PropertyInfo("state", StateFlags.class);
    PropertyInfo PROPERTY_ATTRS = new PropertyInfo("attrs", StateFlags.class); // same as state
    PropertyInfo PROPERTY_SELECTABLE = new PropertyInfo("selectable", Boolean.class);
    PropertyInfo PROPERTY_TEXT = new PropertyInfo("text", String.class);
    PropertyInfo PROPERTY_ICON = new PropertyInfo("icon", IIcon.class);

    /**
     * State flags wrapper for property binding
     */
    class StateFlags {

        private int value;

        public StateFlags(int stateValues) {
            this.value = stateValues;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Listener for the state of the icon.    Will be fired when the icon is changed.
     */
    interface OnIconChangedListener<T extends IAbstractButtonWidget> {
        void onButtonIconChanged(T button);
    }

    /**
     * Listener for the state of the background.    Will be fired when the background is changed.
     */
    interface OnBackgroundChangedListener<T extends IAbstractButtonWidget> {
        void onButtonBackgroundChanged(T button);
    }

    /**
     * Listener for the state of the button.    Will be fired when the button state is changed.
     */
    interface OnStateChangedListener<T extends IAbstractButtonWidget> {
        void onButtonStateChanged(T button);
    }

    /**
     * Listener for the state of the text.    Will be fired when the text is changed.
     */
    interface OnTextChangedListener<T extends IAbstractButtonWidget> {
        void onButtonTextChanged(T button);
    }

    void setSelectable(boolean selectable);
    void setWidgetIcon(IIcon icon);
    IIcon getWidgetIcon();
    void setWidgetBackground(IWidgetBackground bg);
    IWidgetBackground getWidgetBackground();
    void setState(int state);
    int getState();

    /**
     * Set the text for the abstract button widget.
     * @param text the text to set for the abstract button widget.
     */
    void setText(final String text);

    String getText();
    void addOnIconChangedListener(OnIconChangedListener<? extends IAbstractButtonWidget> l);
    void removeOnIconChangedListener(OnIconChangedListener<? extends IAbstractButtonWidget> l);
    void addOnBackgroundChangedListener(OnBackgroundChangedListener<? extends IAbstractButtonWidget> l);
    void removeOnBackgroundChangedListener(OnBackgroundChangedListener<? extends IAbstractButtonWidget> l);
    void addOnTextChangedListener(OnTextChangedListener<? extends IAbstractButtonWidget> l);
    void removeOnTextChangedListener(OnTextChangedListener<? extends IAbstractButtonWidget> l);
    void addOnStateChangedListener(OnStateChangedListener<? extends IAbstractButtonWidget> l);
    void removeOnStateChangedListener(OnStateChangedListener<? extends IAbstractButtonWidget> l);
    void onIconChanged();
    void onBackgroundChanged();
    void onStateChanged();
    void onTextChanged();
}
