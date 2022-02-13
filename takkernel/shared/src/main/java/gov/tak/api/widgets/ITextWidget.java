package gov.tak.api.widgets;

import gov.tak.api.commons.graphics.Font;
import gov.tak.api.commons.graphics.TextFormat;

public interface ITextWidget extends IMapWidget {
    interface OnTextChangedListener {
        void onTextWidgetTextChanged(ITextWidget widget);
    }

    interface OnColorChangedListener {
        void onTextWidgetColorChanged(ITextWidget widget);
    }

    interface OnHasBackgroundChangedListener {
        void onTextWidgetHasBackgroundChanged(ITextWidget widget);
    }

    void addOnTextChangedListener(OnTextChangedListener l);
    void removeOnTextChangedListener(OnTextChangedListener l);

    void addOnColorChangedListener(OnColorChangedListener l);
    void removeOnColorChangedListener(OnColorChangedListener l);

    void addOnHasBackgroundChangedListener(OnHasBackgroundChangedListener l);

    void removeOnHasBackgroundChangedListener(OnHasBackgroundChangedListener l) ;

    TextFormat getWidgetTextFormat();
    String getText();
    int getBackground();
    int[] getColors();

}