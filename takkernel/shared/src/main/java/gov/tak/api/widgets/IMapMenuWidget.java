
package gov.tak.api.widgets;

import gov.tak.platform.binding.PropertyInfo;

public interface IMapMenuWidget extends ILayoutWidget {

    PropertyInfo PROPERTY_BUTTON_BG = new PropertyInfo("buttonBg", IWidgetBackground.class);
    PropertyInfo PROPERTY_INNER_RADIUS = new PropertyInfo("buttonRadius", Float.class, -1f);
    PropertyInfo PROPERTY_BUTTON_WIDTH = new PropertyInfo("buttonWidth", Float.class, -1f);
    PropertyInfo PROPERTY_BUTTON_SPAN = new PropertyInfo("buttonSpan", Float.class, -1f);
    PropertyInfo PROPERTY_DRAG_DISMISS = new PropertyInfo("dragDismiss", Boolean.class, false);

    boolean getExplicitSizing();

    float getCoveredAngle();

    void setInnerRadius(float parentRadius);

    void setButtonWidth(float parentWidth);

    void setCoveredAngle(float coveredAngle);

    boolean isClockwiseWinding();

    void setStartAngle(float startingAngle);

    float getStartAngle();

    float getInnerRadius();
}
