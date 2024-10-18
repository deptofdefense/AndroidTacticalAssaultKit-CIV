package gov.tak.api.commons.graphics;

public final class DisplaySettings {
    private static float relativeScale = 1f;
    private static float fontSize = 14f;
    private static float dpi = 96f;

    public static void setRelativeScaling(float factor) {
        if(factor <= 0f)    throw new IllegalArgumentException();
        relativeScale = factor;
    }

    public static float getRelativeScaling() {
        return relativeScale;
    }

    public static void setDpi(float value) {
        dpi = value;
    }

    public static float getDpi() {
        return dpi;
    }

    public static float getDefaultFontSize() {
        return fontSize;
    }

    public static void setDefaultFontSize(float size) {
        if(size <= 0f)  throw new IllegalArgumentException();
        fontSize = size;
    }
}
