package android.graphics;

public class Color {
    public final static int WHITE = java.awt.Color.WHITE.getRGB();
    public static final int RED = java.awt.Color.RED.getRGB();
    public static final int BLUE = java.awt.Color.BLUE.getRGB();
    public static final int GREEN = java.awt.Color.GREEN.getRGB();
    public static final int CYAN = java.awt.Color.CYAN.getRGB();
    public static final int YELLOW = java.awt.Color.YELLOW.getRGB();
    public static final int DKGRAY = java.awt.Color.DARK_GRAY.getRGB();
    public static final int GRAY = java.awt.Color.GRAY.getRGB();
    public static final int LTGRAY = java.awt.Color.LIGHT_GRAY.getRGB();
    public static final int MAGENTA = java.awt.Color.MAGENTA.getRGB();

    public static int red(int argb) {
        return (argb>>16)&0xFF;
    }

    public static int green(int argb) {
        return (argb>>8)&0xFF;
    }

    public static int blue(int argb) {
        return argb&0xFF;
    }

    public static int alpha(int argb) {
        return (argb>>24)&0xFF;
    }

    public static int argb(int a, int r, int g, int b) {
        a = (a&0xFF)<<24;
        r = (r&0xFF)<<16;
        g = (g&0xFF)<<8;
        b = b&0xFF;
        return a|r|g|b;
    }
}
