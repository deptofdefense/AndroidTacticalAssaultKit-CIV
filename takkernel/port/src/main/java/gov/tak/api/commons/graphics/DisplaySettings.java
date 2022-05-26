package gov.tak.api.commons.graphics;

public final class DisplaySettings {
    private static float relativeScale = 1f;
    private static float fontSize = 14f;

    public static void setRelativeScaling(float factor) {
        if(factor <= 0f)    throw new IllegalArgumentException();
        relativeScale = factor;
    }

    public static float getRelativeScaling() {
        return relativeScale;
    }

    public static float getDefaultFontSize() {
        return fontSize;
    }

    public static void setDefaultFontSize(float size) {
        if(size <= 0f)  throw new IllegalArgumentException();
        fontSize = size;
    }
}
