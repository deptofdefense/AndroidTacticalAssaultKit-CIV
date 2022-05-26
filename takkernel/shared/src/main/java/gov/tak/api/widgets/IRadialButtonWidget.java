package gov.tak.api.widgets;

import gov.tak.platform.binding.PropertyInfo;

public interface IRadialButtonWidget extends IAbstractButtonWidget {

    PropertyInfo PROPERTY_ORIENTATION = new PropertyInfo("orientation", Orientation.class);
    PropertyInfo PROPERTY_BUTTON_SIZE = new PropertyInfo("buttonSize", ButtonSize.class);

    /**
     * Type for buttonSize property binding
     */
    class ButtonSize {

        private final float span;
        private final float width;

        public ButtonSize() {
            this(360f / 8f, 100f);
        }

        public ButtonSize(float span, float width) {
            this.span = span;
            this.width = width;
        }

        public float getWidth() {
            return this.width;
        }

        public float getSpan() {
            return this.span;
        }
    }

    /**
     * Type for orientation property binding
     */
    class Orientation {

        private final float angle;
        private final float radius;

        public Orientation(float angle, float radius) {
            this.angle = angle;
            this.radius = radius;
        }

        public Orientation() {
            this(0f, 100f);
        }

        public float getAngle() {
            return angle;
        }

        public float getRadius() {
            return radius;
        }
    }

    public interface OnSizeChangedListener {
        void onRadialButtonSizeChanged(IRadialButtonWidget button);
    }

    public interface OnOrientationChangedListener {
        void onRadialButtonOrientationChanged(IRadialButtonWidget button);
    }

    void addOnOrientationChangedListener(IRadialButtonWidget.OnOrientationChangedListener l);
    void removeOnOrientationChangedListener(IRadialButtonWidget.OnOrientationChangedListener l);

    public void addOnSizeChangedListener(OnSizeChangedListener l);

    public void removeOnSizeChangedListener(OnSizeChangedListener l);

    float getOrientationAngle();
    float getButtonSpan();
    float getButtonWidth();
    float getOrientationRadius();

    public void setButtonSize(float span, float width);
    public void setOrientation(float angle, float radius);
}