package gov.tak.api.widgets;

public interface IShapeWidget extends IMapWidget {

    int getStrokeColor();
    float getStrokeWeight();

    public interface OnStrokeColorChangedListener {
        void onStrokeColorChanged(IShapeWidget shape);
    }
    void addOnStrokeColorChangedListener(OnStrokeColorChangedListener listener);
    void removeOnStrokeColorChangedListener(OnStrokeColorChangedListener listener);

}