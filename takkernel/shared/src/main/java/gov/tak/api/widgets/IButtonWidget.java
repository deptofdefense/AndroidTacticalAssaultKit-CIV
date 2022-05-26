
package gov.tak.api.widgets;

public interface IButtonWidget extends IAbstractButtonWidget {

    interface OnSizeChangedListener<T extends IButtonWidget>{
        void onButtonSizeChanged(T button);
    }

    void addOnSizeChangedListener(OnSizeChangedListener l);

    void removeOnSizeChangedListener(OnSizeChangedListener l);

    float getButtonWidth();

    float getButtonHeight();

    void onSizeChanged();
}
