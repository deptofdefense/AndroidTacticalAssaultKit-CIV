package gov.tak.api.widgets;

import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.annotations.DeprecatedApi;

/**
 * @deprecated Replaced by {@link IScaleWidget2}
 */
@Deprecated
@DeprecatedApi(since="4.5", removeAt = "4.8", forRemoval = true)
public interface IScaleWidget extends IShapeWidget {
    interface OnTextChangedListener {
        void onScaleTextChanged(IScaleWidget widget);
    }

    void removeOnTextChangedListener(OnTextChangedListener listener);
    void addOnTextChangedListener(OnTextChangedListener listener);

    MapTextFormat getTextFormat();
    String getText();

}
