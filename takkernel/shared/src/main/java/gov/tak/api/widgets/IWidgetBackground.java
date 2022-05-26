
package gov.tak.api.widgets;

public interface IWidgetBackground {
    int getColor(int state);

    IWidgetBackground copy();
}
