package android.graphics;

public final class Typeface {
    public final static int BOLD = 1;
    public final static int BOLD_ITALIC = 2;
    public final static int ITALIC = 3;
    public final static int NORMAL = 4;

    public final static Typeface DEFAULT = new Typeface(false, false);
    public static final Typeface DEFAULT_BOLD = new Typeface(true, false);

    boolean bold;
    boolean italic;

    Typeface(boolean bold, boolean italic) {
        this.bold = bold;
        this.italic = italic;
    }

    public boolean isItalic() {
        return this.italic;
    }

    public boolean isBold() {
        return this.bold;
    }
}
